package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.cff.Type2CharString;
import org.apache.fontbox.ttf.*;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.GlyphList;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.util.InputStreamUtil;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontTrueType extends ZSimpleFont implements Cloneable {

    private static final Logger logger =
            Logger.getLogger(ZFontTrueType.class.toString());

    private static final int START_RANGE_F000 = 0xF000;
    private static final int START_RANGE_F100 = 0xF100;
    private static final int START_RANGE_F200 = 0xF200;

    private CmapSubtable cmapWinUnicode;
    private CmapSubtable cmapWinSymbol;
    private CmapSubtable cmapMacRoman;

    private HorizontalMetricsTable horizontalMetricsTable;

    protected TrueTypeFont trueTypeFont;

    protected ZFontTrueType() {

    }

    public ZFontTrueType(URL url) throws Exception {
        this(InputStreamUtil.readAllBytes(url.openStream()));
        source = url;
    }

    public ZFontTrueType(Stream fontStream) throws Exception {
        this(fontStream.getDecodedStreamBytes());
    }

    public ZFontTrueType(byte[] fontBytes) throws Exception {
        try {
            if (fontBytes != null) {
                TTFParser ttfParser = new TTFParser(true);
                trueTypeFont = ttfParser.parse(new ByteArrayInputStream(fontBytes));
                fontBoxFont = trueTypeFont;

                extractCmapTable();
                extractMetricsTable();
            }
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with", e);
            throw new Exception(e);
        }
    }

    protected ZFontTrueType(ZFontTrueType font) {
        super(font);
        this.trueTypeFont = font.trueTypeFont;
        this.fontBoxFont = this.trueTypeFont;
        this.cmapWinUnicode = font.cmapWinUnicode;
        this.cmapWinSymbol = font.cmapWinSymbol;
        this.cmapMacRoman = font.cmapMacRoman;
        this.horizontalMetricsTable = font.horizontalMetricsTable;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
        font.missingWidth = this.missingWidth;
    }

    @Override
    public Point2D getAdvance(char ech) {
        float advance = 0;
        try {
            int gid = getCharToGid(ech);
            int glyphId = ech - firstCh;
            if (trueTypeFont == null || gid > trueTypeFont.getNumberOfGlyphs()) {
                return new Point2D.Float(advance, 0);
            } else if (widths != null && glyphId >= 0 && glyphId < widths.length && widths[glyphId] <= 1) {
                advance = widths[glyphId];
            }
            if (advance == 0) {
                int metricsWidth = horizontalMetricsTable.getAdvanceWidth(
                        Math.min(gid, (int) horizontalMetricsTable.getLength() - 1));
                advance = metricsWidth * (float) fontMatrix.getScaleX();
            }
            advance = (float) (advance * size * gsTransform.getScaleX());
            return new Point2D.Float(advance, 0);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error getting font width", e);
        }
        return new Point2D.Float(1.0f, 0);
    }

    @Override
    public void paint(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            char echar = estr.charAt(0);

            Shape outline;
            int gid;
            if (trueTypeFont instanceof OpenTypeFont) {
                int cid = codeToGID(echar);
                Type2CharString charstring = ((OpenTypeFont) trueTypeFont).getCFF().getFont().getType2CharString(cid);
                outline = charstring.getPath();
            } else {
                gid = getCharToGid(echar);
                GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(gid);
                if (glyphData == null) {
                    outline = new GeneralPath();
                } else {
                    outline = glyphData.getPath();
                }
            }
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
            logger.log(Level.FINE, "Error painting TrueType font", e);
        }
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    public FontFile deriveFont(float defaultWidth, float[] widths) {
        ZFontTrueType font = (ZFontTrueType) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
        }
        return font;
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.setFontTransform(at);
        return font;
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.setPointSize(pointSize);
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontTrueType font = new ZFontTrueType(this);
        if (encoding != null) {
            font.encoding = encoding;
        }
        font.toUnicode = deriveToUnicode(encoding, toUnicode);
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        if (widths != null && widths.length > 0) {
            font.widths = widths;
        }
        font.cMap = diff;
        font.bbox = bbox;
        font.maxCharBounds = null;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        font.bbox = bbox;
        font.maxCharBounds = null;
        return font;
    }

    @Override
    public boolean canDisplay(char ech) {
//        try {
//            return trueTypeFont.hasGlyph(String.valueOf(ech));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return false;
        // todo directly effects parsing cid string,  need to look at cmap instead to derive byte length for char
        //  first, well pretty sure anyways.
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
            logger.log(Level.FINE, "Error finding font family name", e);
        }
        return null;
    }

    protected int getCharToGid(char character) {
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
        int gid = 0;
        try {
            if (cmapWinSymbol == null) {
                if (encoding == null) {
                    return cmapMacRoman.getGlyphId(code);
                }
                String name = encoding.getName(code);
                // this is slow,  would like to improve.
                if (!".notdef".equals(name)) {
                    // (3, 1) - (Windows, Unicode)
                    if (cmapWinUnicode != null && name != null) {
                        String unicode = GlyphList.getAdobeGlyphList().toUnicode(name);
                        if (unicode != null) {
                            int uni = unicode.codePointAt(0);
                            gid = cmapWinUnicode.getGlyphId(uni);
                        }
                    }
                    // (1, 0) - (Macintosh, Roman)
                    if (gid == 0 && cmapMacRoman != null && name != null) {
                        Character macCode = org.icepdf.core.pobjects.fonts.zfont.Encoding.macRomanEncoding.getChar(name);
                        if (macCode != null) {
                            gid = cmapMacRoman.getGlyphId(macCode);
                        }
                    }
                }
                // 'post' table - comment is incorrect but keeping it for now as the post table is an encoding
                // that we can use. With this little hack we still have issue showing some glyphs correctly.
                // likely looking at font substitutions corner cases.
                if (gid == 0) {
                    // still not happy with this, lots of mystery and deception that needs to be figured out.
                    if (encoding != null) {
                        if (cmapWinUnicode != null &&
                                (encoding.getName().equals(org.icepdf.core.pobjects.fonts.zfont.Encoding.WIN_ANSI_ENCODING_NAME)
                                        || encoding.getName().equals("diff"))) {
                            gid = cmapWinUnicode.getGlyphId(code);
                        } else if (encoding.getName().startsWith("Mac") && cmapMacRoman != null) {
                            gid = cmapMacRoman.getGlyphId(code);
                        } else {
                            gid = code;
                        }
                    } else {
                        gid = code;
                    }
                }
            }
            // symbolic
            else {
                // (3, 0) - (Windows, Symbol)
                gid = cmapWinSymbol.getGlyphId(code);
                if (code >= 0 && code <= 0xFF) {
                    // the CMap may use one of the following code ranges,
                    // so that we have to add the high byte to get the
                    // mapped value
                    if (gid == 0) {
                        // F000 - F0FF
                        gid = cmapWinSymbol.getGlyphId(code + START_RANGE_F000);
                    }
                    if (gid == 0) {
                        // F100 - F1FF
                        gid = cmapWinSymbol.getGlyphId(code + START_RANGE_F100);
                    }
                    if (gid == 0) {
                        // F200 - F2FF
                        gid = cmapWinSymbol.getGlyphId(code + START_RANGE_F200);
                    }
                }

                // (1, 0) - (Mac, Roman)
                if (gid == 0 && cmapMacRoman != null) {
                    gid = cmapMacRoman.getGlyphId(code);
                }

                // PDFBOX-3965: fallback for font has that the symbol flag but isn't
                if (gid == 0 && cmapWinUnicode != null && encoding != null) {
                    String name = encoding.getName(code);
                    if (".notdef".equals(name)) {
                        return 0;
                    }
                    String unicode = GlyphList.getAdobeGlyphList().toUnicode(name);
                    if (unicode != null) {
                        int uni = unicode.codePointAt(0);
                        gid = cmapWinUnicode.getGlyphId(uni);
                    }
                }
            }
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error deriving codeToGID", e);
        }

        return gid;
    }

    protected void extractMetricsTable() throws IOException {
        horizontalMetricsTable = trueTypeFont.getHorizontalMetrics();
    }

    protected void extractCmapTable() throws IOException {
        CmapTable cmapTable = trueTypeFont.getCmap();
        if (cmapTable != null) {
            // get all relevant "cmap" subtables
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
                }
            }
        }
    }

}
