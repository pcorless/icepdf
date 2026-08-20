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
package org.icepdf.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.redaction.RedactionConfidence;
import org.icepdf.core.util.redaction.RedactionReport;
import org.icepdf.core.util.updater.WriteMode;
import org.icepdf.ri.common.MyAnnotationCallback;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.utility.search.RedactSearchTask;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole search-and-redact workflow, driven the way the viewer drives it.
 * <p>
 * Everything below this has unit tests; what they cannot show is that the pieces are actually
 * connected. This runs the real {@link RedactSearchTask} against a real viewer and exports the
 * result, so a step that quietly stopped being called - the task no longer recording its terms, say -
 * fails here and nowhere else.
 * <p>
 * The fixture puts "bravo" in five places: the page, a bookmark, a comment, the document title and
 * its keywords. Only the first is drawn on a page, so only the first is reachable by the rectangles
 * the task creates. If the term axis is wired up, all five go.
 */
public class SearchAndRedactEndToEndTest {

    private static final String FIXTURE = "/redact/positionless_text.pdf";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("searching, redacting and exporting removes the word everywhere it lives")
    @Test
    public void searchAndRedactRemovesEveryCopy() throws Exception {
        SwingController controller = buildViewer();
        byte[] exported;
        RedactionReport report;
        try {
            controller.getDocumentSearchController().addSearchTerm("bravo", false, false);
            runRedactSearchTask(controller);

            Document document = controller.getDocument();
            // Nothing has been removed yet: the task only marks the document, exactly as it does for
            // a user who wants to see the redactions before committing to them.
            assertTrue(pageText(document).contains("bravo"),
                    "the task should mark the document, not redact it");

            exported = export(document);
            report = document.getRedactionReport();
        } finally {
            dispose(controller);
        }

        // The page: what the rectangles covered.
        assertFalse(extractedText(exported).contains("bravo"), "page content");

        // And everywhere a rectangle cannot reach, which is only redacted because the task handed
        // its search terms to the export.
        Document redacted = reopen(exported);
        try {
            assertEquals("**** section", bookmarkTitle(redacted), "bookmark title");
            PInfo info = redacted.getInfo();
            assertEquals("**** report", info.getTitle(), "document title");
            assertEquals("alpha, ****", info.getKeywords(), "keywords");
        } finally {
            redacted.dispose();
        }

        assertNotNull(report, "the export should leave a report");
        assertEquals(RedactionConfidence.VERIFIED, report.getConfidence(),
                "nothing should still be findable: " + report.getHitsAfterByTerm()
                        + " " + report.getUnverifiableRegions());
        assertTrue(report.getGlyphsRemoved() > 0, "glyphs came out of the page");
        assertTrue(report.getStringsRewritten() > 0, "and strings out of everything else");
    }

    @DisplayName("with no search terms the export behaves as it always did")
    @Test
    public void noTermsLeavesTheDocumentAlone() throws Exception {
        SwingController controller = buildViewer();
        byte[] exported;
        try {
            // No search, so the task creates no annotations and records no terms.
            runRedactSearchTask(controller);
            exported = export(controller.getDocument());
        } finally {
            dispose(controller);
        }

        assertTrue(extractedText(exported).contains("bravo"),
                "nothing was searched for, so nothing should have been removed");
    }

    // -- harness ---------------------------------------------------------------------------------

    /**
     * A viewer with the fixture open. Built the way the annotation examples build one, because
     * {@link RedactSearchTask} reads the view's page components and there is no viewer without them.
     */
    private SwingController buildViewer() throws Exception {
        SwingController controller = new SwingController();
        SwingUtilities.invokeAndWait(() -> {
            SwingViewBuilder factory = new SwingViewBuilder(controller);
            JPanel viewerPanel = factory.buildViewerPanel();
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(viewerPanel);
            controller.getDocumentViewController().setViewType(DocumentViewControllerImpl.ONE_COLUMN_VIEW);
            // Without this the task's deferred path adds annotation components to the view and
            // nothing writes them back to the page, so the export finds nothing to burn.
            controller.getDocumentViewController().setAnnotationCallback(
                    new MyAnnotationCallback(controller.getDocumentViewController()));

            InputStream fixture = SearchAndRedactEndToEndTest.class.getResourceAsStream(FIXTURE);
            controller.openDocument(fixture, "redact", "redact");
            frame.pack();
        });
        return controller;
    }

    /**
     * Runs the task and waits for it. It is a SwingWorker, so without the wait the export would
     * race the annotations it is meant to burn.
     */
    private void runRedactSearchTask(SwingController controller) throws Exception {
        RedactSearchTask task = new RedactSearchTask(controller, null);
        task.execute();
        task.get();
        // The task adds annotation components on the event thread, so let those land before the
        // export reads the page.
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private byte[] export(Document document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
        return out.toByteArray();
    }

    private void dispose(SwingController controller) throws Exception {
        SwingUtilities.invokeAndWait(controller::closeDocument);
    }

    private String pageText(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        return page.getViewText().toString();
    }

    private String extractedText(byte[] pdf) throws Exception {
        Document document = reopen(pdf);
        try {
            return pageText(document);
        } finally {
            document.dispose();
        }
    }

    private String bookmarkTitle(Document document) {
        OutlineItem root = document.getCatalog().getOutlines().getRootOutlineItem();
        return root.getSubItem(0).getTitle();
    }

    private Document reopen(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "redacted");
        return document;
    }
}
