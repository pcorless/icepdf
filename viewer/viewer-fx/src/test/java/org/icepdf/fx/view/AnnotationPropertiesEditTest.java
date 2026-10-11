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
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Property edits (colour, opacity, border, fill, flags) apply, undo and redo without a toolkit. */
class AnnotationPropertiesEditTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();

    private static Page page() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        Document document = new Document();
        document.setFile(FIXTURE.toString());
        Page page = document.getPageTree().getPage(0);
        page.init();
        return page;
    }

    @DisplayName("a colour change on a square undoes and redoes")
    @Test
    void colourUndoRedo() throws Exception {
        Page page = page();
        Library library = page.getLibrary();
        AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        MarkupAnnotation square = AnnotationCreator.shape(library, false, new Rectangle2D.Double(50, 50, 120, 60),
                toPage, new AnnotationCreator.Style("t", Color.RED, 255, 2f));
        page.addAnnotation(square);

        AnnotationEdits.Edit edit = AnnotationEdits.properties(NO_LOCK, page, 0, square,
                AnnotationProperties.ofColor(Color.BLUE), toPage);
        assertEquals(Color.BLUE.getRGB(), square.getColor().getRGB());
        edit.undo();
        assertEquals(Color.RED.getRGB(), square.getColor().getRGB());
        edit.redo();
        assertEquals(Color.BLUE.getRGB(), square.getColor().getRGB());
    }

    @DisplayName("fill, border width and flags change together and undo restores all of them")
    @Test
    void multipleProperties() throws Exception {
        Page page = page();
        Library library = page.getLibrary();
        AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        SquareAnnotation square = (SquareAnnotation) AnnotationCreator.shape(library, false,
                new Rectangle2D.Double(50, 50, 120, 60), toPage, new AnnotationCreator.Style("t", Color.RED, 255, 2f));
        page.addAnnotation(square);
        AnnotationProperties before = AnnotationProperties.capture(square);

        AnnotationProperties change = new AnnotationProperties(Color.GREEN, Color.YELLOW, true, null, 5f, null, null,
                null, null, null, null, null, true, null, null, null);
        AnnotationEdits.Edit edit = AnnotationEdits.properties(NO_LOCK, page, 0, square, change, toPage);
        assertTrue(square.isFillColor());
        assertEquals(Color.YELLOW.getRGB(), square.getFillColor().getRGB());
        assertEquals(5f, square.getBorderStyle().getStrokeWidth());
        assertTrue(square.getFlagPrint());

        edit.undo();
        assertEquals(before.fillEnabled(), square.isFillColor());
        assertEquals(before.borderWidth(), square.getBorderStyle().getStrokeWidth());
        assertEquals(before.print(), square.getFlagPrint());
        assertEquals(before.color().getRGB(), square.getColor().getRGB());
    }

    @DisplayName("ink opacity changes and widgets and popups are not editable")
    @Test
    void opacityAndSupport() throws Exception {
        Page page = page();
        Library library = page.getLibrary();
        AffineTransform toPage = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        GeneralPath stroke = new GeneralPath();
        stroke.moveTo(100, 300);
        stroke.lineTo(140, 330);
        stroke.lineTo(180, 290);
        InkAnnotation ink = AnnotationCreator.ink(library, stroke, toPage, new AnnotationCreator.Style("t", Color.RED, 255, 2f));
        page.addAnnotation(ink);
        AnnotationProperties opacity = new AnnotationProperties(null, null, null, 0.5f, null, null, null, null, null,
                null, null, null, null, null, null, null);
        AnnotationEdits.Edit edit = AnnotationEdits.properties(NO_LOCK, page, 0, ink, opacity, toPage);
        assertEquals(0.5f, ink.getOpacity(), 0.01f);
        edit.undo();
        assertEquals(1f, ink.getOpacity(), 0.01f);

        assertTrue(AnnotationProperties.supports(ink));
        assertFalse(AnnotationProperties.supports(null));
    }
}
