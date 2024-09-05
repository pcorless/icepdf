package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.util.Library;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.ResourceBundle;


/**
 * Signature appearance state allows the signature builder to dialog/ui and a SignatureAppearanceCallback
 * implementation to
 * share a common model.  When any of the model property values are changed a
 * SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE
 * even is fired.  The intent is that any properties chane can trigger the SignatureAppearanceCallback to rebuild
 * the signatures appearance stream.
 */
public class SignatureAppearanceModel {

    private BufferedImage signatureImage;
    private Name imageXObjectName;
    private Reference imageXObjectReference;
    private String fontName = "Helvetica";
    private int fontSize = 10;
    private Color fontColor = Color.BLACK;

    private ResourceBundle messageBundle;
    private Locale locale;

    private SignatureType signatureType;
    private boolean signatureVisible = true;
    private boolean isSelectedCertificate;
    private String location;
    private String contact;
    private String name;

    public SignatureAppearanceModel(Library library) {
        imageXObjectName = new Name("sig_img_" + library.getStateManager().getNextImageNumber());
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        messageBundle = ResourceBundle.getBundle(ViewerPropertiesManager.DEFAULT_MESSAGE_BUNDLE, locale);
    }

    public Name getImageXObjectName() {
        return imageXObjectName;
    }

    public Reference getImageXObjectReference() {
        return imageXObjectReference;
    }

    public void setImageXObjectReference(Reference imageXObjectReference) {
        this.imageXObjectReference = imageXObjectReference;
    }

    public SignatureType getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
    }

    public boolean isSignatureVisible() {
        return signatureVisible;
    }

    public boolean isSelectedCertificate() {
        return isSelectedCertificate;
    }

    public void setSelectedCertificate(boolean selectedCertificate) {
        isSelectedCertificate = selectedCertificate;
    }

    public void setSignatureVisible(boolean signatureVisible) {
        this.signatureVisible = signatureVisible;
    }

    public BufferedImage getSignatureImage() {
        return signatureImage;
    }

    public void setSignatureImage(BufferedImage signatureImage) {
        this.signatureImage = signatureImage;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = fontColor;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceBundle getMessageBundle() {
        return messageBundle;
    }

}
