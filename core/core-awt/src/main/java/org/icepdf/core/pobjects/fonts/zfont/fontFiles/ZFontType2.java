package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.*;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType2 extends ZSimpleFont { //extends ZFontTrueType {

    private static final Logger logger =
            Logger.getLogger(ZFontType2.class.toString());

    private final TrueTypeFont trueTypeFont;

    private CmapSubtable cmapWinUnicode;
    private CmapSubtable cmapWinSymbol;
    private CmapSubtable cmapMacRoman;

    private CmapLookup cmapLookup;
    private CMap cmapEncoding;
    private int[] cid2gid;

    public ZFontType2(Stream fontStream) throws Exception {
        try {
            byte[] fontBytes = fontStream.getDecodedStreamBytes();
            // embedded OTF or TTF
            OTFParser otfParser = new OTFParser(true);
            OpenTypeFont openTypeFont = otfParser.parse(new RandomAccessReadBuffer(fontBytes));
            trueTypeFont = openTypeFont;
            if (openTypeFont.isPostScript()) {
                isDamaged = true;
                logger.warning("Found CFF/OTF but expected embedded TTF font " + trueTypeFont.getName());
            }
            cmapLookup = trueTypeFont.getUnicodeCmapLookup(false);
            extractCmapTable();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not initialize type2 font", e);
            throw e;
        }
    }

    public ZFontType2(ZFontTrueType font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
        this.isTypeCidSubstitution = font.isTypeCidSubstitution;
        this.encoding = font.encoding;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
        try {
            cmapLookup = trueTypeFont.getUnicodeCmapLookup(false);
            extractCmapTable();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not initialize type2 font unicode CMap lookup", e);
        }
    }

    private ZFontType2(ZFontType2 font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
        this.encoding = font.encoding;
        this.isTypeCidSubstitution = font.isTypeCidSubstitution;
        this.ucs2Cmap = font.ucs2Cmap;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
        this.cid2gid = font.cid2gid;
        this.cmapLookup = font.cmapLookup;
        this.toUnicode = font.toUnicode;
        this.cmapEncoding = font.cmapEncoding;
        this.cmapWinUnicode = font.cmapWinUnicode;
        this.cmapMacRoman = font.cmapMacRoman;
        this.cmapWinSymbol = font.cmapWinSymbol;
    }

    @Override
    public Point2D getAdvance(char ech) {
        float advance = defaultWidth;
        int gid = ech;
        try {
            if (cid2gid == null) {
                gid = getCharToGid(ech);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (widths != null && gid < widths.length) {
            advance = widths[gid];
        }
        if (advance == 0) {
            if (defaultWidth > 0.0f) {
                advance = defaultWidth;
            } else {
                advance = 1.0f;
            }
        }
        float x = advance * size * (float) gsTransform.getScaleX();
        float y = advance * size * (float) gsTransform.getShearY();
        return new Point2D.Float(x, y);
    }

    @Override
    public void paint(Graphics2D g, char estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            int gid = getCharToGid(estr);
            GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(gid);
            Shape outline;
            if (glyphData == null) {
                outline = new GeneralPath();
            } else {
                // must be scaled by caller using FontMatrix
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
            logger.log(Level.FINE, "Error painting FontType2 font", e);
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
        }
        font.defaultWidth = defaultWidth;
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
        font.toUnicode = deriveToUnicode(encoding, toUnicode);
        return font;
    }

    public FontFile deriveFont(Encoding encoding, CMap cmapEncoding, CMap toUnicode) {
        ZFontType2 font = new ZFontType2(this);
        font.encoding = encoding;
        font.cmapEncoding = cmapEncoding != null ? cmapEncoding : this.toUnicode;
        font.toUnicode = deriveToUnicode(encoding, toUnicode != null ? toUnicode : cmapEncoding);
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent,
                               Rectangle2D bbox, char[] diff) {
        ZFontType2 font = new ZFontType2(this);
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        font.bbox = bbox;
        if (widths != null && widths.length > 0) {
            font.widths = widths;
        }
        font.maxCharBounds = null;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent,
                               float descent, Rectangle2D bbox, char[] diff) {
        ZFontType2 font = new ZFontType2(this);
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        font.bbox = bbox;
        font.maxCharBounds = null;
        return font;
    }

    public ZFontType2 deriveFont(int[] cid2gid, CMap toUnicode) {    // used by PDF Type 0
        ZFontType2 font = new ZFontType2(this);
        font.setCID(cid2gid, toUnicode);
        return font;
    }

    @Override
    public boolean canDisplay(char ech) {
        if (isTypeCidSubstitution) {
            try {
                int gid = getCharToGid(ech);
                return gid != 0;
            } catch (IOException e) {
                logger.warning("Error checking if character can be displayed: " + ech + ", " + e.getMessage());
            }
            return false;
        }
        return true;
    }

    @Override
    public String getFamily() {
        try {
            return trueTypeFont.getName();
        } catch (IOException e) {
            logger.log(Level.FINE, "Error finding font family name", e);
        }
        return null;
    }

    @Override
    public String getName() {
        try {
            return trueTypeFont.getName();
        } catch (IOException e) {
            logger.log(Level.FINE, "Error finding font name", e);
        }
        return null;
    }

    public void setIsCidSubstituted() {
        this.isTypeCidSubstitution = true;
    }

    public void setUcs2Cmap(CMap cmap) {
        this.ucs2Cmap = cmap;
    }

    private void setCID(int[] cid, CMap uni) {
        cid2gid = cid;
        toUnicode = uni;
    }

    private int getCharToGid(int code) throws IOException {
        if (isTypeCidSubstitution) {
            // apply the typ0 encoding
            CMap cmapEncoding = this.cmapEncoding != null ? this.cmapEncoding : toUnicode;
            int echar = cmapEncoding.toCID(code);
            // apply the UCS2 encoding
            String eString = ucs2Cmap.toUnicode(echar);
            // finally we can get a usable glyph;
            CmapLookup cmapLookup = trueTypeFont.getUnicodeCmapLookup(false);
            echar = cmapLookup.getGlyphId(eString.codePointAt(0));
            return echar;
        } else {
            CMap cmapEncoding = this.cmapEncoding != null ? this.cmapEncoding : toUnicode;
            int echar = cmapEncoding.toCID(code);
            if (cid2gid != null && echar < cid2gid.length) {
                return cid2gid[echar];
            } else {
                int gid = 0;
                if (this.cmapEncoding != null) {
                    gid = cmapEncoding.toCID((char) code);
                }
                if (gid == 0 && cmapWinUnicode != null) {
                    gid = cmapWinUnicode.getGlyphId(code);
                }
                if (gid == 0 && cmapMacRoman != null) {
                    String macCode = encoding.getName(code);
                    if (macCode != null) {
                        gid = cmapMacRoman.getGlyphId(macCode.codePointAt(0));
                    }
                }
                if (gid != 0) {
                    echar = gid;
                } else {
                    echar = code; // fallback to the code itself if no mapping found
                }
            }

            return echar;
        }
    }

    private void extractCmapTable() throws IOException {
        CmapTable cmapTable = trueTypeFont.getCmap();
        if (cmapTable != null) {
            CmapSubtable[] cmaps = cmapTable.getCmaps();
            for (CmapSubtable cmap : cmaps) {
                if (CmapTable.PLATFORM_WINDOWS == cmap.getPlatformId()) {
                    if (CmapTable.ENCODING_WIN_UNICODE_BMP == cmap.getPlatformEncodingId()) {
                        cmapWinUnicode = cmap;
                    } else if (CmapTable.ENCODING_WIN_SYMBOL == cmap.getPlatformEncodingId()) {
                        cmapWinSymbol = cmap;
                    }
                } else if (CmapTable.PLATFORM_MACINTOSH == cmap.getPlatformId()
                        && CmapTable.ENCODING_MAC_ROMAN == cmap.getPlatformEncodingId()) {
                    cmapMacRoman = cmap;
                } else if (CmapTable.PLATFORM_UNICODE == cmap.getPlatformId()
                        && CmapTable.ENCODING_UNICODE_1_0 == cmap.getPlatformEncodingId()) {
                    cmapWinUnicode = cmap;
                } else if (CmapTable.PLATFORM_UNICODE == cmap.getPlatformId()
                        && CmapTable.ENCODING_UNICODE_2_0_BMP == cmap.getPlatformEncodingId()) {
                    cmapWinUnicode = cmap;
                }
            }
        }
    }


}
