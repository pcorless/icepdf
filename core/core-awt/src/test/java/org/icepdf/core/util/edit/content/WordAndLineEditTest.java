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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.util.redaction.RedactionFixtures;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two edits the viewer actually offers: right-click a word, or a line, and replace it.
 * <p>
 * Both are reproduced here through the same core API the viewer drives - {@code TextSequence}'s word
 * and line ranges for the selection, the union of the selected glyphs for the bounds - so what is
 * tested is the flow a user gets rather than a convenient approximation of it. The viewer adds a
 * dialog and a transform to screen coordinates on top; neither changes what is edited.
 */
public class WordAndLineEditTest {

    private static final String FIXTURE =
            Paths.get("src/test/resources/redaction/simple_tj.pdf").toString();

    @DisplayName("editing a word replaces that word and leaves its neighbours")
    @Test
    public void editWord() throws Exception {
        String text = RedactionFixtures.extractedText(edit("bravo", true, "BRAVO"));

        assertTrue(text.contains("BRAVO"), "the replacement should be there, got: " + text);
        assertTrue(text.contains("alpha") && text.contains("charlie"),
                "and the words either side untouched, got: " + text);
    }

    /**
     * A longer replacement is the interesting direction: the text after it has to move out of the
     * way, which is what the TJ adjustments are for.
     */
    @DisplayName("a word can be replaced with a longer one")
    @Test
    public void editWordWithLongerText() throws Exception {
        String text = RedactionFixtures.extractedText(edit("bravo", true, "bravissimo"));

        assertTrue(text.contains("bravissimo"), "got: " + text);
        assertTrue(text.contains("alpha") && text.contains("charlie"), "got: " + text);
    }

    /**
     * A line edit selects every word on the line, so the replacement stands in for all of them - the
     * case where the writer has to replace a run spanning several words and write the new text once.
     */
    @DisplayName("editing a line replaces the whole line")
    @Test
    public void editLine() throws Exception {
        String text = RedactionFixtures.extractedText(edit("bravo", false, "one two three"))
                .trim();

        assertEquals("one two three", text, "the line should read as the replacement alone");
    }

    /**
     * Every glyph of the selection has to go. A word whose letters differ in height - "alpha" has an
     * ascender and a descender - is where a selection rectangle that only just covers the glyphs can
     * leave one behind.
     */
    @DisplayName("no glyph of the selected word survives the edit")
    @Test
    public void editWordLeavesNoGlyphBehind() throws Exception {
        String text = RedactionFixtures.extractedText(edit("alpha", true, "ZZZ"));

        assertTrue(text.contains("ZZZ"), "got: " + text);
        assertTrue(!text.contains("alpha"), "no part of the original should remain, got: " + text);
        for (char letter : "alpha".toCharArray()) {
            assertTrue(text.indexOf(letter) < 0 || "ZZZ bravo charlie".indexOf(letter) >= 0,
                    "stray '" + letter + "' left behind in: " + text);
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Selects a word or its whole line the way the viewer does, and edits it.
     *
     * @param word        a word in the selection, standing in for where the user right-clicked
     * @param wordNotLine true to edit the word, false to edit the line it is on
     */
    private byte[] edit(String word, boolean wordNotLine, String newText) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            TextSequence sequence = page.getViewText().getTextSequence();
            int offset = sequence.text().toString().indexOf(word);
            assertTrue(offset >= 0, "fixture should contain '" + word + "'");
            OffsetRange range = wordNotLine ? sequence.wordRange(offset) : sequence.lineRange(offset);
            String selected = sequence.text(range).trim();

            TextContentEditor.updateText(page, boundsOf(sequence, range), newText);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    /**
     * The selection's bounds, as the viewer computes them: the union of the selected glyphs, taken
     * as an integer rectangle - {@code Shape.getBounds()} rounds outward, so the rectangle is never
     * tighter than the glyphs it came from.
     */
    private Rectangle boundsOf(TextSequence sequence, OffsetRange range) {
        Rectangle2D union = null;
        for (GlyphText glyph : sequence.glyphsIn(range)) {
            if (union == null) {
                union = new Rectangle2D.Double();
                union.setRect(glyph.getBounds());
            } else {
                union.add(glyph.getBounds());
            }
        }
        return union != null ? union.getBounds() : null;
    }
}
