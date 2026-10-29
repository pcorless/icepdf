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
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The annotation board edits comments on any page: colour, text and delete through {@link PdfView}
 * for a comment on a page that isn't shown, without /P, each undoable.
 */
class OffscreenAnnotationEditTest {

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

    /** A note made on the shown page, then moved to the second without /P, as many files have them. */
    private TextAnnotation offscreenNote() throws Exception {
        Page second = document.getPageTree().getPage(1);
        Fx.run(() -> skin().annotations().createNote(0, 40, 40));
        TextAnnotation note = (TextAnnotation) newest(0);
        Page first = document.getPageTree().getPage(0);
        first.getAnnotations().remove(note);
        if (note.getPopupAnnotation() != null) first.getAnnotations().remove(note.getPopupAnnotation());
        second.addAnnotation(note, true);
        note.getEntries().remove(Annotation.PARENT_PAGE_KEY);
        note.setPageIndex(-1);
        assertEquals(0, view.getCurrentPageIndex());
        return note;
    }

    @DisplayName("colour, text and delete of a comment on a page that isn't shown, each undone")
    @Test
    void editOffscreen() throws Exception {
        open();
        TextAnnotation note = offscreenNote();
        java.awt.Color before = note.getColor();

        Fx.run(() -> view.applyAnnotationProperties(note, AnnotationProperties.ofColor(java.awt.Color.GREEN)));
        assertEquals(java.awt.Color.GREEN.getRGB(), note.getColor().getRGB());
        Fx.run(view::undo);
        assertEquals(before.getRGB(), note.getColor().getRGB());

        Fx.run(() -> view.setAnnotationContents(note, "from the board"));
        assertEquals("from the board", note.getContents());
        Fx.run(view::undo);
        assertEquals("", note.getContents() == null ? "" : note.getContents());

        Page second = document.getPageTree().getPage(1);
        Fx.run(() -> view.deleteAnnotation(note));
        assertTrue(note.isDeleted() || !second.getAnnotations().contains(note), "deleted");
        Fx.run(view::undo);
        assertTrue(second.getAnnotations().contains(note) && !note.isDeleted(), "back after undo");
    }
}
