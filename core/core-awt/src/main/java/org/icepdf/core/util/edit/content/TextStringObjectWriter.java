package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TextStringObjectWriter extends StringObjectWriter {

    private final String newText;
    private int textIndex = 0;

    public TextStringObjectWriter(String newText) {
        this.newText = newText;
    }

    public float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        int operatorCount = 0;
        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            GlyphText glyphText = null;
            float editStartOffset = -1;

            // can skip it completely
            if (fullyFlagged(glyphTexts)) {
                glyphText = glyphTexts.get(0);
                writeDelimiterStart(glyphText, contentOutputStream);
                float advance = writeEditedText(contentOutputStream, textSprite, newText);
                writeDelimiterEnd(glyphText, contentOutputStream);
                lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
                continue;
            }

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
                        float advance = writeEditedText(contentOutputStream, textSprite, newText);
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
                        editStartOffset = -1;
//                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, glyphText);
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
        float editStartOffset = -1;

        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();

            operatorCount++;
            // can skip it completely
            if (fullyFlagged(glyphTexts)) {
                continue;
            }

            GlyphText glyphText = null;
            int glyphWrittenCount = 0;
            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);

                if (glyphText.isFlagged()) {
                    if (glyphWrittenCount > 0) {
                        glyphWrittenCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                    }
                    if (i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isFlagged()) {
                        writeDelimiterStart(glyphText, contentOutputStream);
                        float advance = writeEditedText(contentOutputStream, textSprite, newText);
                        writeDelimiterEnd(glyphText, contentOutputStream);
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, editStartOffset, advance);
                        editStartOffset = -1;
                    }
                } else {
                    if (i == 0 && operatorCount > 1) {
                        lastTdOffset = writeStartTdOffset(contentOutputStream, lastTdOffset, glyphText);
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
