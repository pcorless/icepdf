package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.cff.*;
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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType0 extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType0.class.toString());

    // todo shout out to PDFbox.
    private CFFCIDFont cidFont;  // Top DICT that uses CIDFont operators
    private FontBoxFont t1Font; // Top DICT that does not use CIDFont operators

    // todo credit pdfbox for stream correction code
    public ZFontType0(Stream fontStream) {

        byte[] fontBytes = fontStream.getDecodedStreamBytes();
        CFFFont cffFont = null;
        if (fontBytes != null && fontBytes.length > 0 && (fontBytes[0] & 0xff) == '%') {
            // todo throw exception so substitution kicks in? still not too sure what to do here for fallback
            logger.warning("Found PFB but expected embedded CFF font");
//            fontIsDamaged = true;
        } else if (fontBytes != null) {
            CFFParser cffParser = new CFFParser();
            try {
                cffFont = cffParser.parse(fontBytes, new FF3ByteSource(fontStream)).get(0);
            } catch (IOException e) {
                // todo throw exception so substitution kicks in.
                logger.log(Level.WARNING, "Can't read the embedded CFF font ", e);
//                fontIsDamaged = true;
            }
        }

        if (cffFont != null) {
            // embedded
            if (cffFont instanceof CFFCIDFont) {
                cidFont = (CFFCIDFont) cffFont;
                t1Font = null;
                fontBoxFont = cidFont;
            } else {
                cidFont = null;
                t1Font = cffFont;
                fontBoxFont = t1Font;
            }
        }
    }

    private ZFontType0(ZFontType0 font) {
        this.cidFont = font.cidFont;
        this.t1Font = font.t1Font;
        if (t1Font != null) {
            this.fontBoxFont = font.t1Font;
        } else {
            this.fontBoxFont = font.cidFont;
        }
        // todo clean up as this really isn't a simple font.
        this.encoding = font.encoding;
        this.toUnicode = font.toUnicode;
        this.missingWidth = font.missingWidth;
        this.firstCh = font.firstCh;
        this.ascent = font.ascent;
        this.descent = font.descent;
        this.bbox = font.bbox;
        this.widths = font.widths;
        this.cMap = font.cMap;
//        this.maxCharBounds = font.maxCharBounds;
    }

    @Override
    public Point2D echarAdvance(char ech) {

        float advance = 0;
        if (widths != null && ech < widths.length) {
            advance = widths[ech];
        }
        advance = advance * size * (float) fontMatrix.getScaleX();
        return new Point2D.Float(advance, 0);
//        return super.echarAdvance(ech);
//        this.fontBoxFont.getWidth()
//        if (t1Font != null) {
//            t1Font.getWidth()
//        } else {
//            this.fontBoxFont = font.cidFont;
//        }
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            Shape outline = null;

            int cid = estr.charAt(0);
            Type2CharString charstring = getType2CharString(cid);
            if (charstring != null) {
                outline = charstring.getPath();
            } else if (t1Font instanceof CFFType1Font) {
                outline = ((CFFType1Font) t1Font).getType2CharString(cid).getPath();
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

    public FontFile deriveFont(float defaultWidth, float[] widths) {
        // parse out the width notation and generate the width
        ZFontType0 font = (ZFontType0) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
        } else {

        }

        return font;
    }

    public Type2CharString getType2CharString(int cid) throws IOException {
        if (cidFont != null) {
            return cidFont.getType2CharString(cid);
        } else if (t1Font instanceof CFFType1Font) {
            return ((CFFType1Font) t1Font).getType2CharString(cid);
        }
        // todo, not sure we want to use font substitution at this point are cut in much earlier as we have
        //  all the info way before we get here.
        else {
            return null;
        }
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        if (cidFont != null) {
            ZFontType0 font = new ZFontType0(this);
            font.fontMatrix = convertFontMatrix(cidFont);
            font.fontMatrix.concatenate(at);
            font.fontMatrix.scale(font.size, -font.size);
            return font;
        } else {
            return null;
        }
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        if (cidFont != null) {
            ZFontType0 font = new ZFontType0(this);
            font.encoding = encoding;
            font.toUnicode = toUnicode;
            return font;
        } else {
            return null;
        }
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        if (cidFont != null) {
            ZFontType0 font = new ZFontType0(this);
            font.missingWidth = this.missingWidth;
            font.firstCh = firstCh;
            font.ascent = ascent;
            font.descent = descent;
            font.widths = widths;
            font.cMap = diff != null ? diff : font.cMap;
            font.bbox = calculateBbox(bbox);
            return font;
        } else {
            return null;
        }
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        if (cidFont != null) {
            ZFontType0 font = new ZFontType0(this);
            font.missingWidth = this.missingWidth;
            font.firstCh = firstCh;
            font.ascent = ascent;
            font.descent = descent;
            font.cMap = diff;
            font.bbox = calculateBbox(bbox);
            return font;
        } else {
            return null;
        }
    }

    @Override
    public boolean canDisplayEchar(char ech) {
//        try {
//            if (cidFont != null) {
//                return cidFont.hasGlyph("\\" + ech);
//            } else {
//                return t1Font.hasGlyph(String.valueOf(ech));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return true;
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    @Override
    public String getFamily() {
        try {
            if (cidFont != null) {
                cidFont.getName();
            } else {
                t1Font.getName();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getName() {
        return getFamily();
    }

    @Override
    public FontFile deriveFont(float pointsize) {
        if (cidFont != null) {
            ZFontType0 font = new ZFontType0(this);
            font.fontMatrix = convertFontMatrix(cidFont);
            font.fontMatrix.scale(pointsize, -pointsize);
            //        font.maxCharBounds = this.maxCharBounds;
            return font;
        } else {
            return null;
        }
    }

    /**
     * Some Type 1 fonts have an invalid Length1, which causes the binary segment of the font
     * to be truncated, see PDFBOX-2350, PDFBOX-3677.
     *
     * @param bytes   Type 1 stream bytes
     * @param length1 Length1 from the Type 1 stream
     * @return repaired Length1 value
     */
    private int repairLength1(byte[] bytes, int length1) {
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

    /**
     * Some Type 1 fonts have an invalid Length2, see PDFBOX-3475. A negative /Length2 brings an
     * IllegalArgumentException in Arrays.copyOfRange(), a huge value eats up memory because of
     * padding.
     *
     * @param bytes   Type 1 stream bytes
     * @param length1 Length1 from the Type 1 stream
     * @param length2 Length2 from the Type 1 stream
     * @return repaired Length2 value
     */
    private int repairLength2(byte[] bytes, int length1, int length2) {
        // repair Length2 if necessary
        if (length2 < 0 || length2 > bytes.length - length1) {
            logger.warning("Ignored invalid Length2 " + length2 + " for Type 1 font " + getName());
            return bytes.length - length1;
        }
        return length2;
    }

    private static int findBinaryOffsetAfterExec(byte[] bytes, int startOffset) {
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

    private class FF3ByteSource implements CFFParser.ByteSource {
        private final Stream fontStream;

        public FF3ByteSource(Stream fontStream) {
            this.fontStream = fontStream;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return fontStream.getDecodedStreamBytes();
        }
    }
}
