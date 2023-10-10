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
        for (Object textOperator : textOperators) {
            if (textOperator instanceof TextSprite) {
                TextSprite textSprite = (TextSprite) textOperator;
                ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
                GlyphText glyphText = null;

                // can skip it completely
                if (fullyRedacted(glyphTexts)) {
                    continue;
                }

                for (int i = 0, max = glyphTexts.size(); i < max; i++) {
                    glyphText = glyphTexts.get(i);
                    if (!glyphText.isRedacted()) {
                        if (operatorCount == 0) {
                            writeDelimiterStart(glyphText, contentOutputStream);
                        }
                        operatorCount++;
                        writeCharacterCode(glyphText, contentOutputStream);
                    } else if (glyphText.isRedacted()) {
                        if (operatorCount > 0) {
                            operatorCount = 0;
                            // close off the current string object
                            writeDelimiterEnd(glyphText, contentOutputStream);
                            contentOutputStream.write(" Tj ".getBytes());
                        }
                        if (i + 1 == max || (i + 1 < max && !glyphTexts.get(i + 1).isRedacted())) {
                            float advance = glyphText.getX() + glyphText.getAdvanceX();
                            float delta = advance - lastTdOffset;
                            lastTdOffset = advance;
                            contentOutputStream.write(' ');
                            contentOutputStream.write(String.valueOf(delta).getBytes());
                            contentOutputStream.write(' ');
                            contentOutputStream.write('0');
                            contentOutputStream.write(" Td".getBytes());
                        }
                    }
                }
                if (operatorCount > 0) {
                    writeDelimiterEnd(glyphText, contentOutputStream);
                    contentOutputStream.write(" Tj ".getBytes());
                }
            } else {
                contentOutputStream.write(String.valueOf(textOperator).getBytes());
            }
        }
    }

    public static void writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators) throws IOException {
        int operatorCount = 0;
        float lastTdOffset = 0;

        for (int i = 0, textOperatorsMax = textOperators.size(); i < textOperatorsMax; i++) {
            TextSprite textSprite = textOperators.get(i);
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();

            // can skip it completely
            if (fullyRedacted(glyphTexts)) {
                continue;
            }

            operatorCount++;

            GlyphText glyphText = null;
            int glyphWrittenCount = 0;
            for (int j = 0, glyphTextMax = glyphTexts.size(); j < glyphTextMax; j++) {
                glyphText = glyphTexts.get(j);
                if (!glyphText.isRedacted()) {
                    if (j == 0 && operatorCount > 1) {
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
                } else if (glyphText.isRedacted()) {
                    if (glyphWrittenCount > 0) {
                        glyphWrittenCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        contentOutputStream.write(" Tj ".getBytes());
                    }
                    if ((j + 1 < glyphTextMax && !glyphTexts.get(j + 1).isRedacted())) {
                        float advance = glyphText.getX() + glyphText.getAdvanceX();
                        float delta = advance - lastTdOffset;
                        lastTdOffset = advance;
                        contentOutputStream.write(' ');
                        contentOutputStream.write(String.valueOf(delta).getBytes());
                        contentOutputStream.write(' ');
                        contentOutputStream.write('0');
                        contentOutputStream.write(" Td ".getBytes());
                    }
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

    private static void writeCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
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
