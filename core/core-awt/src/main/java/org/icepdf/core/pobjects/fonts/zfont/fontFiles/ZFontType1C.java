package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.cff.CFFType1Font;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
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
                fontBoxFont = cffType1Font;
            } catch (IOException e) {
                logger.log(Level.FINE, "Error reading font file with ", e);
//                fontIsDamaged = true;
            }

        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
        }
    }

    private ZFontType1C(ZFontType1C font) {
        this.cffType1Font = font.cffType1Font;
        this.fontBoxFont = this.cffType1Font;
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
        return super.echarAdvance(ech);
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        super.drawEstring(g, estr, x, y, layout, mode, strokeColor);
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType1C font = new ZFontType1C(this);
        font.fontMatrix = convertFontMatrix(cffType1Font);
        font.fontMatrix.concatenate(at);
        font.fontMatrix.scale(font.size, -font.size);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType1C font = new ZFontType1C(this);
        font.encoding = encoding;
        font.toUnicode = toUnicode;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.widths = widths;
        font.cMap = diff != null ? diff : font.cMap;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        return font;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        return cffType1Font.hasGlyph(String.valueOf(ech));
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return cffType1Font.getEncoding();
    }

    @Override
    public String getFamily() {
        return cffType1Font.getName();
    }

    @Override
    public String getName() {
        return cffType1Font.getName();
    }

    @Override
    public FontFile deriveFont(float pointsize) {
        ZFontType1C font = new ZFontType1C(this);
        font.fontMatrix = convertFontMatrix(cffType1Font);
        font.fontMatrix.scale(pointsize, -pointsize);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    private class FontFileByteSource implements CFFParser.ByteSource {
        private final Stream fontStream;

        public FontFileByteSource(Stream fontStream) {
            this.fontStream = fontStream;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return fontStream.getDecodedStreamBytes();
        }
    }
}