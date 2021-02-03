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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType2 extends ZSimpleFont { //extends ZFontTrueType {

    private static final Logger logger =
            Logger.getLogger(ZFontType2.class.toString());

    private TrueTypeFont trueTypeFont;

    private CMap cid2gid;

    // todo credit pdfbox
    public ZFontType2(Stream fontStream) throws Exception {
        try {
            byte[] fontBytes = fontStream.getDecodedStreamBytes();
            // embedded OTF or TTF
            OTFParser otfParser = new OTFParser(true);
            OpenTypeFont otf = otfParser.parse(new ByteArrayInputStream(fontBytes));
            trueTypeFont = otf;

//            extractCmapTable();

//            if (otf.isPostScript())
//            {
//                // PDFBOX-3344 contains PostScript outlines instead of TrueType
//                fontIsDamaged = true;
//                LOG.warn("Found CFF/OTF but expected embedded TTF font " + fd.getFontName());
//            }
        } catch (Throwable e) {
//            // NPE due to TTF parser being buggy
//            fontIsDamaged = true;
            logger.log(Level.SEVERE, "Could not initialize type2 font", e);
            throw new Exception(e);
        }
    }

    public ZFontType2(ZFontTrueType font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
    }

    private ZFontType2(ZFontType2 font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
        this.cid2gid = font.cid2gid;
    }

    @Override
    public Point2D echarAdvance(char ech) {
        // todo, needs som more work.
        float advance = defaultWidth;
        if (encoding != null) {
            return super.echarAdvance(ech);
        } else if (widths != null && ech < widths.length) {
//            int gid = getCharToGid(ech);
            float width = widths[ech];
            if (width <= 1) {
                advance = width;
            } else {
                advance = width * 0.001f;
            }
        }
        if (advance == 0) {
            advance = 1.0f;
        }

        float x = advance * size;//* (float) gsTransform.getScaleX();
        float y = advance * size;//* (float) gsTransform.getShearY();
        return new Point2D.Float(x, y);
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
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    public FontFile deriveFont(float defaultWidth, float[] widths) {
        ZFontType2 font = (ZFontType2) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
            font.defaultWidth = defaultWidth;
        }
        return font;
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType2 font = new ZFontType2(this);
        font.setFontTransform(at);
        return font;
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        ZFontType2 font = new ZFontType2(this);
        font.setPointSize(pointSize);
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
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType2 font = new ZFontType2(this);
        // todo widths array, have worked this inanother class, maybe not be applicable here?
        return font;
    }

    public ZFontType2 deriveFont(CMap cid2gid, CMap toUnicode) {    // used by PDF Type 0
        ZFontType2 font = new ZFontType2(this);
        font.setCID(cid2gid, toUnicode);
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

    private void setCID(CMap cid, CMap uni) {
        cid2gid = cid != null ? cid : null;//ur_.c2g_;
//        touni_ = uni != null ? uni : /*c2g_==ur_.c2g_? CMap.IDENTITY:=>same as default*/ CMap.IDENTITY;
//        spacech_ = Integer.MIN_VALUE;
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
        if (cid2gid != null) {
            return cid2gid.toSelector((char) code);
        }
//        else {
//            return super.codeToGID(code);
//        }
        return code;
    }

}
