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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.builders.SimpleFontFactory;
import org.icepdf.core.pobjects.fonts.builders.TrueTypeFontEmbedder;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.PostScriptEncoder;
import org.icepdf.core.pobjects.graphics.commands.TransformDrawCmd;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Form.RESOURCES_KEY;
import static org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils.EMBEDDED_FONT_NAME;

/**
 * Builds a basic appearance stream using the given signatureImage.  This is meant to be a reference implementation
 * that can be easily tweaked as needed.
 * {@link SignatureAppearanceModelImpl}, so the arrangement can be retuned without subclassing this.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback<SignatureAppearanceModelImpl> {

    protected static final Logger logger =
            Logger.getLogger(BasicSignatureAppearanceCallback.class.getName());

    /**
     * Narrowest column, in points, still worth drawing the signature image into.
     */
    protected static final float MIN_IMAGE_WIDTH = 1;

    protected SignatureAppearanceModelImpl signatureAppearanceModel;


    @Override
    public void setSignatureAppearanceModel(SignatureAppearanceModelImpl signatureAppearanceModel) {
        this.signatureAppearanceModel = signatureAppearanceModel;
    }

    @Override
    public void removeAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       AffineTransform pageSpace) {
        if (signatureAppearanceModel == null) {
            throw new IllegalStateException("SignatureAppearanceModel must be set before calling this method.");
        }
        Library library = signatureWidgetAnnotation.getLibrary();
        SignatureManager signatureManager = library.getSignatureDictionaries();
        signatureWidgetAnnotation.setSignatureDictionary(new SignatureDictionary(library, new DictionaryEntries()));
        signatureManager.clearSignatures();
        StateManager stateManager = library.getStateManager();
        stateManager.removeChange(new PObject(null, signatureAppearanceModel.getImageXObjectReference()));
        // The image's soft mask is a separate object; left behind it would be written as something
        // nothing refers to.
        Reference softMaskReference = signatureAppearanceModel.getImageSoftMaskXObjectReference();
        if (softMaskReference != null) {
            stateManager.removeChange(new PObject(null, softMaskReference));
            signatureAppearanceModel.setImageSoftMaskXObjectReference(null);
        }

        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, 0, 0);
        byte[] postScript = PostScriptEncoder.generatePostScript(shapes.getShapes());
        Rectangle2D bbox = appearanceState.getBbox();
        AffineTransform matrix = appearanceState.getMatrix();
        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix, postScript);
        xObject.getEntries().remove(RESOURCES_KEY);
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       AffineTransform pageSpace) {
        if (signatureAppearanceModel == null) {
            throw new IllegalStateException("SignatureAppearanceModel must be set before calling this method.");
        }
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();

        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, 0, 0);

        if (!signatureAppearanceModel.isSignatureVisible() || !signatureAppearanceModel.isSelectedCertificate()) {
            return;
        }

        ResourceBundle messageBundle = signatureAppearanceModel.getMessageBundle();
        Library library = signatureDictionary.getLibrary();
        Rectangle2D bbox = appearanceState.getBbox();

        List<String> lines = signatureLines(messageBundle);

        boolean drawText = signatureAppearanceModel.isSignatureTextVisible() && !lines.isEmpty();
        BufferedImage signatureImage = signatureAppearanceModel.getSignatureImage();
        boolean drawImage = signatureAppearanceModel.isSignatureImageVisible() && signatureImage != null;

        float padding = signatureAppearanceModel.getAppearancePadding();
        float availableWidth = (float) bbox.getWidth() - padding * 2;
        float availableHeight = (float) bbox.getHeight() - padding * 2;
        // The leftmost the text may start, which a side by side layout moves right of the image.
        float textLeftEdge = padding;

        // The text is measured before anything is drawn, even though the image is drawn first: how
        // much room is left for the image is whatever the text does not need.  The text cannot be
        // reflowed - nothing here wraps - so it is the one with the claim on its own width.
        FontFile fontFile = null;
        int fontSize = signatureAppearanceModel.getFontSize();
        float textWidth = 0;
        if (drawText) {
            fontFile = FontFactory.getInstance().createFontFile(library,
                    signatureAppearanceModel.getFontName()).deriveFont((float) fontSize);
            textWidth = widestLine(fontFile, lines);
        }

        boolean overlay = signatureAppearanceModel.getLayout() == SignatureAppearanceLayout.OVERLAY;
        float imageColumnWidth = 0;
        if (drawImage && overlay) {
            // The mark gets the whole field and the text is drawn over it - the image is added to the
            // shapes first, so it ends up underneath.  Nothing here can promise a line of text will
            // not land on a stroke; that depends on the image, which is why this is the signer's
            // choice and not the default.
            imageColumnWidth = availableWidth;
        } else if (drawImage) {
            float gap = drawText ? padding * 2 : 0;
            // Whatever the text does not want, up to a limit so that short text is not left in a
            // corner of its own field.  The image gives the width up rather than the text being made
            // to fit it: the font size is something the signer chose in the dialog, and quietly
            // overriding it there made the control look broken - every size past the point the text
            // filled its column came out at the same size as the one before it.  Text too big for
            // the field it was given is the signer's own choice and plain to see; a control that
            // does nothing is not.
            float maxImageWidth = signatureAppearanceModel.getImageMaxWidthPercentage() / 100f * availableWidth;
            imageColumnWidth = Math.max(0, Math.min(maxImageWidth, availableWidth - textWidth - gap));
            if (imageColumnWidth < MIN_IMAGE_WIDTH) {
                // Text this large leaves the image no room worth drawing in.  Drawn anyway it is a
                // placement scaled to nothing - and an image XObject, and its soft mask, written into
                // the document for something with no area to be seen in.
                drawImage = false;
                imageColumnWidth = 0;
            } else {
                // Only a side by side layout sets the text aside from the image; under an overlay the
                // two share the width.
                textLeftEdge = padding + imageColumnWidth + gap;
            }
        }

        // create new image stream for the signature image
        Name imageName = signatureAppearanceModel.getImageXObjectName();
        Reference imageReference = signatureAppearanceModel.getImageXObjectReference();
        Reference softMaskReference = signatureAppearanceModel.getImageSoftMaskXObjectReference();
        ImageStream imageStream = null;
        if (drawImage) {
            Rectangle2D imageRegion = new Rectangle2D.Float(padding, padding,
                    imageColumnWidth, availableHeight);
            imageStream = ContentWriterUtils.addImageToShapes(library, imageName, imageReference,
                    softMaskReference, signatureImage, shapes, imageRegion,
                    signatureAppearanceModel.getImageScale());
            signatureAppearanceModel.setImageXObjectReference(imageStream.getPObjectReference());
            // Null for an opaque image, which needs no mask - and which then also releases the object
            // number a previous, transparent image was masked by.
            signatureAppearanceModel.setImageSoftMaskXObjectReference(imageStream.getSoftMaskReference());
        } else {
            // The appearance is rebuilt on every change in the dialog, so an image written for a
            // previous version of it is still registered to be written even though nothing draws it
            // any more.
            discard(library.getStateManager(), imageReference);
            discard(library.getStateManager(), softMaskReference);
            signatureAppearanceModel.setImageXObjectReference(null);
            signatureAppearanceModel.setImageSoftMaskXObjectReference(null);
        }

        TrueTypeFontEmbedder trueTypeFontSubSetter = null;
        if (drawText) {
            trueTypeFontSubSetter = new TrueTypeFontEmbedder(fontFile);
            // All the lines share one font, and whether that font is a simple or a composite one is
            // decided by all of them together - a Japanese signer name settles it for the Latin lines
            // above.  Declared before any of it is laid out, because the first line laid out is
            // written in whichever kind was decided by then.
            for (String text : lines) {
                trueTypeFontSubSetter.addToSubset(text);
            }

            // Set solid and centred in the height it has, rather than spread to fill it: four lines
            // spread down a tall signature field sit in four separate corners of it and stop reading
            // as one block.
            float leading = Math.round(fontSize * signatureAppearanceModel.getLineLeadingPercentage() / 100f);
            float blockHeight = lines.size() * fontSize + (lines.size() - 1) * leading;
            if (blockHeight > availableHeight) {
                leading = 0;
                blockHeight = lines.size() * fontSize;
            }
            float textTop = padding + Math.max(0, (availableHeight - blockHeight) / 2);
            // Right aligned against the far edge, held back to the start of its own column so that
            // text too wide for the field runs off the right rather than back over the image.
            float textLeft = Math.max(textLeftEdge,
                    padding + availableWidth - Math.min(textWidth, availableWidth));

            shapes.add(new TransformDrawCmd(new AffineTransform(1, 0, 0, 1, textLeft, textTop)));
            float advanceY = 0;
            for (String text : lines) {
                Point2D.Float lastOffset = ContentWriterUtils.addTextSpritesToShapes(
                        trueTypeFontSubSetter, 0, advanceY, shapes,
                        fontSize, leading,
                        signatureAppearanceModel.getFontColor(), text);
                advanceY = lastOffset.y + leading;
            }
        }

        // finalized appearance stream and generated postscript
        StateManager stateManager = library.getStateManager();
        AffineTransform matrix = appearanceState.getMatrix();

        byte[] postScript = PostScriptEncoder.generatePostScript(shapes.getShapes());
        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix, postScript);

        if (trueTypeFontSubSetter != null) {
            Dictionary pdfFont = SimpleFontFactory.createFont(
                    library, signatureAppearanceModel.getFontName(), trueTypeFontSubSetter);
            Reference fontReference = pdfFont.getPObjectReference();
            xObject.addFontResource(EMBEDDED_FONT_NAME, fontReference);
        }

        if (imageStream != null) {
            xObject.addImageResource(imageName, imageStream);
        }
        try {
            xObject.init();
            // the image make it more difficult to use the shapes array, so we generated
            // from the postscript array to get a proper shapes
            appearanceState.setShapes(xObject.getShapes());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ContentWriterUtils.setAppearance(signatureWidgetAnnotation, xObject, appearanceState, stateManager);

    }

    /**
     * The lines of the signature block.
     */
    protected List<String> signatureLines(ResourceBundle messageBundle) {
        String reasonTranslated;
        if (signatureAppearanceModel.getSignatureType() == SignatureType.CERTIFIER) {
            reasonTranslated = messageBundle.getString(
                    "viewer.annotation.signature.handler.properties.reason.certification.label");
        } else {
            reasonTranslated = messageBundle.getString(
                    "viewer.annotation.signature.handler.properties.reason.approval.label");
        }
        List<String> lines = new ArrayList<>(4);
        addLine(lines, messageBundle, "viewer.annotation.signature.handler.properties.reason.label",
                reasonTranslated);
        addLine(lines, messageBundle, "viewer.annotation.signature.handler.properties.contact.label",
                signatureAppearanceModel.getContact());
        addLine(lines, messageBundle, "viewer.annotation.signature.handler.properties.signer.label",
                signatureAppearanceModel.getName());
        addLine(lines, messageBundle, "viewer.annotation.signature.handler.properties.location.label",
                signatureAppearanceModel.getLocation());
        return lines;
    }

    /**
     * Drops an object a previous build of the appearance registered and this one does not use.
     */
    private static void discard(StateManager stateManager, Reference reference) {
        if (reference != null) {
            stateManager.removeChange(new PObject(null, reference));
        }
    }

    /**
     * Adds one {@code label: value} line to the block, or nothing when there is no value.
     */
    private static void addLine(List<String> lines, ResourceBundle messageBundle, String labelKey,
                                String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        lines.add(new MessageFormat(messageBundle.getString(labelKey)).format(new Object[]{value}));
    }

    /**
     * @return the width of the widest line, measured through the font that draws them
     */
    private static float widestLine(FontFile fontFile, List<String> lines) {
        float widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, ContentWriterUtils.measureTextWidth(fontFile, line));
        }
        return widest;
    }
}
