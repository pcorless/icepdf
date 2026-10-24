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

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Turning pages from the keyboard and the wheel: Page Down/Up land on page edges in a continuous
 * mode, and in a single-page mode wheeling past the page's edge turns it.
 */
class PageTurnTest {

    @TempDir
    Path dir;
    private PdfView view;
    private Document document;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() throws Exception {
        TinyPdf pdf = new TinyPdf();
        pdf.add("<< /Type /Catalog /Pages 2 0 R >>");
        pdf.add("<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>");
        for (int i = 0; i < 3; i++) pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>");
        Path file = pdf.write(dir.resolve("three.pdf"));
        document = new Document();
        document.setFile(file.toString());
        view = Fx.call(() -> {
            PdfView v = new PdfView();
            Fx.scene(v, 400, 300);
            v.setFitMode(FitMode.NONE);
            v.setDocument(document);
            Fx.layout(v);
            return v;
        });
    }

    @AfterEach
    void dispose() {
        Fx.run(() -> view.setDocument(null));
        document.dispose();
    }

    private PdfViewSkin skin() {
        return (PdfViewSkin) view.getSkin();
    }

    private double scrollY() {
        return Fx.call(() -> skin().scrollY());
    }

    private void wheel(double deltaY, boolean inertia) {
        Fx.run(() -> Event.fireEvent(view, new ScrollEvent(ScrollEvent.SCROLL, 100, 100, 100, 100,
                false, false, false, false, false, inertia, 0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null)));
    }

    private int page() {
        return Fx.call(view::getCurrentPageIndex);
    }

    @DisplayName("single page: wheeling past the bottom turns to the next page's top, past the top back to the previous page's bottom")
    @Test
    void wheelTurnsPages() throws Exception {
        Fx.run(() -> {
            view.setViewMode(ViewMode.SINGLE_PAGE);
            view.setZoom(3); // taller than the viewport, so the page scrolls
            Fx.layout(view);
        });
        assertEquals(0, page());
        wheel(-40, false);
        assertEquals(0, page(), "within the page the wheel scrolls");
        assertTrue(scrollY() > 0);

        Fx.run(() -> skin().scrollTo(0, Double.MAX_VALUE));
        double bottom = scrollY();
        wheel(-40, true);
        assertEquals(0, page(), "a touchpad's coasting doesn't turn the page");
        wheel(-40, false);
        assertEquals(1, page(), "a notch past the bottom turns the page");
        assertEquals(0, scrollY(), "the next page shows from its top");

        wheel(40, false);
        assertEquals(1, page(), "no second turn straight after the first");
        Thread.sleep(300);
        wheel(40, false);
        assertEquals(0, page(), "a notch past the top turns back");
        assertEquals(bottom, scrollY(), 0.5, "the previous page shows from its bottom");
    }

    @DisplayName("continuous: Page Down lands on the next page's top, Page Up back on the first")
    @Test
    void pageKeysLandOnPageEdges() {
        Fx.run(() -> {
            view.setViewMode(ViewMode.CONTINUOUS);
            view.setZoom(1); // a whole page and the next one's top fit in the viewport
            Fx.layout(view);
        });
        Fx.run(() -> Fx.press(view, KeyCode.PAGE_DOWN, false));
        double expected = Fx.call(() -> skin().layout().getSlot(1).y() - view.getPageGap());
        assertTrue(expected < 300, "page 2's top starts on screen");
        // eased over a moment, not jumped.
        assertTrue(scrollY() < expected, "still on its way");
        Fx.waitFor("the page-down scroll to arrive", () -> skin().scrollY() == expected);
        assertEquals(1, page());
        Fx.run(() -> Fx.press(view, KeyCode.PAGE_UP, false));
        Fx.waitFor("back at the top", () -> skin().scrollY() == 0);
        assertEquals(0, page());
    }
}
