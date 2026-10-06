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

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * The drawing tools: NOTE and FREE_TEXT are clicks; RECTANGLE, ELLIPSE, LINE and INK are drags with
 * a live preview shape in the page's UI layer, turned into an annotation on release (see
 * {@link AnnotationCreator}).  The gesture stays on the page it started on.
 */
final class AnnotationCreateHandler implements ToolHandler {

    private final PdfViewSkin skin;
    private ToolMode mode = ToolMode.RECTANGLE;
    private int pageIndex = -1;
    private Point2D start;
    private Point2D end;
    private GeneralPath ink;
    private Path inkPreview;
    private Node preview;

    AnnotationCreateHandler(PdfViewSkin skin) {
        this.skin = skin;
    }

    void setMode(ToolMode mode) {
        this.mode = mode;
    }

    @Override
    public void uninstall() {
        clearPreview();
        pageIndex = -1;
    }

    @Override
    public void pressed(MouseEvent e) {
        PagePoint point = skin.pageAtViewport(e.getX(), e.getY());
        if (point == null) {
            pageIndex = -1;
            return;
        }
        pageIndex = point.pageIndex();
        start = skin.toPageView(pageIndex, e.getX(), e.getY());
        end = start;
        if (mode == ToolMode.INK) {
            ink = new GeneralPath();
            ink.moveTo(start.getX(), start.getY());
            inkPreview = new Path(new MoveTo(start.getX(), start.getY()));
            style(inkPreview);
            inkPreview.setFill(null);
            showPreview(inkPreview);
        }
    }

    @Override
    public void dragged(MouseEvent e) {
        if (pageIndex < 0) return;
        Point2D p = skin.toPageView(pageIndex, e.getX(), e.getY());
        if (p == null) return;
        end = p;
        switch (mode) {
            case INK -> {
                ink.lineTo(p.getX(), p.getY());
                inkPreview.getElements().add(new LineTo(p.getX(), p.getY()));
            }
            case RECTANGLE -> {
                Rectangle2D r = rect();
                Rectangle shape = new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
                style(shape);
                showPreview(shape);
            }
            case ELLIPSE -> {
                Rectangle2D r = rect();
                Ellipse shape = new Ellipse(r.getCenterX(), r.getCenterY(), r.getWidth() / 2, r.getHeight() / 2);
                style(shape);
                showPreview(shape);
            }
            case LINE -> {
                Line shape = new Line(start.getX(), start.getY(), p.getX(), p.getY());
                style(shape);
                showPreview(shape);
            }
            default -> {
            }
        }
    }

    @Override
    public void released(MouseEvent e) {
        if (pageIndex < 0) return;
        int page = pageIndex;
        pageIndex = -1;
        clearPreview();
        boolean click = start.distance(end) < 4;
        switch (mode) {
            case NOTE -> skin.createNote(page, end.getX(), end.getY());
            case FREE_TEXT -> skin.createFreeText(page, end.getX(), end.getY());
            case RECTANGLE, ELLIPSE -> {
                if (!click) skin.createShape(page, mode == ToolMode.ELLIPSE, rect());
            }
            case LINE -> {
                if (!click) skin.createLine(page, start, end);
            }
            case INK -> {
                if (ink != null && ink.getBounds2D().getWidth() + ink.getBounds2D().getHeight() > 3) {
                    skin.createInk(page, ink);
                }
                ink = null;
            }
            default -> {
            }
        }
    }

    @Override
    public Cursor idleCursor() {
        return Cursor.CROSSHAIR;
    }

    private Rectangle2D rect() {
        return new Rectangle2D.Double(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()),
                Math.abs(end.getX() - start.getX()), Math.abs(end.getY() - start.getY()));
    }

    private void style(Shape shape) {
        shape.setFill(null);
        shape.setStroke(Color.rgb(0, 119, 255));
        shape.getStrokeDashArray().setAll(4.0, 3.0);
        shape.setMouseTransparent(true);
    }

    private void showPreview(Node node) {
        preview = node;
        AnnotationUiLayer ui = skin.uiLayer(pageIndex);
        if (ui != null) ui.setCreationPreview(node);
    }

    private void clearPreview() {
        if (preview == null) return;
        skin.uiLayers().forEach(ui -> ui.setCreationPreview(null));
        preview = null;
        inkPreview = null;
    }
}
