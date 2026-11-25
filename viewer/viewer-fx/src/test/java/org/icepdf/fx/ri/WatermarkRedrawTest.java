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
package org.icepdf.fx.ri;

import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.icepdf.core.util.watermark.Watermark;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.icepdf.fx.view.PdfView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-698: a watermark shows on the page on screen when it's applied.  A page with no annotations
 * had its annotation layer remembered as empty, and the redraw after Apply kept trusting that, so
 * the first page of PDF32000_2008.pdf (no /Annots, unlike the pages after it) showed no watermark.
 */
class WatermarkRedrawTest {

    @TempDir
    Path dir;
    private PdfViewer viewer;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() {
        viewer = Fx.call(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).build(),
                    UserLayout.inMemory());
            Fx.scene(v, 800, 600);
            return v;
        });
    }

    @AfterEach
    void dispose() {
        if (viewer != null) Fx.run(viewer::dispose);
    }

    private DocumentSession session() {
        return Fx.call(viewer::getSession);
    }

    private void idle() {
        PdfView view = session().getView();
        Fx.waitFor("the view to finish rendering", () -> !view.isRendering());
        Fx.settle();
    }

    /** Strongly red pixels in a snapshot of the view. */
    private int redPixels() {
        return Fx.call(() -> {
            WritableImage image = session().getView().snapshot(null, null);
            PixelReader pixels = image.getPixelReader();
            int count = 0;
            for (int y = 0; y < (int) image.getHeight(); y++) {
                for (int x = 0; x < (int) image.getWidth(); x++) {
                    javafx.scene.paint.Color c = pixels.getColor(x, y);
                    if (c.getRed() > 0.8 && c.getGreen() < 0.3 && c.getBlue() < 0.3) count++;
                }
            }
            return count;
        });
    }

    @DisplayName("a watermark applied to the page on screen, a page with no annotations, shows at once")
    @Test
    void watermarkShowsOnAPageThatHadNoAnnotations() throws Exception {
        Path file = TinyPdf.onePage("", "").write(dir.resolve("plain.pdf"));
        Fx.run(() -> viewer.open(file));
        Fx.waitFor("the document to show", () -> !session().isLoading() && session().getDocument() != null);
        idle();
        assertEquals(0, redPixels(), "nothing red before the watermark");

        int pages = Fx.call(() -> session().applyWatermark(
                Watermark.of("CONFIDENTIAL").withColor(Color.RED).withOpacity(1f)));
        assertEquals(1, pages);
        idle();
        Fx.waitFor("the watermark to draw", () -> redPixels() > 50);
    }
}
