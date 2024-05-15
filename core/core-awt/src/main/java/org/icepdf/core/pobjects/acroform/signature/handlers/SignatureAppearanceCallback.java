package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

/**
 * Interface for custom appearance streams defined by a user or organization.
 */
public interface SignatureAppearanceCallback {

    /**
     * Create appearance stream for the given SignatureWidgetAnnotation.  The appearance must be associated with
     * the SignatureWidgetAnnotation and all new objects registered with the StateManager
     *
     * @param signatureWidgetAnnotation annotation that created appearance stream will be associated with
     */
    void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation);
}
