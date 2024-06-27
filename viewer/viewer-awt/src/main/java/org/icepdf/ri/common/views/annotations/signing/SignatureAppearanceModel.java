package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.icepdf.core.util.PropertyConstants.SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE;

/**
 * Signature appearance state allows the signature builder to dialog/ui and a SignatureAppearanceCallback
 * implementation to
 * share a common model.  When any of the model property values are changed a
 * SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE
 * even is fired.  The intent is that any properties chane can trigger the SignatureAppearanceCallback to rebuild
 * the signatures appearance stream.
 */
public class SignatureAppearanceModel {

    private int margin = 0;

    private ResourceBundle messageBundle;

    // middle margin
    private int middleMargin;

    // need signature type
    private SignatureType signatureType;

    // language selection
    private Locale locale;

    // signature visibility
    private boolean signatureVisible;

    private int signatureCoordinateX;
    private int signatureCoordinateY;
    private BufferedImage signatureImage;

    //
    private String title;
    private String name;

    private final String fontName = "Helvetica";
    private int fontSize = 15;
    private float lineSpacing = 5;
    private Color fontColor = Color.BLACK;

    private final PropertyChangeSupport changeDispatcher = new PropertyChangeSupport(this);

    /**
     * @param title          signature user's title
     * @param name           display name, may be different from what is defined in the signature
     * @param signatureImage image to embedded in the appearance stream
     * @param locale         locale to use when translating labels.
     */
    public SignatureAppearanceModel(String title, String name,
                                    BufferedImage signatureImage, Locale locale) {
        this.title = title;
        this.name = name;
        this.signatureImage = signatureImage;
        this.locale = locale;
        messageBundle = ResourceBundle.getBundle(ViewerPropertiesManager.DEFAULT_MESSAGE_BUNDLE, locale);
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        messageBundle = ResourceBundle.getBundle(ViewerPropertiesManager.DEFAULT_MESSAGE_BUNDLE, locale);
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public int getColumnLayoutWidth() {
        return middleMargin;
    }

    public void setColumnLayoutWidth(int middleMargin) {
        this.middleMargin = middleMargin;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public SignatureType getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public boolean isSignatureVisible() {
        return signatureVisible;
    }

    public void setSignatureVisible(boolean signatureVisible) {
        this.signatureVisible = signatureVisible;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public int getSignatureCoordinateX() {
        return signatureCoordinateX;
    }

    /**
     * Sets the location of the signature image relative the annotation's bounding box (bbox)
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setSignatureImageLocation(int x, int y) {
        this.signatureCoordinateX = x;
        this.signatureCoordinateY = y;
    }

    public void setSignatureCoordinateX(int signatureCoordinateX) {
        this.signatureCoordinateX = signatureCoordinateX;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public int getSignatureCoordinateY() {
        return signatureCoordinateY;
    }

    public void setSignatureCoordinateY(int signatureCoordinateY) {
        this.signatureCoordinateY = signatureCoordinateY;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public BufferedImage getSignatureImage() {
        return signatureImage;
    }

    public void setSignatureImage(BufferedImage signatureImage) {
        this.signatureImage = signatureImage;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public String getFontName() {
        return fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public float getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(float lineSpacing) {
        this.lineSpacing = lineSpacing;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = fontColor;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public int getMargin() {
        return margin;
    }

    public void setMargin(int margin) {
        this.margin = margin;
        firePropertyChange(SIGNATURE_ANNOTATION_APPEARANCE_PROPERTY_CHANGE, null, null);
    }

    public ResourceBundle getMessageBundle() {
        return messageBundle;
    }

    public void firePropertyChange(String event, Object oldValue,
                                   Object newValue) {
        changeDispatcher.firePropertyChange(event, oldValue, newValue);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        changeDispatcher.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        changeDispatcher.removePropertyChangeListener(l);
    }
}
