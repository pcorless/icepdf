package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.Encoding;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.graphics.images.references.ImageContentWriterReference;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.EmbeddedFontCache;
import org.icepdf.fonts.util.EmbeddedFontUtil;

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
import static org.icepdf.core.pobjects.fonts.Font.FONT_DESCRIPTOR_KEY;
import static org.icepdf.core.pobjects.fonts.Font.SIMPLE_FORMAT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.FONT_FILE_2;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_NAME;
import static org.icepdf.core.pobjects.graphics.images.ImageParams.*;

/**
 * Utility for common rendering methods used when generating annotation content stream and supporting resources.
 */
public class ContentWriterUtils {

    private static final Logger logger =
            Logger.getLogger(ContentWriterUtils.class.toString());

    public static final Name EMBEDDED_FONT_NAME = new Name("ice1");

    public static final boolean isEmbedFonts;

    static {
        // sets if file caching is enabled or disabled.
        isEmbedFonts =
                Defs.sysPropertyBoolean("org.icepdf.core.pobjects.annotations.embedFonts.enabled",
                        true);
    }

    public static Reference createSimpleFont(Library library, String fontName) {
        EmbeddedFontCache embeddedFontCache = library.getEmbeddedFontCache();
        Reference embeddedFontReference = embeddedFontCache.getFontReference(fontName);
        if (embeddedFontReference != null) {
            return embeddedFontReference;
        } else {
            // create the font dictionary
            DictionaryEntries fontDictionary = new DictionaryEntries();
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                    org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
            fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
            fontDictionary.put(TO_UNICODE_KEY, IDENTITY_NAME);
            // double check we have an embedded font available for the font name
            if (isEmbedFonts && EmbeddedFontUtil.isFontResourceAvailable() && EmbeddedFontUtil.isOtfFontMapped(fontName)) {
                // write out the font as TrueType with embedded font file
                fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("TrueType"));
                FontDescriptor fontDescriptor = creatFontDescriptor(library, fontName);
                fontDictionary.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());
            } else {
                fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("Type1"));
            }
            SimpleFont font = new SimpleFont(library, fontDictionary);
            font.setPObjectReference(library.getStateManager().getNewReferenceNumber());
            library.getStateManager().addTempChange(new PObject(font, font.getPObjectReference()));
            embeddedFontCache.putFontReference(fontName, font.getPObjectReference());
            return font.getPObjectReference();
        }
    }

    public static void removeSimpleFont(Library library, Reference fontReference) {
        Object obj = library.getObject(fontReference);
        if (obj instanceof SimpleFont) {
            SimpleFont previousFont = (SimpleFont) obj;
            String fontName = previousFont.getBaseFont();
            StateManager stateManager = library.getStateManager();
            EmbeddedFontCache embeddedFontCache = library.getEmbeddedFontCache();
            // remove the reference from the cache
            embeddedFontCache.removeReference(fontName, fontReference);
            // if the font name has no more references remove from state manager
            if (!embeddedFontCache.hasReference(fontName, fontReference)) {
                obj = library.getObject(fontReference);
                if (obj instanceof SimpleFont) {
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
        }
    }

    public static FontDescriptor creatFontDescriptor(Library library, String fontName) {
        StateManager stateManager = library.getStateManager();
        DictionaryEntries fontDescriptorDictionary = new DictionaryEntries();
        fontDescriptorDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                new Name("FontDescriptor"));
        fontDescriptorDictionary.put(new Name("FontName"),
                fontName + EmbeddedFontCache.ICEPDF_EMBEDDED_FONT_SUFFIX);
//            fontDescriptorDictionary.put(new Name("Flags"), 4); // todo this needs to by dynamic for different styles
//        fontDescriptorDictionary.put(new Name("FontBBox"), Arrays.asList(0, -200, 1000, 900));
//        fontDescriptorDictionary.put(new Name("ItalicAngle"), 0);
//        fontDescriptorDictionary.put(new Name("Ascent"), 800);
//        fontDescriptorDictionary.put(new Name("Descent"), -200);
//        fontDescriptorDictionary.put(new Name("CapHeight"), 700);
//        fontDescriptorDictionary.put(new Name("StemV"), 80);

        // create font file stream
        Reference fontFileReference = stateManager.getNewReferenceNumber();
        Stream fontFileStream = FontUtil.createFontFileStream(library, fontName);
        fontFileStream.setPObjectReference(fontFileReference);
        stateManager.addTempChange(new PObject(fontFileStream, fontFileReference));
        fontDescriptorDictionary.put(new Name("FontFile2"), fontFileReference);

        Reference fontDescriptorReference = stateManager.getNewReferenceNumber();
        FontDescriptor fontDescriptor = new FontDescriptor(library, fontDescriptorDictionary);
        fontDescriptor.setPObjectReference(fontDescriptorReference);
        stateManager.addTempChange(new PObject(fontDescriptor, fontDescriptorReference));
        return fontDescriptor;
    }

    public static DictionaryEntries createImageDictionary() {
        DictionaryEntries imageDictionary = new DictionaryEntries();
        imageDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY);
        imageDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, new Name("Type1"));
        return imageDictionary;
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

    public static Point2D.Float addTextSpritesToShapes(FontFile fontFile,
                                                       final float advanceX,
                                                       final float advanceY,
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

    public static FontFile createFont(Library library, String fontName) {
        FontFile fontFile;
        if (isEmbedFonts && EmbeddedFontUtil.isFontResourceAvailable() && EmbeddedFontUtil.isOtfFontMapped(fontName)) {
            try {
                Stream fontFileStream = FontUtil.createFontFileStream(library, fontName);
                fontFile = FontFactory.getInstance().createFontFile(fontFileStream, FontFactory.FONT_TRUE_TYPE, null);
                fontFile = fontFile.deriveFont(Encoding.standardEncoding, null);
                return fontFile;
            } catch (Exception e) {
                logger.warning("Error loading embedded font resource for: " + fontName +
                        ", falling back to system font. " + e.getMessage());
            }
        }
        fontFile = FontManager.getInstance().initialize().getInstance(fontName, 0);
        fontFile = fontFile.deriveFont(Encoding.standardEncoding, null);
        return fontFile;
    }

    /**
     * Saves the font descriptor and font file associated with the given font to the StateManager.
     *
     * @param font
     */
    public static void saveFont(org.icepdf.core.pobjects.fonts.Font font) {
        FontDescriptor fontDescriptor = font.getFontDescriptor();
        if (fontDescriptor != null) {
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
        ImageStream imageStream = ContentWriterUtils.createImageStream(library, reference, bufferedImage, true);
        ImageReference imageReference = new ImageContentWriterReference(imageStream, imageName);
        // stack em up
        shapes.add(new PushDrawCmd());
        shapes.add(new TransformDrawCmd(centeringTransform));
        shapes.add(new TransformDrawCmd(imageTransform));
        shapes.add(new ImageDrawCmd(imageReference));
        shapes.add(new PopDrawCmd());
        return imageStream;
    }

    public static ImageStream createImageStream(Library library, Reference reference, BufferedImage bufferedImage,
                                                boolean useMask) {
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
        // this is pretty rough, will maks any alpha value,  should build a proper maask
        if (useMask && bufferedImage.getColorModel().hasAlpha()) {
            // we need s softer mask for the image.
            ImageUtility.encodeColorKeyMask(imageStream);
        }
        // setup object reference and put in state manager
        StateManager stateManager = library.getStateManager();
        if (reference == null) {
            reference = stateManager.getNewReferenceNumber();
        }
        imageStream.setPObjectReference(reference);
        stateManager.addChange(new PObject(imageStream, reference), true);
        return imageStream;
    }

}
