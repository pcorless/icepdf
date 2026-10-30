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
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fit page shows the whole current page.  The zoom change alone keeps the viewport centre still,
 * which in a continuous layout left the page's top scrolled off.
 */
class FitPageAnchorTest {

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
            v.setViewMode(ViewMode.CONTINUOUS);
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

    /** The page's top, as Page Down and Go to page place it. */
    private double pageTop(int page) {
        return Fx.call(() -> Math.max(0, skin().layout().getSlot(page).y() - view.getPageGap()));
    }

    private void assertWholePageShows(int page) {
        Fx.run(() -> {
            DocumentLayout.PageSlot slot = skin().layout().getSlot(page);
            double top = skin().scrollY();
            assertTrue(slot.y() >= top, "page " + page + "'s top is on screen");
            assertTrue(slot.y() + slot.height() <= top + 300, "page " + page + "'s bottom is on screen");
        });
        assertEquals(page, (int) Fx.call(view::getCurrentPageIndex));
    }

    @DisplayName("fit page after going to the first page shows it from its top, not shifted by the zoom")
    @Test
    void fitPageAfterGoingToFirstPage() {
        Fx.run(() -> {
            view.setZoom(1.25);
            view.setCurrentPageIndex(2);
            Fx.layout(view);
            view.setCurrentPageIndex(0);
            view.setFitMode(FitMode.PAGE);
            Fx.layout(view);
        });
        assertEquals(pageTop(0), Fx.call(() -> skin().scrollY()), 0.5);
        assertWholePageShows(0);
    }

    @DisplayName("fit page from a high zoom part way down a page brings that whole page into view")
    @Test
    void fitPageFromHighZoomMidPage() {
        Fx.run(() -> {
            view.setZoom(3);
            view.setCurrentPageIndex(1);
            Fx.layout(view);
            // part way down page 2, as after reading it
            skin().scrollTo(skin().scrollX(), skin().scrollY() + 250);
            Fx.layout(view);
        });
        assertEquals(1, (int) Fx.call(view::getCurrentPageIndex));
        Fx.run(() -> {
            view.setFitMode(FitMode.PAGE);
            Fx.layout(view);
        });
        assertEquals(pageTop(1), Fx.call(() -> skin().scrollY()), 0.5);
        assertWholePageShows(1);
    }

    @DisplayName("fit width keeps what is under the viewport centre where it was")
    @Test
    void fitWidthKeepsTheCentre() {
        Fx.run(() -> {
            view.setZoom(1);
            view.setCurrentPageIndex(1);
            Fx.layout(view);
            skin().scrollTo(skin().scrollX(), skin().scrollY() + 60);
            Fx.layout(view);
        });
        double before = Fx.call(() -> skin().scrollY());
        Fx.run(() -> {
            view.setFitMode(FitMode.WIDTH);
            Fx.layout(view);
        });
        double after = Fx.call(() -> skin().scrollY());
        assertTrue(after != pageTop(1) && after > before, "fit width zooms about the centre: " + before + " -> " + after);
    }
}
