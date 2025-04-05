package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TextStringObjectWriter extends StringObjectWriter {

    private final String newText;
    private int replacedCount;

    public TextStringObjectWriter(String newText) {
        this.replacedCount = newText.length() - 1;
        this.newText = newText;
    }

    public float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        int operatorCount = 0;
        GlyphText glyphText = null;
        GlyphText previousGlyphText = null;
        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            if (fullyFlagged(glyphTexts)) {
                if (replacedCount > 0) {
                    writeNewText(contentOutputStream, glyphTexts.get(0), textSprite);
                }
                continue;
            }

            float editStartOffset = -1;

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
                        // write offset to start off the new text
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, previousGlyphText);
                        // write the new text
                        if (replacedCount > 0) {
                            writeNewText(contentOutputStream, glyphText, textSprite);
                        }
                    }
                } else {
                    if (operatorCount == 0) {
                        writeDelimiterStart(glyphText, contentOutputStream);
                    }
                    operatorCount++;
                    writeCharacterCode(glyphText, contentOutputStream);
                    previousGlyphText = glyphText;
                }
            }
            if (operatorCount > 0) {
                writeDelimiterEnd(glyphText, contentOutputStream);
            }
        }
        return lastTdOffset;
    }

    private float writeEditedText(ByteArrayOutputStream contentOutputStream,
                                  TextSprite textSprite) throws IOException {
        float newTextOffset = 0;
        for (int i = 0, max = newText.length(); i < max; i++) {
            char c = newText.charAt(i);
            newTextOffset += (float) textSprite.getFont().getAdvance(c).getX();
            char selector = textSprite.getFont().toSelector(c);
            writeCharacterCode(selector, textSprite.getSubTypeFormat(), contentOutputStream);
            replacedCount -= 1;
        }
        return newTextOffset;
    }

    public void writeNewText(ByteArrayOutputStream contentOutputStream,
                             GlyphText glyphText, TextSprite textSprite) throws IOException {
        writeDelimiterStart(glyphText, contentOutputStream);
        writeEditedText(contentOutputStream, textSprite);
        writeDelimiterEnd(glyphText, contentOutputStream);
    }

    public float writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        int operatorCount = 0;

        GlyphText glyphText = null;
        GlyphText previousGlyphText = null;
        GlyphText lastUnflaggedGlyphText = null;

        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            operatorCount++;

            // can skip it completely
            if (fullyFlagged(glyphTexts)) {
                if (replacedCount > 0) {
                    writeNewText(contentOutputStream, glyphTexts.get(0), textSprite);
                }
                continue;
            }

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
                        // write offset to start off the new text
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, lastUnflaggedGlyphText);

                        // write the new text
                        if (replacedCount > 0) {
                            writeNewText(contentOutputStream, glyphText, textSprite);
                        }
                    }
                } else {
                    if (i == 0 && operatorCount > 1) {
                        if (previousGlyphText != null && previousGlyphText.isFlagged()) {
                            // write offset to start off the new text
                            float charOffset = lastTdOffset > 0 ?
                                    (float) textSprite.getFont().getAdvance(newText.charAt(0)).getX() : 0;
                            float advance = lastTdOffset + charOffset;

                            lastTdOffset = writeTdOffset(contentOutputStream, advance, lastTdOffset);

                            // write the new text
                            if (replacedCount > 0) {
                                writeDelimiterStart(glyphText, contentOutputStream);
                                float textAdvance = writeEditedText(contentOutputStream, textSprite);
                                writeDelimiterEnd(glyphText, contentOutputStream);

                                // write offset to push or contract next text
                                advance = glyphText.getX();
                                float textAdvanceOffset = textAdvance - (advance - lastTdOffset);
                                lastTdOffset -= textAdvanceOffset;
                                lastTdOffset = writeTdOffset(contentOutputStream, advance, lastTdOffset);
                            }
                        } else {
                            lastTdOffset = writeStartTdOffset(contentOutputStream, lastTdOffset, glyphText);
                        }
                    }
                    if (glyphWrittenCount == 0) {
                        writeDelimiterStart(glyphText, contentOutputStream);
                    }
                    glyphWrittenCount++;
                    writeCharacterCode(glyphText, contentOutputStream);
                    lastUnflaggedGlyphText = glyphText;
                }
                previousGlyphText = glyphText;
            }
            if (glyphWrittenCount > 0) {
                writeDelimiterEnd(glyphText, contentOutputStream);
            }
        }
        return lastTdOffset;
    }
}
