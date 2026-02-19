package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.builders.TrueTypeFontEmbedder;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
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
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Stream.FILTER_KEY;
import static org.icepdf.core.pobjects.fonts.Font.SIMPLE_FORMAT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.FONT_FILE_2;

/**
 * Utility for common rendering methods used when generating annotation content stream and supporting resources.
 */
public class ContentWriterUtils {

    private static final Logger logger =
            Logger.getLogger(ContentWriterUtils.class.toString());

    public static final Name EMBEDDED_FONT_NAME = new Name("ice1");

    public static void removeSimpleFont(Library library, Reference fontReference) {
        Object obj = library.getObject(fontReference);
        if (obj instanceof SimpleFont) {
            StateManager stateManager = library.getStateManager();
            SimpleFont font = (SimpleFont) obj;
            stateManager.removeChange(new PObject(font, fontReference));
            FontDescriptor fontDescriptor = font.getFontDescriptor();
            if (fontDescriptor != null) {
                Reference fontFileRef = (Reference) fontDescriptor.getEntries().get(FONT_FILE_2);
                if (fontFileRef != null) {
                    stateManager.removeChange(new PObject(library.getObject(fontFileRef), fontFileRef));
                }
                stateManager.removeChange(new PObject(fontDescriptor, fontDescriptor.getPObjectReference()));
            }
        }
    }

    public static void setAppearance(Annotation annotation, Form form, AppearanceState appearanceState,
                                     StateManager stateManager, boolean isNew) {
        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();
        Rectangle2D bbox = appearanceState.getBbox();
        Rectangle2D formBbox = new Rectangle2D.Float(0, 0,
                (float) bbox.getWidth(), (float) bbox.getHeight());
        form.setAppearance(shapes, matrix, formBbox);
        stateManager.addChange(new PObject(form, form.getPObjectReference()), isNew);
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

    public static Point2D.Float addTextSpritesToShapes(TrueTypeFontEmbedder trueTypeeFontSubSetter,
                                                       final float advanceX,
                                                       final float advanceY,
                                                       Shapes shapes,
                                                       int fontSize,
                                                       float lineSpacing,
                                                       Color fontColor,
                                                       String content) {
        FontFile fontFile = trueTypeeFontSubSetter.getFontFile();
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
            trueTypeeFontSubSetter.addToSubset(currentChar);
            if (!(currentChar == '\n' || currentChar == '\r')) {
                textSprites.addText(
                        currentChar, // cid
                        EMBEDDED_FONT_NAME,
                        String.valueOf(currentChar), // unicode value
                        currentX, currentY, newAdvanceX, 0, 0);
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

    /**
     * Saves the font descriptor and font file associated with the given font to the StateManager which were
     * previously saved in the tmp cache.
     *
     * @param font font object to persist to main state manager.
     */
    public static void saveFont(org.icepdf.core.pobjects.fonts.Font font) {
        FontDescriptor fontDescriptor = font.getFontDescriptor();
        if (fontDescriptor != null && fontDescriptor.getPObjectReference() != null) {
            StateManager stateManager = font.getLibrary().getStateManager();
            stateManager.addChange(new PObject(fontDescriptor, fontDescriptor.getPObjectReference()));
            if (fontDescriptor.getEntries().containsKey(FONT_FILE_2)) {
                Object obj = fontDescriptor.getEntries().get(FONT_FILE_2);
                if (obj instanceof Reference) {
                    Reference ref = (Reference) obj;
                    PObject fontFile = stateManager.getTempChange(ref);
                    stateManager.addChange(fontFile);
                }
            }
        }
    }

    public static ImageStream addImageToShapes(Library library, Name imageName, Reference reference,
                                               BufferedImage bufferedImage, Shapes shapes,
                                               Rectangle2D bbox, float scale) {
        scale = scale / 100;

        // create transform for centering image
        float scaledImageHeight = bufferedImage.getHeight() * scale;
        float offset = (float) (bbox.getHeight() - scaledImageHeight) / 2;
        AffineTransform centeringTransform = new AffineTransform(
                1, 0, 0,
                1, 0,
                -offset);

        // create transform for image placement
        AffineTransform imageTransform = new AffineTransform(
                bufferedImage.getWidth() * scale,
                0, 0,
                -bufferedImage.getHeight() * scale,
                0,
                bbox.getHeight());
        // add image xObject
        ImageStream imageStream = ImageStream.getInstance(library, reference, bufferedImage, true);
        ImageReference imageReference = new ImageContentWriterReference(imageStream, imageName);
        // stack em up
        shapes.add(new PushDrawCmd());
        shapes.add(new TransformDrawCmd(centeringTransform));
        shapes.add(new TransformDrawCmd(imageTransform));
        shapes.add(new ImageDrawCmd(imageReference));
        shapes.add(new PopDrawCmd());
        return imageStream;
    }
}
