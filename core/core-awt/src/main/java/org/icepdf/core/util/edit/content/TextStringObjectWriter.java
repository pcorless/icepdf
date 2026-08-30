/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import org.icepdf.core.util.PdfNumberFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Replaces the text marked for editing with new text.
 * <p>
 * This is the shared writer plus one hook: where a redaction leaves the gap empty, an edit fills it
 * with the replacement. Everything else - walking the runs, deciding where a {@code TJ} adjustment is
 * needed and how large it is - is the base class, which previously carried a near-duplicate of that
 * logic in each subclass with the two quietly disagreeing about which glyph to measure from.
 * <p>
 * The replacement is written once, at the first removed run. Any further removed runs in the same
 * show operation are simply stepped over, so a selection spanning several strings does not repeat the
 * text.
 */
public class TextStringObjectWriter extends StringObjectWriter {

    private final String newText;
    private static final byte[] ARRAY_END = "] TJ ".getBytes(StandardCharsets.ISO_8859_1);

    private final SubstituteFont substitute;
    private boolean written;

    public TextStringObjectWriter(String newText) {
        this(newText, null);
    }

    /**
     * @param substitute font to write the replacement in when the run's own font cannot express it,
     *                   already embedded and present in the page's resources; null to use the run's
     *                   font, which is the ordinary case
     */
    public TextStringObjectWriter(String newText, SubstituteFont substitute) {
        this.newText = newText != null ? newText : "";
        this.substitute = substitute;
    }

    /**
     * A {@code Tf} inside the text object, which is where a font may be changed.
     */
    private void writeFontSelect(Name fontName, float size, ByteArrayOutputStream contentOutputStream)
            throws IOException {
        contentOutputStream.write(('/' + fontName.getName() + ' '
                + PdfNumberFormat.format(size) + " Tf ").getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    protected boolean writesReplacementText() {
        return !newText.isEmpty() && !written;
    }

    /**
     * An edit puts the replacement in the original's place in the line, so what follows it on the
     * line follows the replacement. A redaction, which shares this writer's base, holds that text
     * where it was instead.
     */
    @Override
    protected boolean reflowsFollowingText() {
        return true;
    }

    @Override
    protected float writeRunReplacement(ByteArrayOutputStream contentOutputStream,
                                        TextSprite textSprite, GlyphText firstRemoved) throws IOException {
        // The base class only calls this while writesReplacementText() holds, so there is no need
        // to re-check it here.
        written = true;
        if (substitute == null) {
            return writeIn(textSprite.getFont(), textSprite.getSubTypeFormat(), textSprite,
                    firstRemoved, contentOutputStream);
        }
        // A font can only be changed between show operations, never inside a TJ array - the array
        // holds strings and numbers and nothing else - so the array in progress is closed, the
        // replacement shown in the substitute, and a fresh array opened for the base class to carry
        // on appending to. The brackets stay balanced because the base opened one and closes one.
        float size = textSprite.getFontSize();
        contentOutputStream.write(ARRAY_END);
        writeFontSelect(substitute.getResourceName(), size, contentOutputStream);
        contentOutputStream.write('[');
        float advance = writeIn(substitute.getFontFile(), Font.SIMPLE_FORMAT, textSprite,
                firstRemoved, contentOutputStream);
        contentOutputStream.write(ARRAY_END);
        // Back to the run's own font, named as the content stream names it - the glyph knows the
        // resource name; the sprite does not always carry one.
        writeFontSelect(firstRemoved.getFontName(), size, contentOutputStream);
        contentOutputStream.write('[');
        return advance;
    }

    /**
     * Shows the replacement in one font, and reports how far the reader moved.
     * <p>
     * The advance has to be measured in the font the text is written in: a substitute's glyphs are
     * not the original's widths, and measuring the wrong one puts everything after the replacement in
     * the wrong place.
     */
    private float writeIn(FontFile font, byte subTypeFormat, TextSprite textSprite,
                          GlyphText firstRemoved, ByteArrayOutputStream contentOutputStream)
            throws IOException {
        writeDelimiterStart(subTypeFormat, contentOutputStream);
        float advance = 0;
        for (int i = 0, max = newText.length(); i < max; i++) {
            char character = newText.charAt(i);
            advance += (float) font.getAdvance(character).getX();
            writeCharacterCode(font.toSelector(character), subTypeFormat, contentOutputStream);
        }
        writeDelimiterEnd(subTypeFormat, contentOutputStream);
        // The reader has advanced by the width of what was just shown, plus the character spacing it
        // applies after each glyph.
        return advance + newText.length() * textSprite.getCharSpacing();
    }

}
