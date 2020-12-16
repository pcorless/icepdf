package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.*;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.GlyphList;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
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

    private static final int START_RANGE_F000 = 0xF000;
    private static final int START_RANGE_F100 = 0xF100;
    private static final int START_RANGE_F200 = 0xF200;

    private CmapSubtable cmapWinUnicode;
    private CmapSubtable cmapWinSymbol;
    private CmapSubtable cmapMacRoman;

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

                extractCmapTable();
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
        this.cmapWinUnicode = font.cmapWinUnicode;
        this.cmapWinSymbol = font.cmapWinSymbol;
        this.cmapMacRoman = font.cmapMacRoman;
//        this.maxCharBounds = font.maxCharBounds;
    }

    @Override
    public Point2D echarAdvance(char ech) {
        if (encoding != null) {
            return super.echarAdvance(ech);
        } else if (widths != null) {
            float advance = widths[ech] * 0.001f;
            advance = advance * size;// * (float) fontMatrix.getScaleX();
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
        ZFontTrueType font = (ZFontTrueType) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
        } else {

        }

        return font;
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
    public FontFile deriveFont(float pointsize) {
        ZFontTrueType font = new ZFontTrueType(this);
        font.fontMatrix = convertFontMatrix(trueTypeFont);
        font.fontMatrix.scale(pointsize, -pointsize);
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
        int gid = 0;
        try {
            // todo clean up this is setup for font substitution not an embedded font
            if (true) {//!isSymbolic()) {
                String name = encoding != null ? encoding.getName(code) : ".notdef";  // todo fix hack
                if (".notdef".equals(name)) {
                    return cmapWinUnicode.getGlyphId(code); // null
                } else {
                    // (3, 1) - (Windows, Unicode)
                    if (cmapWinUnicode != null) {
                        String unicode = GlyphList.getAdobeGlyphList().toUnicode(name);
                        if (unicode != null) {
                            int uni = unicode.codePointAt(0);
                            gid = cmapWinUnicode.getGlyphId(uni);
                        }
                    }
                    // (1, 0) - (Macintosh, Roman)
                    if (gid == 0 && cmapMacRoman != null) {
                        return cmapMacRoman.getGlyphId(code);
//                        int macCode = org.icepdf.core.pobjects.fonts.zfont.Encoding.macRomanEncoding.getChar(name);// INVERTED_MACOS_ROMAN.get(name);
//                    if (macCode != null) {
//                        gid = cmapMacRoman.getGlyphId(code);
//                    }
                    }
                    // 'post' table
                    if (gid == 0) {
                        gid = trueTypeFont.nameToGID(name);
                    }
                }
            }
            // symbolic
            else {
                // (3, 0) - (Windows, Symbol)
                if (cmapWinSymbol != null) {
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
                }

                // (1, 0) - (Mac, Roman)
                if (gid == 0 && cmapMacRoman != null) {
                    gid = cmapMacRoman.getGlyphId(code);
                }

                // PDFBOX-3965: fallback for font has that the symbol flag but isn't
                //            if (gid == 0 && cmapWinUnicode != null && encoding != null) {
                //                String name = encoding.getName(code);
                //                if (".notdef".equals(name)) {
                //                    return 0;
                //                }
                //                String unicode = GlyphList.getAdobeGlyphList().toUnicode(name);
                //                if (unicode != null) {
                //                    int uni = unicode.codePointAt(0);
                //                    gid = cmapWinUnicode.getGlyphId(uni);
                //                }
                //            }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return gid;
    }

    private void extractCmapTable() throws IOException {
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
