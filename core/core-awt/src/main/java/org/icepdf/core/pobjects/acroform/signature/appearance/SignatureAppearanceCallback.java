package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.awt.geom.AffineTransform;

/**
 * Interface for custom appearance streams defined by a user or organization.
 */
public interface SignatureAppearanceCallback {

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
}
