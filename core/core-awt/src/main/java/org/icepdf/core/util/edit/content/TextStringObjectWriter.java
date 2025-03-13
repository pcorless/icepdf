package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TextStringObjectWriter extends StringObjectWriter {

    private String newText;
    private final String selectedText;
    private int replacedCount = 0;
    private int selectedTextCount = 0;
    private int remainingSelectedText = 0;

    public TextStringObjectWriter(String selectedText, String newText) {
        this.selectedText = selectedText;
        this.selectedTextCount = selectedText.length();
        this.remainingSelectedText = selectedText.length();
        this.newText = newText;
    }

    protected float writeEditedText(ByteArrayOutputStream contentOutputStream,
                                    TextSprite textSprite, int flaggedCount) throws IOException {
        float newTextOffset = 0;
        if (flaggedCount >= selectedTextCount) {
            for (int i = 0, max = newText.length(); i < max; i++) {
                char c = newText.charAt(i);
                newTextOffset += (float) textSprite.getFont().getAdvance(c).getX();
                // todo this is a raw right without any type of mapping, we should be using a reverse toUnicode mapping
                writeCharacterCode(c, textSprite.getSubTypeFormat(), contentOutputStream);
            }
        } else {
            for (int i = 0, max = newText.length(); i < flaggedCount && i < max; i++) {
                char c = newText.charAt(i);
                newTextOffset += (float) textSprite.getFont().getAdvance(c).getX();
                // todo this is a raw right without any type of mapping, we should be using a reverse toUnicode mapping
                writeCharacterCode(c, textSprite.getSubTypeFormat(), contentOutputStream);
            }
        }
        newText = newText.substring(Math.min(flaggedCount, newText.length()));
        remainingSelectedText -= flaggedCount;
        if (remainingSelectedText == 0 && !newText.isEmpty()) {
            for (int i = 0, max = newText.length(); i < max; i++) {
                char c = newText.charAt(i);
                newTextOffset += (float) textSprite.getFont().getAdvance(c).getX();
                // todo this is a raw right without any type of mapping, we should be using a reverse toUnicode mapping
                writeCharacterCode(c, textSprite.getSubTypeFormat(), contentOutputStream);
            }
            remainingSelectedText = 0;
        }
        return newTextOffset;
    }

    public float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        int operatorCount = 0;
        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            GlyphText glyphText = null;
            float editStartOffset = -1;
            int flaggedCount = flaggedCount(glyphTexts);
            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);
                if (glyphText.isFlagged()) {
                    if (editStartOffset < 0) {
                        editStartOffset = glyphText.getX();
                    }
                    if (operatorCount > 0) {
                        operatorCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                    }
                    if (i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isFlagged()) {
                        // write out new string object returning the new offset
                        writeDelimiterStart(glyphText, contentOutputStream);
                        float advance = writeEditedText(contentOutputStream, textSprite, flaggedCount);
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
                        editStartOffset = -1;
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
            }
        }
        return lastTdOffset;
    }

    public float writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        int operatorCount = 0;

        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            operatorCount++;
            GlyphText glyphText = null;
            int glyphWrittenCount = 0;
            float editStartOffset = -1;
            int flaggedCount = flaggedCount(glyphTexts);
            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);

                if (glyphText.isFlagged()) {
                    if (editStartOffset < 0) {
                        editStartOffset = glyphText.getX();
                    }
                    if (glyphWrittenCount > 0) {
                        glyphWrittenCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                    }
                    if ((i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isFlagged()) ||
                            (glyphTextMax == 1)) {
                        // future me,  translating TJ to Tj here to make layout offsets easier to manage.
                        writeDelimiterStart(glyphText, contentOutputStream);
                        float advance = writeEditedText(contentOutputStream, textSprite, flaggedCount);
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
                        editStartOffset = -1;
                    }
                } else {
                    if (i == 0 && operatorCount > 1) {
//                        lastTdOffset = writeStartTdOffset(contentOutputStream, lastTdOffset, glyphText);
//                        writeDelimiterStart(glyphText, contentOutputStream);
//                        float advance = writeEditedText(contentOutputStream, textSprite, flaggedCount);
//                        writeDelimiterEnd(glyphText, contentOutputStream);
//                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
//                        editStartOffset = -1;
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
            }
        }
        return lastTdOffset;
    }
}
