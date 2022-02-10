package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.type1.Type1Font;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.util.InputStreamUtil;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType1 extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType1.class.toString());

    private static final int PFB_START_MARKER = 0x80;

    private Type1Font type1Font;

    public ZFontType1(Stream fontStream) throws Exception {
        try {
            // add length correction code
            int length1 = fontStream.getInt(new Name("Length1"));
            int length2 = fontStream.getInt(new Name("Length2"));

            byte[] fontBytes = fontStream.getDecodedStreamBytes();

            length1 = repairLength1(fontBytes, length1);
            length2 = repairLength2(fontBytes, length1, length2);

            if (fontBytes.length > 0 && (fontBytes[0] & 0xff) == PFB_START_MARKER) {
                // some bad files embed the entire PFB
                type1Font = Type1Font.createWithPFB(fontBytes);
            } else {
                // the PFB embedded as two segments back-to-back
                byte[] segment1 = Arrays.copyOfRange(fontBytes, 0, length1);
                byte[] segment2 = Arrays.copyOfRange(fontBytes, length1, length1 + length2);

                // empty streams are simply ignored
                if (length1 > 0 && length2 > 0) {
                    type1Font = Type1Font.createWithSegments(segment1, segment2);
                }
            }
            fontBoxFont = type1Font;
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
            throw new Exception(e);
        }
    }

    public ZFontType1(URL url) throws IOException {
        byte[] fontBytes = InputStreamUtil.readAllBytes(url.openStream());
        source = url;
        type1Font = Type1Font.createWithPFB(fontBytes);
    }

    private ZFontType1(ZFontType1 font) {
        super(font);
        this.type1Font = font.type1Font;
        this.fontBoxFont = this.type1Font;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
    }

    @Override
    public Point2D getAdvance(char ech) {
        return super.getAdvance(ech);
    }

    @Override
    protected String codeToName(String estr) {
        return codeToName(estr.charAt(0));
    }

    protected String codeToName(int code) {
        if (encoding != null &&
                org.icepdf.core.pobjects.fonts.zfont.Encoding.STANDARD_ENCODING_NAME.equals(encoding.getName())) {
            return type1Font.getEncoding().getName(code);
        } else {
            return String.valueOf((char) code);
        }
    }

    @Override
    public void paint(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        super.paint(g, estr, x, y, layout, mode, strokeColor);
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType1 font = new ZFontType1(this);
        font.setFontTransform(at);
        return font;
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        ZFontType1 font = new ZFontType1(this);
        font.setPointSize(pointSize);
        return font;
    }


    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType1 font = new ZFontType1(this);
        font.encoding = encoding;
        font.toUnicode = deriveToUnicode(encoding, toUnicode);
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType1 font = new ZFontType1(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        if (widths != null && widths.length > 0) {
            font.widths = widths;
        }
        font.cMap = diff != null ? diff : font.cMap;
        font.bbox = bbox;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType1 font = new ZFontType1(this);
        font.missingWidth = this.missingWidth;
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
        return type1Font.hasGlyph(codeToName(ech));
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return type1Font.getEncoding();
    }

    @Override
    public String getFamily() {
        return type1Font.getFamilyName();
    }

    @Override
    public String getName() {
        return type1Font.getName();
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

}
