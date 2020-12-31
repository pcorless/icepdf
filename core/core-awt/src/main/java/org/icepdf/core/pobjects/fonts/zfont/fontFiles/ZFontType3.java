package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.content.ContentParser;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType3 extends ZSimpleFont implements Cloneable {
    private static final Logger logger =
            Logger.getLogger(ZFontType3.class.toString());

    public static final Name FONT_BBOX_KEY = new Name("FontBBox");
    public static final Name FONT_MATRIX_KEY = new Name("FontMatrix");
    public static final Name CHAR_PROCS_KEY = new Name("CharProcs");
    public static final Name RESOURCES_KEY = new Name("Resources");

    private Library library;
    protected HashMap entries;
    private HashMap charProcedures;
    private HashMap<Name, SoftReference<Shapes>> charShapesCache;
    private HashMap<Name, PRectangle> charBBoxes;
    private HashMap<Name, Point2D.Float> charWidths;
    private Resources resources;

    private AffineTransform glyph2user;
    private PRectangle bBox;

    private Resources parentResource;

    /**
     * <p>Creates a new Type3 Font program.</p>
     *
     * @param library    PDF document's object library
     * @param properties dictionary value associated with this object
     */
    public ZFontType3(Library library, HashMap properties) {
        this.library = library;
        entries = properties;

        glyph2user = new AffineTransform(1.0f, 0.0f, 0.0f, 1f, 0.0f, 0.0f);

        Object o = library.getObject(properties, FONT_BBOX_KEY);
        if (o instanceof List) {
            List rectangle = (List) o;
            bBox = new PRectangle(rectangle);
            bBox.setRect(bBox.getX(), bBox.getY(), bBox.getWidth(), bBox.getHeight());
            // couple corner cases of [0 0 0 0] /FontBBox, zero height will not intersect the clip.
            // width is taken care by the /Width entry so zero is fine.
            if (bBox.getHeight() == 0) {
                bBox.height = 1;
            }
        }

        o = library.getObject(properties, FONT_MATRIX_KEY);
        if (o instanceof List) {
            List oFontMatrix = (List) o;
            fontMatrix = new AffineTransform(((Number) oFontMatrix.get(0)).floatValue(),
                    ((Number) oFontMatrix.get(1)).floatValue(),
                    ((Number) oFontMatrix.get(2)).floatValue(),
                    ((Number) oFontMatrix.get(3)).floatValue(),
                    ((Number) oFontMatrix.get(4)).floatValue(),
                    ((Number) oFontMatrix.get(5)).floatValue());
        } else {
            fontMatrix = new AffineTransform(0.001f, 0.0f, 0.0f, 0.001f, 0.0f, 0.0f);
        }

        // todo fix up all simple font unicode assignments
        toUnicode = null;//CMap.IDENTITY;

        // CharProcs resources, contains glyph name/stream pairs.
        o = library.getObject(properties, CHAR_PROCS_KEY);
        if (o instanceof HashMap) {
            charProcedures = (HashMap) o;
            int length = charProcedures.size();
            charShapesCache = new HashMap<>(length);
            charBBoxes = new HashMap<>(length);
            charWidths = new HashMap<>(length);
        }
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType3 font = (ZFontType3) deriveFont(this.size);
        font.encoding = encoding;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType3 font = (ZFontType3) deriveFont(this.size);
        font.encoding = encoding;
        font.widths = widths;
        font.firstCh = firstCh;
        font.missingWidth = missingWidth;
        font.ascent = ascent;
        font.descent = descent;
        font.bbox = calculateBbox(bbox);
        // todo diff cmap, likely not a type3 thing, see if an example shows up.
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        // not really applicable for type3 but lets play nice just in case
        ZFontType3 font = (ZFontType3) deriveFont(this.size);
        font.encoding = encoding;
        font.firstCh = firstCh;
        font.missingWidth = missingWidth;
        font.ascent = ascent;
        font.descent = descent;
        font.bbox = calculateBbox(bbox);
        return font;
    }

    public FontFile deriveFont(float size) {
        ZFontType3 font = null;
        try {
            font = (ZFontType3) clone();
        } catch (CloneNotSupportedException e) {
            logger.log(Level.FINE, "Could not derive Type3 font ", e);
        }
        if (font != null) {
            font.size = size;
            font.setGlyph2User(new AffineTransform());
        }
        return font;
    }

    public FontFile deriveFont(AffineTransform affinetransform) {
        ZFontType3 font = (ZFontType3) deriveFont(this.size);
        font.setGlyph2User(affinetransform);
        return font;
    }

    /**
     * <p>Draw the specified string data to a graphics context.</p>
     *
     * @param g2d    graphics context in which the string will be drawn to.
     * @param string string to draw
     * @param x      x-coordinate of the string rendering location
     * @param y      y-coordinate of the string rendering location
     * @param layout layout mode of this font, not value for type3 font
     * @param mode   rendering mode, not applicable for type3 fonts.
     */
    public void drawEstring(Graphics2D g2d, String string,
                            float x, float y,
                            long layout, int mode, Color color) {

        AffineTransform oldTransform = g2d.getTransform();
        AffineTransform currentCTM = g2d.getTransform();
        currentCTM.concatenate(fontMatrix);
        g2d.setTransform(currentCTM);

        g2d.translate(x / fontMatrix.getScaleX(), y / fontMatrix.getScaleY());
        g2d.scale(size, -size);
        char displayChar;
        try {
            Shapes shape;
            for (int i = 0, length = string.length(); i < length; i++) {
                displayChar = string.charAt(i);
                shape = getGlyph(displayChar, color);
                if (shape != null) {
                    shape.paint(g2d);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Type3 font painting interrupted.");
        }
        g2d.setTransform(oldTransform);
    }

    public Point2D echarAdvance(char displayChar) {
        String charName = encoding.getName(displayChar);
        float width = 0f;
        if (charName != null && displayChar >= firstCh && displayChar <= 255) {
            width = (float) (widths[displayChar - firstCh] * 1000 * fontMatrix.getScaleX());
        }

        if (width == 0.0f && charWidths.size() > 0) {
            Object tmp = charWidths.get(charName);
            if (tmp != null) {
                width = (float) (((Point2D.Float) tmp).x * fontMatrix.getScaleX());
            }
        }

        return new Point2D.Float(width * size, width * size);
    }

    /**
     * <p>Sets the BBox of this Type3 font for the specified name.  This is important
     * for Type3 fonts as it defines the clip.  The trick though, is that the BBox
     * data is stored inside of the glyph's content stream and thus must be set
     * by the content parser.  The PDF operator is "d1".</p>
     *
     * @param name name of the glyph being parsed (Type3 specific)
     * @param bBox bounding box for glyph.
     */
    public void setBBox(Name name, PRectangle bBox) {
        charBBoxes.put(name, bBox);
    }

    public Rectangle2D getMaxCharBounds() {
        AffineTransform af = new AffineTransform();
        af.scale(size, size);
        af.concatenate(fontMatrix);
        return af.createTransformedShape(bBox.toJava2dCoordinates()).getBounds2D();
    }

    public Rectangle2D getCharBounds(char displayChar) {
        Rectangle2D r = getMaxCharBounds();

        String charName = encoding.getName(displayChar);
        float width = 0f;
        if (widths != null && displayChar - firstCh >= 0 && displayChar - firstCh < widths.length) {
            width = widths[displayChar - firstCh];

        }

        if (width == 0.0f) {
            width = charWidths.get(charName).x;
        }

        PRectangle charRect = charBBoxes.get(charName);
        r.setRect(0.0, r.getY(),
                width * size,
                charRect.getHeight() * size);
        return r;
    }

    /**
     * <p>Sets the horizontal displacement of this Type3 font for the specified
     * name. Like BBox this value must be set from inside the content parser as
     * the data is stored in the glyph's content stream.  The PDF operator is
     * "d0". </p>
     *
     * @param name         name of the glph being parsed (Type3 specific)
     * @param displacement horizontal and vertical displacement
     */
    public void setHorDisplacement(Name name, Point2D.Float displacement) {
        charWidths.put(name, displacement);
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        // not really a thing for type3
        return null;
    }

    /**
     * <p>The font's Format.</p>
     *
     * @return font format "Type3"
     */
    public String getFormat() {
        return "Type3";
    }

    /**
     * <p>The font's Name.</p>
     *
     * @return font format "Type 3"
     */
    public String getName() {
        return "Type 3";
    }

    /**
     * <p>The font's Family.</p>
     *
     * @return font format "Type 3"
     */
    public String getFamily() {
        return "Type 3";
    }

    /**
     * <p>Gets the number of glyphs in this Type 3 font family.</p>
     *
     * @return number of glyphs descibed in font family.
     */
    public int getNumGlyphs() {
        return charProcedures.size();
    }

    /**
     * <p>Gets the width of the space character.</p>
     *
     * @return space character, always 32.
     */
    public char getSpaceEchar() {
        return 32;
    }

    public ByteEncoding getByteEncoding() {
        return ByteEncoding.ONE_BYTE;
    }

    public boolean canDisplay(char c) {
        return canDisplayEchar(c);
    }

    public boolean canDisplayEchar(char c) {
        return (getGlyph(c, Color.black) != null);
    }

    /**
     * Does the font program use hinted glyphs. Always false for Type3 fonts
     *
     * @return false.
     */
    public boolean isHinted() {
        return false;
    }

    private void setGlyph2User(AffineTransform affinetransform) {
        float fontSize = getSize();
        glyph2user = new AffineTransform(fontMatrix);
        AffineTransform affinetransform1 =
                new AffineTransform(affinetransform.getScaleX() * (double) fontSize,
                        affinetransform.getShearY(),
                        affinetransform.getShearX(),
                        -affinetransform.getScaleY() * (double) fontSize,
                        0.0f, 0.0f);
        glyph2user.concatenate(affinetransform1);
    }

    private Shapes getGlyph(int characterIndex, Color fillColor) {
        // Gets the name of the type3 character
        Name charName = new Name(encoding.getName((char) characterIndex));
        // the same glyph name can have a different fills so we need to store the color in the key
        Name charKey = new Name(encoding.getName((char) characterIndex) + fillColor.getRGB());
        SoftReference<Shapes> softShapes = charShapesCache.get(charKey);
        if (softShapes == null || softShapes.get() == null) {
            Object o = library.getObject(charProcedures.get(charName));
            if (o instanceof Stream) {
                Stream stream = (Stream) o;

                // get resources if any for char processing content streams.
                if (resources == null) {
                    resources = library.getResources(entries, RESOURCES_KEY);
                }
                if (resources == null) {
                    resources = parentResource;
                }
                ContentParser cp = new ContentParser(library, resources);
                // Read the type 3 content stream
                try {
                    GraphicsState gs = new GraphicsState(new Shapes());
                    gs.setFillColor(fillColor);
                    cp.setGraphicsState(gs);
                    cp.setGlyph2UserSpaceScale((float) glyph2user.getScaleX());
                    Shapes charShapes = cp.parse(new byte[][]{stream.getDecodedStreamBytes()}, null).getShapes();
                    TextState textState = cp.getGraphicsState().getTextState();
                    setBBox(charName, textState.getType3BBox());
                    setHorDisplacement(charName, textState.getType3HorizontalDisplacement());
                    charShapesCache.put(charKey, new SoftReference<Shapes>(charShapes));
                    // one could add a bbox clip here, but no example where it is needed
                    // not adding it should speed things up a bit
                    return charShapes;
                } catch (IOException e) {
                    logger.log(Level.FINE, "Error loading Type3 stream data.", e);
                } catch (InterruptedException e) {
                    logger.log(Level.FINE, "Thread Interrupted while parsing Type3 stream data.", e);
                }
            }
        } else {
            return softShapes.get();
        }
        return null;
    }

    /**
     * Returns an empty Area object as type 3 fonts can not be used for
     * clipping paths.
     *
     * @param estr text to calculate glyph outline shape
     * @param x    x coordinate to translate outline shape.
     * @param y    y coordinate to translate outline shape.
     * @return empty Area object.
     */
    public Shape getEstringOutline(String estr, float x, float y) {
        return new Area();
    }

    public Resources getParentResource() {
        return parentResource;
    }

    public void setParentResource(Resources parentResource) {
        this.parentResource = parentResource;
    }
}
