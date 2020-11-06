package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontTrueType extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType1C.class.toString());

    private TrueTypeFont trueTypeFont;

    public ZFontTrueType(InputStream inputStream) throws IOException {
        this(inputStream.readAllBytes());
    }

    public ZFontTrueType(Stream fontStream) {
        this(fontStream.getDecodedStreamBytes());
    }

    public ZFontTrueType(byte[] fontBytes) {
        try {
            if (fontBytes != null) {
                TTFParser ttfParser = new TTFParser(true);
                trueTypeFont = ttfParser.parse(new ByteArrayInputStream(fontBytes));
                fontBoxFont = trueTypeFont;
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
//                fontIsDamaged = true;
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
        }
    }

    private ZFontTrueType(ZFontTrueType font) {
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
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
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.fontMatrix = convertFontMatrix(trueTypeFont);
        font.fontMatrix.concatenate(at);
        font.fontMatrix.scale(font.size, -font.size);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.encoding = encoding;
        font.toUnicode = toUnicode;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        ZFontTrueType font = new ZFontTrueType(this);
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
        ZFontTrueType font = new ZFontTrueType(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        return font;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        try {
            return trueTypeFont.hasGlyph(String.valueOf(ech));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public FontFile deriveFont(float pointsize) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.fontMatrix = convertFontMatrix(trueTypeFont);
        font.fontMatrix.scale(pointsize, -pointsize);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public String getFamily() {
        try {
            return trueTypeFont.getName();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getName() {
        try {
            return trueTypeFont.getName();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
