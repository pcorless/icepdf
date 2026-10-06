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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Selection gestures against real documents, no toolkit.  Fixtures are the Swing viewer's
 * selection fixtures, referenced in place (see PROVENANCE.md) rather than copied.
 */
class SelectionControllerTest {

    private static final Path FIXTURES = Paths.get("../viewer-awt/src/test/resources");

    private static Document testPrint;
    private static Document windriver;
    private static final Map<String, TextSequence> sequences = new HashMap<>();

    @BeforeAll
    static void open() throws Exception {
        assumeTrue(Files.isDirectory(FIXTURES), "viewer-awt fixtures not found from " + Paths.get("").toAbsolutePath());
        testPrint = open("redact/test_print.pdf");
        windriver = open("redact/windrivercasestudy1n3d2m8km0r.pdf");
    }

    @AfterAll
    static void close() {
        if (testPrint != null) testPrint.dispose();
        if (windriver != null) windriver.dispose();
    }

    private static Document open(String name) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURES.resolve(name).toString());
        return document;
    }

    private static TextSequence seq(Document document, int page) {
        return sequences.computeIfAbsent(System.identityHashCode(document) + ":" + page, k -> {
            try {
                return document.getPageViewText(page).getTextSequence();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static SelectionController controller(Document document) {
        return new SelectionController(page -> seq(document, page));
    }

    /** Centre of the i-th glyph in reading order, as a page point. */
    private static PagePoint glyph(Document document, int page, int i) {
        TextSequence sequence = seq(document, page);
        Rectangle2D.Double b = sequence.glyphsIn(sequence.fullRange()).get(i).getBounds();
        return new PagePoint(page, b.getCenterX(), b.getCenterY());
    }

    private static int offsetAt(Document document, PagePoint p) {
        return seq(document, p.pageIndex()).caretAt(p.toAwt()).getOffset();
    }

    @DisplayName("press collapses; drag moves only the focus, forwards and backwards")
    @Test
    void pressAndDrag() {
        SelectionController c = controller(testPrint);
        PagePoint a = glyph(testPrint, 0, 5);
        PagePoint b = glyph(testPrint, 0, 40);
        PagePoint before = glyph(testPrint, 0, 2);
        int oa = offsetAt(testPrint, a);

        DocumentSelection s = c.press(null, a, 1, false);
        assertEquals(DocumentSelection.collapsed(0, oa), s);
        s = c.drag(s, b);
        assertEquals(DocumentSelection.of(0, oa, 0, offsetAt(testPrint, b)), s);
        s = c.drag(s, before);
        assertEquals(oa, s.getAnchorOffset(), "anchor stays where the drag started");
        assertEquals(offsetAt(testPrint, before), s.getFocusOffset());
        assertFalse(s.isForward());
        c.release();
    }

    @DisplayName("double / triple press select the word / line; the following drag keeps it")
    @Test
    void wordAndLine() {
        SelectionController c = controller(testPrint);
        TextSequence sequence = seq(testPrint, 0);
        PagePoint p = glyph(testPrint, 0, 40);
        int offset = offsetAt(testPrint, p);

        OffsetRange word = sequence.wordRange(offset);
        DocumentSelection s = c.press(null, p, 2, false);
        assertEquals(DocumentSelection.of(0, word.getStart(), 0, word.getEnd()), s);
        assertSame(s, c.drag(s, glyph(testPrint, 0, 80)), "drag after a double click doesn't collapse the word");
        c.release();

        OffsetRange line = sequence.lineRange(offset);
        s = c.press(null, p, 3, false);
        assertEquals(DocumentSelection.of(0, line.getStart(), 0, line.getEnd()), s);
        assertTrue(line.length() > word.length());
        c.release();
    }

    @DisplayName("shift+press extends the existing selection")
    @Test
    void shiftExtends() {
        SelectionController c = controller(testPrint);
        PagePoint p = glyph(testPrint, 0, 60);
        DocumentSelection s = c.press(DocumentSelection.of(0, 5, 0, 10), p, 1, true);
        assertEquals(DocumentSelection.of(0, 5, 0, offsetAt(testPrint, p)), s);
    }

    @DisplayName("pages without loaded text leave the selection unchanged")
    @Test
    void textNotLoaded() {
        SelectionController c = new SelectionController(page -> null);
        DocumentSelection current = DocumentSelection.of(0, 1, 0, 4);
        PagePoint p = new PagePoint(0, 100, 100);
        assertSame(current, c.press(current, p, 1, false));
        assertSame(current, c.drag(current, p));
    }

    @DisplayName("press off the pages clears; a drag onto a page then starts a selection there")
    @Test
    void pressOffPage() {
        SelectionController c = controller(testPrint);
        assertNull(c.press(DocumentSelection.of(0, 1, 0, 4), null, 1, false));
        PagePoint a = glyph(testPrint, 0, 5);
        PagePoint b = glyph(testPrint, 0, 30);
        DocumentSelection s = c.drag(null, a);
        assertEquals(DocumentSelection.collapsed(0, offsetAt(testPrint, a)), s);
        s = c.drag(s, b);
        assertEquals(DocumentSelection.of(0, offsetAt(testPrint, a), 0, offsetAt(testPrint, b)), s);
    }

    @DisplayName("a drag carries the selection across a page boundary")
    @Test
    void crossPage() {
        assumeTrue(windriver.getNumberOfPages() >= 2);
        SelectionController c = controller(windriver);
        PagePoint a = glyph(windriver, 0, 20);
        PagePoint b = glyph(windriver, 1, 15);
        DocumentSelection s = c.drag(c.press(null, a, 1, false), b);
        assertEquals(DocumentSelection.of(0, offsetAt(windriver, a), 1, offsetAt(windriver, b)), s);
        // and the selected text spans both pages
        String text = s.extractText(page -> seq(windriver, page));
        assertTrue(text.contains(seq(windriver, 1).text(0, offsetAt(windriver, b)).trim().split("\\s+")[0]));
    }

    @DisplayName("column-aware drag: gutter drift stays in the anchor column, text in the next column crosses")
    @Test
    void columnAware() {
        TextSequence sequence = seq(windriver, 1);
        List<ColumnBlock> columns = sequence.columns();
        assumeTrue(columns.size() == 2, "fixture expected to have two body columns");
        ColumnBlock left = columns.get(0);
        ColumnBlock right = columns.get(1);
        double y = (Math.max(left.getBounds().getMinY(), right.getBounds().getMinY())
                + Math.min(left.getBounds().getMaxY(), right.getBounds().getMaxY())) / 2.0;
        double gutterX = (left.getBounds().getMaxX() + right.getBounds().getMinX()) / 2.0;

        SelectionController c = controller(windriver);
        PagePoint start = new PagePoint(1, left.getBounds().getCenterX(), left.getBounds().getMaxY() - 5);
        DocumentSelection s = c.press(null, start, 1, false);
        assertTrue(left.getRange().contains(s.getAnchorOffset()), "press landed in the left column");

        DocumentSelection gutter = c.drag(s, new PagePoint(1, gutterX, y));
        assertFalse(right.getRange().contains(gutter.getFocusOffset()),
                "drifting into the gutter jumped to the right column: " + gutter);

        DocumentSelection across = c.drag(gutter, new PagePoint(1, right.getBounds().getCenterX(), y));
        assertFalse(left.getRange().contains(across.getFocusOffset()),
                "a point over the right column's text should cross into it: " + across);
    }

    @DisplayName("auto-scroll step: zero inside, signed and capped near and past the edges")
    @Test
    void edgeStep() {
        assertEquals(0, TextSelectHandler.edgeStep(500, 1000));
        assertTrue(TextSelectHandler.edgeStep(10, 1000) < 0);
        assertTrue(TextSelectHandler.edgeStep(990, 1000) > 0);
        assertTrue(TextSelectHandler.edgeStep(-200, 1000) < TextSelectHandler.edgeStep(0, 1000));
        assertEquals(-60, TextSelectHandler.edgeStep(-10_000, 1000));
        assertEquals(60, TextSelectHandler.edgeStep(10_000, 1000));
    }
}
