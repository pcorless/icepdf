package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.StateManager;
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


public class Signer {

    public static void signDocument(Document document, File outputFile, SignatureDictionary signatureDictionary) {
        try (final RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");) {
            Library library = document.getCatalog().getLibrary();
            int signatureDictionaryOffset = library.getOffset(signatureDictionary.getPObjectReference());

            // write the signature object as a raw string so that it can be updated with offsets and certs
            StateManager stateManager = document.getStateManager();
            SecurityManager securityManager = document.getSecurityManager();
            CrossReferenceRoot crossReferenceRoot = stateManager.getCrossReferenceRoot();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CountingOutputStream objectOutput = new CountingOutputStream(byteArrayOutputStream);
            BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, objectOutput, 0l);
            writer.initializeWriters();
            writer.writePObject(new PObject(signatureDictionary, signatureDictionary.getPObjectReference()));
            String objectDump = byteArrayOutputStream.toString(StandardCharsets.UTF_8);


            // find byte offset of start of content hex string

            // next start is known, +30k

            // todo calculate the length of the last three offsets


            // update /ByteRange
            objectDump = objectDump.replace("/ByteRange [0 0 1 1]", "/ByteRange [1 1 1 2]");

            // digest the file creating the content signature

            // update /contents

            // write the new object

            final FileChannel fc = raf.getChannel();
            fc.position(signatureDictionaryOffset);
            int count = fc.write(ByteBuffer.wrap(objectDump.getBytes()));

            // make sure the object length didn't change
            if (count != byteArrayOutputStream.size()) {
                System.out.println("BAM!");
                throw new IllegalStateException();
            }

        } catch (CrossReferenceStateException e) {
            throw new RuntimeException(e);
        } catch (ObjectStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // todo handle in Signature dictionary called by the signer class
    //  want to ame sure the generator can only be used by the signature dictionary.
    public byte[] getContents(byte[] data) throws IOException, CMSException {
        CMSProcessableByteArray message =
                new CMSProcessableByteArray(new ASN1ObjectIdentifier(CMSObjectIdentifiers.data.getId()), data);
//        CMSSignedData signedData = signedDataGenerator.generate(message, false);
//        return signedData.getEncoded();
        return null;
    }

}
