package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.icepdf.core.pobjects.Stream;

import java.awt.*;
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
    public Shape getGlphyShape(char estr) throws IOException {
        int gid = getCharToGid(estr);
        GlyphData glyphData = trueTypeFont.getGlyph().getGlyph(gid);
        Shape outline;
        if (glyphData == null) {
            outline = new GeneralPath();
        } else {
            outline = glyphData.getPath();
        }
        return outline;
    }
}
