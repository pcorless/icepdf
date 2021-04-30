package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.FontBoxFont;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.GlyphList;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
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
    protected Rectangle2D maxCharBounds;

    // cid specific, todo new subclass if we get a few more?
    protected float defaultWidth;

    // Why have one encoding when you can three.
    protected Encoding encoding;
    protected char[] cMap;
    protected CMap toUnicode;

    protected Boolean isSymbolic;

    protected FontBoxFont fontBoxFont;
    protected URL source;

    // PDF specific size and text state transform
    protected float size = 1.0f;
    protected AffineTransform fontMatrix = new AffineTransform();
    protected AffineTransform fontTransform = new AffineTransform();
    protected AffineTransform gsTransform = new AffineTransform();

    protected boolean isDamaged;

    protected ZSimpleFont() {

    }

    protected ZSimpleFont(ZSimpleFont font) {
        this.encoding = font.encoding;
        this.isSymbolic = font.isSymbolic;
        this.toUnicode = font.toUnicode;
        this.missingWidth = font.missingWidth;
        this.firstCh = font.firstCh;
        this.ascent = font.ascent;
        this.descent = font.descent;
        this.defaultWidth = font.defaultWidth;
        this.bbox = font.bbox;
        this.widths = font.widths;
        this.cMap = font.cMap;
        this.size = font.size;
        this.source = font.source;
        this.gsTransform = new AffineTransform(gsTransform);
        this.fontMatrix = new AffineTransform(font.fontMatrix);
        this.fontTransform = new AffineTransform(font.fontTransform);
    }

    @Override
    public Point2D getAdvance(final char ech) {
        try {
            String name = encoding != null ? encoding.getName(ech) : null;
            float advance = 0.001f; // todo should be DW.
            if (name != null) {
                advance = fontBoxFont.getWidth(name);
            }
            // widths uses original cid's.
            if (widths != null && ech - firstCh >= 0 && ech - firstCh < widths.length) {
                float width = widths[ech - firstCh];
                if (width > 0) {
                    advance = width / (float) fontMatrix.getScaleX();
                }
            }
            // find any widths in the font descriptor
            else if (missingWidth > 0) {
                advance = missingWidth / (float) fontTransform.getScaleX();
            }
            advance = advance * (float) fontTransform.getScaleX();

            return new Point2D.Float(advance, 0);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not get font glyph width", e);
            return null;
        }
    }

    // allows sub classes to override the codeToName logic.
    protected String codeToName(String estr) {
        return estr;
    }

    protected CMap deriveToUnicode(org.icepdf.core.pobjects.fonts.Encoding encoding, CMap toUnicode) {
        if (toUnicode != null) {
            return toUnicode;
        }
        // try and guess the encoding
        if (encoding != null) {
            return GlyphList.guessToUnicode(encoding);
        }
        return org.icepdf.core.pobjects.fonts.zfont.cmap.CMap.IDENTITY;
    }

    @Override
    public void paint(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            String name = codeToName(estr);
            Shape outline = fontBoxFont.getPath(name);
            if (encoding != null && !fontBoxFont.hasGlyph(name)) {
                name = encoding.getName(estr.charAt(0));
                if (name != null) {
                    outline = fontBoxFont.getPath(name);
                }
            }

            // clean up,  not very efficient
            g.translate(x, y);
            g.transform(this.fontTransform);

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
    public Shape getOutline(String estr, float x, float y) {
        try {
            String name = codeToName(estr);
            Shape glyph = fontBoxFont.getPath(name);
            if (encoding != null && !fontBoxFont.hasGlyph(name)) {
                name = encoding.getName(estr.charAt(0));
                if (name != null) {
                    glyph = fontBoxFont.getPath(name);
                }
            }

            Area outline = new Area(glyph);
            AffineTransform transform = new AffineTransform();
            transform.translate(x, y);
            transform.concatenate(fontTransform);
            outline = outline.createTransformedArea(transform);
            return outline;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Rectangle2D getMaxCharBounds() {
        AffineTransform af = new AffineTransform();
        af.scale(size, -size);
        af.concatenate(fontMatrix);
        return af.createTransformedShape(bbox).getBounds2D();
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
        if (ascent != 0) {
            return ascent * size;
        } else {
            if (maxCharBounds == null) {
                maxCharBounds = getMaxCharBounds();
            }
            return -maxCharBounds.getY();
        }
    }

    @Override
    public double getDescent() {
        if (descent != 0) {
            return -Math.abs(descent) * size;
        } else {
            double height = getHeight();
            double ascent = getAscent();
            return -Math.abs(height - ascent);
        }
    }

    public double getHeight() {
        if (maxCharBounds == null) {
            maxCharBounds = getMaxCharBounds();
        }
        return maxCharBounds.getHeight();
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
    public char getSpace() {
        return 0;
    }

    @Override
    public Rectangle2D getBounds(String estr, int beginIndex, int limit) {
        return null;
    }

    @Override
    public String getFormat() {
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

    @Override
    public AffineTransform getFontTransform() {
        return fontTransform;
    }

    public boolean isDamaged() {
        return isDamaged;
    }

    protected void setFontTransform(AffineTransform at) {
        gsTransform = new AffineTransform(at);
        fontTransform = new AffineTransform(fontMatrix);
        fontTransform.concatenate(at);
        fontTransform.scale(size, -size);
    }

    protected void setPointSize(float pointSize) {
        fontTransform = new AffineTransform(fontMatrix);
        fontTransform.concatenate(gsTransform);
        fontTransform.scale(pointSize, -pointSize);
        size = pointSize;
        maxCharBounds = null;
    }

    protected char getCharDiff(char character) {
        if (cMap != null && character < cMap.length) {
            return cMap[character];
        } else {
            return character;
        }
    }

    protected AffineTransform convertFontMatrix(FontBoxFont fontBoxFont) {
        try {
            java.util.List<Number> matrix = fontBoxFont.getFontMatrix();
            return new AffineTransform(matrix.get(0).floatValue(), matrix.get(1).floatValue(),
                    -matrix.get(2).floatValue(), matrix.get(3).floatValue(),
                    matrix.get(4).floatValue(), matrix.get(5).floatValue());
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Could not convert font matrix ", e);
        }
        return new AffineTransform(0.001f, 0, 0, -0.001f, 0, 0);
    }

    /**
     * Some Type 1 fonts have an invalid Length1, which causes the binary segment of the font
     * to be truncated, see PDFBOX-2350, PDFBOX-3677.
     *
     * @param bytes   Type 1 stream bytes
     * @param length1 Length1 from the Type 1 stream
     * @return repaired Length1 value
     */
    protected int repairLength1(byte[] bytes, int length1) {
        // scan backwards from the end of the first segment to find 'exec'
        int offset = Math.max(0, length1 - 4);
        if (offset <= 0 || offset > bytes.length - 4) {
            offset = bytes.length - 4;
        }

        offset = findBinaryOffsetAfterExec(bytes, offset);
        if (offset == 0 && length1 > 0) {
            // 2nd try with brute force
            offset = findBinaryOffsetAfterExec(bytes, bytes.length - 4);
        }

        if (length1 - offset != 0 && offset > 0) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Ignored invalid Length1 " + length1 + " for Type 1 font " + getName());
            }
            return offset;
        }

        return length1;
    }

    protected static int findBinaryOffsetAfterExec(byte[] bytes, int startOffset) {
        int offset = startOffset;
        while (offset > 0) {
            if (bytes[offset + 0] == 'e'
                    && bytes[offset + 1] == 'x'
                    && bytes[offset + 2] == 'e'
                    && bytes[offset + 3] == 'c') {
                offset += 4;
                // skip additional CR LF space characters
                while (offset < bytes.length &&
                        (bytes[offset] == '\r' || bytes[offset] == '\n' ||
                                bytes[offset] == ' ' || bytes[offset] == '\t')) {
                    offset++;
                }
                break;
            }
            offset--;
        }
        return offset;
    }

}
