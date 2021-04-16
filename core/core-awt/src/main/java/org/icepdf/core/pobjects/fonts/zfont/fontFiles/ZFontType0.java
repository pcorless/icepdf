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

    private CFFCIDFont cidFont;  // Top DICT that uses CIDFont operators
    private FontBoxFont t1Font; // Top DICT that does not use CIDFont operators

    public ZFontType0(Stream fontStream) throws Exception {

        byte[] fontBytes = fontStream.getDecodedStreamBytes();
        CFFFont cffFont = null;
        if (fontBytes != null && fontBytes.length > 0 && (fontBytes[0] & 0xff) == '%') {
            logger.warning("Found PFB but expected embedded CFF font");
            isDamaged = true;
        } else if (fontBytes != null) {
            CFFParser cffParser = new CFFParser();
            try {
                cffFont = cffParser.parse(fontBytes, new FF3ByteSource(fontStream)).get(0);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Can't read the embedded CFF font ", e);
                throw new Exception(e);
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
        super(font);
        this.cidFont = font.cidFont;
        this.t1Font = font.t1Font;
        if (t1Font != null) {
            this.fontBoxFont = font.t1Font;
        } else {
            this.fontBoxFont = font.cidFont;
        }
        this.fontMatrix = convertFontMatrix(fontBoxFont);
    }

    @Override
    public Point2D getAdvance(char ech) {
        float advance = defaultWidth;
        if (widths != null && ech < widths.length) {
            advance = widths[ech];
        }
        if (advance == 0) {
            advance = 1.0f;
        }
        float x = advance * size;//* (float) gsTransform.getScaleX();
        float y = advance * size;//* (float) gsTransform.getShearY();
        return new Point2D.Float(x, y);
    }

    @Override
    public void paint(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
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

    public FontFile deriveFont(float defaultWidth, float[] widths) {
        // parse out the width notation and generate the width
        ZFontType0 font = (ZFontType0) deriveFont(size);
        if (widths != null) {
            font.widths = widths;
            font.defaultWidth = defaultWidth;
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
        // all the info way before we get here.
        else {
            return null;
        }
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType0 font = new ZFontType0(this);
        font.setFontTransform(at);
        return font;
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        ZFontType0 font = new ZFontType0(this);
        font.setPointSize(pointSize);
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType0 font = new ZFontType0(this);
        font.encoding = encoding;
        font.toUnicode = deriveToUnicode(encoding, toUnicode);
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType0 font = new ZFontType0(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.widths = widths;
        font.cMap = diff != null ? diff : font.cMap;
        font.bbox = bbox;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType0 font = new ZFontType0(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.cMap = diff;
        font.bbox = bbox;
        return font;
    }

    @Override
    public boolean canDisplay(char ech) {
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
