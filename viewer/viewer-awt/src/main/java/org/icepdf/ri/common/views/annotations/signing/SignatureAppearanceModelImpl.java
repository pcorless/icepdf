/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * Signature appearance state, shared by the signature creation dialog and the
 * {@link org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback} that
 * builds the appearance stream from it.  The dialog writes to the model and then asks the callback
 * to rebuild the appearance, so a change to any property here is a change to what gets drawn.
 * <p>
 * Everything except the signature image itself and the signer's own details is stored in
 * {@link Preferences}, so the choices a signer makes carry over to the next document they sign.
 * That includes how the appearance is laid out - the padding, how much of the width the image may
 * take, the leading between lines - which means the layout can be retuned without subclassing
 * anything.  Each getter clamps what it reads: a preferences store is a file a user can edit, and a
 * nonsensical value there should produce a plain appearance rather than a broken one.
 */
public class SignatureAppearanceModelImpl implements SignatureAppearanceModel {

    /** Points of breathing room between the appearance's edge and anything drawn in it. */
    public static final int DEFAULT_PADDING = 3;
    /** How the appearance arranges its image and text when the signer has not chosen. */
    public static final SignatureAppearanceLayout DEFAULT_LAYOUT = SignatureAppearanceLayout.SIDE_BY_SIDE;
    /** Percentage of the width the signature image takes at most. */
    public static final int DEFAULT_IMAGE_WIDTH_MAX = 60;
    /** Space between lines, as a percentage of the font size. */
    public static final int DEFAULT_LINE_LEADING = 35;

    private BufferedImage signatureImage;
    private Name imageXObjectName;
    private Reference imageXObjectReference;
    private Reference imageSoftMaskXObjectReference;

    private Color fontColor = Color.BLACK;

    private SignatureType signatureType;
    private boolean signatureVisible = true;
    private boolean isSelectedCertificate = true;
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

    /**
     * @return the object number of the signature image's soft mask, which carries its transparency.
     * Kept alongside the image's own so that regenerating the appearance - which happens on every
     * settings change - rewrites the mask in place rather than leaving the previous one behind
     */
    public Reference getImageSoftMaskXObjectReference() {
        return imageSoftMaskXObjectReference;
    }

    public void setImageSoftMaskXObjectReference(Reference imageSoftMaskXObjectReference) {
        this.imageSoftMaskXObjectReference = imageSoftMaskXObjectReference;
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

    /**
     * @return how much of the space available to it the signature image fills, as a percentage.
     * 100 fits the image to its column; less leaves it smaller within the same column.  A
     * percentage of the space rather than of the image's own pixel size, because the pixel size is
     * something the signer cannot see - the same slider position would give a 200 pixel stamp and a
     * 2000 pixel scan wildly different results
     */
    public int getImageScale() {
        return clamp(preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_SCALE, 100), 0, 100);
    }

    public void setImageScale(int imageScale) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_SCALE, imageScale);
    }

    /**
     * @return breathing room between the appearance's edge and anything drawn in it, in points
     */
    public int getAppearancePadding() {
        return clamp(preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_PADDING,
                DEFAULT_PADDING), 0, 50);
    }

    public void setAppearancePadding(int padding) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_PADDING, padding);
    }

    /**
     * @return how the appearance arranges its image and its text
     */
    public SignatureAppearanceLayout getLayout() {
        return SignatureAppearanceLayout.valueOf(
                preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_LAYOUT, null), DEFAULT_LAYOUT);
    }

    public void setLayout(SignatureAppearanceLayout layout) {
        preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_LAYOUT, layout.name());
    }

    /**
     * @return the share of the width, as a percentage, that the signature image takes at most, so
     * that short text is not left in a corner of its own field.  It takes less than this whenever
     * the text beside it needs the room - the image is what gives way, so that the font size the
     * signer chose is the font size they get.
     * <p>
     * Only {@link SignatureAppearanceLayout#SIDE_BY_SIDE} has a share to limit; under
     * {@link SignatureAppearanceLayout#OVERLAY} the image has the whole width and this is not read
     */
    public int getImageMaxWidthPercentage() {
        return clamp(preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_WIDTH_MAX,
                DEFAULT_IMAGE_WIDTH_MAX), 0, 100);
    }

    public void setImageMaxWidthPercentage(int percentage) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_WIDTH_MAX, percentage);
    }

    /**
     * @return the space between lines of the signature text, as a percentage of the font size
     */
    public int getLineLeadingPercentage() {
        return clamp(preferences.getInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_LINE_LEADING,
                DEFAULT_LINE_LEADING), 0, 500);
    }

    public void setLineLeadingPercentage(int percentage) {
        preferences.putInt(ViewerPropertiesManager.PROPERTY_SIGNATURE_LINE_LEADING, percentage);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
