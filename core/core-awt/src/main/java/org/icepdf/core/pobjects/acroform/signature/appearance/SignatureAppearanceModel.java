package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;

/**
 * SignatureAppearanceModel is used to store appearance properties needed to build out the appearance stream.  This
 * model is used by the SignatureAppearanceCallback to build out the appearance stream for a SignatureWidgetAnnotation.
 */
public interface SignatureAppearanceModel {

    /**
     * Check if the signature appearance stream should be rendered
     *
     * @return true to render the signature appearance stream
     */
    boolean isSignatureVisible();

    /**
     * Check if the signature image should be rendered
     *
     * @return true to render the signature image
     */
    boolean isSignatureImageVisible();

    /**
     * Check if the signature text should be rendered
     *
     * @return true to render the signature text
     */
    boolean isSignatureTextVisible();

    /**
     * Indicates that a certificate has been selected for signing.  If none is selected certain UI elements should
     * be disabled.
     * @return true if a certificate has been selected for signing.
     */
    boolean isSelectedCertificate();

    /**
     * Get the resource bundle selected by the user.  User can localize the signature appearance model to their liking.
     * @return resource bundle for the signature appearance model.
     */
    ResourceBundle getMessageBundle();

    /**
     * Get the signature type: Signer or Certifier.  There can only be on Certifier signature per document but multiple
     * Signer signatures.
     * @return signature type
     */
    SignatureType getSignatureType();

    /**
     * Get the contact information for the signature.
     *
     * @return contact information
     */
    String getContact();

    /**
     * Get the name of the signer.
     * @return name of the signer
     */
    String getName();

    /**
     * Get the location of the signer.
     * @return location of the signer
     */
    String getLocation();

    /**
     * Get the signature image, can be null.
     * @return associated signature image
     */
    BufferedImage getSignatureImage();

    /**
     * Get the name of the image XObject that will be used to store the signature image.
     * @return name of the image XObject
     */
    Name getImageXObjectName();

    /**
     * Get the reference to the image XObject that will be used to store the signature image.  This is needed to
     * properly clean up a signature that was deleted before the document was saved.
     * @return reference to the image XObject
     */
    Reference getImageXObjectReference();

    /**
     * Sets the ImageXObject reference for the signature appearance stream.
     *
     * @param pObjectReference reference to the image XObject
     */
    void setImageXObjectReference(Reference pObjectReference);

    /**
     * Get the Image scale factor for the signature image, 100 is 100%.
     * @return image scale factor
     */
    int getImageScale();

    /**
     * Get the font name for the signature text.  Default is Helvetica.
     * @return font name
     */
    String getFontName();

    /**
     * Get the font size for the signature text.  Default is 6.
     * @return font size
     */
    int getFontSize();

    /**
     * Get the font color for the signature text.  Default is black.
     * @return font color
     */
    Color getFontColor();
}
