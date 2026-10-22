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

import javafx.scene.input.KeyCode;
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
 * Turning pages from the keyboard: Page Down/Up land on page edges in a continuous mode.
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

    private int page() {
        return Fx.call(view::getCurrentPageIndex);
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
