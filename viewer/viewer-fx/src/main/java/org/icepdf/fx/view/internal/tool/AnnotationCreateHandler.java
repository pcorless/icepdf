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
package org.icepdf.fx.view.internal.tool;

import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.ToolMode;

import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.annot.AnnotationUiLayer;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * The drawing tools: NOTE and FREE_TEXT are clicks; RECTANGLE, ELLIPSE, LINE and INK are drags with
 * a live preview shape in the page's UI layer, turned into an annotation on release (see
 * {@link org.icepdf.fx.view.internal.annot.AnnotationCreator}).  The gesture stays on the page it started on.
 * <p>
 * A press on an annotation that can be moved, or on a resize handle of the selected one, is the
 * selection tool's instead (select, move, resize, double-click to edit), so what was just drawn can
 * be adjusted without changing tools.  The ink tool only gives way to the handles: a stroke usually
 * starts inside the bounds of the one before it.
 */
public final class AnnotationCreateHandler implements ToolHandler {

    private final ToolContext context;
    private final TextSelectHandler select;
    // the selection tool has this gesture.
    private boolean selecting;
    private ToolMode mode = ToolMode.RECTANGLE;
    private int pageIndex = -1;
    private Point2D start;
    private Point2D end;
    private GeneralPath ink;
    private Path inkPreview;
    private Node preview;

    public AnnotationCreateHandler(ToolContext context, TextSelectHandler select) {
        this.context = context;
        this.select = select;
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
    }

    @Override
    public void uninstall() {
        clearPreview();
        pageIndex = -1;
        if (selecting) select.uninstall();
        selecting = false;
    }

    /** The press (or hover) at a viewport point belongs to the selection tool. */
    private boolean selects(double vx, double vy) {
        if (context.handleAtViewport(vx, vy) >= 0) return true;
        if (mode == ToolMode.INK) return false;
        AnnotationHit hit = context.annotations().annotationAtViewport(vx, vy);
        return hit != null && !hit.isActionable() && context.annotations().canMove(hit.annotation());
    }

    @Override
    public void moved(MouseEvent e) {
        if (selects(e.getX(), e.getY())) {
            select.moved(e);
            // a press here moves the annotation, selected yet or not.
            if (context.handleAtViewport(e.getX(), e.getY()) < 0) context.setViewportCursor(Cursor.MOVE);
        } else {
            context.annotations().setHovered(null);
            context.setViewportCursor(Cursor.CROSSHAIR);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        // Delete, Esc (cancel a move, deselect) and the rest of the selection keys.
        return select.keyPressed(e);
    }

    @Override
    public void pressed(MouseEvent e) {
        selecting = selects(e.getX(), e.getY());
        if (selecting) {
            pageIndex = -1;
            select.pressed(e);
            return;
        }
        PagePoint point = context.pageAtViewport(e.getX(), e.getY());
        if (point == null) {
            pageIndex = -1;
            return;
        }
        pageIndex = point.pageIndex();
        start = context.toPageView(pageIndex, e.getX(), e.getY());
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
        if (selecting) {
            select.dragged(e);
            return;
        }
        if (pageIndex < 0) return;
        Point2D p = context.toPageView(pageIndex, e.getX(), e.getY());
        if (p == null) return;
        end = p;
        switch (mode) {
            case INK -> {
                ink.lineTo(p.getX(), p.getY());
                inkPreview.getElements().add(new LineTo(p.getX(), p.getY()));
            }
            case RECTANGLE, SIGNATURE, REDACT -> {
                Rectangle2D r = rect();
                Rectangle shape = new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
                style(shape);
                if (mode == ToolMode.SIGNATURE) {
                    shape.getStrokeDashArray().setAll(4.0, 3.0);
                    shape.setFill(javafx.scene.paint.Color.rgb(21, 101, 192, 0.08));
                } else if (mode == ToolMode.REDACT) {
                    shape.getStrokeDashArray().clear();
                    shape.setStroke(Color.BLACK);
                    shape.setFill(Color.rgb(0, 0, 0, 0.35));
                }
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
        if (selecting) {
            selecting = false;
            select.released(e);
            return;
        }
        if (pageIndex < 0) return;
        int page = pageIndex;
        pageIndex = -1;
        clearPreview();
        boolean click = start.distance(end) < 4;
        switch (mode) {
            case NOTE -> context.annotations().createNote(page, end.getX(), end.getY());
            case FREE_TEXT -> context.annotations().createFreeText(page, end.getX(), end.getY());
            case RECTANGLE, ELLIPSE -> {
                if (!click) context.annotations().createShape(page, mode == ToolMode.ELLIPSE, rect());
            }
            case LINE -> {
                if (!click) context.annotations().createLine(page, start, end);
            }
            case SIGNATURE -> {
                if (!click) context.signatures().createField(page, rect());
            }
            case REDACT -> {
                if (!click) context.annotations().createRedactionBox(page, rect());
            }
            case INK -> {
                if (ink != null && ink.getBounds2D().getWidth() + ink.getBounds2D().getHeight() > 3) {
                    context.annotations().createInk(page, ink);
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
        AnnotationUiLayer ui = context.uiLayer(pageIndex);
        if (ui != null) ui.setCreationPreview(node);
    }

    private void clearPreview() {
        if (preview == null) return;
        context.uiLayers().forEach(ui -> ui.setCreationPreview(null));
        preview = null;
        inkPreview = null;
    }
}
