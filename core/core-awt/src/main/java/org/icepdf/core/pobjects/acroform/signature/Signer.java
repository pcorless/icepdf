package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.cms.CMSException;
import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.out;

public class Signer {

    public static int PLACEHOLDER_PADDING_LENGTH = 30000;
    public static int PLACEHOLDER_BYTE_OFFSET_LENGTH = 9;

    public static void signDocument(Document document, File outputFile, SignatureDictionary signatureDictionary) {
        try (final RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");) {
            Library library = document.getCatalog().getLibrary();
            int signatureDictionaryOffset = library.getOffset(signatureDictionary.getPObjectReference());

            // write the signature object as a raw string so that it can be updated with offsets and certs
            StateManager stateManager = document.getStateManager();
            SecurityManager securityManager = document.getSecurityManager();
            CrossReferenceRoot crossReferenceRoot = stateManager.getCrossReferenceRoot();

            // write out the securityDictionary, so we can make the necessary edits for setting up signing
            String contentsDump = writeSignatureDictionary(crossReferenceRoot, securityManager, signatureDictionary);
            int signatureDictionaryLength = contentsDump.length();

            final FileChannel fc = raf.getChannel();
            fc.position(0);
            long fileLength = fc.size();

            // find byte offset of the start of content hex string
            int firstStart = 0;
            String contents = "/Contents <";
            int firstOffset = signatureDictionaryOffset + contentsDump.indexOf(contents) + contents.length();
            int secondStart = firstOffset + PLACEHOLDER_PADDING_LENGTH;
            int secondOffset = (int) fileLength - secondStart; // just awkward, but should 32bit max.

            // find length of the new array.
            List byteRangeArray = List.of(firstStart, firstOffset, secondStart, secondOffset);
            String byteRangeDump = writeByteOffsets(crossReferenceRoot, securityManager, byteRangeArray);
            // adjust the second start, we will make sure the padding zeros on the /contents hex string adjust
            // accordingly
            int byteRangeLength = byteRangeDump.length() - PLACEHOLDER_BYTE_OFFSET_LENGTH;
            // we want to make sure the /content <hex> value has an even length
            // if odd we need to account for the extra byte,  negate the secondStart and add a space the byteRangeDump
            boolean oddOffsetCompensation = false;
            if (byteRangeLength % 2 != 0) {
                byteRangeLength += 1;
                oddOffsetCompensation = true;
            }
            secondStart -= byteRangeLength;

            secondOffset = (int) fileLength - secondStart;
            byteRangeArray = List.of(firstStart, firstOffset, secondStart, secondOffset);
            byteRangeDump = writeByteOffsets(crossReferenceRoot, securityManager, byteRangeArray);
            // update /ByteRange
            if (oddOffsetCompensation) {
                // newton's law, remove a byte then you need to add a byte
                byteRangeDump = byteRangeDump.concat(" ");
            }
            contentsDump = contentsDump.replace("/ByteRange [0 0 0 0]", "/ByteRange " + byteRangeDump);

            // update /contents with adjusted length for byteRange offset
            Pattern pattern = Pattern.compile("/Contents <([A-Fa-f0-9]+)>");
            Matcher matcher = pattern.matcher(contentsDump);
            contentsDump = matcher.replaceFirst("/Contents <" + generateContentsPlaceholder(byteRangeLength) + ">");

            // write the altered signature dictionary
            fc.position(signatureDictionaryOffset);
            fc.write(ByteBuffer.wrap(contentsDump.getBytes()));

            // digest the file creating the content signature
            ByteBuffer preContent = ByteBuffer.allocateDirect(firstOffset - firstStart);
            ByteBuffer postContent = ByteBuffer.allocateDirect(secondStart + secondOffset);
            fc.position(firstStart);
            fc.read(preContent);
            fc.position(secondStart);
            fc.read(postContent);
            byte[] combined = new byte[preContent.limit() + postContent.limit()];
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            preContent.flip();
            postContent.flip();
            buffer.put(preContent);
            buffer.put(postContent);

            byte[] signature = signatureDictionary.getSignedData(combined);
            Pkcs7Validator pkcs7Validator = new Pkcs7Validator(null);
            ASN1Sequence signedData = pkcs7Validator.captureSignedData(signature);
            out.println(signedData);

            String hexContent = HexStringObject.encodeHexString(signature);
            if (hexContent.length() < PLACEHOLDER_PADDING_LENGTH) {
                int padding = PLACEHOLDER_PADDING_LENGTH - byteRangeLength - hexContent.length();
                hexContent = hexContent + "0".repeat(Math.max(0, padding));
            } else {
                throw new IllegalStateException("signature content is larger than placeholder");
            }
            // update /contents with signature
            contentsDump = matcher.replaceFirst("/Contents <" + hexContent + ">");

            HexStringObject hexStringObject = new HexStringObject(hexContent);
            // make sure we don't lose any bytes converting the string in the raw.\
            String literalString = hexStringObject.getLiteralString();
            byte[] cmsData = Utils.convertByteCharSequenceToByteArray(literalString);
            out.println(signature + " " + cmsData);

            // write the altered signature dictionary
            fc.position(signatureDictionaryOffset);
            int count = fc.write(ByteBuffer.wrap(contentsDump.getBytes()));

            // make sure the object length didn't change
            if (count != signatureDictionaryLength) {
                System.out.println("BAM! " + count + " " + signatureDictionaryLength);
                throw new IllegalStateException();
            }

        } catch (CrossReferenceStateException e) {
            throw new RuntimeException(e);
        } catch (ObjectStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CMSException e) {
            throw new RuntimeException(e);
        } catch (SignatureIntegrityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String writeSignatureDictionary(CrossReferenceRoot crossReferenceRoot,
                                                  SecurityManager securityManager,
                                                  SignatureDictionary signatureDictionary) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CountingOutputStream objectOutput = new CountingOutputStream(byteArrayOutputStream);
        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, objectOutput, 0l);
        writer.initializeWriters();
        writer.writePObject(new PObject(signatureDictionary, signatureDictionary.getPObjectReference()));
        String objectDump = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        objectOutput.close();
        return objectDump;
    }

    public static String writeByteOffsets(CrossReferenceRoot crossReferenceRoot, SecurityManager securityManager,
                                          List offsets) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CountingOutputStream objectOutput = new CountingOutputStream(byteArrayOutputStream);
        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, objectOutput, 0l);
        writer.initializeWriters();
        writer.writeValue(new PObject(offsets, new Reference(1, 0)), objectOutput);
        String objectDump = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        objectOutput.close();
        return objectDump;
    }

    public static String generateContentsPlaceholder() {
        return generateContentsPlaceholder(0);
    }

    public static String generateContentsPlaceholder(int reductionAdjustment) {
        int capacity = PLACEHOLDER_PADDING_LENGTH - reductionAdjustment;
        StringBuilder paddedZeros = new StringBuilder(capacity);
        paddedZeros.append("0".repeat(Math.max(0, capacity)));
        return paddedZeros.toString();
    }


}
