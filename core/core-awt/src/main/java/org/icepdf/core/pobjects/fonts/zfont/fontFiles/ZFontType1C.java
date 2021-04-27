package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.cff.CFFType1Font;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontType1C extends ZSimpleFont {

    private static final Logger logger =
            Logger.getLogger(ZFontType1C.class.toString());

    private CFFType1Font cffType1Font;

    public ZFontType1C(Stream fontStream) throws Exception {
        byte[] fontBytes = fontStream.getDecodedStreamBytes();
        try {
            if (fontBytes != null) {
                // note: this could be an OpenType file, fortunately CFFParser can handle that
                CFFParser cffParser = new CFFParser();
                cffType1Font = (CFFType1Font) cffParser.parse(fontBytes, new FontFileByteSource(fontStream)).get(0);
            }
            fontBoxFont = cffType1Font;
        } catch (IOException e) {
            logger.log(Level.FINE, "Error reading font file with ", e);
            throw new Exception(e);
        }
    }

    private ZFontType1C(ZFontType1C font) {
        super(font);
        this.cffType1Font = font.cffType1Font;
        this.fontBoxFont = this.cffType1Font;
        this.fontMatrix = convertFontMatrix(fontBoxFont);
    }

    @Override
    public Point2D getAdvance(char ech) {
        return super.getAdvance(ech);
    }

    @Override
    protected String codeToName(String estr) {
        // This isn't quite right yet.  But if we are using the standard encoding as set in the TypeFont class
        // use the font's internal encoding.
        if (org.icepdf.core.pobjects.fonts.zfont.Encoding.STANDARD_ENCODING_NAME.equals(encoding.getName())) {
            return cffType1Font.getEncoding().getName(estr.charAt(0));
        } else {
            return estr;
        }
    }

    @Override
    public void paint(Graphics2D g, String estr, float x, float y, long layout, int mode, Color strokeColor) {
        super.paint(g, estr, x, y, layout, mode, strokeColor);
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        ZFontType1C font = new ZFontType1C(this);
        font.setFontTransform(at);
        return font;
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        ZFontType1C font = new ZFontType1C(this);
        font.setPointSize(pointSize);
        return font;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        ZFontType1C font = new ZFontType1C(this);
        font.encoding = encoding;
        font.toUnicode = deriveToUnicode(encoding, toUnicode);
        return font;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
        font.missingWidth = this.missingWidth;
        font.firstCh = firstCh;
        font.ascent = ascent;
        font.descent = descent;
        if (widths != null && widths.length > 0) {
            font.widths = widths;
        }
        font.cMap = diff != null ? diff : font.cMap;
        font.bbox = bbox;
        font.maxCharBounds = null;
        return font;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth, float ascent, float descent, Rectangle2D bbox, char[] diff) {
        ZFontType1C font = new ZFontType1C(this);
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
        return cffType1Font.hasGlyph(String.valueOf(ech));
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return cffType1Font.getEncoding();
    }

    @Override
    public String getFamily() {
        return cffType1Font.getName();
    }

    @Override
    public String getName() {
        return cffType1Font.getName();
    }

    private static class FontFileByteSource implements CFFParser.ByteSource {
        private final Stream fontStream;

        public FontFileByteSource(Stream fontStream) {
            this.fontStream = fontStream;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return fontStream.getDecodedStreamBytes();
        }
    }
}
