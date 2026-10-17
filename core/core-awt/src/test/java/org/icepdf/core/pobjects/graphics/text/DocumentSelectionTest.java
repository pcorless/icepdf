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

import java.util.Map;

import static org.icepdf.core.pobjects.graphics.text.TextFixtures.pageOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The anchor/focus semantics are the Swing viewer's {@code DocumentTextSelection}'s (its
 * DocumentTextSelectionTest cases are ported here), on an immutable value.
 */
public class DocumentSelectionTest {

    @DisplayName("collapsed, extend forward, extend backward normalise start/end")
    @Test
    public void anchorFocusNormalisation() {
        DocumentSelection caret = DocumentSelection.collapsed(2, 40);
        assertTrue(caret.isCollapsed());
        assertEquals(2, caret.startPage());
        assertEquals(40, caret.startOffset());

        DocumentSelection forward = caret.withFocus(3, 10);
        assertFalse(forward.isCollapsed());
        assertTrue(forward.isForward());
        assertEquals(2, forward.startPage());
        assertEquals(40, forward.startOffset());
        assertEquals(3, forward.endPage());
        assertEquals(10, forward.endOffset());

        DocumentSelection backward = DocumentSelection.collapsed(1, 50).withFocus(1, 20);
        assertFalse(backward.isForward());
        assertEquals(20, backward.startOffset());
        assertEquals(50, backward.endOffset());
        assertEquals(50, backward.getAnchorOffset(), "anchor stays where the drag started");
    }

    @DisplayName("immutable: withFocus returns a new value, the original is unchanged")
    @Test
    public void immutable() {
        DocumentSelection caret = DocumentSelection.collapsed(0, 5);
        DocumentSelection extended = caret.withFocus(0, 9);
        assertNotSame(caret, extended);
        assertTrue(caret.isCollapsed());
        assertSame(extended, extended.withFocus(0, 9), "no-op extend returns the same instance");
        assertEquals(DocumentSelection.of(0, 5, 0, 9), extended);
        assertEquals(DocumentSelection.of(0, 5, 0, 9).hashCode(), extended.hashCode());
    }

    @DisplayName("rangeForPage: first page anchor->end, middle full, last start->focus")
    @Test
    public void rangeForPage() {
        TextSequence seq = pageOf("alpha beta gamma", "delta epsilon").getTextSequence();
        int len = seq.length();
        DocumentSelection sel = DocumentSelection.of(1, 3, 3, 12);

        assertNull(sel.rangeForPage(0, seq), "page before the selection");
        assertEquals(OffsetRange.of(3, len), sel.rangeForPage(1, seq));
        assertEquals(OffsetRange.of(0, len), sel.rangeForPage(2, seq));
        assertEquals(OffsetRange.of(0, 12), sel.rangeForPage(3, seq));
        assertNull(sel.rangeForPage(4, seq), "page after the selection");
        assertNull(sel.rangeForPage(2, null), "page text not loaded");

        // reversed anchor/focus give the same ranges
        DocumentSelection reversed = DocumentSelection.of(3, 12, 1, 3);
        for (int p = 0; p <= 4; p++) {
            assertEquals(sel.rangeForPage(p, seq), reversed.rangeForPage(p, seq), "page " + p);
        }
    }

    @DisplayName("offsets past the page end clamp to the sequence")
    @Test
    public void clampsToSequence() {
        TextSequence seq = pageOf("short line").getTextSequence();
        assertEquals(OffsetRange.of(2, seq.length()),
                DocumentSelection.of(0, 2, 0, 10_000).rangeForPage(0, seq));
    }

    @DisplayName("all(): every page, whole page, without knowing page lengths up front")
    @Test
    public void selectAll() {
        TextSequence first = pageOf("first page text").getTextSequence();
        TextSequence last = pageOf("last page", "two lines").getTextSequence();
        DocumentSelection all = DocumentSelection.all(3);
        assertEquals(first.fullRange(), all.rangeForPage(0, first));
        assertEquals(last.fullRange(), all.rangeForPage(2, last));
        assertNull(all.rangeForPage(3, last));
        assertThrows(IllegalArgumentException.class, () -> DocumentSelection.all(0));
    }

    @DisplayName("extractText matches TextSequence.extractText and joins pages with a blank line")
    @Test
    public void extractText() {
        TextSequence p0 = pageOf("alpha beta gamma").getTextSequence();
        TextSequence p1 = pageOf("delta epsilon").getTextSequence();
        Map<Integer, TextSequence> pages = Map.of(0, p0, 1, p1);

        DocumentSelection onePage = DocumentSelection.of(0, 0, 0, 10);
        assertEquals(p0.extractText(OffsetRange.of(0, 10)), onePage.extractText(pages::get));
        assertEquals(onePage.extractText(pages::get),
                DocumentSelection.of(0, 10, 0, 0).extractText(pages::get), "direction doesn't matter");

        String sep = p0.extractSeparator();
        DocumentSelection twoPages = DocumentSelection.of(0, 6, 1, 5);
        assertEquals(p0.extractText(OffsetRange.of(6, p0.length())) + sep + sep
                + p1.extractText(OffsetRange.of(0, 5)), twoPages.extractText(pages::get));

        // an unloaded middle page is skipped rather than failing
        Map<Integer, TextSequence> gappy = Map.of(0, p0, 2, p1);
        assertEquals(p0.extractText(OffsetRange.of(6, p0.length())) + sep + sep
                        + p1.extractText(OffsetRange.of(0, 5)),
                DocumentSelection.of(0, 6, 2, 5).extractText(gappy::get));

        assertEquals("", DocumentSelection.collapsed(0, 4).extractText(pages::get));
    }

    @DisplayName("negative positions are rejected")
    @Test
    public void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> DocumentSelection.collapsed(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> DocumentSelection.of(0, 0, 0, -3));
    }
}
