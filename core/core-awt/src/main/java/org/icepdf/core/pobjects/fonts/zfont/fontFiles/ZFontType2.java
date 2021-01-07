package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

public class ZFontType2 extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType2.class.toString());

    private TrueTypeFont trueTypeFont;

    // todo figure out how to translate this to cmap
    private int[] cid2gid;

    // todo credit pdfbox
    public ZFontType2(Stream fontStream) {
        try {
            byte[] fontBytes = fontStream.getDecodedStreamBytes();
            // embedded OTF or TTF
            OTFParser otfParser = new OTFParser(true);
            OpenTypeFont otf = otfParser.parse(new ByteArrayInputStream(fontBytes));
            trueTypeFont = otf;

//            if (otf.isPostScript())
//            {
//                // PDFBOX-3344 contains PostScript outlines instead of TrueType
//                fontIsDamaged = true;
//                LOG.warn("Found CFF/OTF but expected embedded TTF font " + fd.getFontName());
//            }
        } catch (NullPointerException | IOException e) {
//            // NPE due to TTF parser being buggy
//            fontIsDamaged = true;
//            LOG.warn("Could not read embedded OTF for font " + getBaseFont(), e);
        }
    }

    private ZFontType2(ZFontType2 font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
    }

    @Override
    public Point2D echarAdvance(char ech) {
        if (encoding != null) {
            return super.echarAdvance(ech);
        } else if (widths != null) {
            float advance = widths[ech] * 0.001f;
            advance = advance * size;//* (float) fontMatrix.getScaleX();
            return new Point2D.Float(advance, 0);
        } else {
            return new Point2D.Float(0.001f, 0);
        }
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            char echar = estr.charAt(0);
            int gid = getCharToGid(echar);
            GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(gid);
            Shape outline;
            if (glyphData == null) {
                outline = new GeneralPath();
            } else {
                // must scaled by caller using FontMatrix
                outline = glyphData.getPath();
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
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    public FontFile deriveFont(float defaultWidth, float[] widths) {
        ZFontType2 font = (ZFontType2) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
        } else {

        }

        return font;
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType2 font = new ZFontType2(this);
        font.fontMatrix = convertFontMatrix(trueTypeFont);
        font.fontMatrix.concatenate(at);
        font.fontMatrix.scale(font.size, -font.size);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(float pointsize) {
        ZFontType2 font = new ZFontType2(this);
        font.fontMatrix = convertFontMatrix(trueTypeFont);
        font.fontMatrix.scale(pointsize, -pointsize);
        font.size = pointsize;
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType2 font = new ZFontType2(this);
        font.encoding = encoding;
        font.toUnicode = toUnicode;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType2 font = new ZFontType2(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.widths = widths;
        font.cMap = diff;
        font.bbox = calculateBbox(bbox);
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType2 font = new ZFontType2(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        font.bbox = calculateBbox(bbox);
        return font;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
//        try {
//            return trueTypeFont.hasGlyph(String.valueOf(ech));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return false;
        // todo directly effects parsing cid string,  need to look at cmap instead to derive byte lenght for char
        //  first, well pretty sure anyways.
        return true;
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

    private int getCharToGid(char character) {
//        if (isType0CidSub) {
//            // apply the typ0 encoding
//            ech = type0Encoding.toSelector(ech);
//            // apply the UCS2 encoding
//            ech = touni_.toUnicode(ech).charAt(0);
//            // finally we can get a usable glyph;
//            return c2g_.toSelector(ech);
//        } else {
        return codeToGID(character);
//        }
    }

    public int codeToGID(int code) {

        return code;
    }


}
