package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.cff.CFFType1Font;
import org.icepdf.core.pobjects.Stream;
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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType1C extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType1C.class.toString());

    private CFFType1Font cffType1Font;

    public ZFontType1C(Stream fontStream) {
        try {
            byte[] fontBytes = fontStream.getDecodedStreamBytes();

            try {
                if (fontBytes != null) {
                    // note: this could be an OpenType file, fortunately CFFParser can handle that
                    CFFParser cffParser = new CFFParser();
                    cffType1Font = (CFFType1Font) cffParser.parse(fontBytes, new FontFileByteSource(fontStream)).get(0);
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Error reading font file with ", e);
//                fontIsDamaged = true;
            }

        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
        }
    }

    private ZFontType1C(ZFontType1C font) {
//        this.echarAdvanceCache = font.echarAdvanceCache;
        this.cffType1Font = font.cffType1Font;
        this.encoding = font.encoding;
        this.toUnicode = font.toUnicode;
        this.missingWidth = font.missingWidth;
        this.firstCh = font.firstCh;
        this.ascent = font.ascent;
        this.descent = font.descent;
        this.widths = font.widths;
        this.cMap = font.cMap;
//        this.maxCharBounds = font.maxCharBounds;
    }

    @Override
    public Point2D echarAdvance(char ech) {
        try {
            float advance = cffType1Font.getWidth(String.valueOf(ech)) * 0.001f;

            // widths uses original cid's, not the converted to unicode value.
            if (widths != null && ech - firstCh >= 0 && ech - firstCh < widths.length) {
                advance = widths[ech - firstCh] * size;
            }
//            else if (cidWidths != null) {
//                Float width = cidWidths.get((int) ech);
//                if (width != null) {
//                    advance = cidWidths.get((int) ech) * awtFont.getSize2D();
//                }
//            }
            // find any widths in the font descriptor
            else if (missingWidth > 0) {
                advance = missingWidth / 1000f;
            }

            return new Point2D.Float(advance, 0);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return cffType1Font.getEncoding();
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType1C font = new ZFontType1C(this);
        java.util.List<Number> matrix = cffType1Font.getFontMatrix();
        font.fontMatrix = new AffineTransform(matrix.get(0).floatValue(), matrix.get(1).floatValue(),
                matrix.get(2).floatValue(), matrix.get(3).floatValue(),
                matrix.get(4).floatValue(), matrix.get(5).floatValue());
        font.fontMatrix.concatenate(at);
        font.fontMatrix.scale(font.size, font.size);
        // clear font metric cache if we change the font's transform
//        if (!font.getTransform().equals(this.awtFont.getTransform())) {
//            this.echarAdvanceCache.clear();
//        }
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType1C font = new ZFontType1C(this);
//        this.echarAdvanceCache.clear();
        font.encoding = encoding;
        font.toUnicode = toUnicode;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
//        this.echarAdvanceCache.clear();
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.widths = widths;
        font.cMap = diff;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
//        this.echarAdvanceCache.clear();
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        // todo figure out why we have a map over an array, actually actually probably to do with the size and range of cid fonts.
//        font.cidWidths = widths;
        font.cMap = diff;
        return font;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        return cffType1Font.hasGlyph(String.valueOf(ech));
    }

    @Override
    public void setIsCid() {

    }

    @Override
    public FontFile deriveFont(float pointsize) {
        ZFontType1C font = new ZFontType1C(this);
        java.util.List<Number> matrix = cffType1Font.getFontMatrix();
        // todo make simple font method to make getting affineTransform easier.
        font.fontMatrix = new AffineTransform(matrix.get(0).floatValue(), matrix.get(1).floatValue(),
                matrix.get(2).floatValue(), matrix.get(3).floatValue(),
                matrix.get(4).floatValue(), matrix.get(5).floatValue());
        font.fontMatrix.concatenate(font.fontMatrix);
        font.fontMatrix.scale(pointsize, pointsize);
        // clear font metric cache if we change the font's transform
//        if (!font.getTransform().equals(this.awtFont.getTransform())) {
//            this.echarAdvanceCache.clear();
//        }
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public CMap getToUnicode() {
        return null;
    }

    @Override
    public String toUnicode(String displayText) {
        return null;
    }

    @Override
    public String toUnicode(char displayChar) {
        return null;
    }

    @Override
    public String getFamily() {
        return cffType1Font.getName();
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
    public Rectangle2D getMaxCharBounds() {
        return new Rectangle2D.Double(0.0, 0.0, 1.0, 1.0);
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
    public String getName() {
        return null;
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
        return 0;
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
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            java.util.List<Number> fontMatrix = cffType1Font.getFontMatrix();
            Shape outline = cffType1Font.getPath(estr);
            org.apache.fontbox.encoding.Encoding encoding = cffType1Font.getEncoding();

            if (!cffType1Font.hasGlyph(estr)) {
                // todo need a way to use the new Encoding...
                org.icepdf.core.pobjects.fonts.zfont.Encoding encoding1 = org.icepdf.core.pobjects.fonts.zfont.Encoding.getInstance(org.icepdf.core.pobjects.fonts.zfont.Encoding.STANDARD_ENCODING_NAME);
                String name = encoding1.getName(estr.charAt(0));
                if (name != null) {
                    outline = cffType1Font.getPath(name);
                } else {
                    outline = cffType1Font.getPath("F");
                }
            }

            g.translate(x, y);
            g.transform(new AffineTransform(0.001f, 0, 0, -0.001f, 0, 0));

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
    public Shape getEstringOutline(String estr, float x, float y) {
        return null;
    }

    @Override
    public ByteEncoding getByteEncoding() {
        return null;
    }

    @Override
    public URL getSource() {
        return null;
    }


    private class FontFileByteSource implements CFFParser.ByteSource {
        private Stream fontStream;

        public FontFileByteSource(Stream fontStream) {
            this.fontStream = fontStream;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return fontStream.getDecodedStreamBytes();
        }
    }
}
