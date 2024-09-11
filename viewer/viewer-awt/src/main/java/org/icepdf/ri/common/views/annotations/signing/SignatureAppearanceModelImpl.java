package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceModel;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.util.Library;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;


/**
 * Signature appearance state allows the signature builder to dialog/ui and a SignatureAppearanceCallback
 * implementation to
 * share a common model.  When any of the model property values are changed a
 * SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE
 * even is fired.  The intent is that any properties chane can trigger the SignatureAppearanceCallback to rebuild
 * the signatures appearance stream.
 */
public class SignatureAppearanceModelImpl implements SignatureAppearanceModel {

    private BufferedImage signatureImage;
    private Name imageXObjectName;
    private Reference imageXObjectReference;

    private Color fontColor = Color.BLACK;

    private SignatureType signatureType;
    private boolean signatureVisible = true;
    private boolean isSelectedCertificate;
    private String location;
    private String contact;
    private String name;

    private ResourceBundle messageBundle;
    private Locale locale;
    private final Preferences preferences;


    public SignatureAppearanceModelImpl(Library library) {
        imageXObjectName = new Name("sig_img_" + library.getStateManager().getNextImageNumber());
        preferences = ViewerPropertiesManager.getInstance().getPreferences();
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

    public void setSignatureImage(BufferedImage image) {
        this.signatureImage = image;
    }

    public String getFontName() {
        return preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_NAME, "Helvetica");
    }

    public void setFontName(String fontName) {
        preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_NAME, fontName);
    }

    public int getFontSize() {
        return preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_SIZE, 6);
    }

    public void setFontSize(int fontSize) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_FONT_SIZE, fontSize);
    }

    public boolean isSignatureTextVisible() {
        return preferences.getBoolean(ViewerPropertiesManager.PROPERTY_SIGNATURE_SHOW_TEXT, true);
    }

    public void setSignatureTextVisible(boolean signatureTextVisible) {
        preferences.putBoolean(ViewerPropertiesManager.PROPERTY_SIGNATURE_SHOW_TEXT, signatureTextVisible);
    }

    public boolean isSignatureImageVisible() {
        return preferences.getBoolean(ViewerPropertiesManager.PROPERTY_SIGNATURE_SHOW_IMAGE, true);
    }

    public void setSignatureImageVisible(boolean signatureImageVisible) {
        preferences.putBoolean(ViewerPropertiesManager.PROPERTY_SIGNATURE_SHOW_IMAGE, signatureImageVisible);
    }

    public int getImageScale() {
        return preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_SCALE, 100);
    }

    public void setImageScale(int imageScale) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_SCALE, imageScale);
    }

    public void setSignatureImagePath(String imagePath) {
        preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_PATH, imagePath);
        signatureImage = SignatureUtilities.loadSignatureImage(imagePath);
    }

    public String getSignatureImagePath() {
        return preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_PATH, "");
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
