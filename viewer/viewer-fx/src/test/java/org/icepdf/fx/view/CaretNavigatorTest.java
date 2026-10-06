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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.*;
import org.icepdf.fx.view.CaretNavigator.Move;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Keyboard caret movement against real text, no toolkit.  Fixtures as in SelectionControllerTest.
 */
class CaretNavigatorTest {

    private static final Path FIXTURES = Paths.get("../viewer-awt/src/test/resources");
    private static Document windriver;
    private static TextSequence page0;
    private static TextSequence page1;

    @BeforeAll
    static void open() throws Exception {
        assumeTrue(Files.isDirectory(FIXTURES), "viewer-awt fixtures not found");
        windriver = new Document();
        windriver.setFile(FIXTURES.resolve("redact/windrivercasestudy1n3d2m8km0r.pdf").toString());
        assumeTrue(windriver.getNumberOfPages() >= 2);
        page0 = windriver.getPageViewText(0).getTextSequence();
        page1 = windriver.getPageViewText(1).getTextSequence();
    }

    @AfterAll
    static void close() {
        if (windriver != null) windriver.dispose();
    }

    /** Pages as given; null entries are loaded pages with no text; absent keys aren't loaded. */
    private static final class FakeTexts implements CaretNavigator.Texts {
        final Map<Integer, TextSequence> pages = new HashMap<>();
        final List<Integer> requested = new ArrayList<>();

        FakeTexts with(int page, TextSequence sequence) {
            pages.put(page, sequence);
            return this;
        }

        public TextSequence get(int pageIndex) {
            return pages.get(pageIndex);
        }

        public boolean isLoaded(int pageIndex) {
            return pages.containsKey(pageIndex);
        }

        public void request(int pageIndex) {
            requested.add(pageIndex);
        }
    }

    private static CaretNavigator twoPages() {
        return new CaretNavigator(new FakeTexts().with(0, page0).with(1, page1), 2);
    }

    @DisplayName("left/right step one glyph; shift extends from the anchor")
    @Test
    void horizontal() {
        CaretNavigator nav = twoPages();
        int start = page0.lineRange(page0.length() / 2).getStart();
        DocumentSelection caret = DocumentSelection.collapsed(0, start);
        int next = page0.nextBoundary(start, BreakType.GLYPH, true);

        DocumentSelection right = nav.move(caret, Move.RIGHT, false);
        assertEquals(DocumentSelection.collapsed(0, next), right);
        assertEquals(caret, nav.move(right, Move.LEFT, false));

        DocumentSelection extended = nav.move(nav.move(caret, Move.RIGHT, true), Move.RIGHT, true);
        assertEquals(start, extended.getAnchorOffset(), "shift keeps the anchor");
        assertEquals(page0.nextBoundary(next, BreakType.GLYPH, true), extended.getFocusOffset());
    }

    @DisplayName("word moves and line edges use the sequence's boundaries")
    @Test
    void wordAndLine() {
        CaretNavigator nav = twoPages();
        int offset = page0.length() / 2;
        DocumentSelection caret = DocumentSelection.collapsed(0, offset);
        assertEquals(page0.nextBoundary(offset, BreakType.WORD, true),
                nav.move(caret, Move.WORD_RIGHT, false).getFocusOffset());
        assertEquals(page0.nextBoundary(offset, BreakType.WORD, false),
                nav.move(caret, Move.WORD_LEFT, false).getFocusOffset());
        OffsetRange line = page0.lineRange(offset);
        assertEquals(line.getStart(), nav.move(caret, Move.LINE_START, false).getFocusOffset());
        DocumentSelection toEnd = nav.move(caret, Move.LINE_END, true);
        assertEquals(DocumentSelection.of(0, offset, 0, line.getEnd()), toEnd);
    }

    @DisplayName("down then up returns to the same place (sticky goal x)")
    @Test
    void verticalStickyGoal() {
        CaretNavigator nav = twoPages();
        int offset = page0.lineRange(page0.length() / 3).getStart() + 3;
        DocumentSelection caret = DocumentSelection.collapsed(0, offset);
        DocumentSelection down = nav.move(caret, Move.DOWN, false);
        assertNotEquals(caret, down);
        assertTrue(page0.lineIndexOf(down.getFocusOffset()) > page0.lineIndexOf(offset));
        DocumentSelection down2 = nav.move(down, Move.DOWN, false);
        DocumentSelection up2 = nav.move(nav.move(down2, Move.UP, false), Move.UP, false);
        assertEquals(caret, up2, "two down, two up lands back at the start column");
    }

    @DisplayName("moves cross page boundaries in both directions")
    @Test
    void crossPage() {
        CaretNavigator nav = twoPages();
        DocumentSelection end0 = DocumentSelection.collapsed(0, page0.length());
        assertEquals(DocumentSelection.collapsed(1, 0), nav.move(end0, Move.RIGHT, false));
        assertEquals(end0, nav.move(DocumentSelection.collapsed(1, 0), Move.LEFT, false));

        int lastLine0 = page0.lineRange(page0.length() - 1).getStart();
        DocumentSelection down = nav.move(DocumentSelection.collapsed(0, lastLine0), Move.DOWN, false);
        assertEquals(1, down.getFocusPage(), "down off the last line goes to the next page");
        assertEquals(0, page1.lineIndexOf(down.getFocusOffset()), "onto its first line");

        DocumentSelection up = nav.move(DocumentSelection.collapsed(1, 0), Move.UP, true);
        assertEquals(0, up.getFocusPage());
        assertEquals(1, up.getAnchorPage(), "shift keeps the anchor on the original page");
    }

    @DisplayName("document edges: nothing moves")
    @Test
    void edges() {
        CaretNavigator nav = twoPages();
        DocumentSelection start = DocumentSelection.collapsed(0, 0);
        assertSame(start, nav.move(start, Move.LEFT, false));
        DocumentSelection end = DocumentSelection.collapsed(1, page1.length());
        assertSame(end, nav.move(end, Move.RIGHT, false));
        assertNull(nav.move(null, Move.RIGHT, false));
    }

    @DisplayName("a page not loaded yet: the move is dropped and the page requested")
    @Test
    void notLoaded() {
        FakeTexts texts = new FakeTexts().with(0, page0);
        CaretNavigator nav = new CaretNavigator(texts, 2);
        DocumentSelection end0 = DocumentSelection.collapsed(0, page0.length());
        assertSame(end0, nav.move(end0, Move.RIGHT, false));
        assertEquals(List.of(1), texts.requested);
        texts.with(1, page1);
        assertEquals(DocumentSelection.collapsed(1, 0), nav.move(end0, Move.RIGHT, false), "next press succeeds");
    }

    @DisplayName("pages without text (scans) are skipped")
    @Test
    void skipsTextlessPages() {
        CaretNavigator nav = new CaretNavigator(new FakeTexts().with(0, page0).with(1, null).with(2, null)
                .with(3, page1), 4);
        DocumentSelection end0 = DocumentSelection.collapsed(0, page0.length());
        assertEquals(DocumentSelection.collapsed(3, 0), nav.move(end0, Move.RIGHT, false));
        assertEquals(end0, nav.move(DocumentSelection.collapsed(3, 0), Move.LEFT, false));
        int lastLine0 = page0.lineRange(page0.length() - 1).getStart();
        assertEquals(3, nav.move(DocumentSelection.collapsed(0, lastLine0), Move.DOWN, false).getFocusPage());
    }
}
