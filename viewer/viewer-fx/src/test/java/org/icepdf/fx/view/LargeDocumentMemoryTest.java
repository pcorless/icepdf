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
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GH-625: the view holds only what it draws.  Pages are looked up, not pinned, so a page's parsed
 * content goes once nothing draws it; at a low zoom page text loads only when something needs it;
 * and what the user is working on - a field being filled, an edited annotation - survives the pages
 * being collected.  Plus the copy limit on a selection over too many pages.
 */
class LargeDocumentMemoryTest {

    private static final Path FORM = Paths.get("../../core/core-awt/src/test/resources/acroform/all_fields.pdf");

    @TempDir
    Path dir;
    private PdfView view;
    private Document document;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @AfterEach
    void dispose() {
        System.clearProperty(PdfView.MAX_COPY_PAGES_PROPERTY);
        if (view != null) Fx.run(() -> view.setDocument(null));
        if (document != null) document.dispose();
    }

    /** {@code pages} 200x200 pages of a line of Helvetica each; {@code annotate} adds a red square to the first. */
    private Path textPages(int pages, boolean annotate) throws Exception {
        TinyPdf pdf = new TinyPdf();
        pdf.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        // pages are objects 4, 6, 8...: each followed by its content stream; the font is 3.
        for (int i = 0; i < pages; i++) kids.append(4 + 2 * i).append(" 0 R ");
        pdf.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>");
        pdf.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        int annot = 4 + 2 * pages;
        for (int i = 0; i < pages; i++) {
            String annots = annotate && i == 0 ? " /Annots [" + annot + " 0 R]" : "";
            pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents " + (5 + 2 * i)
                    + " 0 R /Resources << /Font << /F1 3 0 R >> >>" + annots + " >>");
            pdf.addStream("", ("BT /F1 12 Tf 20 100 Td (Page " + (i + 1) + " text) Tj ET")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        if (annotate) pdf.add("<< /Type /Annot /Subtype /Square /Rect [40 40 160 160] /C [1 0 0] /F 4 >>");
        return pdf.write(dir.resolve("pages.pdf"));
    }

    private void show(Path file, ViewMode mode, double zoom) throws Exception {
        document = new Document();
        document.setFile(file.toString());
        view = Fx.call(() -> {
            PdfView v = new PdfView();
            Fx.scene(v, 600, 400);
            v.setViewMode(mode);
            v.setFitMode(FitMode.NONE);
            v.setZoom(zoom);
            v.setDocument(document);
            Fx.layout(v);
            return v;
        });
        idle();
    }

    private PdfViewSkin skin() {
        return (PdfViewSkin) view.getSkin();
    }

    private void idle() {
        Fx.waitFor("the view to finish rendering", () -> !view.isRendering());
        Fx.settle();
    }

    /** Collects until {@code done}, or gives up after a while. */
    private static boolean collect(BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 40 && !done.getAsBoolean(); i++) {
            System.gc();
            Thread.sleep(25);
        }
        return done.getAsBoolean();
    }

    private void moveMouse(double x, double y) {
        Fx.run(() -> Event.fireEvent(skin().viewportNode(), new MouseEvent(MouseEvent.MOUSE_MOVED, x, y, x, y,
                MouseButton.NONE, 0, false, false, false, false, false, false, false, false, false, false, null)));
    }

    @DisplayName("a page on screen isn't pinned by the view: its parsed content can be collected")
    @Test
    void visiblePageIsNotPinned() throws Exception {
        show(textPages(3, false), ViewMode.CONTINUOUS, 1);
        WeakReference<Page> page = new WeakReference<>(document.getPageTree().getPage(0));
        assertTrue(page.get() != null && page.get().isInitiated(), "the page was drawn");
        assertTrue(collect(() -> page.get() == null), "nothing in the view holds the drawn page");
        // and the view still finds it when it next needs it.
        Fx.run(() -> view.setZoom(1.5));
        idle();
        assertNotNull(Fx.call(() -> skin().textSequence(0)), "page 1's text loads again");
    }

    @DisplayName("below 50% zoom on-screen text loads only when the pointer comes over its page")
    @Test
    void lowZoomTextLoadsOnHover() throws Exception {
        show(textPages(40, false), ViewMode.FACING_CONTINUOUS, 0.25);
        for (int i = 0; i < 6; i++) {
            int page = i;
            assertFalse(Fx.call(() -> skin().isTextLoaded(page)), "page " + (page + 1) + " text not loaded up front");
        }
        Center center = center(0);
        moveMouse(center.x(), center.y());
        Fx.waitFor("page 1's text to load on hover", () -> skin().isTextLoaded(0));
        assertFalse(Fx.call(() -> skin().isTextLoaded(1)), "only the page under the pointer");

        Fx.run(() -> view.setZoom(1));
        idle();
        assertTrue(Fx.call(() -> skin().isTextLoaded(view.getCurrentPageIndex())), "from 50% up text loads eagerly");
    }

    private record Center(double x, double y) {
    }

    /** The middle of a page's slot, in viewport coordinates. */
    private Center center(int pageIndex) {
        return Fx.call(() -> {
            DocumentLayout.PageSlot s = skin().layout().getSlot(pageIndex);
            return new Center(s.x() + s.width() / 2 - skin().scrollX(), s.y() + s.height() / 2 - skin().scrollY());
        });
    }

    @DisplayName("a field being filled in survives its page being collected, and the value lands in the document")
    @Test
    void focusedFieldSurvivesCollection() throws Exception {
        assumeTrue(Files.exists(FORM), "fixture " + FORM);
        show(FORM, ViewMode.CONTINUOUS, 1);
        AbstractWidgetAnnotation name = widget("name");
        Fx.run(() -> view.focusField(name));
        Fx.waitFor("the field editor", () -> view.lookup(".pdf-field-editor") != null);
        TextInputControl editor = Fx.call(() -> (TextInputControl) view.lookup(".pdf-field-editor"));
        Fx.run(() -> editor.setText("Kept through a GC"));
        collect(() -> false);
        assertSame(name, Fx.call(view::getFocusedField), "focus is on the same field after collections");
        Fx.run(view::clearFieldFocus);
        idle();
        assertEquals("Kept through a GC", String.valueOf(widget("name").getFieldDictionary().getFieldValue()),
                "the document's own widget has the value");
        assertEquals("Kept through a GC", saveAndReopenFieldValue("name"));
    }

    private AbstractWidgetAnnotation widget(String fieldName) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation<?> w
                    && fieldName.equals(w.getFieldDictionary().getFullyQualifiedFieldName())) return w;
        }
        throw new AssertionError("no field " + fieldName);
    }

    private String saveAndReopenFieldValue(String fieldName) throws Exception {
        Path saved = dir.resolve("saved.pdf");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
            document.saveToOutputStream(out);
        }
        Document reopened = new Document();
        reopened.setFile(saved.toString());
        try {
            Page page = reopened.getPageTree().getPage(0);
            page.init();
            for (Annotation a : page.getAnnotations()) {
                if (a instanceof AbstractWidgetAnnotation<?> w
                        && fieldName.equals(w.getFieldDictionary().getFullyQualifiedFieldName())) {
                    return String.valueOf(w.getFieldDictionary().getFieldValue());
                }
            }
            return null;
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("an edited annotation survives scrolling far away, collection, and scrolling back")
    @Test
    void editedAnnotationSurvivesScrolling() throws Exception {
        int pages = 60;
        show(textPages(pages, true), ViewMode.CONTINUOUS, 1);
        Page first = document.getPageTree().getPage(0);
        first.init();
        SquareAnnotation square = (SquareAnnotation) first.getAnnotations().get(0);
        Fx.run(() -> view.applyAnnotationProperties(square, AnnotationProperties.ofColor(Color.BLUE)));
        idle();
        first = null;

        Fx.run(() -> view.setCurrentPageIndex(pages - 1));
        idle();
        collect(() -> false);
        Fx.run(() -> view.setCurrentPageIndex(0));
        idle();

        Page again = document.getPageTree().getPage(0);
        again.init();
        List<SquareAnnotation> squares = new ArrayList<>();
        for (Annotation a : again.getAnnotations()) if (a instanceof SquareAnnotation s) squares.add(s);
        assertEquals(1, squares.size());
        assertEquals(Color.BLUE, squares.get(0).getColor(), "the edit is still on the page's annotation");
        assertTrue(Fx.call(view::isCanUndo), "and still undoable");
    }

    @DisplayName("copying a selection over more pages than the limit is refused with an error event; the limit is a system property")
    @Test
    void copyLimit() throws Exception {
        show(textPages(3, false), ViewMode.CONTINUOUS, 1);
        List<String> errors = new ArrayList<>();
        Fx.run(() -> {
            view.addEventHandler(PdfViewErrorEvent.ERROR, e -> errors.add(e.getMessage()));
            view.selectAll();
        });

        System.setProperty(PdfView.MAX_COPY_PAGES_PROPERTY, "2");
        Fx.run(view::copySelection);
        assertEquals(1, errors.size(), "three pages over a limit of two");
        assertTrue(errors.get(0).contains("2"), errors.get(0));

        System.setProperty(PdfView.MAX_COPY_PAGES_PROPERTY, "3");
        assertEquals("Page 1 text\nPage 2 text\nPage 3 text",
                view.selectedTextAsync().get().strip().replaceAll("\\s*\\n\\s*", "\n"));
        Fx.run(view::copySelection);
        Fx.settle();
        assertEquals(1, errors.size(), "within the limit: copied, no error");
    }
}
