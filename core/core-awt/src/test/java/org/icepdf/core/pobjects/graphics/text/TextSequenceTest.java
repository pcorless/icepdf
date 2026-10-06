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
package org.icepdf.core.pobjects.graphics.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.icepdf.core.pobjects.graphics.text.TextFixtures.GLYPH_WIDTH;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.LEFT_MARGIN;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.glyphCentreX;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.lineCentreY;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.pageOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TextSequence}, the flattened reading-order view a page's text is selected,
 * searched and extracted through.
 * <p>
 * Its whole job is to keep four coordinate systems agreeing: character offsets, glyphs, the
 * word/line tree, and page-space geometry.  A test therefore asserts a round trip wherever it can -
 * offset to glyph to offset, point to caret to rectangle - because a mapping that is merely
 * self-consistent in one direction is the failure this class exists to prevent.
 * <p>
 * The fixture page is two lines, so every offset below can be checked against the literal string
 * {@code "Hello World\nSecond line here"}.
 */
public class TextSequenceTest {

    private static final String LINE_ONE = "Hello World";
    private static final String LINE_TWO = "Second line here";
    private static final String CANONICAL = LINE_ONE + "\n" + LINE_TWO;

    private static TextSequence sequence() {
        return pageOf(LINE_ONE, LINE_TWO).getTextSequence();
    }

    // ------------------------------------------------------------------
    // the canonical string
    // ------------------------------------------------------------------

    @DisplayName("canonical text - lines joined by a single newline, with no trailing one")
    @Test
    public void canonicalText() {
        TextSequence sequence = sequence();
        assertEquals(CANONICAL, sequence.text().toString());
        assertEquals(CANONICAL.length(), sequence.length());
        assertEquals(2, sequence.lineCount());
        // every character except the synthetic newline is a glyph
        assertEquals(CANONICAL.length() - 1, sequence.glyphCount());
        assertFalse(sequence.isEmpty());
    }

    @DisplayName("canonical text - substrings by offset and by range")
    @Test
    public void substrings() {
        TextSequence sequence = sequence();
        assertEquals("Hello", sequence.text(0, 5));
        assertEquals("World", sequence.text(6, 11));
        assertEquals("Second", sequence.text(OffsetRange.of(12, 18)));
        assertEquals(CANONICAL, sequence.text(sequence.fullRange()));
    }

    @DisplayName("canonical text - the full range spans the whole string")
    @Test
    public void fullRange() {
        assertEquals(OffsetRange.of(0, CANONICAL.length()), sequence().fullRange());
    }

    @DisplayName("an empty page has an empty sequence rather than a null one")
    @Test
    public void emptyPage() {
        TextSequence sequence = new PageText().getTextSequence();
        assertTrue(sequence.isEmpty());
        assertEquals(0, sequence.length());
        assertEquals(0, sequence.lineCount());
        assertEquals(0, sequence.glyphCount());
        assertEquals("", sequence.text().toString());
        assertEquals(OffsetRange.of(0, 0), sequence.fullRange());
    }

    @DisplayName("a line whose words hold no glyphs does not emit a stray newline")
    @Test
    public void emptyLineIsRolledBack() {
        // A line can survive parsing with only empty words; counting it would shift every offset
        // after it by one and put the text out of step with the glyphs.
        PageText pageText = TextFixtures.page(
                TextFixtures.line(TextFixtures.FIRST_LINE_Y, "Alpha"),
                new LineText(0),
                TextFixtures.line(TextFixtures.FIRST_LINE_Y - 24, "Bravo"));
        TextSequence sequence = pageText.getTextSequence();
        assertEquals("Alpha\nBravo", sequence.text().toString());
        assertEquals(2, sequence.lineCount());
    }

    // ------------------------------------------------------------------
    // offsets, glyphs and structure
    // ------------------------------------------------------------------

    @DisplayName("offset to glyph and back is a round trip")
    @Test
    public void glyphRoundTrip() {
        TextSequence sequence = sequence();
        for (int offset = 0; offset < LINE_ONE.length(); offset++) {
            GlyphText glyph = sequence.glyphAt(offset);
            assertNotNull(glyph, "no glyph at offset " + offset);
            assertEquals(String.valueOf(CANONICAL.charAt(offset)), glyph.getUnicode());
            assertEquals(offset, sequence.offsetOf(glyph));
        }
    }

    @DisplayName("lineIndexOf places every offset on the line that holds it")
    @Test
    public void lineIndexOf() {
        TextSequence sequence = sequence();
        assertEquals(0, sequence.lineIndexOf(0));
        assertEquals(0, sequence.lineIndexOf(LINE_ONE.length() - 1));
        assertEquals(1, sequence.lineIndexOf(LINE_ONE.length() + 1));
        assertEquals(1, sequence.lineIndexOf(CANONICAL.length() - 1));
    }

    @DisplayName("wordRange snaps an offset out to the whole word around it")
    @Test
    public void wordRange() {
        TextSequence sequence = sequence();
        assertEquals(OffsetRange.of(0, 5), sequence.wordRange(2));      // inside "Hello"
        assertEquals(OffsetRange.of(6, 11), sequence.wordRange(8));     // inside "World"
        assertEquals("Second", sequence.text(sequence.wordRange(14)));
    }

    @DisplayName("lineRange snaps an offset out to the whole line around it")
    @Test
    public void lineRange() {
        TextSequence sequence = sequence();
        assertEquals(OffsetRange.of(0, LINE_ONE.length()), sequence.lineRange(3));
        assertEquals(LINE_TWO, sequence.text(sequence.lineRange(LINE_ONE.length() + 3)));
    }

    @DisplayName("glyphsIn and wordsIn return what a range covers")
    @Test
    public void rangeContents() {
        TextSequence sequence = sequence();
        assertEquals(5, sequence.glyphsIn(OffsetRange.of(0, 5)).size());
        assertTrue(sequence.glyphsIn(OffsetRange.of(3, 3)).isEmpty(), "an empty range covers nothing");
        assertNotNull(sequence.glyphsIn(null));

        List<WordText> words = sequence.wordsIn(OffsetRange.of(0, LINE_ONE.length()));
        assertFalse(words.isEmpty());
        assertEquals("Hello", words.get(0).getText());
    }

    @DisplayName("a word maps back to its own line and to its own offsets")
    @Test
    public void wordToLineAndRange() {
        TextSequence sequence = sequence();
        WordText hello = sequence.wordsIn(OffsetRange.of(0, 5)).get(0);
        assertEquals(OffsetRange.of(0, 5), sequence.rangeOf(hello));
        LineText line = sequence.lineOf(hello);
        assertNotNull(line);
        assertTrue(line.getWords().contains(hello));
    }

    // ------------------------------------------------------------------
    // boundaries
    // ------------------------------------------------------------------

    @DisplayName("nextBoundary - a glyph step moves one character and clamps at the ends")
    @Test
    public void glyphBoundary() {
        TextSequence sequence = sequence();
        assertEquals(4, sequence.nextBoundary(3, BreakType.GLYPH, true));
        assertEquals(2, sequence.nextBoundary(3, BreakType.GLYPH, false));
        assertEquals(0, sequence.nextBoundary(0, BreakType.GLYPH, false));
        assertEquals(CANONICAL.length(),
                sequence.nextBoundary(CANONICAL.length(), BreakType.GLYPH, true));
    }

    @DisplayName("nextBoundary - a word step skips the space rather than stopping on it")
    @Test
    public void wordBoundary() {
        // Stopping on the whitespace word would cost two presses per word in the viewer.
        TextSequence sequence = sequence();
        assertEquals(5, sequence.nextBoundary(0, BreakType.WORD, true));    // end of "Hello"
        assertEquals(11, sequence.nextBoundary(5, BreakType.WORD, true));   // end of "World"
        assertEquals(6, sequence.nextBoundary(11, BreakType.WORD, false));  // start of "World"
        assertEquals(0, sequence.nextBoundary(5, BreakType.WORD, false));   // start of "Hello"
    }

    @DisplayName("nextBoundary - a word step at the page edge returns the offset unchanged")
    @Test
    public void wordBoundaryAtPageEdge() {
        // The caller uses "unchanged" to decide it should roll over to the neighbouring page.
        TextSequence sequence = sequence();
        assertEquals(0, sequence.nextBoundary(0, BreakType.WORD, false));
        assertEquals(CANONICAL.length(),
                sequence.nextBoundary(CANONICAL.length(), BreakType.WORD, true));
    }

    @DisplayName("nextBoundary - a line step lands on the line's own start and end")
    @Test
    public void lineBoundary() {
        TextSequence sequence = sequence();
        assertEquals(LINE_ONE.length(), sequence.nextBoundary(3, BreakType.LINE, true));
        assertEquals(0, sequence.nextBoundary(3, BreakType.LINE, false));
    }

    // ------------------------------------------------------------------
    // hit testing
    // ------------------------------------------------------------------

    @DisplayName("caretAt - a point left of a glyph's centre lands before it, right of it after")
    @Test
    public void caretAtGlyph() {
        TextSequence sequence = sequence();
        double y = lineCentreY(0);
        // just inside the left edge of the third glyph
        Caret before = sequence.caretAt(new Point2D.Double(LEFT_MARGIN + 2 * GLYPH_WIDTH + 1, y));
        assertEquals(2, before.getOffset());
        assertEquals(Bias.FORWARD, before.getBias());
        // just inside its right edge
        Caret after = sequence.caretAt(new Point2D.Double(LEFT_MARGIN + 3 * GLYPH_WIDTH - 1, y));
        assertEquals(3, after.getOffset());
        assertEquals(Bias.BACKWARD, after.getBias());
    }

    @DisplayName("caretAt - a point left of the line clamps to the line start, right of it to the end")
    @Test
    public void caretAtLineMargins() {
        TextSequence sequence = sequence();
        assertEquals(0,
                sequence.caretAt(new Point2D.Double(LEFT_MARGIN - 100, lineCentreY(0))).getOffset());
        // Far right of the last line: the fallback picks the nearest line in 2D, so this is asserted
        // on the longest line, where no other line's box is nearer.
        assertEquals(CANONICAL.length(),
                sequence.caretAt(new Point2D.Double(LEFT_MARGIN + 1000, lineCentreY(1))).getOffset());
    }

    @DisplayName("caretAt - the nearest line wins when a point overshoots a short line")
    @Test
    public void caretAtOvershootsShortLine() {
        // Past the right edge of the short first line, the second line's box is nearer in 2D, so a
        // free hit test lands there.  caretAtLine is the call that stays on a chosen line.
        TextSequence sequence = sequence();
        Point2D past = new Point2D.Double(LEFT_MARGIN + 1000, lineCentreY(0));
        assertEquals(1, sequence.lineIndexOf(sequence.caretAt(past).getOffset()));
        assertEquals(LINE_ONE.length(),
                sequence.caretAtLine(0, LEFT_MARGIN + 1000).getOffset());
    }

    @DisplayName("caretAt - a point far off the page still returns a valid caret")
    @Test
    public void caretAtOffPage() {
        // Total function: drag selection asks for a caret wherever the pointer is.
        TextSequence sequence = sequence();
        Caret caret = sequence.caretAt(new Point2D.Double(-5000, -5000));
        assertTrue(caret.getOffset() >= 0 && caret.getOffset() <= sequence.length());
    }

    @DisplayName("caretAt - an empty page gives the zero caret")
    @Test
    public void caretAtOnEmptyPage() {
        Caret caret = new PageText().getTextSequence().caretAt(new Point2D.Double(10, 10));
        assertEquals(0, caret.getOffset());
        assertEquals(Bias.FORWARD, caret.getBias());
    }

    @DisplayName("hitsText - true only over a glyph, false in the margin and between the lines")
    @Test
    public void hitsText() {
        TextSequence sequence = sequence();
        assertTrue(sequence.hitsText(new Point2D.Double(glyphCentreX(0), lineCentreY(0))));
        assertFalse(sequence.hitsText(new Point2D.Double(LEFT_MARGIN - 20, lineCentreY(0))));
        // the leading between the two lines carries no glyph
        assertFalse(sequence.hitsText(new Point2D.Double(glyphCentreX(0),
                TextFixtures.FIRST_LINE_Y - TextFixtures.LINE_HEIGHT / 2)));
    }

    // ------------------------------------------------------------------
    // geometry of a range
    // ------------------------------------------------------------------

    @DisplayName("rectsFor - one rectangle per line covered, spanning the selected glyphs")
    @Test
    public void rectsForRange() {
        TextSequence sequence = sequence();
        List<Rectangle2D.Double> rects = sequence.rectsFor(0, 5);
        assertEquals(1, rects.size(), "a selection inside one line needs one rectangle");
        assertEquals(LEFT_MARGIN, rects.get(0).getMinX(), 0.5);
        assertEquals(LEFT_MARGIN + 5 * GLYPH_WIDTH, rects.get(0).getMaxX(), 0.5);

        // a selection that crosses the line break needs one rectangle per line
        assertEquals(2, sequence.rectsFor(6, LINE_ONE.length() + 6).size());
        assertEquals(rects, sequence.rectsFor(OffsetRange.of(0, 5)));
    }

    @DisplayName("rectsFor - an empty or null range highlights nothing")
    @Test
    public void rectsForEmptyRange() {
        TextSequence sequence = sequence();
        assertTrue(sequence.rectsFor(4, 4).isEmpty());
        assertTrue(sequence.rectsFor((OffsetRange) null).isEmpty());
    }

    @DisplayName("caretRect - a thin rectangle at the caret, the height of its line")
    @Test
    public void caretRect() {
        TextSequence sequence = sequence();
        Rectangle2D.Double rect = sequence.caretRect(new Caret(0, Bias.FORWARD));
        assertNotNull(rect);
        assertEquals(LEFT_MARGIN, rect.getX(), 0.5);
        assertTrue(rect.getWidth() < GLYPH_WIDTH, "a caret is not as wide as a glyph");
        assertEquals(TextFixtures.LINE_HEIGHT, rect.getHeight(), 0.5);
    }

    // ------------------------------------------------------------------
    // vertical navigation
    // ------------------------------------------------------------------

    @DisplayName("caretBelow and caretAbove step between lines, keeping the goal column")
    @Test
    public void verticalNavigation() {
        TextSequence sequence = sequence();
        double goalX = glyphCentreX(2);
        Caret onLineOne = new Caret(2, Bias.FORWARD);

        Caret onLineTwo = sequence.caretBelow(onLineOne, goalX);
        assertNotNull(onLineTwo);
        assertEquals(1, sequence.lineIndexOf(onLineTwo.getOffset()));

        Caret backUp = sequence.caretAbove(onLineTwo, goalX);
        assertNotNull(backUp);
        assertEquals(0, sequence.lineIndexOf(backUp.getOffset()));
    }

    @DisplayName("caretAbove at the top and caretBelow at the bottom return null")
    @Test
    public void verticalNavigationAtPageEdge() {
        // null is how the viewer learns to move to the previous or next page.
        TextSequence sequence = sequence();
        assertNull(sequence.caretAbove(new Caret(0, Bias.FORWARD), LEFT_MARGIN));
        assertNull(sequence.caretBelow(new Caret(CANONICAL.length(), Bias.BACKWARD), LEFT_MARGIN));
    }

    @DisplayName("caretAtLine clamps a line index into range")
    @Test
    public void caretAtLine() {
        TextSequence sequence = sequence();
        assertEquals(0, sequence.lineIndexOf(sequence.caretAtLine(-5, LEFT_MARGIN).getOffset()));
        assertEquals(1, sequence.lineIndexOf(sequence.caretAtLine(99, LEFT_MARGIN).getOffset()));
    }

    // ------------------------------------------------------------------
    // search corpus
    // ------------------------------------------------------------------

    @DisplayName("searchText collapses the line break to a single space")
    @Test
    public void searchText() {
        // A term that spans a line break has to be findable, so the corpus has no newlines in it.
        assertEquals(LINE_ONE + " " + LINE_TWO, sequence().searchText());
    }

    @DisplayName("a search hit maps back to the canonical offsets it came from")
    @Test
    public void searchToCanonical() {
        TextSequence sequence = sequence();
        String corpus = sequence.searchText();
        int hit = corpus.indexOf("Second");
        OffsetRange range = sequence.searchToCanonicalRange(hit, hit + "Second".length());
        assertEquals("Second", sequence.text(range));
    }

    @DisplayName("a search hit spanning the line break maps back across it")
    @Test
    public void searchAcrossLineBreak() {
        TextSequence sequence = sequence();
        String corpus = sequence.searchText();
        int hit = corpus.indexOf("World Second");
        assertTrue(hit >= 0, "the corpus should join the lines with a space");
        OffsetRange range = sequence.searchToCanonicalRange(hit, hit + "World Second".length());
        assertEquals("World\nSecond", sequence.text(range));
    }

    @DisplayName("foldDiacritics strips accents so an unaccented term still matches")
    @Test
    public void foldDiacritics() {
        assertEquals("ataudes", TextSequence.foldDiacritics("ataúdes"));
        assertEquals("aeiou", TextSequence.foldDiacritics("áéíóú"));
        assertEquals("plain", TextSequence.foldDiacritics("plain"));
    }

    @DisplayName("the folded corpus maps a match back to the accented original")
    @Test
    public void foldedToCanonical() {
        TextSequence sequence = pageOf("los ataúdes").getTextSequence();
        String folded = sequence.foldedSearchText();
        int hit = folded.indexOf("ataudes");
        assertTrue(hit >= 0, "the folded corpus should contain the unaccented term");
        OffsetRange range = sequence.foldedToCanonicalRange(hit, hit + "ataudes".length());
        assertEquals("ataúdes", sequence.text(range));
    }

    @DisplayName("the search corpora are built once and cached")
    @Test
    public void corporaAreCached() {
        TextSequence sequence = sequence();
        assertSame(sequence.searchText(), sequence.searchText());
        assertSame(sequence.foldedSearchText(), sequence.foldedSearchText());
    }

    // ------------------------------------------------------------------
    // extraction
    // ------------------------------------------------------------------

    @DisplayName("extractText of the whole page returns both lines")
    @Test
    public void extractWholePage() {
        String text = sequence().extractText();
        assertTrue(text.contains("Hello World"), text);
        assertTrue(text.contains("Second line here"), text);
    }

    @DisplayName("extractText of a sub-range returns only that range")
    @Test
    public void extractRange() {
        TextSequence sequence = sequence();
        assertEquals("Hello", sequence.extractText(OffsetRange.of(0, 5)));
        assertEquals("", sequence.extractText(OffsetRange.of(4, 4)));
        assertEquals("", sequence.extractText(null));
    }

    @DisplayName("extractText clamps a range that runs off either end")
    @Test
    public void extractOutOfRange() {
        TextSequence sequence = sequence();
        assertEquals(sequence.extractText(), sequence.extractText(OffsetRange.of(-100, 10_000)));
    }

    @DisplayName("extractSeparator reports the separator the extraction used")
    @Test
    public void extractSeparator() {
        assertNotNull(sequence().extractSeparator());
    }

    // ------------------------------------------------------------------
    // columns
    // ------------------------------------------------------------------

    @DisplayName("columns - a single-column page is one block covering the whole text")
    @Test
    public void singleColumn() {
        TextSequence sequence = sequence();
        List<ColumnBlock> columns = sequence.columns();
        assertEquals(1, columns.size());
        assertEquals(sequence.fullRange(), columns.get(0).getRange());
    }

    @DisplayName("columns - an offset and a point both resolve to the column that holds them")
    @Test
    public void columnLookup() {
        TextSequence sequence = sequence();
        ColumnBlock byOffset = sequence.columnAt(3);
        assertNotNull(byOffset);
        ColumnBlock byPoint = sequence.columnAt(new Point2D.Double(glyphCentreX(3), lineCentreY(0)));
        assertSame(byOffset, byPoint);
        assertNotNull(byOffset.getBounds());
        assertTrue(byOffset.getBottom() <= byOffset.getBounds().getMaxY());
    }

    @DisplayName("columns - an empty page has no columns and resolves to none")
    @Test
    public void columnsOnEmptyPage() {
        TextSequence sequence = new PageText().getTextSequence();
        assertTrue(sequence.columns().isEmpty());
        assertNull(sequence.columnAt(0));
    }
}
