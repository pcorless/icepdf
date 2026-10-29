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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Private annotations through {@link PdfView}: marking comments on pages that aren't shown, one
 * undo step for several, and the default for new annotations.
 */
class AnnotationPrivacyTest {

    /** Two pages: in single page view only the first is shown. */
    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/potato_out.pdf");

    private Document document;
    private PdfView view;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @AfterEach
    void dispose() {
        if (view != null) Fx.run(() -> view.setDocument(null));
        if (document != null) document.dispose();
    }

    private void open() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        document = new Document();
        document.setFile(FIXTURE.toString());
        assumeTrue(document.getNumberOfPages() >= 2, "fixture has two pages");
        view = Fx.call(() -> {
            PdfView v = new PdfView();
            Fx.scene(v, 600, 500);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setDocument(document);
            Fx.layout(v);
            return v;
        });
        Fx.settle();
    }

    private PdfViewSkin skin() {
        return (PdfViewSkin) view.getSkin();
    }

    /** The newest markup annotation on a page. */
    private MarkupAnnotation newest(int pageIndex) {
        List<Annotation> annotations = document.getPageTree().getPage(pageIndex).getAnnotations();
        for (int i = annotations.size() - 1; i >= 0; i--) {
            if (annotations.get(i) instanceof MarkupAnnotation markup) return markup;
        }
        return null;
    }

    @DisplayName("a note on a page that isn't shown, without /P, is marked private and undo makes it public")
    @Test
    void offscreenWithoutParentPage() throws Exception {
        open();
        Page second = document.getPageTree().getPage(1);
        // made on the shown page, then moved to the second without /P, as many files have them.
        Fx.run(() -> skin().annotations().createNote(0, 40, 40));
        TextAnnotation note = (TextAnnotation) newest(0);
        assertNotNull(note);
        Page first = document.getPageTree().getPage(0);
        first.getAnnotations().remove(note);
        if (note.getPopupAnnotation() != null) first.getAnnotations().remove(note.getPopupAnnotation());
        second.addAnnotation(note, true);
        note.getEntries().remove(Annotation.PARENT_PAGE_KEY);
        note.setPageIndex(-1);
        assertTrue(second.getAnnotations().contains(note));
        assertEquals(0, view.getCurrentPageIndex());

        int changed = Fx.call(() -> view.setAnnotationsPrivate(List.of(note), true));
        assertEquals(1, changed);
        assertTrue(note.getFlagPrivateContents());
        assertTrue(note.getPopupAnnotation().getFlagPrivateContents());

        assertEquals(0, (int) Fx.call(() -> view.setAnnotationsPrivate(List.of(note), true)), "already private");

        Fx.run(view::undo);
        assertFalse(note.getFlagPrivateContents());
        assertFalse(note.getPopupAnnotation().getFlagPrivateContents());
    }

    @DisplayName("several comments change as one undo step")
    @Test
    void severalOneUndo() throws Exception {
        open();
        Fx.run(() -> skin().annotations().createNote(0, 40, 40));
        MarkupAnnotation a = newest(0);
        Fx.run(() -> skin().annotations().createNote(0, 120, 40));
        MarkupAnnotation b = newest(0);
        assertNotSame(a, b);

        assertEquals(2, (int) Fx.call(() -> view.setAnnotationsPrivate(List.of(a, b, a), true)));
        assertTrue(a.getFlagPrivateContents() && b.getFlagPrivateContents());
        Fx.run(view::undo);
        assertFalse(a.getFlagPrivateContents() || b.getFlagPrivateContents());
        Fx.run(view::redo);
        assertTrue(a.getFlagPrivateContents() && b.getFlagPrivateContents());
    }

    @DisplayName("with new annotations private, a note is made private and a redaction mark is not")
    @Test
    void newAnnotationsPrivate() throws Exception {
        open();
        Fx.run(() -> view.setNewAnnotationsPrivate(true));
        Fx.run(() -> skin().annotations().createNote(0, 40, 40));
        MarkupAnnotation note = newest(0);
        assertTrue(note.getFlagPrivateContents());
        assertTrue(note.getPopupAnnotation().getFlagPrivateContents());

        Fx.run(() -> skin().annotations().createRedactionBox(0, new Rectangle2D.Double(60, 60, 50, 20)));
        MarkupAnnotation mark = newest(0);
        assertInstanceOf(RedactionAnnotation.class, mark);
        assertFalse(mark.getFlagPrivateContents(), "redaction marks stay public");

        Fx.run(() -> view.setNewAnnotationsPrivate(false));
        Fx.run(() -> skin().annotations().createNote(0, 140, 40));
        assertFalse(newest(0).getFlagPrivateContents());
    }
}
