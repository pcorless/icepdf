package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.Encoding;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageContentWriterReference;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Dictionary.SUBTYPE_KEY;
import static org.icepdf.core.pobjects.Dictionary.TYPE_KEY;
import static org.icepdf.core.pobjects.Stream.FILTER_DCT_DECODE;
import static org.icepdf.core.pobjects.Stream.FILTER_KEY;
import static org.icepdf.core.pobjects.fonts.Font.SIMPLE_FORMAT;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.*;

/**
 * Utility for common rendering methods used when generating annotation content stream and supporting resources.
 */
public class ContentWriterUtils {

    private static final Logger logger =
            Logger.getLogger(ContentWriterUtils.class.toString());

    protected static final Name EMBEDDED_FONT_NAME = new Name("ice1");

    public static DictionaryEntries createDefaultFontDictionary(String fontName) {
        // create the font dictionary
        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("Type1"));
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.NAME_KEY, EMBEDDED_FONT_NAME);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
        fontDictionary.put(new Name("FirstChar"), 32);
        fontDictionary.put(new Name("LastChar"), 255);
        return fontDictionary;
    }

    public static DictionaryEntries createImageDictionary() {
        DictionaryEntries imageDictionary = new DictionaryEntries();
        imageDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
        imageDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("Type1"));
        return imageDictionary;
    }

//    public static void setFontDictionary(Form form, String fontName, StateManager stateManager, boolean isNew) {
//        // create the font dictionary
//        Library library = form.getLibrary();
//        DictionaryEntries fontDictionary = new DictionaryEntries();
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
//                org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("Type1"));
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.NAME_KEY, EMBEDDED_FONT_NAME);
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
//        fontDictionary.put(new Name("FirstChar"), 32);
//        fontDictionary.put(new Name("LastChar"), 255);
//
//        org.icepdf.core.pobjects.fonts.Font newFont;
//        if (form.getResources() == null ||
//                form.getResources().getFont(EMBEDDED_FONT_NAME) == null) {
//            newFont = new org.icepdf.core.pobjects.fonts.zfont.SimpleFont(library, fontDictionary);
//            newFont.setPObjectReference(stateManager.getNewReferenceNumber());
//            // create font entry
//            DictionaryEntries fontResources = new DictionaryEntries();
//            fontResources.put(EMBEDDED_FONT_NAME, newFont.getPObjectReference());
//            // add the font resource entry.
//            DictionaryEntries resources = new DictionaryEntries();
//            resources.put(new Name("Font"), fontResources);
//            // and finally add it to the form.
//            form.getEntries().put(new Name("Resources"), resources);
//        } else {
//            try {
//                form.init();
//            } catch (InterruptedException e) {
//                logger.log(Level.WARNING, "Could not initialized Annotation", e);
//                throw new IllegalStateException("Could not initialized Annotation");
//            }
//            newFont = form.getResources().getFont(EMBEDDED_FONT_NAME);
//            Reference reference = newFont.getPObjectReference();
//            newFont = new org.icepdf.core.pobjects.fonts.zfont.SimpleFont(library, fontDictionary);
//            newFont.setPObjectReference(reference);
//        }
//        // update hard reference to state manager and weak library reference.
//        stateManager.addChange(new PObject(newFont, newFont.getPObjectReference()), isNew);
//        library.addObject(newFont, newFont.getPObjectReference());
//    }

    public static void setAppearance(Annotation annotation, Form form, AppearanceState appearanceState,
                                     StateManager stateManager, boolean isNew) {
        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();
        Rectangle2D bbox = appearanceState.getBbox();
        Rectangle2D formBbox = new Rectangle2D.Float(0, 0,
                (float) bbox.getWidth(), (float) bbox.getHeight());
        form.setAppearance(shapes, matrix, formBbox);
        stateManager.addChange(new PObject(form, form.getPObjectReference()), isNew);
        // update the AP's stream bytes so contents can be written out
        form.setRawBytes(PostScriptEncoder.generatePostScript(shapes.getShapes()));
        DictionaryEntries appearanceRefs = new DictionaryEntries();
        appearanceRefs.put(Annotation.APPEARANCE_STREAM_NORMAL_KEY, form.getPObjectReference());
        annotation.getEntries().put(Annotation.APPEARANCE_STREAM_KEY, appearanceRefs);

        // compress the form object stream.
        if (Annotation.isCompressAppearanceStream()) {
            form.getEntries().put(FILTER_KEY, new Name("FlateDecode"));
        } else {
            form.getEntries().remove(FILTER_KEY);
        }
    }

    public static Point2D.Float addTextSpritesToShapes(FontFile fontFile, final float advanceX, final float advanceY,
                                                       Shapes shapes,
                                                       int fontSize,
                                                       float lineSpacing,
                                                       Color fontColor,
                                                       String content) {
        TextSprite textSprites =
                new TextSprite(fontFile,
                        SIMPLE_FORMAT,
                        content.length(),
                        new AffineTransform(), null);
        textSprites.setRMode(TextState.MODE_FILL);
        textSprites.setStrokeColor(fontColor);
        textSprites.setFontName(EMBEDDED_FONT_NAME.toString());
        textSprites.setFontSize(fontSize);

        fontFile = fontFile.deriveFont(fontSize);

        StringBuilder contents = new StringBuilder(content);

        float currentX = 0;
        // we don't want to shift the whole line width just the ascent
        float currentY = advanceY + fontSize;

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
                currentY += fontSize + lineSpacing;
                lastx = 0;
            }
        }

        // actual font.
        shapes.add(new ColorDrawCmd(fontColor));
        shapes.add(new TextSpriteDrawCmd(textSprites));

        shapes.add(new AlphaDrawCmd(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)));

        return new Point2D.Float(currentX, currentY);
    }

    public static Shapes createAppearanceShapes(AppearanceState appearanceState, int xInsets, int yInsets) {

        appearanceState.setMatrix(new AffineTransform());
        appearanceState.setShapes(new Shapes());

        Rectangle2D bbox = appearanceState.getBbox();
        bbox.setRect(0, 0, bbox.getWidth(), bbox.getHeight());

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

        // set up the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.scale(1, -1);
        af.translate(0, -bbox.getHeight());
        af.translate(xInsets, yInsets);
        shapes.add(new TransformDrawCmd(af));

        return shapes;
    }

    public static FontFile createFont(String fontName) {
        FontFile fontFile = FontManager.getInstance().initialize().getInstance(fontName, 0);
        fontFile = fontFile.deriveFont(Encoding.standardEncoding, null);
        return fontFile;
    }

    public static ImageStream addImageToShapes(Library library, Name imageName,
                                               BufferedImage bufferedImage, Shapes shapes,
                                               Rectangle2D bbox, int leftMargin) {

        // calculate the scale factor to fit in width - leftMargin
        double scaleWidth = (float) bufferedImage.getWidth() / leftMargin;
        double scaleHeight = bufferedImage.getHeight() / bbox.getHeight();
        double scale = Math.max(scaleWidth, scaleHeight);

        // create transform for image placement
        AffineTransform imageTransform = new AffineTransform(
                bufferedImage.getWidth() / scale,
                0, 0,
                -bufferedImage.getHeight() / scale,
                0,
                bbox.getHeight());
        // add image xObject
        ImageStream imageStream = ContentWriterUtils.createImageStream(library, bufferedImage, true);
        ImageReference imageReference = new ImageContentWriterReference(imageStream, imageName);
        // stack em up
        shapes.add(new PushDrawCmd());
        shapes.add(new TransformDrawCmd(imageTransform));
        shapes.add(new ImageDrawCmd(imageReference));
        shapes.add(new PopDrawCmd());
        return imageStream;
    }

    public static ImageStream createImageStream(Library library, BufferedImage bufferedImage, boolean useMask) {
        DictionaryEntries imageDictionary = new DictionaryEntries();
        // build base dictionary and image params, use jpeg so that we get a png when encoding the stream
        imageDictionary.put(FILTER_KEY, FILTER_DCT_DECODE);
        imageDictionary.put(TYPE_KEY, Form.TYPE_VALUE);
        imageDictionary.put(BITS_PER_COMPONENT_KEY, 8);
        imageDictionary.put(SUBTYPE_KEY, ImageStream.TYPE_VALUE);
        imageDictionary.put(WIDTH_KEY, bufferedImage.getWidth());
        imageDictionary.put(HEIGHT_KEY, bufferedImage.getHeight());
        // mask out white background if alpha is specified in colour model, this is man
        if (useMask && bufferedImage.getColorModel().hasAlpha()) {
            imageDictionary.put(MASK_KEY, Arrays.asList(255, 255, 255, 255, 255, 255));
        }
        ImageStream imageStream = new ImageStream(library, imageDictionary, null);
        imageStream.setDecodedImage(bufferedImage);
        // setup object reference and put in state manager
        StateManager stateManager = library.getStateManager();
        Reference reference = stateManager.getNewReferenceNumber();
        imageStream.setPObjectReference(reference);
        stateManager.addChange(new PObject(imageStream, reference), true);
        return imageStream;
    }

}
