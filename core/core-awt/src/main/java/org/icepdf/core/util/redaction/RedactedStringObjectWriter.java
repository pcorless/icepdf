package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * StringObjectWriter is responsible for rewriting text that has been marked as redacted.   This is done by building
 * out new TJ/Tj layout operations and adjusted Td offset as needed.  This was hard.
 *
 * @since 7.2.0
 */
public class RedactedStringObjectWriter extends StringObjectWriter {

    public float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                                float lastTdOffset) throws IOException {
        int operatorCount = 0;
        for (TextSprite textSprite : textOperators) {
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
            GlyphText glyphText = null;

            // can skip it completely
            if (notFlagged(glyphTexts)) {
                continue;
            }

            for (int i = 0, glyphTextMax = glyphTexts.size(); i < glyphTextMax; i++) {
                glyphText = glyphTexts.get(i);
                if (glyphText.isFlagged()) {
                    if (operatorCount > 0) {
                        operatorCount = 0;
                        // close off the current string object
                        writeDelimiterEnd(glyphText, contentOutputStream);
                    }
                    if (i + 1 < glyphTextMax && !glyphTexts.get(i + 1).isFlagged()) {
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, glyphText);
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
            // can skip it completely
            if (notFlagged(glyphTexts)) {
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
                        lastTdOffset = writeLastTdOffset(contentOutputStream, lastTdOffset, glyphText);
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
