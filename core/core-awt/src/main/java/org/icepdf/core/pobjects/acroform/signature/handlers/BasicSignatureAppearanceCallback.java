package org.icepdf.core.pobjects.acroform.signature.handlers;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.Encoding;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.fonts.Font.SIMPLE_FORMAT;

/**
 * Builds a basic appearance stream using the given signatureImage.
 */
public class BasicSignatureAppearanceCallback implements SignatureAppearanceCallback {

    private static final Logger logger =
            Logger.getLogger(BasicSignatureAppearanceCallback.class.toString());

    private static final int INSETS = 5;
    public static final Name EMBEDDED_FONT_NAME = new Name("ice1");

    private BufferedImage bufferedImage;
    private String title;


    private String fontName = "Helvetica";
    protected FontFile fontFile;
    protected boolean fontPropertyChanged;

    /**
     * Create a new signature appearance
     *
     * @param title          title of signer, for example 'Software Developer', can be null.
     * @param signatureImage signature image,  if null no images is inserted.
     */
    public BasicSignatureAppearanceCallback(String title,
                                            BufferedImage signatureImage) {
        this.title = title;
        this.bufferedImage = signatureImage;
    }

    @Override
    public void createAppearanceStream(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        Name currentAppearance = signatureWidgetAnnotation.getCurrentAppearance();
        HashMap<Name, Appearance> appearances = signatureWidgetAnnotation.getAppearances();
        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        appearanceState.setMatrix(new AffineTransform());
        appearanceState.setShapes(new Shapes());

        Rectangle2D bbox = appearanceState.getBbox();
        bbox.setRect(0, 0, bbox.getWidth(), bbox.getHeight());

        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();

        if (shapes == null) {
            shapes = new Shapes();
            appearanceState.setShapes(shapes);
        } else {
            // remove any previous text
            appearanceState.getShapes().getShapes().clear();
        }

        // remove any previous text
        shapes.getShapes().clear();

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.scale(1, -1);
        af.translate(0, -bbox.getHeight());
        // adjust of the border offset, offset is define in viewer,
        // so we can't use the constant because of dependency issues.
        af.translate(INSETS, INSETS);
        shapes.add(new TransformDrawCmd(af));

        String content = title;

        // create the new font to draw with
        if (fontFile == null || fontPropertyChanged) {
            fontFile = FontManager.getInstance().initialize().getInstance(fontName, 0);
            fontFile = fontFile.deriveFont(Encoding.standardEncoding, null);
            fontPropertyChanged = false;
        }
        int fontSize = 10;
        fontFile = fontFile.deriveFont(10); // todo might be dynamically set?
        TextSprite textSprites =
                new TextSprite(fontFile,
                        SIMPLE_FORMAT,
                        content.length(),
                        new AffineTransform(), null);
        textSprites.setRMode(TextState.MODE_FILL);
        textSprites.setStrokeColor(Color.BLACK);
        textSprites.setFontName(EMBEDDED_FONT_NAME.toString());
        textSprites.setFontSize(fontSize);

        // iterate over each line of text painting the strings.
        StringBuilder contents = new StringBuilder(content);

        // todo build out factory to setup x,y for sig content
        //  fontsize, offset based on bbox size.
        float lineHeight = fontSize;

        float offsetX = INSETS;  // 1 pixel padding
        float offsetY = INSETS;
        // is generally going to be zero, and af takes care of the offset for inset.
        float advanceX = (float) bbox.getMinX() + offsetX;
        float advanceY = (float) bbox.getMinY() + offsetY;

        float currentX;
        // we don't want to shift the whole line width just the ascent
        float currentY = advanceY + lineHeight;

        float lastx = 0;
        float newAdvanceX;
        char currentChar;
        for (int i = 0, max = contents.length(); i < max; i++) {

            currentChar = contents.charAt(i);

            newAdvanceX = (float) fontFile.getAdvance(currentChar).getX();
            currentX = advanceX + lastx;
            lastx += newAdvanceX;

            if (!(currentChar == '\n' || currentChar == '\r')) {
                textSprites.addText(
                        currentChar, // cid
                        String.valueOf(currentChar), // unicode value
                        currentX, currentY, newAdvanceX, 0);
            } else {
                // move back to start of next line
                currentY += lineHeight;
                advanceX = (float) bbox.getMinX() + offsetX;
                lastx = 0;
            }
        }

        // actual font.
        shapes.add(new ColorDrawCmd(Color.BLACK));
        shapes.add(new TextSpriteDrawCmd(textSprites));

        shapes.add(new AlphaDrawCmd(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)));

        // update the appearance stream
        // create/update the appearance stream of the xObject.
        boolean isNew = true;
        Library library = signatureWidgetAnnotation.getLibrary();
        StateManager stateManager = library.getStateManager();
        Form form = signatureWidgetAnnotation.updateAppearanceStream(shapes, bbox, matrix,
                PostScriptEncoder.generatePostScript(shapes.getShapes()), isNew);

        // todo font resources.
        if (form != null) {
            Rectangle2D formBbox = new Rectangle2D.Float(0, 0,
                    (float) bbox.getWidth(), (float) bbox.getHeight());
            form.setAppearance(shapes, matrix, formBbox);
            stateManager.addChange(new PObject(form, form.getPObjectReference()), isNew);
            // update the AP's stream bytes so contents can be written out
            form.setRawBytes(PostScriptEncoder.generatePostScript(shapes.getShapes()));
            DictionaryEntries appearanceRefs = new DictionaryEntries();
            appearanceRefs.put(Annotation.APPEARANCE_STREAM_NORMAL_KEY, form.getPObjectReference());
            signatureWidgetAnnotation.getEntries().put(Annotation.APPEARANCE_STREAM_KEY, appearanceRefs);

            // compress the form object stream.
            if (Annotation.isCompressAppearanceStream()) {
                form.getEntries().put(Stream.FILTER_KEY, new Name("FlateDecode"));
            } else {
                form.getEntries().remove(Stream.FILTER_KEY);
            }

            // create the font
            DictionaryEntries fontDictionary = new DictionaryEntries();
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                    org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY,
                    new Name("Type1"));
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.NAME_KEY,
                    EMBEDDED_FONT_NAME);
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY,
                    new Name(fontName));
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY,
                    new Name("WinAnsiEncoding"));
            fontDictionary.put(new Name("FirstChar"), 32);
            fontDictionary.put(new Name("LastChar"), 255);

            org.icepdf.core.pobjects.fonts.Font newFont;
            if (form.getResources() == null ||
                    form.getResources().getFont(EMBEDDED_FONT_NAME) == null) {
                newFont = new org.icepdf.core.pobjects.fonts.zfont.SimpleFont(
                        library, fontDictionary);
                newFont.setPObjectReference(stateManager.getNewReferenceNumber());
                // create font entry
                DictionaryEntries fontResources = new DictionaryEntries();
                fontResources.put(EMBEDDED_FONT_NAME, newFont.getPObjectReference());
                // add the font resource entry.
                DictionaryEntries resources = new DictionaryEntries();
                resources.put(new Name("Font"), fontResources);
                // and finally add it to the form.
                form.getEntries().put(new Name("Resources"), resources);
            } else {
                try {
                    form.init();
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Could not initialized Annotation", e);
                    throw new IllegalStateException("Could not initialized Annotation");
                }
                newFont = form.getResources().getFont(EMBEDDED_FONT_NAME);
                Reference reference = newFont.getPObjectReference();
                newFont = new org.icepdf.core.pobjects.fonts.zfont.SimpleFont(library, fontDictionary);
                newFont.setPObjectReference(reference);
            }
            // update hard reference to state manager and weak library reference.
            stateManager.addChange(new PObject(newFont, newFont.getPObjectReference()), isNew);
            library.addObject(newFont, newFont.getPObjectReference());
        }

    }
}
