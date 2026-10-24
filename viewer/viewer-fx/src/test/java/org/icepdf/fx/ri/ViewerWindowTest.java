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

import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.panels.Attachment;
import org.icepdf.fx.ri.document.DocumentPane;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.ScriptedDialogs;
import org.icepdf.fx.testing.TinyPdf;
import org.icepdf.fx.view.internal.annot.AnnotationCreator;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.form.FormController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The viewer as the user drives it - saving, busy refusals, drops, attachments, typing in a form
 * field - in a scene that is never shown, its choosers and questions answered by a script.
 */
class ViewerWindowTest {

    @TempDir
    Path dir;
    private PdfViewer viewer;
    private final ScriptedDialogs dialogs = new ScriptedDialogs();

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() {
        viewer = Fx.call(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).multipleDocuments(true).build(),
                    UserLayout.inMemory());
            v.setDialogs(dialogs);
            Fx.scene(v, 1000, 700);
            return v;
        });
    }

    @AfterEach
    void dispose() {
        if (viewer != null) Fx.run(viewer::dispose);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Path pdf(String name) throws IOException {
        return TinyPdf.onePage("", "").write(dir.resolve(name));
    }

    private DocumentSession session() {
        return Fx.call(viewer::getSession);
    }

    /** Opens in the selected tab and waits until it shows. */
    private void open(Path file) {
        Fx.run(() -> viewer.open(file));
        awaitShowing(file);
    }

    private void awaitShowing(Path file) {
        Path real = real(file);
        Fx.waitFor(file.getFileName() + " to show", () -> {
            DocumentSession s = viewer.getSession();
            return !s.isLoading() && s.getDocument() != null && s.getFile() != null && real(s.getFile()).equals(real);
        });
        Fx.run(() -> Fx.layout(viewer));
    }

    private static Path real(Path file) {
        try {
            return file.toRealPath();
        } catch (IOException e) {
            return file.toAbsolutePath().normalize();
        }
    }

    private String error() {
        return Fx.call(() -> viewer.getToast().getError());
    }

    private String message() {
        Fx.waitFor("a message", () -> !viewer.getToast().getMessage().isEmpty());
        return Fx.call(() -> viewer.getToast().getMessage());
    }

    // ---- the error card -----------------------------------------------------------------------

    @DisplayName("Esc in the document dismisses the error card")
    @Test
    void escapeInDocument() throws Exception {
        open(pdf("a.pdf"));
        Fx.run(() -> viewer.showError("Could not print a.pdf"));
        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.ESCAPE, false));
        assertEquals("", error());
    }

    @DisplayName("Esc that closes a field editor or clears the search leaves the error card")
    @Test
    void escapeUsedElsewhere() throws Exception {
        open(form());
        TextField editor = typeIntoName("Grace");
        Fx.run(() -> viewer.showError("Could not print form.pdf"));
        Fx.run(() -> Fx.press(editor, KeyCode.ESCAPE, false));
        assertEquals("Could not print form.pdf", error(), "the first Esc cancels the edit");
        assertFalse(Fx.call(() -> viewer.getSession().isModified()), "the typed text is dropped");

        TextField query = Fx.call(() -> (TextField) viewer.getCurrentDocument().getSearchPanel().lookup(".text-field"));
        Fx.run(() -> {
            query.setText("bravo");
            Fx.press(query, KeyCode.ESCAPE, false);
        });
        assertEquals("Could not print form.pdf", error(), "Esc in the search box clears the search");

        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.ESCAPE, false));
        assertNull(Fx.call(() -> viewer.getView().getFocusedField()), "the next Esc leaves the field");
        assertEquals("Could not print form.pdf", error());

        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.ESCAPE, false));
        assertEquals("", error(), "then an Esc nothing uses dismisses it");
    }

    // ---- saving -------------------------------------------------------------------------------

    @DisplayName("a successful save clears the error card")
    @Test
    void saveClearsError() throws Exception {
        Path a = pdf("a.pdf");
        open(a);
        Fx.run(() -> viewer.showError("Could not print a.pdf"));
        assertTrue(Fx.call(viewer::save));
        assertEquals("", error());
        awaitShowing(a);
    }

    @DisplayName("Save As \"report\" when report.pdf exists asks before replacing it")
    @Test
    void saveAsAsksBeforeReplacing() throws Exception {
        open(pdf("a.pdf"));
        Path report = dir.resolve("report.pdf");
        Files.writeString(report, "not a pdf yet");

        // cancelled: the existing file is left alone.
        dialogs.choose(dir.resolve("report"));
        assertFalse(Fx.call(viewer::saveAs));
        assertEquals(Messages.format("pdfTargets.exists", "report.pdf"), dialogs.asked.get(dialogs.asked.size() - 1));
        assertEquals("not a pdf yet", Files.readString(report));

        // replaced: the document is saved there and becomes the open file.
        dialogs.choose(dir.resolve("report")).answer(ButtonBar.ButtonData.YES);
        assertTrue(Fx.call(viewer::saveAs));
        awaitShowing(report);
        assertTrue(Files.readString(report, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @DisplayName("Save As onto a file open in another tab is refused, without writing it")
    @Test
    void saveAsOntoAnotherTab() throws Exception {
        Path a = pdf("a.pdf");
        Path b = pdf("b.pdf");
        open(a);
        Fx.run(() -> viewer.openInNewTab(b));
        awaitShowing(b);
        byte[] before = Files.readAllBytes(a);

        dialogs.choose(a);
        assertFalse(Fx.call(viewer::saveAs));
        assertEquals(Messages.format("viewer.openInAnotherTab", "a.pdf"), error());
        assertArrayEquals(before, Files.readAllBytes(a));
        assertEquals(2, Fx.call(() -> viewer.getDocuments().size()));
    }

    /** core's redaction fixture ("alpha bravo charlie"), with "bravo" marked for redaction in the open document. */
    private void openMarked(String name) throws Exception {
        Path copy = Files.copy(Paths.get("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf"), dir.resolve(name));
        open(copy);
        Fx.run(() -> {
            Page page = viewer.getSession().getDocument().getPageTree().getPage(0);
            page.init();
            AffineTransform toPageSpace = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1f);
            Rectangle2D viewBox = toPageSpace.createInverse()
                    .createTransformedShape(new Rectangle2D.Double(53, 96, 34, 16)).getBounds2D();
            RedactionAnnotation redaction = AnnotationCreator.redaction(page.getLibrary(), List.of(viewBox), toPageSpace, "tester");
            AnnotationEdits.add((index, mutation) -> mutation.run(), page, 0, redaction, null);
        });
        assertTrue(Fx.call(() -> viewer.getSession().hasRedactions()));
    }

    @DisplayName("a markup tool chosen with text selected marks up the selection and keeps the tool")
    @Test
    void markupToolUsesSelection() throws Exception {
        Path copy = Files.copy(Paths.get("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf"), dir.resolve("markup.pdf"));
        open(copy);
        java.util.function.Supplier<List<Annotation>> annotations = () -> {
            // the markups alone: each comes with a popup.
            List<Annotation> list = viewer.getSession().getDocument().getPageTree().getPage(0).getAnnotations();
            return list == null ? List.of() : list.stream()
                    .filter(a -> a instanceof org.icepdf.core.pobjects.annotations.TextMarkupAnnotation).toList();
        };
        // the page's text loads in the background; a markup made before it has nothing to cover.
        Fx.waitFor("the squiggly", () -> {
            if (!annotations.get().isEmpty()) return true;
            viewer.getView().selectAll();
            viewer.getActions().execute("annotation.squiggly");
            return !annotations.get().isEmpty();
        });
        assertEquals(1, Fx.call(() -> annotations.get().size()));
        assertEquals(org.icepdf.core.pobjects.annotations.TextMarkupAnnotation.SUBTYPE_SQUIGGLY,
                Fx.call(() -> annotations.get().get(0).getSubType()));
        assertEquals(org.icepdf.fx.view.ToolMode.TEXT_SELECT, Fx.call(() -> viewer.getView().getToolMode()),
                "the tool is unchanged");
        assertNull(Fx.call(() -> viewer.getView().getTextSelection()), "the selection is used up");

        Fx.run(() -> viewer.getView().undo());
        assertTrue(Fx.call(() -> annotations.get().isEmpty()), "undo takes it away");

        // with nothing selected, the same action arms the tool.
        Fx.run(() -> viewer.getActions().execute("annotation.squiggly"));
        assertEquals(org.icepdf.fx.view.ToolMode.SQUIGGLY, Fx.call(() -> viewer.getView().getToolMode()));
        assertTrue(Fx.call(() -> annotations.get().isEmpty()));
    }

    @DisplayName("opening the search panel puts the caret in its query; closing it puts the caret back on the page")
    @Test
    void searchPanelTakesFocus() throws Exception {
        open(pdf("panel.pdf"));
        javafx.scene.control.ToggleButton icon = Fx.call(() -> (javafx.scene.control.ToggleButton) viewer.lookupAll(".toggle-button")
                .stream().filter(n -> ("panel." + SidePanel.SEARCH.id()).equals(n.getId())).findFirst().orElse(null));
        assertNotNull(icon, "the search panel's icon");
        TextField query = Fx.call(() -> (TextField) viewer.getCurrentDocument().getSearchPanel().lookup(".text-field"));

        Fx.run(() -> {
            icon.fire();
            Fx.layout(viewer);
        });
        Fx.settle();
        assertSame(query, Fx.call(() -> viewer.getScene().getFocusOwner()), "clicked open: the query has the caret");

        Fx.run(() -> {
            icon.fire();
            Fx.layout(viewer);
        });
        Fx.settle();
        assertSame(viewer.getView(), Fx.call(() -> viewer.getScene().getFocusOwner()), "closed: back to the page");

        // F4 reopens the panel shown last - the search panel - and focuses it the same way.
        Fx.run(() -> {
            Fx.press(viewer.getView(), KeyCode.F4, false);
            Fx.layout(viewer);
        });
        Fx.settle();
        assertSame(query, Fx.call(() -> viewer.getScene().getFocusOwner()), "F4: the query has the caret");
    }

    @DisplayName("Ctrl+0 fits the page, Ctrl+1 is actual size, Ctrl+2 fits the width - on the page or anywhere else")
    @Test
    void fitShortcuts() throws Exception {
        open(pdf("fit.pdf"));
        org.icepdf.fx.view.PdfView view = Fx.call(viewer::getView);
        Fx.run(() -> Fx.press(view, KeyCode.DIGIT0, true));
        assertEquals(org.icepdf.fx.view.FitMode.PAGE, Fx.call(view::getFitMode));
        Fx.run(() -> Fx.press(view, KeyCode.DIGIT2, true));
        assertEquals(org.icepdf.fx.view.FitMode.WIDTH, Fx.call(view::getFitMode));
        Fx.run(() -> Fx.press(view, KeyCode.DIGIT1, true));
        assertEquals(org.icepdf.fx.view.FitMode.NONE, Fx.call(view::getFitMode));
        assertEquals(1, Fx.call(view::getZoom), 1e-9);

        // from the search field: the scene's shortcut, not the view's key handling.
        Fx.run(() -> viewer.getActions().execute("search.find"));
        TextField query = Fx.call(() -> (TextField) viewer.lookup(".search-bar .text-field"));
        Fx.run(() -> Fx.press(query, KeyCode.DIGIT2, true));
        assertEquals(org.icepdf.fx.view.FitMode.WIDTH, Fx.call(view::getFitMode));

        // Ctrl+Shift+digit is left to the application (recent files), not taken by the view.
        Fx.run(() -> javafx.event.Event.fireEvent(view, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                "", "", KeyCode.DIGIT0, true, true, false, false)));
        assertEquals(org.icepdf.fx.view.FitMode.WIDTH, Fx.call(view::getFitMode));
    }

    @DisplayName("Ctrl+F on the page opens the search bar; F3 moves to the next hit")
    @Test
    void findShortcuts() throws Exception {
        Path copy = Files.copy(Paths.get("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf"), dir.resolve("keys.pdf"));
        open(copy);
        assertFalse(Fx.call(() -> viewer.getView().isShowSearchHits()));
        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.F, true));
        assertTrue(Fx.call(() -> viewer.getView().isShowSearchHits()), "Ctrl+F shows the search bar");

        Fx.run(() -> viewer.getView().search("a"));
        Fx.waitFor("the hits", () -> !viewer.getView().isSearching() && viewer.getView().getSearchHits().size() > 1);
        int first = Fx.call(() -> viewer.getView().getCurrentSearchHitIndex());
        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.F3, false));
        assertEquals(first + 1, Fx.call(() -> viewer.getView().getCurrentSearchHitIndex()), "F3: the next hit");
    }

    @DisplayName("Ctrl+G puts the caret in the page number field, its number selected; Enter goes there, Esc puts it back")
    @Test
    void goToPageFocusesThePageField() throws Exception {
        TinyPdf pdf = TinyPdf.onePage("", "");
        pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>");
        pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>");
        Path file = dir.resolve("three.pdf");
        java.nio.file.Files.write(file, new String(pdf.bytes(), StandardCharsets.ISO_8859_1)
                .replace("/Kids [3 0 R] /Count 1", "/Kids [3 0 R 4 0 R 5 0 R] /Count 3").getBytes(StandardCharsets.ISO_8859_1));
        open(file);
        TextField field = Fx.call(() -> (TextField) viewer.lookup(".page-field .text-field"));
        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.G, true));
        assertSame(field, Fx.call(() -> viewer.getScene().getFocusOwner()), "Ctrl+G: the page field has the caret");
        assertEquals("1", Fx.call(field::getSelectedText), "the page number is selected to type over");

        Fx.run(() -> {
            field.replaceSelection("3");
            field.fireEvent(new javafx.event.ActionEvent());
        });
        assertEquals(2, Fx.call(() -> viewer.getView().getCurrentPageIndex()), "Enter goes to page 3");

        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.G, true));
        Fx.run(() -> {
            field.replaceSelection("2");
            Fx.press(field, KeyCode.ESCAPE, false);
        });
        assertEquals("3", Fx.call(field::getText), "Esc puts the current page back");
        assertEquals(2, Fx.call(() -> viewer.getView().getCurrentPageIndex()));
    }

    /**
     * Redact all from a search, on lines set closer than their glyph bounds are tall (2005CAT.pdf):
     * the boxes reach a sliver into the neighbouring lines, which must survive, and the report must
     * not call the result a failure.
     */
    @DisplayName("search, redact all and export a number on tightly set lines: neighbours survive, the report verifies")
    @Test
    void redactAllOnTightLines() throws Exception {
        String content = "BT /F1 10 Tf 12.4 TL 20 160 Td (dates to even before) Tj T* (in 1949 when the) Tj "
                + "T* (insisted that their) Tj T* (dates to come) Tj ET";
        TinyPdf pdf = TinyPdf.onePage("/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >>", "");
        pdf.addStream("", content.getBytes(StandardCharsets.ISO_8859_1));
        pdf.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        open(pdf.write(dir.resolve("tight.pdf")));

        Fx.run(() -> viewer.getView().search("1949"));
        Fx.waitFor("the hit", () -> !viewer.getView().isSearching() && viewer.getView().getSearchHits().size() == 1);
        assertEquals(1, Fx.call(() -> viewer.getView().redactSearchHits()).get(30, java.util.concurrent.TimeUnit.SECONDS));

        java.util.concurrent.CompletableFuture<org.icepdf.core.util.redaction.RedactionReport> done =
                new java.util.concurrent.CompletableFuture<>();
        Path out = dir.resolve("tight-redacted.pdf");
        Fx.run(() -> viewer.getSession().exportRedacted(out, done::complete));
        org.icepdf.core.util.redaction.RedactionReport report = done.get(60, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(org.icepdf.core.util.redaction.RedactionConfidence.VERIFIED, report.getConfidence(), report.toString());

        org.icepdf.core.pobjects.Document written = new org.icepdf.core.pobjects.Document();
        written.setFile(out.toString());
        try {
            Page page = written.getPageTree().getPage(0);
            page.init();
            String text = page.getViewText().toString().replaceAll("\\s+", " ");
            assertFalse(text.contains("1949"), text);
            for (String kept : new String[]{"dates", "insisted"}) {
                assertTrue(text.contains(kept), "'" + kept + "' on a neighbouring line survives: " + text);
            }
        } finally {
            written.dispose();
        }
    }

    @DisplayName("the redaction report lists a few reasons and warnings and counts the rest, however many there are")
    @Test
    void reportDetailsAreCapped() {
        List<String> details = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) details.add("warning " + i);
        String summary = PdfViewer.detailsSummary(details);
        assertEquals(PdfViewer.REPORT_DETAILS + 1, summary.strip().lines().count(), summary);
        assertTrue(summary.contains("warning 4") && !summary.contains("warning 5\n"), summary);
        assertTrue(summary.endsWith(Messages.format("viewer.report.moreWarnings", 395)), summary);
        assertEquals(2, PdfViewer.detailsSummary(details.subList(0, 2)).strip().lines().count(), "a few: all listed");
    }

    @DisplayName("search hits are highlighted while the search bar shows, hidden with it, and F3 brings it back")
    @Test
    void searchHighlightsFollowTheBar() throws Exception {
        Path copy = Files.copy(Paths.get("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf"), dir.resolve("search.pdf"));
        open(copy);
        assertFalse(Fx.call(() -> viewer.getView().isShowSearchHits()), "no bar, no highlighting");

        Fx.run(() -> viewer.getActions().execute("search.find"));
        assertTrue(Fx.call(() -> viewer.getView().isShowSearchHits()), "the bar shows: highlighted");
        Fx.run(() -> viewer.getView().search("bravo"));
        Fx.waitFor("a hit", () -> viewer.getView().getCurrentSearchHitIndex() >= 0);

        TextField query = Fx.call(() -> (TextField) viewer.lookup(".search-bar .text-field"));
        Fx.run(() -> Fx.press(query, KeyCode.ESCAPE, false));
        assertFalse(Fx.call(() -> viewer.getView().isShowSearchHits()), "the bar hidden: so is the highlighting");
        assertFalse(Fx.call(() -> viewer.getView().getSearchHits().isEmpty()), "the hits are kept");

        Fx.run(() -> viewer.getActions().execute("search.find"));
        assertTrue(Fx.call(() -> viewer.getView().isShowSearchHits()), "shown again with the bar");

        Fx.run(() -> Fx.press(query, KeyCode.ESCAPE, false));
        Fx.run(() -> viewer.getView().search("charlie"));
        Fx.waitFor("the hit to move", () -> viewer.getView().isShowSearchHits());
    }

    @DisplayName("Save with redactions marked asks first; cancelling writes nothing")
    @Test
    void saveAsksAboutRedactions() throws Exception {
        openMarked("a.pdf");
        byte[] before = Files.readAllBytes(dir.resolve("a.pdf"));
        assertFalse(Fx.call(viewer::save));
        assertEquals(Messages.get("viewer.redactionsPending"), dialogs.asked.get(0));
        assertArrayEquals(before, Files.readAllBytes(dir.resolve("a.pdf")));
    }

    @DisplayName("Export Redacted Copy suggests a -redacted name, opens the copy and reports")
    @Test
    void exportRedacted() throws Exception {
        openMarked("a.pdf");
        Path out = dir.resolve("a-redacted.pdf");
        dialogs.choose(dir.resolve("a-redacted"));
        Fx.run(() -> {
            viewer.exportRedacted();
            assertTrue(viewer.getView().isReadOnly(), "no edits while the copy is written");
            assertTrue(viewer.getSession().isBusy());
        });
        assertEquals("a-redacted.pdf", dialogs.suggested.get(0));
        awaitShowing(out);
        Fx.waitFor("the report", () -> !dialogs.shown.isEmpty());
        assertFalse(Fx.call(() -> viewer.getView().isReadOnly()), "editable again once written");
        assertFalse(Fx.call(() -> viewer.getSession().isBusy()));
    }

    // ---- busy ---------------------------------------------------------------------------------

    @DisplayName("while a job holds the document: save, Ctrl+S, Ctrl+W, open and window close say please wait")
    @Test
    void busyRefuses() throws Exception {
        Path a = Files.move(form(), dir.resolve("a.pdf"));
        Path b = pdf("b.pdf");
        open(a);
        // a change, so Save is enabled.
        assertTrue(Fx.call(() -> viewer.getView().setFieldValue("name", "Grace")));
        CountDownLatch release = new CountDownLatch(1);
        Task<Void> job = new Task<>() {
            @Override
            protected Void call() throws Exception {
                release.await();
                return null;
            }
        };
        Fx.run(() -> viewer.getSession().hold(job));
        new Thread(job, "test-job").start();
        String busy = Messages.format("documentSession.busy", "a.pdf");

        assertFalse(Fx.call(viewer::save));
        assertEquals(busy, error());
        Fx.run(() -> viewer.getToast().dismissError());

        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.S, true));
        assertEquals(busy, error(), "Ctrl+S");
        Fx.run(() -> viewer.getToast().dismissError());

        Fx.run(() -> Fx.press(viewer.getView(), KeyCode.W, true));
        assertEquals(busy, error(), "Ctrl+W");
        assertNotNull(Fx.call(() -> viewer.getSession().getDocument()), "still open after Ctrl+W");
        Fx.run(() -> viewer.getToast().dismissError());

        assertFalse(Fx.call(viewer::confirmClose), "the window stays");
        assertEquals(busy, error());
        assertFalse(Fx.call(() -> viewer.getSession().open(b)));
        assertTrue(dialogs.asked.isEmpty(), "nothing asked while busy: " + dialogs.asked);

        release.countDown();
        Fx.waitFor("the job to end", () -> !viewer.getSession().isBusy());
        assertTrue(Fx.call(viewer::confirmClose));
    }

    // ---- drops --------------------------------------------------------------------------------

    @DisplayName("a dropped PDF without an extension opens; a dropped non-PDF is named")
    @Test
    void drops() throws Exception {
        open(pdf("a.pdf"));
        Path noExtension = Files.write(dir.resolve("scan"), TinyPdf.blank());
        Path notes = Files.writeString(dir.resolve("notes.txt"), "hello");

        assertTrue(Fx.call(() -> viewer.filesDropped(List.of(noExtension.toFile(), notes.toFile()))));
        assertEquals(Messages.format("viewer.notPdf", "notes.txt"), message());
        awaitShowing(noExtension);
        assertEquals(1, Fx.call(() -> viewer.getDocuments().size()), "dropped on an unchanged document: it's replaced");

        assertFalse(Fx.call(() -> viewer.filesDropped(List.of(notes.toFile()))), "only a non-PDF: the drop is refused");
    }

    @DisplayName("a PDF dropped on a document with unsaved changes opens in a new tab, keeping the changes")
    @Test
    void dropOnModifiedOpensNewTab() throws Exception {
        openMarked("marked.pdf");
        assertTrue(Fx.call(() -> viewer.getSession().isModified()));
        DocumentPane marked = Fx.call(viewer::getCurrentDocument);
        Path b = pdf("b.pdf");
        assertTrue(Fx.call(() -> viewer.filesDropped(List.of(b.toFile()))));
        awaitShowing(b);
        assertEquals(2, Fx.call(() -> viewer.getDocuments().size()));
        assertTrue(Fx.call(() -> marked.getSession().isModified()), "the changed document is still open, unsaved");
        assertTrue(dialogs.shown.isEmpty(), "nothing was asked");
    }

    @DisplayName("PDFs dropped on the tab bar open in new tabs")
    @Test
    void dropOnTabBarOpensNewTabs() throws Exception {
        open(pdf("a.pdf"));
        Path b = pdf("b.pdf");
        Path c = pdf("c.pdf");
        assertTrue(Fx.call(() -> viewer.filesDroppedOnTabBar(List.of(b.toFile(), c.toFile()))));
        Fx.waitFor("three tabs", () -> viewer.getDocuments().size() == 3);
        awaitShowing(c);
    }

    // ---- attachments --------------------------------------------------------------------------

    /** A document with an embedded PDF (inner.pdf) and an embedded text file (notes.txt). */
    private Path withAttachments() throws IOException {
        TinyPdf pdf = TinyPdf.onePage("",
                "/Names << /EmbeddedFiles << /Names [(inner.pdf) 4 0 R (notes.txt) 6 0 R] >> >>");
        pdf.add("<< /Type /Filespec /F (inner.pdf) /UF (inner.pdf) /EF << /F 5 0 R >> >>");
        pdf.addStream("/Type /EmbeddedFile", TinyPdf.blank());
        pdf.add("<< /Type /Filespec /F (notes.txt) /UF (notes.txt) /EF << /F 7 0 R >> >>");
        pdf.addStream("/Type /EmbeddedFile", "hello".getBytes(StandardCharsets.ISO_8859_1));
        return pdf.write(dir.resolve("outer.pdf"));
    }

    @DisplayName("an attached PDF opened twice shows the same tab")
    @Test
    void attachmentOpensOnce() throws Exception {
        open(withAttachments());
        DocumentPane outer = Fx.call(viewer::getCurrentDocument);
        Attachment inner = Fx.call(() -> Attachment.of(outer.getSession().getDocument())).stream()
                .filter(Attachment::isPdf).findFirst().orElseThrow();

        Fx.run(() -> outer.getAttachments().open(inner));
        Fx.waitFor("inner.pdf to open", () -> viewer.getDocuments().size() == 2
                && viewer.getSession().getDocument() != null && !viewer.getSession().isLoading());
        DocumentPane innerTab = Fx.call(viewer::getCurrentDocument);
        assertEquals("inner.pdf", Fx.call(() -> innerTab.getSession().getFile().getFileName().toString()));

        Fx.run(() -> viewer.selectDocument(outer));
        Fx.run(() -> outer.getAttachments().open(inner));
        Fx.settle();
        assertEquals(2, Fx.call(() -> viewer.getDocuments().size()), "no second tab");
        assertSame(innerTab, Fx.call(viewer::getCurrentDocument));
        assertTrue(dialogs.asked.isEmpty(), "a PDF is opened, not saved: " + dialogs.asked);
    }

    @DisplayName("an attachment that isn't a PDF is saved where the chooser says, in the background")
    @Test
    void attachmentSaved() throws Exception {
        open(withAttachments());
        DocumentPane outer = Fx.call(viewer::getCurrentDocument);
        Attachment notes = Fx.call(() -> Attachment.of(outer.getSession().getDocument())).stream()
                .filter(a -> !a.isPdf()).findFirst().orElseThrow();
        Path target = dir.resolve("saved notes.txt");
        dialogs.choose(target);

        Fx.run(() -> outer.getAttachments().open(notes));
        assertEquals("notes.txt", dialogs.suggested.get(0));
        Fx.waitFor("the attachment to be written", () -> Files.isRegularFile(target));
        assertEquals("hello", Files.readString(target));
        assertEquals("", error());
    }

    // ---- typing in a form field ---------------------------------------------------------------

    private Path form() throws Exception {
        Path source = Paths.get(Objects.requireNonNull(getClass().getResource("/forms/all_fields.pdf")).toURI());
        return Files.copy(source, dir.resolve("form.pdf"));
    }

    /** Opens the "name" text field's editor and types into it; @return the editor */
    private TextField typeIntoName(String text) {
        AbstractWidgetAnnotation name = Fx.call(() -> {
            Page page = viewer.getSession().getDocument().getPageTree().getPage(0);
            for (Annotation annotation : page.getAnnotations()) {
                if (annotation instanceof AbstractWidgetAnnotation w && "name".equals(FormController.fieldNameOf(w))) return w;
            }
            throw new AssertionError("no field \"name\"");
        });
        Fx.run(() -> {
            viewer.getView().focusField(name);
            Fx.layout(viewer);
        });
        Fx.waitFor("the field editor", () -> editor() != null);
        TextField editor = Fx.call(this::editor);
        Fx.run(() -> {
            editor.selectAll();
            Fx.type(editor, text);
        });
        assertEquals(text, Fx.call(editor::getText));
        assertTrue(Fx.call(() -> viewer.getSession().isModified()), "typed text counts as a change");
        return editor;
    }

    private TextField editor() {
        Fx.layout(viewer);
        for (Node node : viewer.getView().lookupAll(".text-field")) {
            if (node instanceof TextField field && field.isVisible() && field.getScene() != null) return field;
        }
        return null;
    }

    @DisplayName("Ctrl+S while typing in a field saves the typed value")
    @Test
    void saveWhileTyping() throws Exception {
        Path form = form();
        open(form);
        TextField editor = typeIntoName("Grace");
        Fx.run(() -> Fx.press(editor, KeyCode.S, true));
        awaitShowing(form);
        assertEquals("Grace", Fx.call(() -> viewer.getView().getFieldValue("name")));
        assertFalse(Fx.call(() -> viewer.getSession().isModified()));
    }

    @DisplayName("Ctrl+W while typing in a field asks about the change, and staying keeps the text")
    @Test
    void closeWhileTyping() throws Exception {
        open(form());
        int[] asked = new int[1];
        Fx.run(() -> viewer.setConfirmDiscard(() -> {
            asked[0]++;
            return false;
        }));
        TextField editor = typeIntoName("Grace");
        Fx.run(() -> Fx.press(editor, KeyCode.W, true));
        assertEquals(1, asked[0]);
        assertNotNull(Fx.call(() -> viewer.getSession().getDocument()), "stayed open");
        assertTrue(Fx.call(() -> viewer.getSession().isModified()));
    }
}
