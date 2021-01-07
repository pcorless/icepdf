package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.type1.Type1Font;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

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

    // todo credit pdfbox for stream correction code
    public ZFontType1(Stream fontStream) {
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
        }
    }

    public ZFontType1(URL url) throws IOException {
        byte[] fontBytes = url.openStream().readAllBytes();
        source = url;
        // todo clean up error handling
        type1Font = Type1Font.createWithPFB(fontBytes);
    }

    private ZFontType1(ZFontType1 font) {
        super(font);
        this.type1Font = font.type1Font;
        this.fontBoxFont = this.type1Font;
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
        ZFontType1 font = new ZFontType1(this);
        font.fontMatrix = convertFontMatrix(type1Font);
        font.fontMatrix.concatenate(at);
        font.fontMatrix.scale(font.size, -font.size);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType1 font = new ZFontType1(this);
        font.encoding = encoding;
        font.toUnicode = toUnicode;
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType1 font = new ZFontType1(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        font.widths = widths;
        font.cMap = diff != null ? diff : font.cMap;
        font.bbox = calculateBbox(bbox);
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
        font.bbox = calculateBbox(bbox);
        return font;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        return type1Font.hasGlyph(String.valueOf(ech));
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

    @Override
    public FontFile deriveFont(float pointsize) {
        ZFontType1 font = new ZFontType1(this);
        font.fontMatrix = convertFontMatrix(type1Font);
        font.fontMatrix.scale(pointsize, -pointsize);
//        font.maxCharBounds = this.maxCharBounds;
        return font;
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
}
