package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;

import java.awt.image.BufferedImage;

/**
 * Builds a basic appearance stream using the given signatureImage.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback {

    private BufferedImage bufferedImage;
    private String title;

    /**
     * Create a new signature appearance
     *
     * @param title          title of signer, for example 'Software Developer', can be null.
     * @param signatureImage signature image,  if null no images is inserted.
     */
    public BasicSignatureAppearanceCallback(String title,
                                            BufferedImage signatureImage) {
        this.title = title;
        this.bufferedImage = signatureImage;
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation) {

    }
}
