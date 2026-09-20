/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;
import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.icepdf.core.pobjects.acroform.SignatureDictionary.BYTE_RANGE_PADDING_LENGTH;

/**
 * DocumentSigner does the awkward task of populating a SignatureDictionary's /content and /ByteRange entries with
 * valid values.  The updated SignatureDictionary is inserted back into the file using he same byte footprint but
 * contains a singed digest and respective offset of the signed content.
 */
public class DocumentSigner {

    public static int PLACEHOLDER_PADDING_LENGTH = 30000;

    /** How far past the signature dictionary's start to look for its endobj. */
    private static final int MAX_OBJECT_SCAN = PLACEHOLDER_PADDING_LENGTH + 8192;

    /**
     * The given Document instance will be singed using signatureDictionary location and written to the specified
     * output stream.
     *
     * @param document            document contents to be signed
     * @param outputFile          output file for singed document output
     * @param signatureDictionary dictionary to update signer information
     */
    public static void signDocument(Document document, File outputFile, SignatureDictionary signatureDictionary)
            throws IOException, CrossReferenceStateException, ObjectStateException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, NoSuchAlgorithmException, OperatorCreationException, CMSException {
        try (final RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");) {
            Library library = document.getCatalog().getLibrary();
            int signatureDictionaryOffset = library.getOffset(signatureDictionary.getPObjectReference());

            StateManager stateManager = document.getStateManager();
            SecurityManager securityManager = document.getSecurityManager();
            CrossReferenceRoot crossReferenceRoot = stateManager.getCrossReferenceRoot();

            // write out the securityDictionary, so we can make the necessary edits for setting up signing
            String rawSignatureDiciontary = writeSignatureDictionary(crossReferenceRoot, securityManager,
                    signatureDictionary);

            // figure out byte offset around the content hex string
            final FileChannel fc = raf.getChannel();
            fc.position(0);
            long fileLength = fc.size();

            // The digest covers the whole file except the signature itself, and "the signature
            // itself" is the hex string *including* its angle brackets - the gap between the two
            // ranges is <...>, not the digits inside it (PDF 32000-1 12.8.1).  The brackets used to
            // be signed, which put both boundaries one byte out: the file verified against itself
            // because the digest was computed from the same offsets, but the byte range did not
            // describe what the specification says it describes, and PDF/A-2 6.4.3 checks exactly
            // that by re-deriving the range from the file.
            int firstStart = 0;
            String contents = "/Contents <";
            int openAngleBracket = signatureDictionaryOffset
                    + rawSignatureDiciontary.indexOf(contents) + contents.length() - 1;
            int firstOffset = openAngleBracket;
            // past the placeholder's digits and its closing bracket
            int secondStart = openAngleBracket + 1 + PLACEHOLDER_PADDING_LENGTH + 1;
            int secondOffset = (int) fileLength - secondStart;
            List<Integer> byteRangeArray = List.of(firstStart, firstOffset, secondStart, secondOffset);
            String byteRangeDump = writeByteOffsets(crossReferenceRoot, securityManager, byteRangeArray);

            // Replace the placeholder with the real offsets, padded back out to exactly the length
            // of what was matched.  The dictionary is written over the bytes already in the file,
            // so the two have to be the same size to the character.  Measured from the placeholder
            // rather than taken from a constant: the constant was two short of what the placeholder
            // actually serialises to, which left the last byte of the object it overwrote sitting
            // in the file as rubbish between two objects.
            Matcher byteRange = Pattern.compile("/ByteRange \\[[ 0]*]").matcher(rawSignatureDiciontary);
            if (!byteRange.find()) {
                throw new IllegalStateException("Signature dictionary has no /ByteRange placeholder");
            }
            int placeholderLength = byteRange.group().length();
            String replacement = "/ByteRange " + byteRangeDump;
            if (replacement.length() > placeholderLength) {
                throw new IllegalStateException("Byte range " + byteRangeDump
                        + " does not fit the space reserved for it");
            }
            rawSignatureDiciontary = rawSignatureDiciontary.substring(0, byteRange.start())
                    + replacement + " ".repeat(placeholderLength - replacement.length())
                    + rawSignatureDiciontary.substring(byteRange.end());

            int signatureDictionaryLength = rawSignatureDiciontary.length();
            // What the dictionary already occupies in the file.  Writing a different number of bytes
            // over it either runs into the object that follows or leaves part of the old one behind,
            // and every offset taken above - the byte range included - is measured against it.
            int originalLength = objectLengthAt(fc, signatureDictionaryOffset);
            if (signatureDictionaryLength != originalLength) {
                throw new IllegalStateException("Signature dictionary length change original "
                        + originalLength + " new " + signatureDictionaryLength);
            }

            // write the altered signature dictionary
            fc.position(signatureDictionaryOffset);
            fc.write(ByteBuffer.wrap(rawSignatureDiciontary.getBytes(StandardCharsets.ISO_8859_1)));

            // digest the file creating the content signature
            ByteBuffer preContent = ByteBuffer.allocateDirect(firstOffset);
            ByteBuffer postContent = ByteBuffer.allocateDirect(secondOffset);
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
            String hexContent = HexStringObject.encodeHexString(signature);
            int hexContentLength = hexContent.length();
            if (hexContentLength < PLACEHOLDER_PADDING_LENGTH) {
                hexContent = hexContent + "0".repeat(PLACEHOLDER_PADDING_LENGTH - hexContentLength);
            } else {
                throw new IllegalStateException("signature content is larger than placeholder");
            }
            // update /contents with signature
            Pattern pattern = Pattern.compile("/Contents <([A-Fa-f0-9]+)>");
            Matcher matcher = pattern.matcher(rawSignatureDiciontary);
            rawSignatureDiciontary = matcher.replaceFirst("/Contents <" + hexContent + ">");

            // write the altered signature dictionary
            fc.position(signatureDictionaryOffset);
            int count = fc.write(ByteBuffer.wrap(
                    rawSignatureDiciontary.getBytes(StandardCharsets.ISO_8859_1)));

            // The signature went in without changing the size of anything.  Comparable to a byte
            // count only because the string holds one byte per character.
            if (count != signatureDictionaryLength) {
                throw new IllegalStateException("Signature dictionary length change original " + count +
                        " new " + signatureDictionaryLength);
            }

        }
    }

    /**
     * How many bytes the object starting at {@code offset} takes up in the file, up to and
     * including its {@code endobj} and the newline that follows it.
     *
     * @param fc     channel over the document
     * @param offset where the object starts
     * @return the object's length in bytes
     * @throws IOException          if the file cannot be read
     * @throws IllegalStateException if the object has no endobj
     */
    private static int objectLengthAt(FileChannel fc, int offset) throws IOException {
        byte[] endObj = "endobj".getBytes(StandardCharsets.ISO_8859_1);
        long remaining = fc.size() - offset;
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(remaining, MAX_OBJECT_SCAN));
        fc.position(offset);
        fc.read(buffer);
        byte[] bytes = buffer.array();
        for (int i = 0; i <= buffer.position() - endObj.length; i++) {
            boolean match = true;
            for (int j = 0; j < endObj.length; j++) {
                if (bytes[i + j] != endObj[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                // Including the newline the writer puts after endobj, so that this measures the same
                // span as the serialized string it is compared against.
                int end = i + endObj.length;
                if (end < buffer.position() && bytes[end] == '\n') {
                    end++;
                }
                return end;
            }
        }
        throw new IllegalStateException("Signature dictionary at " + offset + " has no endobj");
    }

    public static String writeSignatureDictionary(CrossReferenceRoot crossReferenceRoot,
                                                  SecurityManager securityManager,
                                                  SignatureDictionary signatureDictionary) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CountingOutputStream objectOutput = new CountingOutputStream(byteArrayOutputStream);
        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, objectOutput, 0L);
        writer.initializeWriters();
        writer.writePObject(new PObject(signatureDictionary, signatureDictionary.getPObjectReference()));
        // ISO-8859-1 maps the 256 byte values onto the first 256 characters, so the string that
        // comes back carries the bytes exactly and its length is their count.  Decoded as UTF-8 a
        // byte that is not valid UTF-8 becomes the replacement character - which is most of an
        // encrypted string, and any signer name that needed UTF-16 - and the bytes are then gone.
        String objectDump = byteArrayOutputStream.toString(StandardCharsets.ISO_8859_1);
        objectOutput.close();
        return objectDump;
    }

    public static String writeByteOffsets(CrossReferenceRoot crossReferenceRoot, SecurityManager securityManager,
                                          List<Integer> offsets) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CountingOutputStream objectOutput = new CountingOutputStream(byteArrayOutputStream);
        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, objectOutput, 0L);
        writer.initializeWriters();
        writer.writeValue(new PObject(offsets, new Reference(1, 0)), objectOutput);
        String objectDump = byteArrayOutputStream.toString(StandardCharsets.ISO_8859_1);
        objectOutput.close();
        return objectDump;
    }

    public static String generateContentsPlaceholder() {
        return generateContentsPlaceholder(0);
    }

    public static String generateContentsPlaceholder(int reductionAdjustment) {
        int capacity = PLACEHOLDER_PADDING_LENGTH - reductionAdjustment;
        return "0".repeat(Math.max(0, capacity));
    }


}
