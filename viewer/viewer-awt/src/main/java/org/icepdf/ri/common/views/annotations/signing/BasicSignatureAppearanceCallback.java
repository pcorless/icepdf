package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.PostScriptEncoder;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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

    protected FontFile fontFile;

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
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();

        int margin = signatureAppearanceModel.getMargin();
        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, margin, margin);

        // create the new font to draw with
        if (fontFile == null) {
            fontFile = ContentWriterUtils.createFont(signatureAppearanceModel.getFontName());
        }

        ResourceBundle messageBundle = signatureAppearanceModel.getMessageBundle();

        float offsetX = margin;
        float offsetY = margin;
        // is generally going to be zero, and af takes care of the offset for inset.
        Rectangle2D bbox = appearanceState.getBbox();
        float advanceX = (float) bbox.getMinX() + offsetX;
        float advanceY = (float) bbox.getMinY() + offsetY;
        float midX = signatureAppearanceModel.getColumnLayoutWidth();

        Library library = signatureDictionary.getLibrary();
        Name imageName = new Name("sig_img_" + library.getStateManager().getNextImageNumber());

        // create new image stream for the signature image
        int x = signatureAppearanceModel.getSignatureCoordinateX();
        int y = signatureAppearanceModel.getSignatureCoordinateY();
        ImageStream imageStream = ContentWriterUtils.addImageToShapes(library, imageName, x, y,
                signatureAppearanceModel.getSignatureImage(), shapes);

        // title
        ContentWriterUtils.addTextSpritesToShapes(fontFile, advanceX, advanceY, shapes,
                signatureAppearanceModel.getFontSize() + 2,
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                signatureAppearanceModel.getTitle());

        // reasons
        MessageFormat reasonFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.reason.label"));
        String reason = reasonFormatter.format(new Object[]{signatureDictionary.getReason()});
        Point2D.Float lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, advanceY,
                shapes,
                signatureAppearanceModel.getFontSize(),
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                reason);

        float groupSpacing = signatureAppearanceModel.getLineSpacing() + 5;

        // name
        ContentWriterUtils.addTextSpritesToShapes(fontFile, advanceX,
                lastOffset.y + groupSpacing, shapes,
                signatureAppearanceModel.getFontSize() + 2,
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                signatureAppearanceModel.getName());

        // contact info
        MessageFormat contactFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.contact.label"));
        String contactInfo = contactFormatter.format(new Object[]{signatureDictionary.getContactInfo()});
        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes,
                signatureAppearanceModel.getFontSize(),
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                contactInfo);

        // common name
        MessageFormat signerFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.signer.label"));
        String commonName = signerFormatter.format(new Object[]{signatureDictionary.getName()});
        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes,
                signatureAppearanceModel.getFontSize(),
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                commonName);

        // location
        MessageFormat locationFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.location.label"));
        String location = locationFormatter.format(new Object[]{signatureDictionary.getLocation()});
        ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes,
                signatureAppearanceModel.getFontSize(),
                signatureAppearanceModel.getLineSpacing(),
                signatureAppearanceModel.getFontColor(),
                location);

        // finalized appearance stream and generated postscript
        boolean isNew = true;
        StateManager stateManager = library.getStateManager();
        AffineTransform matrix = appearanceState.getMatrix();

        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix,
                PostScriptEncoder.generatePostScript(shapes.getShapes()), isNew);
        xObject.addFontResource(ContentWriterUtils.createDefaultFontDictionary(signatureAppearanceModel.getFontName()));
        xObject.addImageResource(imageName, imageStream);
        ContentWriterUtils.setAppearance(signatureWidgetAnnotation, xObject, appearanceState, stateManager, isNew);
    }

}
