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

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
    private boolean written;

    public TextStringObjectWriter(String newText) {
        this.newText = newText != null ? newText : "";
    }

    @Override
    protected boolean writesReplacementText() {
        return !newText.isEmpty() && !written;
    }

    @Override
    protected float writeRunReplacement(ByteArrayOutputStream contentOutputStream,
                                        TextSprite textSprite, GlyphText firstRemoved) throws IOException {
        if (written || newText.isEmpty()) {
            return 0;
        }
        written = true;
        writeDelimiterStart(firstRemoved, contentOutputStream);
        float advance = 0;
        for (int i = 0, max = newText.length(); i < max; i++) {
            char character = newText.charAt(i);
            advance += (float) textSprite.getFont().getAdvance(character).getX();
            writeCharacterCode(textSprite.getFont().toSelector(character), textSprite.getSubTypeFormat(),
                    contentOutputStream);
        }
        writeDelimiterEnd(firstRemoved, contentOutputStream, false);
        // The reader has advanced by the width of what was just shown, plus the character spacing it
        // applies after each glyph.
        return advance + newText.length() * textSprite.getCharSpacing();
    }
}
