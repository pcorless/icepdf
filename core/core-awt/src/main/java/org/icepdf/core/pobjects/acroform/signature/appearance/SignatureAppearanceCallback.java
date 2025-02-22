package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.awt.geom.AffineTransform;

/**
 * Interface for custom appearance streams defined by a user or organization.
 */
public interface SignatureAppearanceCallback<T extends SignatureAppearanceModel> {

    /**
     * Set the SignatureAppearanceModel for the callback.  The model is used to store appearance properties needed
     * to build out the appearance stream.  The model just be set before the create or remove methods are called.
     *
     * @param signatureAppearanceModel appearance model for the callback
     */
    void setSignatureAppearanceModel(T signatureAppearanceModel);

    /**
     * Create appearance stream for the given SignatureWidgetAnnotation.  The appearance must be associated with
     * the SignatureWidgetAnnotation and all new objects registered with the StateManager
     *
     * @param signatureWidgetAnnotation annotation that created appearance stream will be associated with
     * @param pageSpace                page space transform for the annotation
     * @param isNew                    true if annotation is considered new and should be added to state manager
     */
    void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation, AffineTransform pageSpace,
                                boolean isNew);

    /**
     * Remove appearance stream for the given SignatureWidgetAnnotation.  Clean up any resources or StateManager state
     * associated with the SignatureWidgetAnnotation.
     *
     * @param signatureWidgetAnnotation
     * @param pageSpace
     * @param isNew
     */
    void removeAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation, AffineTransform pageSpace,
                                boolean isNew);
}
