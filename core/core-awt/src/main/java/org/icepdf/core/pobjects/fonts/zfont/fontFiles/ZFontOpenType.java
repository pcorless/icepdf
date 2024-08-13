package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZFontOpenType extends ZFontTrueType {

    private static final Logger logger =
            Logger.getLogger(ZFontOpenType.class.toString());

    public ZFontOpenType(Stream fontStream) throws Exception {
        this(fontStream.getDecodedStreamBytes());
    }

    public ZFontOpenType(byte[] fontBytes) throws Exception {
        super();
        try {
            if (fontBytes != null) {
                OTFParser otfParser = new OTFParser(true);
                OpenTypeFont openTypeFont = otfParser.parse(new RandomAccessReadBuffer(fontBytes));
                trueTypeFont = openTypeFont;
                fontBoxFont = trueTypeFont;
                if (openTypeFont.isPostScript()) {
                    isDamaged = true;
                    logger.warning("Found CFF/OTF but expected embedded TTF font " + trueTypeFont.getName());
                }
                extractCmapTable();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reading font file with", e);
            throw e;
        }
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
                outline = glyphData.getPath();
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
            logger.log(Level.FINE, "Error painting OpenType font", e);
        }
    }
}
