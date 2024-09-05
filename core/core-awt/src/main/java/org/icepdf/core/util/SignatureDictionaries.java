package org.icepdf.core.util;

import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureReferenceDictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * SignatureDictionaries keeps track of all signatures added to a document instance and is primarily used to keep
 * track of which signature should be applied during a document write.  This class also does basic
 * validation to make sure there is only one dictionary marked as the /DocMDP or "certifier" distinction.
 */
public class SignatureDictionaries {

    private final ArrayList<SignatureDictionary> signatureDictionaries;
    private SignatureDictionary currentSignatureDictionary;

    public SignatureDictionaries() {
        this.signatureDictionaries = new ArrayList<>();
    }

    public void addCertifierSignature(SignatureDictionary signatureDictionary) throws IllegalStateException {
        if (hasExistingCertifier()) {
            throw new IllegalStateException("Certifier signature already exists");
        }
        signatureDictionaries.add(0, signatureDictionary);
    }

    public void addSignerSignature(SignatureDictionary signatureDictionary) {
        signatureDictionaries.add(signatureDictionary);
    }

    public ArrayList<SignatureDictionary> getSignatures() {
        return signatureDictionaries;
    }

    /**
     * Sets the current signature dictionary that will be used sign a document during an incremental update.
     *
     * @param signatureDictionary dictionary to sign document with
     */
    public void setCurrentSignatureDictionary(SignatureDictionary signatureDictionary) {
        currentSignatureDictionary = signatureDictionary;
    }

    public SignatureDictionary getCurrentSignatureDictionary() {
        return currentSignatureDictionary;
    }

    public boolean hasSigners() {
        return !signatureDictionaries.isEmpty();
    }

    public boolean hasExistingCertifier() {
        for (SignatureDictionary signatureDictionary : signatureDictionaries) {
            List<SignatureReferenceDictionary> signatureReferenceDictionaries = signatureDictionary.getReferences();
            for (SignatureReferenceDictionary signatureReferenceDictionary : signatureReferenceDictionaries) {
                if (signatureReferenceDictionary.getTransformMethod().equals(SignatureReferenceDictionary.TransformMethods.DocMDP)) {
                    return true;
                }
            }
        }
        return false;
    }
}
