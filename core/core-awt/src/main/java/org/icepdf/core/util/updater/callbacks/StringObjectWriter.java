package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * StringObjectWriter is responsible for rewriting text that has been marked as redacted.   This is done by building
 * out new TJ/Tj layout operations and adjusted Td offset as needed.  This was hard.
 *
 * @since 7.2.0
 */
public abstract class StringObjectWriter {
    public static boolean containsFlaggedText(ArrayList<TextSprite> textOperators) {
        for (TextSprite textSprite : textOperators) {
            boolean hasFlagged = partiallyFlaggedGlyphs(textSprite.getGlyphSprites());
            if (hasFlagged) {
                return true;
            }
        }
        return false;
    }

    public static boolean partiallyFlaggedGlyphs(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (glyphText.isFlagged()) {
                return true;
            }
        }
        return false;
    }

    public static boolean allFlagged(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (!glyphText.isFlagged()) {
                return false;
            }
        }
        return true;
    }

    public abstract float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                                  float lastTdOffset) throws IOException;

    public abstract float writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                                  float lastTdOffset) throws IOException;

    protected static float writeLastTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                             GlyphText glyphText) throws IOException {
        float advance = glyphText.getX() + glyphText.getAdvanceX();
        // still not sure how to handle this in a 100% of cases as advance can technically be negative
        // but if we have a negative glyph advance we likely have a negative font value and should
        // treat this as a positive value when writing the advance.
        if (glyphText.getAdvanceX() < 0) {
            advance = Math.abs(advance);
        }
        return writeTdOffset(contentOutputStream, advance, lastTdOffset);
    }

    protected static float writeStartTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                              GlyphText glyphText) throws IOException {
        float advance = glyphText.getX();
        return writeTdOffset(contentOutputStream, advance, lastTdOffset);
    }

    protected static float writeTdOffset(ByteArrayOutputStream contentOutputStream, float advance,
                                         float lastTdOffset) throws IOException {
        float delta = advance - lastTdOffset;
        lastTdOffset = advance;
        contentOutputStream.write(' ');
        contentOutputStream.write(String.valueOf(delta).getBytes());
        contentOutputStream.write(' ');
        contentOutputStream.write('0');
        contentOutputStream.write(" Td ".getBytes());
        return lastTdOffset;
    }

    protected static void writeCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        if (glyphText.getFontSubTypeFormat() == Font.SIMPLE_FORMAT) {
            writeSimpleCharacterCode(glyphText, contentOutputStream);
        } else {
            writeCidCharacterCode(glyphText, contentOutputStream);
        }
    }

    protected static void writeSimpleCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        char cid = glyphText.getCid();
        // simple fonts
        if (cid <= 127) {
            if (cid == '(' || cid == ')' || cid == '\\') {
                contentOutputStream.write('\\');
            }
            contentOutputStream.write(cid);
        } else {
            // write out octal values
            contentOutputStream.write('\\');
            contentOutputStream.write(Integer.toString(cid, 8).getBytes());
        }
    }

    protected static void writeCidCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        char cid = glyphText.getCid();
        String hex = Integer.toHexString(cid);
        if (hex.length() == 2) {
            hex = "00" + hex;
        } else if (hex.length() == 1) {
            hex = "000" + hex;
        }
        contentOutputStream.write(hex.getBytes());
    }

    protected static void writeDelimiterStart(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? '(' : '<';
        contentOutputStream.write(' ');
        contentOutputStream.write(delimiter);
    }

    protected static void writeDelimiterEnd(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? ')' : '>';
        contentOutputStream.write(delimiter);
        contentOutputStream.write(" Tj ".getBytes());
    }

}
