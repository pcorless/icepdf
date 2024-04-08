package org.icepdf.core.pobjects.acroform.signature;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.io.OutputStream;


public class Signer {

    public static void signDocument(Document document, OutputStream out, SignatureDictionary signatureDictionary) {

        try {
            Library library = document.getCatalog().getLibrary();
            int offset = library.getOffset(signatureDictionary.getPObjectReference());

            // write the signature dictionary so we can calculate the offset

            // update /ByteRange

            // digest the file creating the content signature

            // update /contents

            // assert new dictionary is the same size as placeholder version

            // write the modified dictionary

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
