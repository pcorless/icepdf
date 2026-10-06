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
package org.icepdf.core.pobjects.annotations.utils;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.apache.fontbox.ttf.CmapLookup;
import org.icepdf.core.pobjects.fonts.builders.SimpleFontFactory;
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
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Stream.FILTER_KEY;
import static org.icepdf.core.pobjects.fonts.Font.CID_FORMAT;
import static org.icepdf.core.pobjects.fonts.Font.SIMPLE_FORMAT;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.FONT_FILE_2;

/**
 * Utility for common rendering methods used when generating annotation content stream and supporting resources.
 */
public class ContentWriterUtils {

    private static final Logger logger =
            Logger.getLogger(ContentWriterUtils.class.getName());

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
                                     StateManager stateManager) {
        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();
        Rectangle2D bbox = appearanceState.getBbox();
        Rectangle2D formBbox = new Rectangle2D.Float(0, 0,
                (float) bbox.getWidth(), (float) bbox.getHeight());
        form.setAppearance(shapes, matrix, formBbox);
        stateManager.addChange(new PObject(form, form.getPObjectReference()));
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
                                                       float fontSize,
                                                       float lineSpacing,
                                                       Color fontColor,
                                                       String content) {
        FontFile fontFile = trueTypeeFontSubSetter.getFontFile();
        // The font kind is a property of all the text that shares the font, not of this run, so it is
        // asked of the subsetter - which every run is declared to - rather than worked out here.  A
        // signature appearance lays out four separate lines into one font: deciding per run wrote
        // one-byte codes for the Latin lines and two-byte CIDs for a Japanese one, into a single
        // font that can only be read one of those ways.
        trueTypeeFontSubSetter.addToSubset(content);
        boolean composite = trueTypeeFontSubSetter.requiresCompositeFont();
        CmapLookup cmapLookup = composite ? unicodeCmapLookup(trueTypeeFontSubSetter) : null;

        TextSprite textSprites =
                new TextSprite(fontFile,
                        composite ? CID_FORMAT : SIMPLE_FORMAT,
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
                // Under Identity-H the code in the content stream is the CID, and the CID is the
                // glyph's index in the original font - not its Unicode value.
                char code = cmapLookup != null
                        ? (char) cmapLookup.getGlyphId(currentChar) : currentChar;
                textSprites.addText(
                        code, // cid
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

    /**
     * @return the font's Unicode character map, which is what a CID is looked up through
     */
    private static CmapLookup unicodeCmapLookup(TrueTypeFontEmbedder fontSubSetter) {
        try {
            return fontSubSetter.getFontFile().getTrueTypeFont().getUnicodeCmapLookup();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the character map of "
                    + fontSubSetter.getFontFile().getName(), e);
        }
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
    /**
     * Moves a newly built object, and everything it refers to, from the temporary cache into the
     * changes that actually get written.
     * <p>
     * A font is not one object. A simple font reaches a descriptor, a font programme and a CMap; a
     * composite font also reaches a descendant font, a CIDToGIDMap and a CIDSet. Every one of them is
     * built as a temporary change and every one has to be promoted, because a reference to an object
     * that was never written is a dangling reference - and a reader following it gets nothing, with
     * no error to say why. Promoting a hand-written list of keys is how the /ToUnicode CMap came to
     * be written as a dangling reference once already, so this follows whatever the font actually
     * refers to instead.
     */
    private static void promoteTempObjects(StateManager stateManager, Object entry, Set<Reference> visited) {
        if (entry instanceof Reference) {
            Reference reference = (Reference) entry;
            if (!visited.add(reference)) {
                return;
            }
            PObject pObject = stateManager.getTempChange(reference);
            if (pObject != null) {
                stateManager.addChange(pObject);
                promoteTempObjects(stateManager, pObject.getObject(), visited);
            }
        } else if (entry instanceof List) {
            for (Object element : (List<?>) entry) {
                promoteTempObjects(stateManager, element, visited);
            }
        } else if (entry instanceof Dictionary) {
            promoteTempObjects(stateManager, ((Dictionary) entry).getEntries(), visited);
        } else if (entry instanceof DictionaryEntries) {
            for (Object value : ((DictionaryEntries) entry).values()) {
                promoteTempObjects(stateManager, value, visited);
            }
        }
    }

    public static void saveFont(org.icepdf.core.pobjects.fonts.Font font) {
        StateManager stateManager = font.getLibrary().getStateManager();
        Set<Reference> visited = new HashSet<>();

        FontDescriptor fontDescriptor = font.getFontDescriptor();
        if (fontDescriptor != null && fontDescriptor.getPObjectReference() != null) {
            stateManager.addChange(new PObject(fontDescriptor, fontDescriptor.getPObjectReference()));
            visited.add(fontDescriptor.getPObjectReference());
            promoteTempObjects(stateManager, fontDescriptor.getEntries(), visited);
        }
        // A composite font has no descriptor of its own - the descendant owns it - so this cannot be
        // conditional on there being one, which is what used to leave a Type 0 font with nothing
        // written but the parent dictionary.
        promoteTempObjects(stateManager, font.getEntries(), visited);
    }

    /**
     * @see #addImageToShapes(Library, Name, Reference, Reference, BufferedImage, Shapes, Rectangle2D, float)
     */
    public static ImageStream addImageToShapes(Library library, Name imageName, Reference reference,
                                               BufferedImage bufferedImage, Shapes shapes,
                                               Rectangle2D bbox, float scale) {
        return addImageToShapes(library, imageName, reference, null, bufferedImage, shapes, bbox, scale);
    }

    /**
     * Writes an image into an appearance stream's shapes, centred in {@code region} and never
     * larger than it, and registers the image XObject it draws.
     * <p>
     * The image's transparency is carried through as a soft mask, so an image drawn over a coloured
     * background or over other appearance content shows that content through its transparent and
     * partly transparent pixels rather than a keyed-out block of colour.
     *
     * @param library           document library
     * @param imageName         resource name the content stream refers to the image by
     * @param reference         object number for the image, or null for a new one
     * @param softMaskReference object number for the image's soft mask, or null for a new one
     * @param bufferedImage     image to draw
     * @param shapes            appearance shapes to draw it into
     * @param region            where in the appearance to draw it, in the shapes' own space - which
     *                          {@link #createAppearanceShapes} sets up with the origin at the top
     *                          left of the bounding box and y running down.  Pass the whole bounding
     *                          box to use all of it, or a part of it to leave room for something
     *                          else, such as the text beside a signature image
     * @param scale             percentage of the region the image fills.  100 fits the image to the
     *                          region, keeping its aspect ratio; less leaves it smaller within the
     *                          same region, centred.  A share of the space rather than of the
     *                          image's own pixel size, because the pixel size is not something the
     *                          person choosing the image can see - the same percentage would
     *                          otherwise give a 200 pixel stamp and a 2000 pixel scan of the same
     *                          signature wildly different results, and anything above the region's
     *                          own size would simply be clipped
     * @return the registered image stream
     */
    public static ImageStream addImageToShapes(Library library, Name imageName, Reference reference,
                                               Reference softMaskReference, BufferedImage bufferedImage,
                                               Shapes shapes, Rectangle2D region, float scale) {
        // add image xObject.  Done first: the image that ends up drawn is the normalized one the
        // stream holds, whose dimensions are what the placement has to be measured against.
        ImageStream imageStream = ImageStream.getInstance(library, reference, softMaskReference,
                bufferedImage, true);
        BufferedImage image = imageStream.getDecodedImage();

        float fit = Math.min((float) region.getWidth() / image.getWidth(),
                (float) region.getHeight() / image.getHeight());
        scale = fit * Math.max(0, Math.min(100, scale)) / 100;
        float scaledWidth = image.getWidth() * scale;
        float scaledHeight = image.getHeight() * scale;

        // Centred in the region.  The y translation is the image's bottom edge rather than its top
        // because the height is negative: the shapes' space runs y down and a PDF image is drawn up
        // from its own origin, so the placement is flipped back here.
        AffineTransform imageTransform = new AffineTransform(
                scaledWidth, 0, 0, -scaledHeight,
                region.getX() + (region.getWidth() - scaledWidth) / 2,
                region.getY() + (region.getHeight() + scaledHeight) / 2);
        ImageReference imageReference = new ImageContentWriterReference(imageStream, imageName);
        // stack em up
        shapes.add(new PushDrawCmd());
        shapes.add(new TransformDrawCmd(imageTransform));
        shapes.add(new ImageDrawCmd(imageReference));
        shapes.add(new PopDrawCmd());
        return imageStream;
    }

    /**
     * How wide {@code text} is when drawn by {@code fontFile}, in the same units the appearance is
     * laid out in.
     * <p>
     * Measured through the font that actually draws it, and character by character the way
     * {@link #addTextSpritesToShapes} advances.  Measuring with an equivalent {@code java.awt.Font}
     * instead - which is what the signature appearance used to do to work out its margin - asks a
     * different font, found by name, for metrics that only happen to be close: the text was laid out
     * against one set of advances and positioned against another.
     *
     * @param fontFile font the text will be drawn with, already derived to its point size
     * @param text     text to measure
     * @return the width of the text
     */
    public static float measureTextWidth(FontFile fontFile, String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\n' || character == '\r') {
                continue;
            }
            width += (float) fontFile.getAdvance(character).getX();
        }
        return width;
    }

}
