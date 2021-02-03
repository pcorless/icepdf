package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.TextState;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.ByteArrayInputStream;
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
                OpenTypeFont openTypeFont = otfParser.parse(new ByteArrayInputStream(fontBytes));
                trueTypeFont = openTypeFont;
                fontBoxFont = trueTypeFont;

                if (openTypeFont.isPostScript()) {
//                    fontIsDamaged = true;
//                    logger.warning("Found CFF/OTF but expected embedded TTF font " + fd.getFontName());
                }

                extractCmapTable();
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Error reading font file with ", e);
//                fontIsDamaged = true;
            throw new Exception(e);
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
}
