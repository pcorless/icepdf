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

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.icepdf.core.pobjects.graphics.text.TextFixtures.FIRST_LINE_Y;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.LEFT_MARGIN;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.LINE_HEIGHT;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.line;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.lineAt;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.page;
import static org.icepdf.core.pobjects.graphics.text.TextFixtures.pageOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PageText}: the word/line tree a page's text is parsed into, the selection and
 * highlight state the viewer drives through it, and the reading order it is sorted into.
 * <p>
 * The selection methods are the ones worth pinning.  They are called from the viewer on every drag
 * and every search hit, they mutate state spread across the whole tree, and the tree has more than
 * one copy of it - the parsed lines and the sorted lines - so clearing through one view and reading
 * back through the other is exactly the mistake these tests are here to catch.
 */
public class PageTextTest {

    // ------------------------------------------------------------------
    // structure and extraction
    // ------------------------------------------------------------------

    @DisplayName("toString extracts every line, one per line of output")
    @Test
    public void extraction() {
        String text = pageOf("Hello World", "Second line").toString();
        assertEquals("Hello World\nSecond line\n", text);
    }

    @DisplayName("an empty page extracts to nothing and has no lines")
    @Test
    public void emptyPage() {
        PageText pageText = new PageText();
        assertEquals("", pageText.toString());
        assertTrue(pageText.getPageLines().isEmpty());
        assertEquals(0, pageText.getSelected().length());
        assertTrue(pageText.getSelectedWordText().isEmpty());
    }

    @DisplayName("getPageLines sorts, caches, and hands back the same list next time")
    @Test
    public void pageLinesAreCached() {
        PageText pageText = pageOf("Alpha", "Bravo");
        assertSame(pageText.getPageLines(), pageText.getPageLines());
    }

    @DisplayName("lines are ordered top to bottom whatever order they were parsed in")
    @Test
    public void readingOrder() {
        // Content streams are under no obligation to draw a page in reading order; the bottom line
        // here is added first.
        PageText pageText = page(
                line(FIRST_LINE_Y - LINE_HEIGHT * 2, "Bottom"),
                line(FIRST_LINE_Y, "Top"));
        assertEquals("Top\nBottom\n", pageText.toString());
    }

    @DisplayName("words within a line are ordered left to right whatever order they were parsed in")
    @Test
    public void wordOrderWithinLine() {
        LineText lineText = new LineText(0);
        List<WordText> words = new ArrayList<>();
        words.add(TextFixtures.word("second", LEFT_MARGIN + 100, FIRST_LINE_Y, false));
        words.add(TextFixtures.word("first", LEFT_MARGIN, FIRST_LINE_Y, false));
        lineText.addAll(words);
        assertEquals("firstsecond\n", page(lineText).toString());
    }

    // ------------------------------------------------------------------
    // selection
    // ------------------------------------------------------------------

    @DisplayName("selectAll selects every word, and getSelected reads the text back")
    @Test
    public void selectAll() {
        PageText pageText = pageOf("Hello World");
        pageText.selectAll();
        assertEquals("Hello World\n", pageText.getSelected().toString());
        assertFalse(pageText.getSelectedWordText().isEmpty());
    }

    @DisplayName("clearSelected clears the sorted view as well as the parsed one")
    @Test
    public void clearSelected() {
        // getSelected reads the sorted lines; selectAll writes through them too, so a clear that
        // only touched the parsed lines would leave text selected that nothing can unselect.
        PageText pageText = pageOf("Hello World");
        pageText.getPageLines();     // force the sort, so both views exist
        pageText.selectAll();
        pageText.clearSelected();
        assertEquals("", pageText.getSelected().toString());
        assertTrue(pageText.getSelectedWordText().isEmpty());
    }

    @DisplayName("deselectAll clears the selection too")
    @Test
    public void deselectAll() {
        PageText pageText = pageOf("Hello World");
        pageText.selectAll();
        pageText.deselectAll();
        assertEquals("", pageText.getSelected().toString());
    }

    @DisplayName("selecting one word selects only that word")
    @Test
    public void selectOneWord() {
        PageText pageText = pageOf("Hello World");
        WordText hello = pageText.getPageLines().get(0).getWords().get(0);
        hello.selectAll();
        assertEquals("Hello", pageText.getSelected().toString().trim());
        assertEquals(1, pageText.getSelectedWordText().size());
    }

    @DisplayName("a word marked selected reports itself selected")
    @Test
    public void wordSelectionFlag() {
        WordText word = TextFixtures.word("Word", LEFT_MARGIN, FIRST_LINE_Y, false);
        assertFalse(word.isSelected());
        word.setSelected(true);
        assertTrue(word.isSelected());
        word.clearSelected();
        assertFalse(word.isSelected());
    }

    // ------------------------------------------------------------------
    // highlighting
    // ------------------------------------------------------------------

    @DisplayName("clearHighlighted clears the highlight a search hit left behind")
    @Test
    public void clearHighlighted() {
        PageText pageText = pageOf("Hello World");
        WordText hello = pageText.getPageLines().get(0).getWords().get(0);
        hello.setHighlighted(true);
        assertTrue(hello.isHighlighted());
        pageText.clearHighlighted();
        assertFalse(hello.isHighlighted());
    }

    @DisplayName("clearHighlightedCursor clears only the cursor highlight")
    @Test
    public void clearHighlightedCursor() {
        PageText pageText = pageOf("Hello World");
        WordText hello = pageText.getPageLines().get(0).getWords().get(0);
        hello.setHighlightCursor(true);
        hello.setHighlighted(true);
        pageText.clearHighlightedCursor();
        assertFalse(hello.isHighlightCursor());
        assertTrue(hello.isHighlighted(), "the search highlight should have survived");
    }

    // ------------------------------------------------------------------
    // geometry
    // ------------------------------------------------------------------

    @DisplayName("a word's bounds span every glyph in it")
    @Test
    public void wordBounds() {
        WordText word = TextFixtures.word("Hello", LEFT_MARGIN, FIRST_LINE_Y, false);
        Rectangle2D.Double bounds = word.getBounds();
        assertEquals(LEFT_MARGIN, bounds.getMinX(), 0.5);
        assertEquals(LEFT_MARGIN + 5 * TextFixtures.GLYPH_WIDTH, bounds.getMaxX(), 0.5);
        assertEquals(LINE_HEIGHT, bounds.getHeight(), 0.5);
    }

    @DisplayName("a line's bounds span every word on it")
    @Test
    public void lineBounds() {
        Rectangle2D.Double bounds = line(FIRST_LINE_Y, "Hello", "World").getBounds();
        assertEquals(LEFT_MARGIN, bounds.getMinX(), 0.5);
        // "Hello" + space + "World" is eleven glyphs wide
        assertEquals(LEFT_MARGIN + 11 * TextFixtures.GLYPH_WIDTH, bounds.getMaxX(), 0.5);
    }

    @DisplayName("intersects answers for a point and for a rectangle")
    @Test
    public void intersects() {
        WordText word = TextFixtures.word("Hello", LEFT_MARGIN, FIRST_LINE_Y, false);
        assertTrue(word.intersects(new Rectangle2D.Double(LEFT_MARGIN, FIRST_LINE_Y, 5, 5)));
        assertFalse(word.intersects(new Rectangle2D.Double(0, 0, 5, 5)));
    }

    // ------------------------------------------------------------------
    // copies
    // ------------------------------------------------------------------

    @DisplayName("transformedCopy maps a copy into page space and leaves the original alone")
    @Test
    public void transformedCopy() {
        // This is how a form XObject drawn more than once gets one set of glyphs per placement;
        // sharing them would make every placement report the last one's coordinates.
        PageText formText = pageOf("Logo");
        double originalX = formText.getPageLines().get(0).getBounds().getMinX();

        List<LineText> placement = formText.transformedCopy(
                AffineTransform.getTranslateInstance(100, 0));

        assertEquals(1, placement.size());
        assertNotSame(formText.getPageLines().get(0), placement.get(0));
        assertEquals(originalX + 100, placement.get(0).getBounds().getMinX(), 0.5);
        assertEquals(originalX, formText.getPageLines().get(0).getBounds().getMinX(), 0.5,
                "the form's own text should not have moved");
    }

    @DisplayName("a copied word carries the text but not the selection state")
    @Test
    public void wordCopy() {
        WordText word = TextFixtures.word("Hello", LEFT_MARGIN, FIRST_LINE_Y, false);
        word.setSelected(true);
        WordText copy = word.copy();
        assertEquals("Hello", copy.getText());
        assertFalse(copy.isSelected(), "a copy belongs to a placement nobody has interacted with");
    }

    @DisplayName("find locates the live instance equal to a word from an earlier parse")
    @Test
    public void find() {
        // A page can be re-parsed under the viewer, leaving the caller holding a stale word.
        PageText pageText = pageOf("Hello World");
        WordText stale = TextFixtures.word("Hello", LEFT_MARGIN, FIRST_LINE_Y, false);
        WordText found = pageText.find(stale);
        assertNotNull(found, "the page should hold a word equal to the stale one");
        assertNotSame(stale, found);
        assertEquals("Hello", found.getText());
    }

    // ------------------------------------------------------------------
    // the cached sequence
    // ------------------------------------------------------------------

    @DisplayName("the text sequence is built once and cached with the sort")
    @Test
    public void textSequenceIsCached() {
        PageText pageText = pageOf("Hello World");
        assertSame(pageText.getTextSequence(), pageText.getTextSequence());
    }

    @DisplayName("the text sequence agrees with the extracted text")
    @Test
    public void textSequenceMatchesExtraction() {
        PageText pageText = pageOf("Hello World", "Second line");
        assertEquals(pageText.toString().trim(),
                pageText.getTextSequence().text().toString().trim());
    }

    // ------------------------------------------------------------------
    // two columns
    // ------------------------------------------------------------------

    @DisplayName("two columns are read down one and then down the other, not across")
    @Test
    public void twoColumnReadingOrder() {
        // Lines of a two-column page interleave in the content stream; reading across the gutter
        // produces text that is grammatical nonsense, which is the bug the reading order fixes.
        // A column has to be more than a line or two before it is treated as one, so each side here
        // carries four.
        double rightColumn = LEFT_MARGIN + 300;
        List<LineText> lines = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            double y = FIRST_LINE_Y - row * LINE_HEIGHT * 2;
            lines.add(lineAt(LEFT_MARGIN, y, "left", "row" + row));
            lines.add(lineAt(rightColumn, y, "right", "row" + row));
        }
        String text = page(lines.toArray(new LineText[0])).toString();

        assertTrue(text.indexOf("left row3") < text.indexOf("right row0"),
                "the left column should be read out before the right one, was:\n" + text);
    }
}
