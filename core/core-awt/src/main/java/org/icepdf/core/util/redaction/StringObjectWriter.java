package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class StringObjectWriter {

    public static boolean containsRedactions(ArrayList<TextSprite> textOperators) {
        for (TextSprite textSprite : textOperators) {
            boolean hasRedaction = containsGlyphRedactions(textSprite.getGlyphSprites());
            if (hasRedaction) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsGlyphRedactions(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (glyphText.isRedacted()) {
                return true;
            }
        }
        return false;
    }

    public static boolean fullyRedacted(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (!glyphText.isRedacted()) {
                return false;
            }
        }
        return true;
    }

    public static void writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators) throws IOException {
        float lastTdOffset = 0;
        int operatorCount = 0;
        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            GlyphText glyphText = null;

            // can skip it completely
            if (fullyRedacted(glyphTexts)) {
                continue;
            }

            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);
                if (glyphText.isRedacted()) {
                    if (operatorCount > 0) {
                        operatorCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        contentOutputStream.write(" Tj ".getBytes());
                    }
                    if (i + 1 == glyphTextMax || (i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isRedacted())) {
                        lastTdOffset = getLastTdOffset(contentOutputStream, lastTdOffset, glyphText);
                        contentOutputStream.write(" Td".getBytes());
                    }
                } else {
                    if (operatorCount == 0) {
                        writeDelimiterStart(glyphText, contentOutputStream);
                    }
                    operatorCount++;
                    writeCharacterCode(glyphText, contentOutputStream);
                }
            }
            if (operatorCount > 0 && glyphText != null) {
                writeDelimiterEnd(glyphText, contentOutputStream);
                contentOutputStream.write(" Tj ".getBytes());
            }
        }
    }

    public static void writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators) throws IOException {
        int operatorCount = 0;
        float lastTdOffset = 0;

        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();

            // can skip it completely
            if (fullyRedacted(glyphTexts)) {
                operatorCount++;
                continue;
            }

            operatorCount++;

            GlyphText glyphText = null;
            int glyphWrittenCount = 0;
            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);
                if (glyphText.isRedacted()) {
                    if (glyphWrittenCount > 0) {
                        glyphWrittenCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        contentOutputStream.write(" Tj ".getBytes());
                    }
                    if ((i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isRedacted())) {
                        lastTdOffset = getLastTdOffset(contentOutputStream, lastTdOffset, glyphText);
                        contentOutputStream.write(" Td ".getBytes());
                    }
                } else {
                    if (i == 0 && operatorCount > 1) {
                        float advance = glyphText.getX();
                        float delta = advance - lastTdOffset;
                        lastTdOffset = advance;
                        contentOutputStream.write(' ');
                        contentOutputStream.write(String.valueOf(delta).getBytes());
                        contentOutputStream.write(' ');
                        contentOutputStream.write('0');
                        contentOutputStream.write(" Td ".getBytes());
                    }
                    if (glyphWrittenCount == 0) {
                        writeDelimiterStart(glyphText, contentOutputStream);
                    }
                    glyphWrittenCount++;
                    writeCharacterCode(glyphText, contentOutputStream);
                }
            }
            if (glyphWrittenCount > 0) {
                writeDelimiterEnd(glyphText, contentOutputStream);
                contentOutputStream.write(" Tj ".getBytes());
            }
        }
        // revert back to the original td offset.
        if (operatorCount > 0) {
            contentOutputStream.write(String.valueOf(-lastTdOffset).getBytes());
            contentOutputStream.write(' ');
            contentOutputStream.write('0');
            contentOutputStream.write(" Td ".getBytes());
        }
    }

    private static float getLastTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                         GlyphText glyphText) throws IOException {
        float advance = glyphText.getX() + glyphText.getAdvanceX();
        float delta = advance - lastTdOffset;
        lastTdOffset = advance;
        contentOutputStream.write(' ');
        contentOutputStream.write(String.valueOf(delta).getBytes());
        contentOutputStream.write(' ');
        contentOutputStream.write('0');
        return lastTdOffset;
    }

    private static void writeCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        // todo maybe avoid simpl/cid and just go with the StringObject type, and use the byte length in the wrapper
        //  or keep the parent StringObject and get it write the bytes as it has all the info as to how it was parse?
        //  probably a bit of both.  When we get to variable bye hex strings things get harder to define generically
        if (glyphText.getFontSubTypeFormat() == Font.SIMPLE_FORMAT) {
            writeSimpleCharacterCode(glyphText, contentOutputStream);
        } else {
            writeCidCharacterCode(glyphText, contentOutputStream);
        }
    }

    private static void writeSimpleCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        char cid = glyphText.getCid();
        // simple fonts
        if (cid <= 127) {
            if (cid == '(' || cid == ')' || cid == '\\') {
                contentOutputStream.write('\\');
            }
            contentOutputStream.write(cid);
        } else {
            contentOutputStream.write('\\');
            contentOutputStream.write(Integer.toString(cid, 8).getBytes());
        }
    }

    private static void writeCidCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        char cid = glyphText.getCid();
        String hex = Integer.toHexString(cid);
        if (hex.length() == 2) {
            hex = "00" + hex;
        } else if (hex.length() == 1) {
            hex = "000" + hex;
        }
        contentOutputStream.write(hex.getBytes());
    }

    private static void writeDelimiterStart(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? '(' : '<';
        contentOutputStream.write(' ');
        contentOutputStream.write(delimiter);
    }

    private static void writeDelimiterEnd(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? ')' : '>';
        contentOutputStream.write(delimiter);
    }

}
