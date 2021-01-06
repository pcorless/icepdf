package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.FontBoxFont;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.awt.Font.PLAIN;

/**
 * Base class for
 */
public abstract class ZSimpleFont implements FontFile {

    private static final Logger logger =
            Logger.getLogger(ZSimpleFont.class.toString());

    // text layout map, very expensive to create, so we'll cache them.
    private HashMap<String, Point2D.Float> echarAdvanceCache;

    // copied over from font descriptor
    protected float missingWidth;

    // simpleFont properties.
    protected float[] widths;
    protected int firstCh;
    protected float ascent;
    protected float descent;
    protected Rectangle2D bbox = new Rectangle2D.Double(0.0, 0.0, 1.0, 1.0);

    // Why have one encoding when you can three.
    protected Encoding encoding;
    protected char[] cMap;
    protected CMap toUnicode;

    protected FontBoxFont fontBoxFont;
    protected URL source;

    // PDF specific size and text state transform
    protected float size = 1.0f;
    protected AffineTransform fontMatrix = new AffineTransform();

    // todo fontDamaged flags

    @Override
    public Point2D echarAdvance(final char ech) {
        try {
            String name = encoding.getName(ech);
            float advance = 0.001f; // todo should be DW.
            if (name != null) {
                advance = fontBoxFont.getWidth(name);
            } else {
                advance = widths[ech];//* 0.001f
            }
            // widths uses original cid's.
            if (widths != null && ech - firstCh >= 0 && ech - firstCh < widths.length) {
                float width = widths[ech - firstCh];
                AffineTransform fontMatrix = convertFontMatrix(fontBoxFont);
                advance = width / (float) fontMatrix.getScaleX();
            }
            // find any widths in the font descriptor
            else if (missingWidth > 0) {
                AffineTransform fontMatrix = convertFontMatrix(fontBoxFont);
                advance = missingWidth / (float) fontMatrix.getScaleX();
            }
            advance = advance * (float) fontMatrix.getScaleX();

            return new Point2D.Float(advance, 0);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not get font glyph width", e);
            return null;
        }
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            Shape outline = fontBoxFont.getPath(estr);
            if (encoding != null && !fontBoxFont.hasGlyph(estr)) {
                String name = encoding.getName(estr.charAt(0));
                if (name != null) {
                    outline = fontBoxFont.getPath(name);
                }
            }

            // clean up,  not very efficient
            g.translate(x, y);
            g.transform(this.fontMatrix);

            if (TextState.MODE_FILL == mode || TextState.MODE_FILL_STROKE == mode ||
                    TextState.MODE_FILL_ADD == mode || TextState.MODE_FILL_STROKE_ADD == mode) {
                g.fill(outline);
            }
            if (TextState.MODE_STROKE == mode || TextState.MODE_FILL_STROKE == mode ||
                    TextState.MODE_STROKE_ADD == mode || TextState.MODE_FILL_STROKE_ADD == mode) {
                g.draw(outline);
            }
            g.setTransform(af);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Rectangle2D getMaxCharBounds() {
        return new Rectangle2D.Double(0.0, 0.0, 1.0, 1.0);
    }

    @Override
    public CMap getToUnicode() {
        return toUnicode;
    }

    @Override
    public String toUnicode(String displayText) {
        // Check string for displayable Glyphs,  try and substitute any failed ones
        StringBuilder sb = new StringBuilder(displayText.length());
        for (int i = 0; i < displayText.length(); i++) {
            // Updated with displayable glyph when possible
            sb.append(toUnicode(displayText.charAt(i)));
        }
        return sb.toString();
    }

    @Override
    public String toUnicode(char displayChar) {
        // the toUnicode map is used for font substitution and especially for CID fonts.  If toUnicode is available
        // we use it as is, if not then we can use the charDiff mapping, which takes care of font encoding
        // differences.
        char c = toUnicode == null ? getCharDiff(displayChar) : displayChar;

        if (toUnicode != null) {
            return toUnicode.toUnicode(c);
        }
        return String.valueOf(c);
    }

    @Override
    public float getSize() {
        return size;
    }

    @Override
    public double getAscent() {
        return ascent;
    }

    @Override
    public double getDescent() {
        return descent;
    }

    @Override
    public AffineTransform getTransform() {
        return null;
    }

    @Override
    public int getRights() {
        return 0;
    }

    @Override
    public boolean isHinted() {
        return false;
    }

    @Override
    public int getNumGlyphs() {
        return 0;
    }

    @Override
    public int getStyle() {
        return PLAIN;
    }

    @Override
    public char getSpaceEchar() {
        return 0;
    }

    @Override
    public Rectangle2D getEstringBounds(String estr, int beginIndex, int limit) {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public Shape getEstringOutline(String estr, float x, float y) {
        return null;
    }

    @Override
    public ByteEncoding getByteEncoding() {
        return null;
    }

    @Override
    public URL getSource() {
        return source;
    }

    @Override
    public void setIsCid() {

    }

    protected char getCharDiff(char character) {
        if (cMap != null && character < cMap.length) {
            return cMap[character];
        } else {
            return character;
        }
    }

    protected Rectangle2D calculateBbox(Rectangle2D bbox) {
        if (bbox != null) {
            double scaleX = 0.001 / fontMatrix.getScaleX();
            double scaleY = 0.001 / fontMatrix.getScaleY();
            return new Rectangle2D.Double(
                    bbox.getX() * scaleX,
                    bbox.getY() * scaleY,
                    bbox.getWidth() * scaleX,
                    bbox.getHeight() * scaleY);
        }
        return this.bbox;
    }

    protected AffineTransform convertFontMatrix(FontBoxFont fontBoxFont) {
        try {
            java.util.List<Number> matrix = fontBoxFont.getFontMatrix();
            return new AffineTransform(matrix.get(0).floatValue(), matrix.get(1).floatValue(),
                    matrix.get(2).floatValue(), matrix.get(3).floatValue(),
                    matrix.get(4).floatValue(), matrix.get(5).floatValue());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not convert font matrix ", e);
        }
        return new AffineTransform(0.001f, 0, 0, -0.001f, 0, 0);
    }

}
