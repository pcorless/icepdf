package org.icepdf.core.util;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureReferenceDictionary;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.util.ArrayList;
import java.util.List;

/**
 * SignatureManager is used to manage the signature dictionaries associated with a document.  A users can create
 * more than one Signature annotation but, they must be linked to the same SignatureDictionary.  When a document is
 * written to disk, only one signature dictionary can be used to sign the document.
 * <p>
 * This class also does basic validation to make sure there is only one dictionary marked as the
 * /DocMDP or "certifier" distinction.
 */
public class SignatureManager {

    private SignatureDictionary currentSignatureDictionary;
    private final ArrayList<SignatureWidgetAnnotation> signatureWidgetAnnotations = new ArrayList<>();

    public void addSignature(SignatureDictionary signatureDictionary, SignatureWidgetAnnotation signatureAnnotation) {
        // if not the same dictionary then we need to apply it to all the existing signature widgets and clean up
        // the old dictionary.
        if (currentSignatureDictionary != null && !currentSignatureDictionary.equals(signatureDictionary)) {
            for (SignatureWidgetAnnotation signatureWidgetAnnotation : signatureWidgetAnnotations) {
                signatureWidgetAnnotation.setSignatureDictionary(signatureDictionary);
            }
            // remove the old signature dictionary
            StateManager stateManager = signatureAnnotation.getLibrary().getStateManager();
            stateManager.removeChange(new PObject(currentSignatureDictionary,
                    currentSignatureDictionary.getPObjectReference()));
        }

        currentSignatureDictionary = signatureDictionary;
        signatureAnnotation.setSignatureDictionary(currentSignatureDictionary);

        // add the new signature widget to the list
        if (!signatureWidgetAnnotations.contains(signatureAnnotation)) {
            signatureWidgetAnnotations.add(signatureAnnotation);
        }
    }

    /**
     * Clears the current signature dictionary and references to associated SignatureWidgetAnnotation.
     * This should be done after the document has been signed or if the signature process is cancelled.
     */
    public void clearSignatures() {
        currentSignatureDictionary = null;
        signatureWidgetAnnotations.clear();
    }

    /**
     * Returns the signature dictionaries associated with the document edits and will be used to sign the document.
     *
     * @return current signature dictionary for signing or null if not set.
     */
    public SignatureDictionary getCurrentSignatureDictionary() {
        return currentSignatureDictionary;
    }

    /**
     * Check if a signature dictionary has been set. If a signature dictionary has been set, then the current
     * signature dictionary should be used to sign the document and a new one should not be created
     *
     * @return true if a signature dictionary has been set, otherwise false.
     */
    public boolean hasSignatureDictionary() {
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
                if (signatureReferenceDictionary != null) {
                    for (SignatureReferenceDictionary reference : signatureReferenceDictionary) {
                        if (reference.getTransformMethod() == SignatureReferenceDictionary.TransformMethods.DocMDP) {
                            return true;
                        }
                    }
                }

            }
        }
        return false;
    }
}
