package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StateManager;
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
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;

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

/**
 * Builds a basic appearance stream using the given signatureImage.  This is meant to be a reference implementation
 * that can be easily tweaked as needed.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback {

    protected static final Logger logger =
            Logger.getLogger(BasicSignatureAppearanceCallback.class.toString());

    protected final SignatureAppearanceModel signatureAppearanceModel;

    /**
     * Create a new signature appearance stream builder
     *
     * @param signatureAppearanceModel model to store signature properties that are shared between the UI build and
     *                                 annotation appearance builder
     */
    public BasicSignatureAppearanceCallback(SignatureAppearanceModel signatureAppearanceModel) {
        this.signatureAppearanceModel = signatureAppearanceModel;
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation, AffineTransform pageSpace
            , boolean isNew) {
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();

        int margin = 0;
        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, margin, margin);

        // create the new font to draw with
        FontFile fontFile = ContentWriterUtils.createFont(signatureAppearanceModel.getFontName());
        fontFile = fontFile.deriveFont(signatureAppearanceModel.getFontSize());

        ResourceBundle messageBundle = signatureAppearanceModel.getMessageBundle();

        float offsetX = margin;
        float offsetY = margin;
        // is generally going to be zero, and af takes care of the offset for inset.
        Rectangle2D bbox = appearanceState.getBbox();
        float advanceX = (float) bbox.getMinX() + offsetX;
        float advanceY = (float) bbox.getMinY() + offsetY;
        float midX = (float) bbox.getWidth() / 2;

        Library library = signatureDictionary.getLibrary();
        Name imageName = signatureAppearanceModel.getImageXObjectName();

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

        int leftMargin = calculateLeftMargin(bbox, reason, contactInfo, commonName, location);

        // create new image stream for the signature image 25, 50
        BufferedImage signatureImage = signatureAppearanceModel.getSignatureImage();
        ImageStream imageStream = null;
        if (signatureImage != null) {
            imageStream = ContentWriterUtils.addImageToShapes(library, imageName, signatureImage, shapes, bbox,
                    leftMargin);
        }

        int lineSpacing = 5;
        Point2D.Float lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, leftMargin, advanceY,
                shapes,
                signatureAppearanceModel.getFontSize(),
                lineSpacing,
                signatureAppearanceModel.getFontColor(),
                reason);

        float groupSpacing = lineSpacing + 5;

        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, leftMargin, lastOffset.y + groupSpacing,
                shapes,
                signatureAppearanceModel.getFontSize(),
                lineSpacing,
                signatureAppearanceModel.getFontColor(),
                contactInfo);

        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, leftMargin, lastOffset.y + groupSpacing,
                shapes,
                signatureAppearanceModel.getFontSize(),
                lineSpacing,
                signatureAppearanceModel.getFontColor(),
                commonName);

        ContentWriterUtils.addTextSpritesToShapes(fontFile, leftMargin, lastOffset.y + groupSpacing, shapes,
                signatureAppearanceModel.getFontSize(),
                lineSpacing,
                signatureAppearanceModel.getFontColor(),
                location);

        // finalized appearance stream and generated postscript
        StateManager stateManager = library.getStateManager();
        AffineTransform matrix = appearanceState.getMatrix();

        byte[] postScript = PostScriptEncoder.generatePostScript(shapes.getShapes());
        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix, postScript, isNew);
        xObject.addFontResource(ContentWriterUtils.createDefaultFontDictionary(signatureAppearanceModel.getFontName()));
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
        ContentWriterUtils.setAppearance(signatureWidgetAnnotation, xObject, appearanceState, stateManager, isNew);

    }

    private int calculateLeftMargin(Rectangle2D bbox, String... text) {
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
