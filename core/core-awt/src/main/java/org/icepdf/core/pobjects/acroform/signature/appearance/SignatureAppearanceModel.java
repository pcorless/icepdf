package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;

public interface SignatureAppearanceModel<T> {
    boolean isSignatureVisible();

    boolean isSelectedCertificate();

    String getFontName();

    int getFontSize();

    ResourceBundle getMessageBundle();

    SignatureType getSignatureType();

    Object getContact();

    Object getName();

    Object getLocation();

    BufferedImage getSignatureImage();

    Name getImageXObjectName();

    Reference getImageXObjectReference();

    boolean isSignatureImageVisible();

    int getImageScale();

    void setImageXObjectReference(Reference pObjectReference);

    boolean isSignatureTextVisible();

    Color getFontColor();
}
