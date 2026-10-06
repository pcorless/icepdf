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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.icepdf.core.pobjects.annotations.Annotation;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Annotation UI for one visible page, deliberately <b>not</b> clipped to the page: selection
 * chrome and markup popups may extend past the page edge into the gap or margin, as the Swing
 * viewer's popups do (it hosts them on the document view's POPUP_LAYER, not the page component).
 * <p>
 * It sits at the same viewport-relative origin as the page's {@link PageLayer}, above every page
 * layer, and is created and dropped with it.  Chrome is drawn in page view space (logical px), so
 * outlines and handles keep a constant on-screen size at any zoom.
 */
final class AnnotationUiLayer extends Group {

    static final double HANDLE = 7;
    private static final Color CHROME = Color.rgb(0, 119, 255);

    private final int pageIndex;
    private final Rectangle hover = new Rectangle();
    private final Rectangle selection = new Rectangle();
    private final List<Rectangle> handles = new ArrayList<>(8);
    private final Group popups = new Group();
    private AffineTransform pageToView;
    private Annotation hovered;
    private Annotation selected;
    private boolean handlesShown;

    AnnotationUiLayer(int pageIndex) {
        this.pageIndex = pageIndex;
        setAutoSizeChildren(false);
        hover.setFill(null);
        hover.setStroke(CHROME);
        hover.getStrokeDashArray().setAll(3.0, 3.0);
        hover.setVisible(false);
        hover.setMouseTransparent(true);
        selection.setFill(null);
        selection.setStroke(CHROME);
        selection.setStrokeWidth(1.5);
        selection.setVisible(false);
        selection.setMouseTransparent(true);
        for (int i = 0; i < 8; i++) {
            Rectangle handle = new Rectangle(HANDLE, HANDLE, Color.WHITE);
            handle.setStroke(CHROME);
            handle.setVisible(false);
            handle.setMouseTransparent(true);
            handles.add(handle);
        }
        getChildren().add(popups);
        getChildren().addAll(hover, selection);
        getChildren().addAll(handles);
    }

    int getPageIndex() {
        return pageIndex;
    }

    /** Popup nodes (page space, scaled with the page) live here, under the chrome. */
    Group popups() {
        return popups;
    }

    void setPageToView(AffineTransform pageToView) {
        this.pageToView = pageToView;
        place();
    }

    void setHovered(Annotation annotation) {
        if (annotation == hovered) return;
        hovered = annotation;
        place();
    }

    void setSelected(Annotation annotation, boolean withHandles) {
        if (annotation == selected && withHandles == handlesShown) return;
        selected = annotation;
        handlesShown = withHandles;
        place();
    }

    /** Re-reads the annotations' rectangles (after a move or resize). */
    void refresh() {
        place();
    }

    /**
     * An annotation's rectangle in this page's view space; axis-aligned for the 90° rotations.
     */
    Rectangle2D viewBounds(Annotation annotation) {
        return pageToView.createTransformedShape(annotation.getUserSpaceRectangle()).getBounds2D();
    }

    /**
     * Which handle (0-7, clockwise from top-left: TL, T, TR, R, BR, B, BL, L) is at a point in page
     * view space, or -1.
     */
    int handleAt(double x, double y) {
        if (!handlesShown || selected == null) return -1;
        for (int i = 0; i < handles.size(); i++) {
            Rectangle h = handles.get(i);
            if (x >= h.getX() - 2 && x <= h.getX() + HANDLE + 2 && y >= h.getY() - 2 && y <= h.getY() + HANDLE + 2) {
                return i;
            }
        }
        return -1;
    }

    private void place() {
        boolean showHover = hovered != null && hovered != selected && pageToView != null;
        hover.setVisible(showHover);
        if (showHover) setRect(hover, viewBounds(hovered), 1);
        boolean showSelection = selected != null && pageToView != null;
        selection.setVisible(showSelection);
        Rectangle2D b = showSelection ? viewBounds(selected) : null;
        if (showSelection) setRect(selection, b, 2);
        for (int i = 0; i < handles.size(); i++) {
            Rectangle handle = handles.get(i);
            handle.setVisible(showSelection && handlesShown);
            if (!showSelection || !handlesShown) continue;
            double[] p = handlePoint(b, i);
            handle.setX(p[0] - HANDLE / 2);
            handle.setY(p[1] - HANDLE / 2);
        }
    }

    /** Handle centres: corners and edge midpoints, clockwise from top-left. */
    static double[] handlePoint(Rectangle2D b, int i) {
        double cx = b.getCenterX();
        double cy = b.getCenterY();
        return switch (i) {
            case 0 -> new double[]{b.getMinX(), b.getMinY()};
            case 1 -> new double[]{cx, b.getMinY()};
            case 2 -> new double[]{b.getMaxX(), b.getMinY()};
            case 3 -> new double[]{b.getMaxX(), cy};
            case 4 -> new double[]{b.getMaxX(), b.getMaxY()};
            case 5 -> new double[]{cx, b.getMaxY()};
            case 6 -> new double[]{b.getMinX(), b.getMaxY()};
            default -> new double[]{b.getMinX(), cy};
        };
    }

    private static void setRect(Rectangle r, Rectangle2D b, double outset) {
        r.setX(b.getX() - outset);
        r.setY(b.getY() - outset);
        r.setWidth(b.getWidth() + 2 * outset);
        r.setHeight(b.getHeight() + 2 * outset);
    }

    /** For tests: the selection outline node. */
    Node selectionOutline() {
        return selection;
    }
}
