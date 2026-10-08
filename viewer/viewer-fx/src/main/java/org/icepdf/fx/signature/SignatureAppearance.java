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
package org.icepdf.fx.signature;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceModel;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.util.Library;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * What a new signature's visible appearance shows and how: the text lines (signer, reason, contact,
 * location), an optional image (a scanned signature or a stamp), font and layout.  A plain bean,
 * read by {@link SignatureAppearanceBuilder}; no JavaFX types and no stored preferences, so an
 * application decides what to remember.
 * <p>
 * Ported from the Swing viewer's {@code SignatureAppearanceModelImpl}, which keeps its settings in
 * {@code java.util.prefs}; see PROVENANCE.md.
 */
public class SignatureAppearance implements SignatureAppearanceModel {

    /** Points of breathing room between the appearance's edge and anything drawn in it. */
    public static final int DEFAULT_PADDING = 3;
    /** Percentage of the width the signature image takes at most (side by side). */
    public static final int DEFAULT_IMAGE_WIDTH_MAX = 60;
    /** Space between lines, as a percentage of the font size. */
    public static final int DEFAULT_LINE_LEADING = 35;

    /** How the image and the text share the field. */
    public enum Layout {
        /** Image on the left, text right aligned beside it, never drawn over one another. */
        SIDE_BY_SIDE,
        /** Image across the whole field with the text over it. */
        OVERLAY
    }

    /**
     * The label of each text line, as {@link java.text.MessageFormat} patterns with the value as
     * {0}; and the words for the two signature types.  English by default.
     */
    public record Labels(String reason, String approval, String certification, String contact, String signer,
                         String location) {
        public static final Labels ENGLISH = new Labels("Reason: {0}", "Approval", "Certification",
                "Contact: {0}", "Digitally signed by {0}", "{0}");
    }

    private final Name imageXObjectName;
    private Reference imageXObjectReference;
    private Reference imageSoftMaskXObjectReference;

    private SignatureType signatureType = SignatureType.SIGNER;
    private boolean signatureVisible = true;
    private String name;
    private String contact;
    private String location;
    private Labels labels = Labels.ENGLISH;

    private boolean textVisible = true;
    private String fontName = "Helvetica";
    private int fontSize = 6;
    private Color fontColor = Color.BLACK;

    private boolean imageVisible = true;
    private BufferedImage image;
    private int imageScale = 100;
    private int imageMaxWidthPercentage = DEFAULT_IMAGE_WIDTH_MAX;

    private Layout layout = Layout.SIDE_BY_SIDE;
    private int padding = DEFAULT_PADDING;
    private int lineLeadingPercentage = DEFAULT_LINE_LEADING;

    /** @param library the document's library: names the image resource uniquely within it */
    public SignatureAppearance(Library library) {
        imageXObjectName = new Name("sig_img_" + library.getStateManager().getNextImageNumber());
    }

    // ---- what the appearance says -------------------------------------------------------

    public SignatureType getSignatureType() {
        return signatureType;
    }

    /** Approval (SIGNER) or certification (CERTIFIER); names the reason line. */
    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType != null ? signatureType : SignatureType.SIGNER;
    }

    /** False makes an invisible signature: the field keeps an empty appearance. */
    public boolean isSignatureVisible() {
        return signatureVisible;
    }

    public void setSignatureVisible(boolean signatureVisible) {
        this.signatureVisible = signatureVisible;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Labels getLabels() {
        return labels;
    }

    public void setLabels(Labels labels) {
        this.labels = labels != null ? labels : Labels.ENGLISH;
    }

    // ---- text ---------------------------------------------------------------------------

    public boolean isTextVisible() {
        return textVisible;
    }

    public void setTextVisible(boolean textVisible) {
        this.textVisible = textVisible;
    }

    public String getFontName() {
        return fontName;
    }

    /** A font name core can resolve (a standard 14 name such as Helvetica, or an installed font). */
    public void setFontName(String fontName) {
        this.fontName = fontName != null ? fontName : "Helvetica";
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(1, fontSize);
    }

    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = fontColor != null ? fontColor : Color.BLACK;
    }

    // ---- image --------------------------------------------------------------------------

    public boolean isImageVisible() {
        return imageVisible;
    }

    public void setImageVisible(boolean imageVisible) {
        this.imageVisible = imageVisible;
    }

    public BufferedImage getImage() {
        return image;
    }

    /** A scanned signature or stamp; transparency is kept (written as a soft mask). */
    public void setImage(BufferedImage image) {
        this.image = image;
    }

    /** How much of its column the image fills, 0-100 (a share of the space, not of its pixels). */
    public int getImageScale() {
        return imageScale;
    }

    public void setImageScale(int imageScale) {
        this.imageScale = clamp(imageScale, 0, 100);
    }

    /** Side by side: the share of the width, 0-100, the image takes at most. */
    public int getImageMaxWidthPercentage() {
        return imageMaxWidthPercentage;
    }

    public void setImageMaxWidthPercentage(int percentage) {
        this.imageMaxWidthPercentage = clamp(percentage, 0, 100);
    }

    // ---- layout -------------------------------------------------------------------------

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout != null ? layout : Layout.SIDE_BY_SIDE;
    }

    /** Room between the field's edge and its contents, in points (0-50). */
    public int getPadding() {
        return padding;
    }

    public void setPadding(int padding) {
        this.padding = clamp(padding, 0, 50);
    }

    /** Space between text lines as a percentage of the font size (0-500). */
    public int getLineLeadingPercentage() {
        return lineLeadingPercentage;
    }

    public void setLineLeadingPercentage(int percentage) {
        this.lineLeadingPercentage = clamp(percentage, 0, 500);
    }

    // ---- bookkeeping for the builder ----------------------------------------------------

    Name getImageXObjectName() {
        return imageXObjectName;
    }

    Reference getImageXObjectReference() {
        return imageXObjectReference;
    }

    void setImageXObjectReference(Reference reference) {
        this.imageXObjectReference = reference;
    }

    /**
     * The image's soft mask (its transparency) object, kept so that rebuilding the appearance -
     * which happens on every settings change - rewrites it in place rather than leaving one behind.
     */
    Reference getImageSoftMaskXObjectReference() {
        return imageSoftMaskXObjectReference;
    }

    void setImageSoftMaskXObjectReference(Reference reference) {
        this.imageSoftMaskXObjectReference = reference;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
