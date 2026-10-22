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

import org.icepdf.fx.view.DocumentLayout.PageSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentLayoutTest {

    private static final double GAP = 10;

    private static DocumentLayout.PageSizes uniform(int count, double w, double h) {
        return sizes(count, i -> w, i -> h);
    }

    private static DocumentLayout.PageSizes sizes(int count, java.util.function.IntToDoubleFunction w,
                                                  java.util.function.IntToDoubleFunction h) {
        return new DocumentLayout.PageSizes() {
            public int count() {
                return count;
            }

            public double width(int i) {
                return w.applyAsDouble(i);
            }

            public double height(int i) {
                return h.applyAsDouble(i);
            }
        };
    }

    @Test
    void continuousStacksPagesWithGapsAndCentres() {
        DocumentLayout layout = new DocumentLayout(uniform(3, 100, 200), ViewMode.CONTINUOUS, false, 0,
                GAP, 400, 300);
        assertEquals(400, layout.getWidth(), "narrow content widens to the viewport");
        assertEquals(GAP + 3 * (200 + GAP), layout.getHeight());
        for (int i = 0; i < 3; i++) {
            PageSlot s = layout.getSlot(i);
            assertEquals(150, s.x(), "centred: (400 - 100) / 2");
            assertEquals(GAP + i * (200 + GAP), s.y());
        }
    }

    @Test
    void continuousWiderThanViewportIsNotCentredAway() {
        DocumentLayout layout = new DocumentLayout(uniform(2, 1000, 200), ViewMode.CONTINUOUS, false, 0,
                GAP, 400, 300);
        assertEquals(1000 + 2 * GAP, layout.getWidth());
        assertEquals(GAP, layout.getSlot(0).x());
    }

    @Test
    void mixedSizesCentreEachPageInTheColumn() {
        DocumentLayout layout = new DocumentLayout(sizes(2, i -> i == 0 ? 100 : 300, i -> 100),
                ViewMode.CONTINUOUS, false, 0, GAP, 0, 0);
        assertEquals(300 + 2 * GAP, layout.getWidth());
        assertEquals(GAP + 100, layout.getSlot(0).x(), "narrow page centred against the widest");
        assertEquals(GAP, layout.getSlot(1).x());
    }

    @Test
    void singlePageLaysOutOnlyTheCurrentPage() {
        DocumentLayout layout = new DocumentLayout(uniform(5, 100, 200), ViewMode.SINGLE_PAGE, false, 3,
                GAP, 400, 1000);
        assertNull(layout.getSlot(0));
        assertNotNull(layout.getSlot(3));
        assertEquals(1000, layout.getHeight());
        assertEquals((1000 - (200 + 2 * GAP)) / 2 + GAP, layout.getSlot(3).y(), "vertically centred");
    }

    @Test
    void facingContinuousPairsPagesAroundTheSpine() {
        DocumentLayout layout = new DocumentLayout(uniform(5, 100, 200), ViewMode.FACING_CONTINUOUS, false, 0,
                GAP, 0, 0);
        double spine = layout.getWidth() / 2;
        assertEquals(spine - GAP / 2 - 100, layout.getSlot(0).x());
        assertEquals(spine + GAP / 2, layout.getSlot(1).x());
        assertEquals(layout.getSlot(0).y(), layout.getSlot(1).y());
        assertTrue(layout.getSlot(2).y() > layout.getSlot(0).y());
        // odd last page sits on the left of the spine, not centred.
        assertEquals(spine - GAP / 2 - 100, layout.getSlot(4).x());
    }

    @Test
    void coverPagePutsFirstPageAloneOnTheRight() {
        DocumentLayout layout = new DocumentLayout(uniform(4, 100, 200), ViewMode.FACING_CONTINUOUS, true, 0,
                GAP, 0, 0);
        double spine = layout.getWidth() / 2;
        assertEquals(spine + GAP / 2, layout.getSlot(0).x(), "cover on the right");
        assertEquals(spine - GAP / 2 - 100, layout.getSlot(1).x(), "pages 2-3 form the next spread");
        assertEquals(spine + GAP / 2, layout.getSlot(2).x());
        assertEquals(layout.getSlot(1).y(), layout.getSlot(2).y());
        assertTrue(layout.getSlot(1).y() > layout.getSlot(0).y());
    }

    @Test
    void facingShowsTheSpreadContainingTheCurrentPage() {
        DocumentLayout layout = new DocumentLayout(uniform(6, 100, 200), ViewMode.FACING, false, 3,
                GAP, 0, 0);
        assertNull(layout.getSlot(0));
        assertNotNull(layout.getSlot(2));
        assertNotNull(layout.getSlot(3));
        assertNull(layout.getSlot(4));
    }

    @Test
    void lopsidedSpreadKeepsTheSpineCentred() {
        // landscape page on the left, portrait on the right
        DocumentLayout layout = new DocumentLayout(sizes(2, i -> i == 0 ? 300 : 100, i -> 200),
                ViewMode.FACING, false, 0, GAP, 0, 0);
        double spine = layout.getWidth() / 2;
        assertEquals(spine - GAP / 2, layout.getSlot(0).maxX(), 1e-9);
        assertEquals(spine + GAP / 2, layout.getSlot(1).x(), 1e-9);
        assertTrue(layout.getSlot(0).x() >= GAP - 1e-9);
    }

    @Test
    void slotsIntersectingFindsOnlyVisiblePages() {
        DocumentLayout layout = new DocumentLayout(uniform(1000, 100, 200), ViewMode.CONTINUOUS, false, 0,
                GAP, 100, 300);
        double y = layout.getSlot(500).y() + 50;
        List<PageSlot> visible = layout.slotsIntersecting(0, y, layout.getWidth(), 300);
        assertEquals(List.of(500, 501), visible.stream().map(PageSlot::pageIndex).toList());
        assertTrue(layout.slotsIntersecting(0, -500, 100, 400).isEmpty());
        assertTrue(layout.slotsIntersecting(0, 0, 100, 0).isEmpty());
    }

    @Test
    void currentPageIsTheOneCoveringMostOfTheViewport() {
        DocumentLayout layout = new DocumentLayout(uniform(10, 100, 200), ViewMode.CONTINUOUS, false, 0,
                GAP, 100, 300);
        PageSlot p4 = layout.getSlot(4);
        assertEquals(4, layout.currentPage(0, p4.y() + 10, layout.getWidth(), 300));
        assertEquals(5, layout.currentPage(0, p4.y() + 150, layout.getWidth(), 300));
        // viewport entirely inside a gap resolves to the next page down.
        assertEquals(5, layout.currentPage(0, p4.maxY() + 1, layout.getWidth(), GAP - 2));
    }

    @Test
    void deepZoomCoordinatesStayExact() {
        // 1000 letter pages at 4000%: ~31M px tall.  Doubles must still resolve single pixels.
        DocumentLayout layout = new DocumentLayout(uniform(1000, 612 * 40, 792 * 40), ViewMode.CONTINUOUS,
                false, 0, GAP, 1600, 1000);
        PageSlot last = layout.getSlot(999);
        assertEquals(GAP + 999 * (792 * 40 + GAP), last.y());
        assertEquals(List.of(999), layout.slotsIntersecting(0, last.y() + 0.5, 1600, 1)
                .stream().map(PageSlot::pageIndex).toList());
    }

    @Test
    void pageKeysFlipWholePagesWhenAPageFits() {
        // a viewport one page (and its gap) tall: page tops are at i * 210.
        DocumentLayout layout = new DocumentLayout(uniform(5, 100, 200), ViewMode.CONTINUOUS, false, 0,
                GAP, 100, 210);
        assertEquals(210, layout.pageDownTarget(0, 210, GAP));
        assertEquals(420, layout.pageDownTarget(210, 210, GAP));
        assertEquals(210, layout.pageUpTarget(420, 210, GAP));
        assertEquals(0, layout.pageUpTarget(210, 210, GAP));
        // from part way down a page, onto the next page's top / back onto this page's top.
        assertEquals(420, layout.pageDownTarget(300, 210, GAP));
        assertEquals(210, layout.pageUpTarget(300, 210, GAP));
        assertEquals(0, layout.pageUpTarget(0, 210, GAP), "nothing above the first page");
    }

    @Test
    void pageKeysStepThroughATallPageThenSnapToTheNext() {
        // 1000 px pages in a 300 px viewport: page 1's top is at 1010.
        DocumentLayout layout = new DocumentLayout(uniform(3, 100, 1000), ViewMode.CONTINUOUS, false, 0,
                GAP, 100, 300);
        assertEquals(270, layout.pageDownTarget(0, 300, GAP), "a screen less a tenth, within the page");
        assertEquals(540, layout.pageDownTarget(270, 300, GAP));
        assertEquals(1010, layout.pageDownTarget(810, 300, GAP), "the next page's top is on screen: onto it");
        assertEquals(1010, layout.pageDownTarget(750, 300, GAP), "never past the next page's top");
        assertEquals(740, layout.pageUpTarget(1010, 300, GAP), "back a screen into the page above");
        assertEquals(1010, layout.pageUpTarget(1200, 300, GAP), "this page's top within a screen: onto it");
        assertEquals(0, layout.pageUpTarget(200, 300, GAP));
    }

    @Test
    void pageKeysMoveByRowsWhenFacing() {
        DocumentLayout layout = new DocumentLayout(uniform(6, 100, 200), ViewMode.FACING_CONTINUOUS, false, 0,
                GAP, 300, 210);
        double secondRow = layout.getSlot(2).y() - GAP;
        assertEquals(layout.getSlot(3).y(), layout.getSlot(2).y(), "pages 3 and 4 share a row");
        assertEquals(secondRow, layout.pageDownTarget(0, 210, GAP));
        assertEquals(0, layout.pageUpTarget(secondRow, 210, GAP));
    }

    @Test
    void emptyDocument() {
        DocumentLayout layout = new DocumentLayout(uniform(0, 0, 0), ViewMode.CONTINUOUS, false, 0,
                GAP, 400, 300);
        assertEquals(400, layout.getWidth());
        assertTrue(layout.slotsIntersecting(0, 0, 400, 300).isEmpty());
        assertEquals(-1, layout.currentPage(0, 0, 400, 300));
        assertNull(layout.getSlot(0));
    }
}
