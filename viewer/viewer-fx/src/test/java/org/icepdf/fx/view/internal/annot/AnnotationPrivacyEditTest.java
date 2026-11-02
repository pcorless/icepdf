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
package org.icepdf.fx.view.internal.annot;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The private flag edit: markup and popup together, undo, redo, and through a save. */
class AnnotationPrivacyEditTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();
    private static final String AUTHOR = "privacy tester";

    private static Document open(Path file) {
        Document document = new Document();
        try {
            document.setFile(file.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return document;
    }

    private static TextAnnotation note(Page page) {
        AffineTransform pageToView = page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        TextAnnotation note = AnnotationCreator.note(page.getLibrary(), 120, 120, pageToView, toPage,
                new AnnotationCreator.Style(AUTHOR, Color.YELLOW, 255, 1f));
        PopupAnnotation popup = AnnotationCreator.popup(page.getLibrary(), note, false, toPage);
        AnnotationEdits.add(NO_LOCK, page, 0, note, popup);
        return note;
    }

    @DisplayName("marking private sets the markup and its popup; undo restores each as it was")
    @Test
    void privateUndoRedo() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        Document document = open(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextAnnotation note = note(page);
            PopupAnnotation popup = note.getPopupAnnotation();
            // a popup out of step with its markup (another tool) is put back out of step by undo.
            popup.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, true);
            note.setModifiedDate("D:20000101000000Z");
            String oldDate = note.getModifiedDate().toString();

            AnnotationEdits.Edit edit = AnnotationEdits.privacy(NO_LOCK, page, 0, note, true);
            assertTrue(note.getFlagPrivateContents());
            assertTrue(popup.getFlagPrivateContents());
            assertNotEquals(oldDate, note.getModifiedDate().toString(), "/M is updated");

            edit.undo();
            assertFalse(note.getFlagPrivateContents());
            assertTrue(popup.getFlagPrivateContents());

            edit.redo();
            assertTrue(note.getFlagPrivateContents());

            boolean print = note.getFlagPrint(), noZoom = note.getFlagNoZoom();
            AnnotationEdits.privacy(NO_LOCK, page, 0, note, false);
            assertFalse(note.getFlagPrivateContents());
            assertFalse(popup.getFlagPrivateContents());
            // the bit is the only flag touched.
            assertEquals(print, note.getFlagPrint());
            assertEquals(noZoom, note.getFlagNoZoom());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a private note and its popup are still private after a save and reopen")
    @Test
    void survivesSave() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        Document document = open(FIXTURE);
        Path saved = Files.createTempFile("pdfview-privacy", ".pdf");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextAnnotation note = note(page);
            AnnotationEdits.privacy(NO_LOCK, page, 0, note, true);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(saved))) {
                document.saveToOutputStream(out);
            }

            Document reopened = open(saved);
            try {
                Page again = reopened.getPageTree().getPage(0);
                again.init();
                MarkupAnnotation mine = null;
                for (Annotation a : again.getAnnotations()) {
                    if (a instanceof MarkupAnnotation m && AUTHOR.equals(m.getTitleText())) mine = m;
                }
                assertNotNull(mine, "the note was saved");
                assertTrue(mine.getFlagPrivateContents());
                assertNotNull(mine.getPopupAnnotation());
                assertTrue(mine.getPopupAnnotation().getFlagPrivateContents());
            } finally {
                reopened.dispose();
            }
        } finally {
            document.dispose();
            Files.deleteIfExists(saved);
        }
    }
}
