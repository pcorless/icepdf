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
    // live drag: the annotation rendered alone, mapped from where it was to where it is being put.
    private final javafx.scene.image.ImageView proxy = new javafx.scene.image.ImageView();
    private final javafx.scene.transform.Affine proxyTransform = new javafx.scene.transform.Affine();
    private Rectangle2D proxyFrom;
    private Rectangle2D dragBounds;
    // open popups: one node and one connector line each.
    private final java.util.Map<org.icepdf.core.pobjects.annotations.PopupAnnotation, PopupNode> popupNodes =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<PopupNode, javafx.scene.shape.Line> glue = new java.util.HashMap<>();
    private final Group glueLines = new Group();
    // a popup being dragged/resized: placed from the live gesture, not its /Rect.
    private PopupNode reshaping;
    private double[] reshape;
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
        proxy.getTransforms().add(proxyTransform);
        proxy.setMouseTransparent(true);
        proxy.setVisible(false);
        glueLines.setMouseTransparent(true);
        getChildren().addAll(glueLines, popups, proxy);
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

    /**
     * Shows the live drag node: {@code buffer} holds the annotation rendered alone over the device
     * region whose top-left is (deviceX, deviceY); {@code from} is the annotation's view bounds when
     * the drag began.
     */
    void setProxy(RasterBuffer buffer, double deviceX, double deviceY, double scale, Rectangle2D from) {
        proxy.setImage(buffer.getImage());
        proxy.setX(deviceX / scale);
        proxy.setY(deviceY / scale);
        proxy.setFitWidth(buffer.getWidth() / scale);
        proxy.setFitHeight(buffer.getHeight() / scale);
        proxyFrom = from;
        proxy.setVisible(true);
        placeProxy();
    }

    boolean hasProxy() {
        return proxy.isVisible();
    }

    void clearProxy() {
        proxy.setVisible(false);
        proxy.setImage(null);
        proxyFrom = null;
    }

    /** During a drag, the bounds the selection chrome and proxy follow; null when not dragging. */
    void setDragBounds(Rectangle2D bounds) {
        dragBounds = bounds;
        place();
        placeProxy();
    }

    private void placeProxy() {
        if (proxyFrom == null) return;
        Rectangle2D to = dragBounds != null ? dragBounds : proxyFrom;
        double sx = to.getWidth() / Math.max(1e-6, proxyFrom.getWidth());
        double sy = to.getHeight() / Math.max(1e-6, proxyFrom.getHeight());
        // map proxyFrom onto to: translate(to) . scale . translate(-from)
        proxyTransform.setToTransform(sx, 0, to.getX() - sx * proxyFrom.getX(),
                0, sy, to.getY() - sy * proxyFrom.getY());
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
        Rectangle2D b = showSelection ? (dragBounds != null ? dragBounds : viewBounds(selected)) : null;
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

    // ---- popups ---------------------------------------------------------------------------

    /**
     * Creates, places and drops popup nodes to match the page's open popups.  Popups are not
     * clipped to the page and are placed exactly from their /Rect, so one can sit past the page edge.
     */
    void updatePopups(java.util.List<org.icepdf.core.pobjects.annotations.Annotation> annotations, double zoom,
                      PopupNode.Listener listener) {
        java.util.Set<org.icepdf.core.pobjects.annotations.PopupAnnotation> open = new java.util.HashSet<>();
        if (annotations != null && pageToView != null) {
            for (org.icepdf.core.pobjects.annotations.Annotation a : annotations) {
                if (!(a instanceof org.icepdf.core.pobjects.annotations.PopupAnnotation popup) || popup.isDeleted()
                        || !popup.isOpen()) continue;
                org.icepdf.core.pobjects.annotations.MarkupAnnotation parent = popup.getParent();
                if (parent == null || parent.isDeleted()) continue;
                open.add(popup);
                PopupNode node = popupNodes.get(popup);
                if (node == null) {
                    node = new PopupNode(popup, parent, listener);
                    popupNodes.put(popup, node);
                    popups.getChildren().add(node);
                    javafx.scene.shape.Line line = new javafx.scene.shape.Line();
                    line.setStroke(Color.rgb(153, 153, 153));
                    glue.put(node, line);
                    glueLines.getChildren().add(line);
                }
                placePopup(node, zoom);
            }
        }
        popupNodes.entrySet().removeIf(e -> {
            if (open.contains(e.getKey())) return false;
            popups.getChildren().remove(e.getValue());
            glueLines.getChildren().remove(glue.remove(e.getValue()));
            if (reshaping == e.getValue()) reshaping = null;
            return true;
        });
    }

    /** Live feedback while a popup is dragged or resized (view px since the gesture began). */
    void setPopupReshape(PopupNode node, double dx, double dy, double dw, double dh, double zoom) {
        reshaping = node;
        reshape = new double[]{dx, dy, dw, dh};
        placePopup(node, zoom);
    }

    void clearPopupReshape(double zoom) {
        PopupNode node = reshaping;
        reshaping = null;
        reshape = null;
        if (node != null) placePopup(node, zoom);
    }

    /** The popup's top-left in this page's view space: its /Rect's view bounds' minimum. */
    double[] popupAnchor(org.icepdf.core.pobjects.annotations.PopupAnnotation popup) {
        Rectangle2D b = viewBounds(popup);
        return new double[]{b.getMinX(), b.getMinY()};
    }

    private void placePopup(PopupNode node, double zoom) {
        java.awt.geom.Rectangle2D.Float rect = node.getPopup().getUserSpaceRectangle();
        double[] anchor = popupAnchor(node.getPopup());
        double x = anchor[0];
        double y = anchor[1];
        double w = rect.getWidth();
        double h = rect.getHeight();
        if (node == reshaping && reshape != null) {
            x += reshape[0];
            y += reshape[1];
            w = Math.max(PopupNode.MIN_WIDTH, w + reshape[2] / zoom);
            h = Math.max(PopupNode.MIN_HEIGHT, h + reshape[3] / zoom);
        }
        node.place(x, y, w, h, zoom);
        // connector: from the markup annotation's centre to the popup's nearest corner.
        javafx.scene.shape.Line line = glue.get(node);
        if (line != null) {
            Rectangle2D m = viewBounds(node.getMarkup());
            double pw = Math.max(PopupNode.MIN_WIDTH, w) * zoom;
            double ph = Math.max(PopupNode.MIN_HEIGHT, h) * zoom;
            double cx = m.getCenterX();
            double cy = m.getCenterY();
            line.setStartX(cx);
            line.setStartY(cy);
            line.setEndX(Math.abs(cx - x) < Math.abs(cx - (x + pw)) ? x : x + pw);
            line.setEndY(Math.abs(cy - y) < Math.abs(cy - (y + ph)) ? y : y + ph);
            line.setVisible(!m.intersects(x, y, pw, ph));
        }
    }

    /** Open popup nodes, for tests and lookups. */
    java.util.Collection<PopupNode> popupNodes() {
        return popupNodes.values();
    }

    /** For tests: the selection outline node. */
    Node selectionOutline() {
        return selection;
    }
}
