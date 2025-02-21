package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.PostScriptEncoder;
import org.icepdf.core.pobjects.graphics.commands.TransformDrawCmd;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureManager;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Form.RESOURCES_KEY;

/**
 * Builds a basic appearance stream using the given signatureImage.  This is meant to be a reference implementation
 * that can be easily tweaked as needed.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback<SignatureAppearanceModelImpl> {

    protected static final Logger logger =
            Logger.getLogger(BasicSignatureAppearanceCallback.class.toString());

    protected SignatureAppearanceModelImpl signatureAppearanceModel;


    @Override
    public void setSignatureAppearanceModel(SignatureAppearanceModelImpl signatureAppearanceModel) {
        this.signatureAppearanceModel = signatureAppearanceModel;
    }

    @Override
    public void removeAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       AffineTransform pageSpace, boolean isNew) {
        if (signatureAppearanceModel == null) {
            throw new IllegalStateException("SignatureAppearanceModel must be set before calling this method.");
        }
        Library library = signatureWidgetAnnotation.getLibrary();
        SignatureManager signatureManager = library.getSignatureDictionaries();
        signatureWidgetAnnotation.setSignatureDictionary(new SignatureDictionary(library, new DictionaryEntries()));
        signatureManager.clearSignatures();
        StateManager stateManager = library.getStateManager();
        stateManager.removeChange(new PObject(null, signatureAppearanceModel.getImageXObjectReference()));

        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, 0, 0);
        byte[] postScript = PostScriptEncoder.generatePostScript(shapes.getShapes());
        Rectangle2D bbox = appearanceState.getBbox();
        AffineTransform matrix = appearanceState.getMatrix();
        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix, postScript, isNew);
        xObject.getEntries().remove(RESOURCES_KEY);
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       AffineTransform pageSpace, boolean isNew) {
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

        // create the new font to draw with
        FontFile fontFile = ContentWriterUtils.createFont(signatureAppearanceModel.getFontName());
        fontFile = fontFile.deriveFont(signatureAppearanceModel.getFontSize());

        ResourceBundle messageBundle = signatureAppearanceModel.getMessageBundle();

        Library library = signatureDictionary.getLibrary();

        // reasons
        MessageFormat reasonFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.reason.label"));
        String reasonTranslated;
        if (signatureAppearanceModel.getSignatureType() == SignatureType.CERTIFIER) {
            reasonTranslated = messageBundle.getString(
                    "viewer.annotation.signature.handler.properties.reason.certification.label");
        } else {
            reasonTranslated = messageBundle.getString(
                    "viewer.annotation.signature.handler.properties.reason.approval.label");
        }
        String reason = reasonFormatter.format(new Object[]{reasonTranslated});
        // contact info
        MessageFormat contactFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.contact.label"));
        String contactInfo = contactFormatter.format(new Object[]{signatureAppearanceModel.getContact()});
        // common name
        MessageFormat signerFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.signer.label"));
        String commonName = signerFormatter.format(new Object[]{signatureAppearanceModel.getName()});
        // location
        MessageFormat locationFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.location.label"));
        String location = locationFormatter.format(new Object[]{signatureAppearanceModel.getLocation()});

        Rectangle2D bbox = appearanceState.getBbox();

        // create new image stream for the signature image
        BufferedImage signatureImage = signatureAppearanceModel.getSignatureImage();
        Name imageName = signatureAppearanceModel.getImageXObjectName();
        Reference imageReference = signatureAppearanceModel.getImageXObjectReference();
        ImageStream imageStream = null;
        if (signatureAppearanceModel.isSignatureImageVisible() && signatureImage != null) {
            imageStream = ContentWriterUtils.addImageToShapes(library, imageName, imageReference, signatureImage,
                    shapes, bbox, signatureAppearanceModel.getImageScale());
            signatureAppearanceModel.setImageXObjectReference(imageStream.getPObjectReference());
        }

        if (signatureAppearanceModel.isSignatureTextVisible()) {
            float offsetY = 0;
            int lineSpacing = signatureAppearanceModel.getFontSize();
            int fontSize = signatureAppearanceModel.getFontSize();

            String[] signatureText = {reason, contactInfo, commonName, location};
            int leftMargin = calculateLeftMargin(bbox, signatureText);
            int padding = 3;
            float groupSpacing = calculateTextSpacing(bbox, signatureText, fontSize, padding);
            AffineTransform centeringTransform = calculatePaddingTransform(leftMargin, padding);

            Point2D.Float lastOffset;
            float advanceY = (float) bbox.getMinY() + offsetY;
            shapes.add(new TransformDrawCmd(centeringTransform));
            for (String text : signatureText) {
                lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, 0, advanceY,
                        shapes,
                        fontSize,
                        lineSpacing,
                        signatureAppearanceModel.getFontColor(),
                        text);
                advanceY = lastOffset.y + groupSpacing;
            }
        }

        // finalized appearance stream and generated postscript
        StateManager stateManager = library.getStateManager();
        AffineTransform matrix = appearanceState.getMatrix();

        byte[] postScript = PostScriptEncoder.generatePostScript(shapes.getShapes());
        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix, postScript, isNew);
        xObject.addFontResource(ContentWriterUtils.createDefaultFontDictionary(signatureAppearanceModel.getFontName()));
        if (signatureAppearanceModel.isSignatureImageVisible() && imageStream != null) {
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
        ContentWriterUtils.setAppearance(signatureWidgetAnnotation, xObject, appearanceState, stateManager, isNew);

    }

    private float calculateTextSpacing(Rectangle2D bbox, String[] text, int fontSize, int padding) {
        float textHeight = text.length * fontSize;
        float bboxHeight = (float) bbox.getHeight() - (padding * 2);
        if (textHeight > bboxHeight) {
            return 0;
        } else {
            return (bboxHeight - textHeight) / (text.length - 1);
        }
    }

    private AffineTransform calculatePaddingTransform(int leftMargin, int padding) {
        // this is a little fuzzy but should work for most cases to center text in the middle of the bbox0;
        return new AffineTransform(
                1, 0, 0,
                1, leftMargin,
                padding);
    }

    private int calculateLeftMargin(Rectangle2D bbox, String[] text) {
        Font font = new Font(signatureAppearanceModel.getFontName(), Font.PLAIN,
                signatureAppearanceModel.getFontSize());
        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), true, true);
        int maxWidth = 0;

        for (String s : text) {
            GlyphVector glyphVector = font.createGlyphVector(fontRenderContext, s);
            Rectangle2D rect = glyphVector.getOutline().getBounds2D();
            if (rect.getWidth() > maxWidth) {
                maxWidth = (int) rect.getWidth();
            }
        }
        return (int) bbox.getWidth() - maxWidth;
    }

}
