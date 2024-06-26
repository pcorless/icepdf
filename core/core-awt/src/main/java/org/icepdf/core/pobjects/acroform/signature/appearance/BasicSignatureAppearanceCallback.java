package org.icepdf.core.pobjects.acroform.signature.appearance;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
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
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Builds a basic appearance stream using the given signatureImage.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback {

    private static final Logger logger =
            Logger.getLogger(BasicSignatureAppearanceCallback.class.toString());

    protected static final int INSETS = 0;
    protected float lineSpacing = 5;

    private BufferedImage bufferedImage;
    private final String title;
    private final String name;
    private final ResourceBundle messageBundle;

    // todo config object
    private final String fontName = "Helvetica";
    protected FontFile fontFile;
    protected boolean fontPropertyChanged;

    /**
     * Create a new signature appearance
     *
     * @param title          title of signer, for example 'Software Developer', can be null.
     * @param name           name of signer, custom name which might be different the name in cert.
     * @param signatureImage signature image,  if null no images is inserted.
     */
    public BasicSignatureAppearanceCallback(String title, String name,
                                            BufferedImage signatureImage, ResourceBundle messageBundle) {
        // todo add a configuration object for turning on/off the fields that are setup for this implementation.
        this.title = title;
        this.name = name;
        this.bufferedImage = signatureImage;
        this.messageBundle = messageBundle;
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        Shapes shapes = ContentWriterUtils.createAppearanceShapes(appearanceState, INSETS, INSETS);

        // create the new font to draw with
        if (fontFile == null || fontPropertyChanged) {
            fontFile = ContentWriterUtils.createFont(fontName);
            fontPropertyChanged = false;
        }

        float offsetX = INSETS;
        float offsetY = INSETS;
        // is generally going to be zero, and af takes care of the offset for inset.
        Rectangle2D bbox = appearanceState.getBbox();
        float advanceX = (float) bbox.getMinX() + offsetX;
        float advanceY = (float) bbox.getMinY() + offsetY;
        float midX = (float) (bbox.getWidth() + offsetX) / 2;

        Library library = signatureDictionary.getLibrary();
        Name imageName = new Name("sig_img_" + library.getStateManager().getNextImageNumber());

        // create new image stream for the signature image
        int x = 25;
        int y = 50;
        ImageStream imageStream = ContentWriterUtils.addImageToShapes(library, imageName, x, y, bufferedImage, shapes);

        // title
        ContentWriterUtils.addTextSpritesToShapes(fontFile, advanceX, advanceY, shapes, 15, lineSpacing, Color.BLACK,
                title);

        // reasons
        MessageFormat reasonFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.reason.label"));
        String reason = reasonFormatter.format(new Object[]{signatureDictionary.getReason()});
        Point2D.Float lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, advanceY + 4, shapes, 12,
                lineSpacing, Color.BLACK, reason);

        float groupSpacing = lineSpacing + 5;

        // name
        ContentWriterUtils.addTextSpritesToShapes(fontFile, advanceX, lastOffset.y + groupSpacing, shapes, 15,
                lineSpacing,
                Color.BLACK, name);

        // contact info
        MessageFormat contactFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.contact.label"));
        String contactInfo = contactFormatter.format(new Object[]{signatureDictionary.getContactInfo()});
        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes, 12,
                lineSpacing, Color.BLACK, contactInfo);

        // common name
        MessageFormat signerFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.signer.label"));
        String commonName = signerFormatter.format(new Object[]{signatureDictionary.getName()});
        lastOffset = ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes, 12,
                lineSpacing, Color.BLACK, commonName);

        // location
        MessageFormat locationFormatter = new MessageFormat(messageBundle.getString(
                "viewer.annotation.signature.handler.properties.location.label"));
        String location = locationFormatter.format(new Object[]{signatureDictionary.getLocation()});
        ContentWriterUtils.addTextSpritesToShapes(fontFile, midX, lastOffset.y + groupSpacing, shapes, 12, lineSpacing,
                Color.BLACK, location);

        // finalized appearance stream and generated postscript
        boolean isNew = true;
        StateManager stateManager = library.getStateManager();
        AffineTransform matrix = appearanceState.getMatrix();

        Form xObject = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix,
                PostScriptEncoder.generatePostScript(shapes.getShapes()), isNew);
        xObject.addFontResource(ContentWriterUtils.createDefaultFontDictionary(fontName));
        xObject.addImageResource(imageName, imageStream);
        ContentWriterUtils.setAppearance(signatureWidgetAnnotation, xObject, appearanceState, stateManager, isNew);
    }

}
