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

    @DisplayName("the ink tool draws over an ink annotation: a stroke starts inside the last one's bounds")
    @Test
    void inkToolDrawsOverInk() {
        Fx.run(() -> view.setToolMode(ToolMode.INK));
        drag(40, 40, 140, 140);
        assertEquals(1, annotations().size());
        drag(90, 60, 60, 120);
        assertEquals(2, annotations().size(), "the second stroke is an annotation of its own");
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
}
