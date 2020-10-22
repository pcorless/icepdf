package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.type1.Type1Font;
import org.icepdf.core.pobjects.Name;
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
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FontType1 implements FontFile {

    private static final Logger logger =
            Logger.getLogger(FontType1.class.toString());

    private static final int PFB_START_MARKER = 0x80;

    private Type1Font type1Font;

    // store deriveFont state
    // - size, transform, encoding, cmap, widths, size
    //  maybe this should be simplefont state?

    // todo fontDamaged flags

    public FontType1(Stream fontStream) {
        InputStream in = null;
        try {
            // add length correction code
            int length1 = fontStream.getInt(new Name("Length1"));
            int length2 = fontStream.getInt(new Name("Length2"));

            byte[] fontBytes = fontStream.getDecodedStreamBytes();

            length1 = repairLength1(fontBytes, length1);
            length2 = repairLength2(fontBytes, length1, length2);

            if (fontBytes.length > 0 && (fontBytes[0] & 0xff) == PFB_START_MARKER) {
                // some bad files embed the entire PFB, see PDFBOX-2607
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

        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
        }
    }

    @Override
    public Point2D echarAdvance(char ech) {
        try {
            float x = type1Font.getWidth(String.valueOf(ech)) * 0.001f;
            return new Point2D.Float(x, 0);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        return this;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        return this;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        return this;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, char[] diff) {
        return this;
    }

    @Override
    public boolean canDisplayEchar(char ech) {
        return false;
    }

    @Override
    public void setIsCid() {

    }

    @Override
    public FontFile deriveFont(float pointsize) {
        return this;
    }

    @Override
    public CMap getToUnicode() {
        return null;
    }

    @Override
    public String toUnicode(String displayText) {
        return null;
    }

    @Override
    public String toUnicode(char displayChar) {

        return null;
    }

    @Override
    public String getFamily() {
        return type1Font.getFamilyName();
    }

    @Override
    public float getSize() {
        return 1;
    }

    @Override
    public double getAscent() {
        return 0;
    }

    @Override
    public double getDescent() {
        return 0;
    }

    @Override
    public Rectangle2D getMaxCharBounds() {
        return new Rectangle2D.Double(0.0, 0.0, 1.0, 1.0);
    }

    @Override
    public AffineTransform getTransform() {
        return null;
    }

    @Override
    public int getRights() {
        return 0;
    }

    @Override
    public String getName() {
        return type1Font.getName();
    }

    @Override
    public boolean isHinted() {
        return false;
    }

    @Override
    public int getNumGlyphs() {
        return 0;
    }

    @Override
    public int getStyle() {
        return 0;
    }

    @Override
    public char getSpaceEchar() {
        return 0;
    }

    @Override
    public Rectangle2D getEstringBounds(String estr, int beginIndex, int limit) {
        return null;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public void drawEstring(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        try {
            AffineTransform af = g.getTransform();
            java.util.List<Number> fontMatrix = type1Font.getFontMatrix();
            Shape outline = type1Font.getPath(estr);
            org.apache.fontbox.encoding.Encoding encoding = type1Font.getEncoding();

            if (!type1Font.hasGlyph(estr)) {
                org.icepdf.core.pobjects.fonts.zfont.Encoding encoding1 = org.icepdf.core.pobjects.fonts.zfont.Encoding.getInstance(org.icepdf.core.pobjects.fonts.zfont.Encoding.STANDARD_ENCODING_NAME);
                String name = encoding1.getName(estr.charAt(0));
                outline = type1Font.getPath(name);
            }

            g.translate(x, y);
            g.transform(new AffineTransform(0.001f, 0, 0, -0.001f, 0, 0));

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
    public Shape getEstringOutline(String estr, float x, float y) {
        return null;
    }

    @Override
    public ByteEncoding getByteEncoding() {
        return null;
    }

    @Override
    public URL getSource() {
        return null;
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
