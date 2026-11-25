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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.WatermarkAnnotation;
import org.icepdf.core.util.watermark.Watermark;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.icepdf.fx.view.PdfView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GH-698: a watermark puts an annotation on every page, so every page becomes an unsaved edit the
 * document's state manager holds until the next save.  Scrolling through the document must not
 * keep every page's parsed content alive with it: on PDF32000_2008.pdf (756 pages) that ran a 1 GB
 * heap out and the view stopped scrolling.
 * <p>
 * An edited page keeps its parsed content softly, and the GC only takes soft references under
 * pressure; the {@code softMemoryTest} task runs this with {@code -XX:SoftRefLRUPolicyMSPerMB=0} so
 * a plain {@code System.gc()} takes them, as a full heap would.
 */
@Tag("soft-memory")
class WatermarkScrollMemoryTest {

    private static final int PAGES = 40;

    @TempDir
    Path dir;
    private PdfViewer viewer;

    @BeforeAll
    static void fx() {
        assumeTrue(ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:SoftRefLRUPolicyMSPerMB=0"),
                "run with -XX:SoftRefLRUPolicyMSPerMB=0 (./gradlew :viewer:viewer-fx:softMemoryTest)");
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

    /** {@code pages} letter pages, each a line of Helvetica and no /Annots, so a watermark edits the page itself. */
    private Path textPages(int pages) throws Exception {
        TinyPdf pdf = new TinyPdf();
        pdf.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages; i++) kids.append(4 + 2 * i).append(" 0 R ");
        pdf.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>");
        pdf.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        for (int i = 0; i < pages; i++) {
            pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents " + (5 + 2 * i)
                    + " 0 R /Resources << /Font << /F1 3 0 R >> >> >>");
            pdf.addStream("", ("BT /F1 12 Tf 72 700 Td (Page " + (i + 1) + " text) Tj ET")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        return pdf.write(dir.resolve("pages.pdf"));
    }

    private DocumentSession session() {
        return Fx.call(viewer::getSession);
    }

    private PdfView view() {
        return session().getView();
    }

    private void idle() {
        PdfView view = view();
        Fx.waitFor("the view to finish rendering", () -> !view.isRendering());
        Fx.settle();
    }

    private static void collect() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(20);
        }
    }

    @DisplayName("after a watermark on every page, scrolling through keeps only the pages near the view parsed")
    @Test
    void scrollingAWatermarkedDocumentDoesNotPinEveryPage() throws Exception {
        Path file = textPages(PAGES);
        Fx.run(() -> viewer.open(file));
        Fx.waitFor("the document to show", () -> !session().isLoading() && session().getDocument() != null);
        idle();

        int watermarked = Fx.call(() -> session().applyWatermark(
                Watermark.of("CONFIDENTIAL").withPosition(Watermark.Position.HEADER)));
        assertEquals(PAGES, watermarked);
        idle();

        Document document = session().getDocument();
        for (int i = 0; i < PAGES; i++) {
            Page page = document.getPageTree().getPage(i);
            assertTrue(document.getStateManager().contains(page.getPObjectReference()),
                    "page " + (i + 1) + " is an edit the state manager holds");
        }

        // scroll through the whole document, a page at a time, letting each draw
        for (int i = 0; i < PAGES; i++) {
            int index = i;
            Fx.run(() -> view().setCurrentPageIndex(index));
            idle();
        }
        collect();

        int parsed = 0;
        for (int i = 0; i < PAGES; i++) {
            if (document.getPageTree().getPage(i).getShapes() != null) parsed++;
        }
        assertTrue(parsed <= 6, parsed + " of " + PAGES + " pages still hold their parsed content after "
                + "scrolling through; only the few pages around the view should");

        // back at the top, the first page parses again and still carries its watermark
        Fx.run(() -> view().setCurrentPageIndex(0));
        idle();
        Page first = document.getPageTree().getPage(0);
        assertTrue(first.isInitiated(), "page 1 parsed again to draw");
        assertNotNull(first.getShapes());
        boolean hasWatermark = false;
        for (Annotation a : first.getAnnotations()) hasWatermark |= a instanceof WatermarkAnnotation;
        assertTrue(hasWatermark, "the watermark is still on page 1");
    }
}
