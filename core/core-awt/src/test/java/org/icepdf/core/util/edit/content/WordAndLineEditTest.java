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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
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

    /**
     * The fixture's font is uniform by construction - every {@code /Widths} entry is 500, shown at
     * 12pt with no character spacing - so a character occupies exactly 6pt and what the text after
     * an edit should do is arithmetic rather than an estimate.
     */
    private static final double CHARACTER_ADVANCE = 6.0;

    private static final String EDITED_WORD = "bravo";

    private static final double TOLERANCE = 0.1;

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

    /**
     * The control, and the part that is not in doubt: nothing ahead of an edit may move, and a
     * replacement starts where the word it replaced started.
     */
    @DisplayName("an edit moves nothing ahead of it")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"bravissimo", "bo", "brava"})
    public void editMovesNothingAheadOfIt(String replacement) throws Exception {
        byte[] before = Files.readAllBytes(Paths.get(FIXTURE));
        byte[] after = edit(EDITED_WORD, true, replacement);

        assertEquals(leftEdgeOf(before, "alpha"), leftEdgeOf(after, "alpha"), TOLERANCE,
                "the word before the replacement should not move");
        assertEquals(leftEdgeOf(before, EDITED_WORD), leftEdgeOf(after, replacement), TOLERANCE,
                "the replacement should start where the word it replaced started");
    }

    /**
     * A replacement of the same length is the case with no repositioning to get wrong, and pins the
     * measurement itself: if this drifts, the two tests below are measuring something else.
     */
    @DisplayName("a same-length replacement leaves the line exactly as it was")
    @Test
    public void sameLengthReplacementLeavesTheLineWhereItWas() throws Exception {
        byte[] before = Files.readAllBytes(Paths.get(FIXTURE));
        byte[] after = edit(EDITED_WORD, true, "brava");

        assertEquals(leftEdgeOf(before, "charlie"), leftEdgeOf(after, "charlie"), TOLERANCE,
                "nothing changed width, so nothing should have moved");
    }

    /**
     * The case text extraction cannot see. Extraction reads "alpha bravissimo charlie" whether the
     * words are laid out that way or printed on top of one another, so this measures where the
     * glyphs are: a replacement wider than what it replaced must push the rest of the line along,
     * not run underneath it.
     */
    @DisplayName("a longer replacement does not overlap the text after it")
    @Test
    public void longerReplacementDoesNotOverlapTheTail() throws Exception {
        byte[] after = edit(EDITED_WORD, true, "bravissimo");

        double replacementEnd = wordBounds(after, "bravissimo").getMaxX();
        double tailStart = leftEdgeOf(after, "charlie");

        assertTrue(replacementEnd <= tailStart + TOLERANCE,
                "the replacement ends at " + replacementEnd + " and the next word starts at "
                        + tailStart + ", so they are printed over each other");
    }

    /**
     * The other direction, and a matter of policy rather than of correctness: a shorter replacement
     * should close the line up behind it rather than leave the hole the original word occupied.
     * <p>
     * This is where an edit parts company with a redaction. The writer they share adjusts the text
     * after a rewritten run back to the position the parser recorded for it, which is exactly right
     * for a redaction - removing text must not shift the page - and wrong for an edit, where the
     * replacement is meant to take the original's place in the line.
     */
    @DisplayName("a shorter replacement closes the line up behind it")
    @Test
    public void shorterReplacementClosesTheLineUp() throws Exception {
        byte[] before = Files.readAllBytes(Paths.get(FIXTURE));
        byte[] after = edit(EDITED_WORD, true, "bo");

        double expectedShift = ("bo".length() - EDITED_WORD.length()) * CHARACTER_ADVANCE;

        assertEquals(leftEdgeOf(before, "charlie") + expectedShift, leftEdgeOf(after, "charlie"),
                TOLERANCE, "the rest of the line should follow the shorter replacement");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private double leftEdgeOf(byte[] pdf, String word) throws Exception {
        return wordBounds(pdf, word).getMinX();
    }

    /**
     * Where a word sits on the page of a saved document, in page space.
     */
    private Rectangle2D wordBounds(byte[] pdf, String word) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "test");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextSequence sequence = page.getViewText().getTextSequence();
            int offset = sequence.text().toString().indexOf(word);
            assertTrue(offset >= 0, "'" + word + "' should be on the page, got: " + sequence.text());
            return union(sequence, sequence.wordRange(offset));
        } finally {
            document.dispose();
        }
    }

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
        Rectangle2D union = union(sequence, range);
        return union != null ? union.getBounds() : null;
    }

    private Rectangle2D union(TextSequence sequence, OffsetRange range) {
        Rectangle2D union = null;
        for (GlyphText glyph : sequence.glyphsIn(range)) {
            if (union == null) {
                union = new Rectangle2D.Double();
                union.setRect(glyph.getBounds());
            } else {
                union.add(glyph.getBounds());
            }
        }
        return union;
    }
}
