package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TextObjectWriter {

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
                GlyphText glyphText;

                for (int i = 0, max = glyphTexts.size(); i < max; i++) {
                    glyphText = glyphTexts.get(i);
                    if (!glyphText.isRedacted()) {
                        if (operatorCount == 0) {
                            // todo may need to write string or hex, so we can deal with cid 4 byte codes correctly
                            contentOutputStream.write(' ');
                            contentOutputStream.write('(');
                        }
                        operatorCount++;
                        contentOutputStream.write(glyphText.getCid().getBytes());
                    } else if (glyphText.isRedacted()) {
                        if (operatorCount > 0) {
                            operatorCount = 0;
                            contentOutputStream.write(") Tj ".getBytes());
                        }
                        if (i + 1 == max || (i + 1 < max && !glyphTexts.get(i + 1).isRedacted())) {
                            float advance = glyphText.getAdvanceX();
                            float delta = advance - lastTdOffset;
                            lastTdOffset = advance;
                            contentOutputStream.write(String.valueOf(delta).getBytes());
                            contentOutputStream.write(' ');
                            contentOutputStream.write('0');
                            contentOutputStream.write(" Td".getBytes());
                        }
                    }
                }
                if (operatorCount > 0) {
                    contentOutputStream.write(") Tj".getBytes());
                }
            } else {
                contentOutputStream.write(String.valueOf(textOperator).getBytes());
            }
        }
        // revert back to the original td offset.
//        contentOutputStream.write(String.valueOf(-lastTdOffset).getBytes());
//        contentOutputStream.write(' ');
//        contentOutputStream.write('0');
//        contentOutputStream.write(" Td ".getBytes());
    }

    public static void writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators) throws IOException {
        int operatorCount = 0;
        float lastTdOffset = 0;

        for (int i = 0, textOperatorsMax = textOperators.size(); i < textOperatorsMax; i++) {
            TextSprite textSprite = textOperators.get(i);
            ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();

            operatorCount++;

            // can skip it completely
            if (fullyRedacted(glyphTexts)) {
                continue;
            }

            GlyphText glyphText;
            int glyphWrittenCount = 0;
            for (int j = 0, glyphTextMax = glyphTexts.size(); j < glyphTextMax; j++) {
                glyphText = glyphTexts.get(j);

                if (!glyphText.isRedacted()) {
                    if (j == 0 && operatorCount > 1) {
                        float advance = glyphText.getX();
                        float delta = advance - lastTdOffset;
                        lastTdOffset = advance;
                        contentOutputStream.write(String.valueOf(delta).getBytes());
                        contentOutputStream.write(' ');
                        contentOutputStream.write('0');
                        contentOutputStream.write(" Td ".getBytes());
                    }
                    if (glyphWrittenCount == 0) {
                        contentOutputStream.write(' ');
                        contentOutputStream.write('(');
                    }
                    glyphWrittenCount++;
                    contentOutputStream.write(glyphText.getCid().getBytes());
                } else if (glyphText.isRedacted()) {
                    if (glyphWrittenCount > 0) {
                        glyphWrittenCount = 0;
                        // close off the current string object
                        contentOutputStream.write(") Tj ".getBytes());
                    }
                    if ((j + 1 < glyphTextMax && !glyphTexts.get(j + 1).isRedacted())) {
                        float advance = glyphText.getX() + glyphText.getAdvanceX();
                        float delta = advance - lastTdOffset;
                        lastTdOffset = advance;
                        contentOutputStream.write(String.valueOf(delta).getBytes());
                        contentOutputStream.write(' ');
                        contentOutputStream.write('0');
                        contentOutputStream.write(" Td ".getBytes());
                    }
                }
            }
            if (glyphWrittenCount > 0) {
                contentOutputStream.write(") Tj ".getBytes());
            }
        }
        // revert back to the original td offset.
        contentOutputStream.write(String.valueOf(-lastTdOffset).getBytes());
        contentOutputStream.write(' ');
        contentOutputStream.write('0');
        contentOutputStream.write(" Td ".getBytes());
    }

}
