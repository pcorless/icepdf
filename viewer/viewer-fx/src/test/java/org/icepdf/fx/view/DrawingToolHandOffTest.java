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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.TinyPdf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A drawing tool hands a press on an annotation to selection: what was just drawn can be moved
 * and resized without changing tools.  And new annotations take the tool's colour.
 */
class DrawingToolHandOffTest {

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
        TinyPdf pdf = TinyPdf.onePage("", "");
        Path file = pdf.write(dir.resolve("one.pdf"));
        document = new Document();
        document.setFile(file.toString());
        view = Fx.call(() -> {
            PdfView v = new PdfView();
            Fx.scene(v, 600, 600);
            v.setFitMode(FitMode.NONE);
            v.setZoom(1);
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

    /** Drags between two points given relative to page 1's top-left, in view pixels. */
    private void drag(double fromX, double fromY, double toX, double toY) {
        Fx.run(() -> {
            DocumentLayout.PageSlot slot = skin().layout().getSlot(0);
            double ox = slot.x() - skin().scrollX();
            double oy = slot.y() - skin().scrollY();
            int steps = 6;
            for (int i = 0; i <= steps + 1; i++) {
                double t = Math.min(1, i / (double) steps);
                double x = ox + fromX + (toX - fromX) * t;
                double y = oy + fromY + (toY - fromY) * t;
                var type = i == 0 ? MouseEvent.MOUSE_PRESSED : i <= steps ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_RELEASED;
                Event.fireEvent(skin().viewportNode(), new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                        false, false, false, false, type != MouseEvent.MOUSE_RELEASED, false, false, true, false,
                        true, null));
            }
        });
    }

    private List<Annotation> annotations() {
        return Fx.call(() -> document.getPageTree().getPage(0).getAnnotations().stream()
                .filter(Objects::nonNull)
                .filter(a -> !a.isDeleted() && a instanceof org.icepdf.core.pobjects.annotations.MarkupAnnotation)
                .toList());
    }

    private Rectangle2D bounds(Annotation a) {
        return Fx.call(() -> (Rectangle2D) a.getUserSpaceRectangle().clone());
    }

    @DisplayName("the rectangle tool moves the rectangle it just drew, and an unselected one, rather than drawing another")
    @Test
    void rectangleToolMovesAnnotations() {
        Fx.run(() -> view.setToolMode(ToolMode.RECTANGLE));
        drag(40, 40, 120, 120);
        assertEquals(1, annotations().size(), "the drag drew a rectangle");
        Annotation drawn = annotations().get(0);
        assertSame(drawn, Fx.call(view::getSelectedAnnotation), "and selected it");
        Rectangle2D before = bounds(drawn);

        drag(80, 80, 110, 80);
        assertEquals(1, annotations().size(), "a drag on it is no second rectangle");
        assertEquals(ToolMode.RECTANGLE, Fx.call(view::getToolMode), "the tool stays");
        assertEquals(before.getX() + 30, bounds(drawn).getX(), 1, "it moved right");

        Fx.run(view::clearAnnotationSelection);
        drag(100, 80, 100, 110);
        assertEquals(1, annotations().size());
        assertEquals(before.getY() - 30, bounds(drawn).getY(), 1, "an unselected one moves too (down the page is down in user space)");

        drag(10, 160, 50, 195);
        assertEquals(2, annotations().size(), "off the annotation the tool draws");
    }

    @DisplayName("the ink tool moves ink grabbed by a stroke, and draws in the space inside its bounds")
    @Test
    void inkToolTakesInkByItsStrokes() {
        Fx.run(() -> view.setToolMode(ToolMode.INK));
        drag(40, 40, 140, 140);
        assertEquals(1, annotations().size());
        Annotation first = annotations().get(0);
        Rectangle2D before = bounds(first);

        drag(130, 50, 100, 70);
        assertEquals(2, annotations().size(), "off the stroke, inside its bounds: a second stroke");

        Fx.run(view::clearAnnotationSelection);
        drag(90, 90, 110, 90);
        assertEquals(2, annotations().size(), "on the stroke: no new ink");
        assertEquals(before.getX() + 20, bounds(first).getX(), 1, "the first stroke moved");

        drag(105, 85, 105, 115);
        assertEquals(before.getY() - 30, bounds(first).getY(), 1, "and is still taken by its stroke where it is now");
    }

    @DisplayName("a tool's colour comes first, then the general colour, then the tool's default")
    @Test
    void toolColours() {
        Fx.run(() -> view.setToolMode(ToolMode.RECTANGLE));
        assertEquals(PdfView.defaultToolColor(ToolMode.RECTANGLE), Fx.call(() -> view.toolColor(ToolMode.RECTANGLE)));
        Fx.run(() -> view.setAnnotationColor(Color.BLUE));
        assertEquals(Color.BLUE, Fx.call(() -> view.toolColor(ToolMode.RECTANGLE)));
        Fx.run(() -> view.getToolColors().put(ToolMode.RECTANGLE, Color.LIME));
        assertEquals(Color.LIME, Fx.call(() -> view.toolColor(ToolMode.RECTANGLE)));
        assertEquals(Color.BLUE, Fx.call(() -> view.toolColor(ToolMode.ELLIPSE)), "other tools keep the general colour");

        drag(40, 40, 120, 120);
        assertEquals(new java.awt.Color(0, 255, 0), Fx.call(() -> annotations().get(0).getColor()));
    }

    private javafx.scene.control.TextArea textEditor() {
        return Fx.call(() -> (javafx.scene.control.TextArea) view.lookup(".pdf-free-text-editor"));
    }

    private void key(javafx.scene.Node target, javafx.scene.input.KeyCode code, boolean shift) {
        Fx.run(() -> Event.fireEvent(target, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                "", "", code, shift, false, false, false)));
        Fx.settle();
    }

    @DisplayName("a text box: Shift+Enter is a new line, Enter saves; Esc on a new box takes it away")
    @Test
    void freeTextKeys() {
        Fx.run(() -> view.setToolMode(ToolMode.FREE_TEXT));
        drag(20, 20, 20, 20);
        assertEquals(1, annotations().size());
        javafx.scene.control.TextArea editor = textEditor();
        assertNotNull(editor, "the editor opens on the new box");
        assertNotNull(Fx.call(() -> view.lookup(".pdf-free-text-actions")), "with its save and cancel buttons");
        Fx.run(() -> editor.replaceSelection("Hello"));
        key(editor, javafx.scene.input.KeyCode.ENTER, true);
        assertEquals("Hello\n", Fx.call(editor::getText), "Shift+Enter: a new line");
        assertNotNull(textEditor(), "still editing");
        Fx.run(() -> editor.replaceSelection("world"));
        key(editor, javafx.scene.input.KeyCode.ENTER, false);
        assertNull(textEditor(), "Enter: saved and closed");
        Annotation box = annotations().get(0);
        assertEquals("Hello\nworld", Fx.call(box::getContents));

        drag(20, 140, 20, 140);
        assertEquals(2, annotations().size());
        key(textEditor(), javafx.scene.input.KeyCode.ESCAPE, false);
        assertNull(textEditor());
        assertEquals(1, annotations().size(), "Esc: the new box is gone");

        drag(20, 140, 20, 140);
        assertEquals(2, annotations().size());
        drag(120, 160, 120, 160);
        assertEquals(2, annotations().size(), "a click away from an open box doesn't start another");
    }

    @DisplayName("a text box's save button saves; saving a new box empty takes it away")
    @Test
    void freeTextButtons() {
        Fx.run(() -> view.setToolMode(ToolMode.FREE_TEXT));
        drag(20, 20, 20, 20);
        javafx.scene.control.TextArea editor = textEditor();
        Fx.run(() -> editor.replaceSelection("Saved"));
        javafx.scene.Parent actions = Fx.call(() -> (javafx.scene.Parent) view.lookup(".pdf-free-text-actions"));
        Fx.run(() -> ((javafx.scene.control.Button) actions.getChildrenUnmodifiable().get(0)).fire());
        Fx.settle();
        assertNull(textEditor());
        assertEquals("Saved", Fx.call(() -> annotations().get(0).getContents()));

        drag(20, 140, 20, 140);
        assertEquals(2, annotations().size());
        key(textEditor(), javafx.scene.input.KeyCode.ENTER, false);
        assertEquals(1, annotations().size(), "an empty new box isn't kept");
    }

    /** A point of an annotation's /Rect in page-1 view pixels (zoom 1, unrotated, 200pt page). */
    private double[] corner(Annotation a, boolean max) {
        Rectangle2D r = bounds(a);
        return max ? new double[]{r.getMaxX(), 200 - r.getMinY()} : new double[]{r.getMinX(), 200 - r.getMaxY()};
    }

    @DisplayName("resizing ink stretches its strokes with the box, and undo puts them back")
    @Test
    void inkResizesLinearly() {
        Fx.run(() -> view.setToolMode(ToolMode.INK));
        drag(40, 40, 140, 140);
        org.icepdf.core.pobjects.annotations.InkAnnotation ink =
                (org.icepdf.core.pobjects.annotations.InkAnnotation) annotations().get(0);
        Rectangle2D rect = bounds(ink);
        Rectangle2D strokes = Fx.call(() -> ink.getInkPath().getBounds2D());
        double[] br = corner(ink, true);
        drag(br[0], br[1], br[0] + 40, br[1] + 20);
        Rectangle2D rectAfter = bounds(ink);
        Rectangle2D strokesAfter = Fx.call(() -> ink.getInkPath().getBounds2D());
        assertEquals(rect.getWidth() + 40, rectAfter.getWidth(), 1, "the box grew");
        double sx = rectAfter.getWidth() / rect.getWidth();
        double sy = rectAfter.getHeight() / rect.getHeight();
        assertEquals(strokes.getWidth() * sx, strokesAfter.getWidth(), 0.5, "the strokes stretched across");
        assertEquals(strokes.getHeight() * sy, strokesAfter.getHeight(), 0.5, "and down");
        assertTrue(rectAfter.contains(strokesAfter), "inside the new box");
        Fx.run(view::undo);
        Rectangle2D undone = Fx.call(() -> ink.getInkPath().getBounds2D());
        assertEquals(strokes.getX(), undone.getX(), 0.01, "undo: the strokes as they were");
        assertEquals(strokes.getWidth(), undone.getWidth(), 0.01);
        assertEquals(strokes.getHeight(), undone.getHeight(), 0.01);
        assertEquals(rect.getWidth(), bounds(ink).getWidth(), 0.01);
    }

    @DisplayName("resizing a line moves its ends with the box")
    @Test
    void lineResizesLinearly() {
        Fx.run(() -> view.setToolMode(ToolMode.LINE));
        drag(40, 40, 140, 100);
        org.icepdf.core.pobjects.annotations.LineAnnotation line =
                (org.icepdf.core.pobjects.annotations.LineAnnotation) annotations().get(0);
        Rectangle2D rect = bounds(line);
        double length = Fx.call(() -> line.getStartOfLine().distance(line.getEndOfLine()));
        double[] br = corner(line, true);
        drag(br[0], br[1], br[0] + 30, br[1]);
        Rectangle2D rectAfter = bounds(line);
        double sx = rectAfter.getWidth() / rect.getWidth();
        assertTrue(sx > 1.2, "the box grew");
        java.awt.geom.Point2D start = Fx.call(() -> (java.awt.geom.Point2D) line.getStartOfLine().clone());
        java.awt.geom.Point2D end = Fx.call(() -> (java.awt.geom.Point2D) line.getEndOfLine().clone());
        assertTrue(rectAfter.contains(start) && rectAfter.contains(end), "both ends inside the new box");
        assertTrue(start.distance(end) > length * 1.1, "the line got longer");
    }

    @DisplayName("text boxes and notes only move: a drag from the corner of a selected one moves it, same size")
    @Test
    void textBoxesAndNotesOnlyMove() {
        Fx.run(() -> view.setToolMode(ToolMode.FREE_TEXT));
        drag(20, 20, 20, 20);
        Fx.run(() -> textEditor().replaceSelection("Fixed"));
        key(textEditor(), javafx.scene.input.KeyCode.ENTER, false);
        Fx.run(() -> view.setToolMode(ToolMode.NOTE));
        drag(150, 150, 150, 150);
        assertEquals(2, annotations().size());
        Fx.run(() -> view.setToolMode(ToolMode.TEXT_SELECT));
        for (Annotation a : annotations()) {
            Fx.run(() -> view.selectAnnotation(a));
            Rectangle2D before = bounds(a);
            double[] br = corner(a, true);
            drag(br[0] - 1, br[1] - 1, br[0] - 11, br[1] - 1);
            Rectangle2D after = bounds(a);
            assertEquals(before.getWidth(), after.getWidth(), 0.01, a.getClass().getSimpleName() + " kept its size");
            assertEquals(before.getHeight(), after.getHeight(), 0.01);
            assertEquals(before.getX() - 10, after.getX(), 1, a.getClass().getSimpleName() + " moved");
        }
    }
}
