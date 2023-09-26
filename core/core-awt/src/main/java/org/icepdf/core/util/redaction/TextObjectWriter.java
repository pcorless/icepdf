package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TextObjectWriter {

    public static boolean containsRedactions(ArrayList<Object> textOperators) {
        for (Object textOperator : textOperators) {
            if (textOperator instanceof TextSprite) {
                TextSprite textSprite = (TextSprite) textOperator;
                boolean hasRedaction = containsGlyphRedactions(textSprite.getGlyphSprites());
                if (hasRedaction) {
                    return true;
                }
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

    public static void write(ByteArrayOutputStream contentOutputStream, ArrayList<Object> textOperators) throws IOException {
        for (Object textOperator : textOperators) {
            if (textOperator instanceof TextSprite) {
                TextSprite textSprite = (TextSprite) textOperator;
                ArrayList<GlyphText> glyphTexts = textSprite.getGlyphSprites();
                GlyphText glyphText;
                float lastOffset = 0;
                float totalRedactOffset = 0;
                int count = 0;
                for (int i = 0, max = glyphTexts.size(); i < max; i++) {
                    glyphText = glyphTexts.get(i);
                    lastOffset = glyphText.getAdvanceX() - totalRedactOffset;
                    if (!glyphText.isRedacted()) {
                        if (count == 0) {
                            contentOutputStream.write(' ');
                            contentOutputStream.write('(');
                        }
                        count++;
                        contentOutputStream.write(glyphText.getCid().getBytes());
                    }
                    if (glyphText.isRedacted()) {
                        if (count > 0) {
                            count = 0;
                            contentOutputStream.write(' ');
                            contentOutputStream.write(") Tj ".getBytes());
                        }
                        if (i + 1 == max || (i + 1 < max && !glyphTexts.get(i + 1).isRedacted())) {
                            totalRedactOffset += lastOffset;
                            contentOutputStream.write(String.valueOf(lastOffset).getBytes());
                            contentOutputStream.write(' ');
                            contentOutputStream.write('0');
                            contentOutputStream.write(" Td ".getBytes());
                        }
                    }
                }
                if (count > 0) {
                    contentOutputStream.write(' ');
                    contentOutputStream.write(") Tj".getBytes());
                }
            } else {

            }
        }
    }

}
