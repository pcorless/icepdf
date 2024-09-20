package org.icepdf.core.util;

import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureReferenceDictionary;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.util.ArrayList;
import java.util.List;

/**
 * SignatureDictionaries keeps track signatures added to a document instance and is primarily used to keep
 * track of which signature should be applied during a document write.  This class also does basic
 * validation to make sure there is only one dictionary marked as the /DocMDP or "certifier" distinction.
 */
public class SignatureDictionaries {

    private SignatureDictionary currentSignatureDictionary;

    public void addCertifierSignature(SignatureDictionary signatureDictionary) throws IllegalStateException {
        if (currentSignatureDictionary != null) throw new IllegalStateException("Signature already exists");
        currentSignatureDictionary = signatureDictionary;
    }

    public void addSignerSignature(SignatureDictionary signatureDictionary) {
        if (currentSignatureDictionary != null) throw new IllegalStateException("Signature already exists");
        currentSignatureDictionary = signatureDictionary;
    }

    public void removeSignature(SignatureDictionary signatureDictionary) {
        currentSignatureDictionary = null;
    }

    /**
     * Returns the signature dictionaries associated with the document edits and will be used to sign the document.
     *
     * @return current signature dictionary for signing or null if not set.
     */
    public SignatureDictionary getCurrentSignature() {
        return currentSignatureDictionary;
    }

    public boolean hasSigners() {
        return currentSignatureDictionary != null;
    }

    /**
     * Checks to see if a certifier signature already exists in the document.
     *
     * @param library document library
     * @return true if there is already a certifier signature, otherwise false.
     */
    public boolean hasExistingCertifier(Library library) {
        InteractiveForm interactiveForm = library.getCatalog().getInteractiveForm();
        if (interactiveForm != null) {
            ArrayList<SignatureWidgetAnnotation> signatureWidgets = interactiveForm.getSignatureFields();
            for (SignatureWidgetAnnotation signatureWidget : signatureWidgets) {
                List<SignatureReferenceDictionary> signatureReferenceDictionary =
                        signatureWidget.getSignatureDictionary().getReferences();
                for (SignatureReferenceDictionary reference : signatureReferenceDictionary) {
                    if (reference.getTransformMethod() == SignatureReferenceDictionary.TransformMethods.DocMDP) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
