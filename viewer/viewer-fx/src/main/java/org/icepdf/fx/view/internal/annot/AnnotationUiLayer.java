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

import org.icepdf.fx.view.internal.raster.RasterBuffer;

import org.icepdf.fx.i18n.Messages;

import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
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
 * It sits at the same viewport-relative origin as the page's {@code PageLayer}, above every page
 * layer, and is created and dropped with it.  Chrome is drawn in page view space (logical px), so
 * outlines and handles keep a constant on-screen size at any zoom.
 */
public final class AnnotationUiLayer extends Group {

    static final double HANDLE = 7;
    private static final Color CHROME = Color.rgb(0, 119, 255);

    private static final double BADGE = 16;
    private final int pageIndex;
    private final Rectangle hover = new Rectangle();
    private final Rectangle selection = new Rectangle();
    private final Rectangle fieldFocus = new Rectangle();
    private Annotation focused;
    private final List<Rectangle> handles = new ArrayList<>(8);
    private final Group popups = new Group();
    // live drag: the annotation rendered alone, mapped from where it was to where it is being put.
    private final javafx.scene.image.ImageView proxy = new javafx.scene.image.ImageView();
    private final javafx.scene.transform.Affine proxyTransform = new javafx.scene.transform.Affine();
    private Rectangle2D proxyFrom;
    private Rectangle2D dragBounds;
    // open popups: one node and one connector tail each.
    private final java.util.Map<org.icepdf.core.pobjects.annotations.PopupAnnotation, PopupNode> popupNodes =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<PopupNode, javafx.scene.shape.Path> glue = new java.util.HashMap<>();
    private final Group glueLines = new Group();
    // signature validity badges, top-left of each signed field on this page.
    private final Group badges = new Group();
    private java.util.Map<Annotation, org.icepdf.fx.signature.SignatureStatus> badgeStatuses = java.util.Map.of();
    private final java.util.Map<Annotation, Node> badgeNodes = new java.util.HashMap<>();
    // a popup being dragged/resized: placed from the live gesture, not its /Rect.
    private PopupNode reshaping;
    private double[] reshape;
    private AffineTransform pageToView;
    private Annotation hovered;
    private Annotation selected;
    private boolean handlesShown;

    public AnnotationUiLayer(int pageIndex) {
        this.pageIndex = pageIndex;
        setAutoSizeChildren(false);
        hover.setFill(null);
        hover.setStroke(CHROME);
        hover.getStrokeDashArray().setAll(3.0, 3.0);
        hover.setVisible(false);
        hover.setMouseTransparent(true);
        fieldFocus.setFill(null);
        fieldFocus.setStroke(CHROME);
        fieldFocus.setStrokeWidth(2);
        fieldFocus.setVisible(false);
        fieldFocus.setMouseTransparent(true);
        fieldFocus.getStyleClass().add("pdf-field-focus");
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
        getChildren().addAll(badges, glueLines, popups, proxy);
        getChildren().addAll(hover, selection, fieldFocus);
        getChildren().addAll(handles);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    /** Popup nodes (page space, scaled with the page) live here, under the chrome. */
    Group popups() {
        return popups;
    }

    public void setPageToView(AffineTransform pageToView) {
        this.pageToView = pageToView;
        place();
    }

    public void setHovered(Annotation annotation) {
        if (annotation == hovered) return;
        hovered = annotation;
        place();
    }

    /** The form field with input focus on this page (focus ring), or null. */
    public void setFocusedField(Annotation annotation) {
        if (annotation == focused) return;
        focused = annotation;
        place();
    }

    public void setSelected(Annotation annotation, boolean withHandles) {
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
    public void setProxy(RasterBuffer buffer, double deviceX, double deviceY, double scale, Rectangle2D from,
                  boolean multiply) {
        proxy.setImage(buffer.getImage());
        // a highlight follows the pointer as it shows on the page: multiplied over it.
        proxy.setBlendMode(multiply ? BlendMode.MULTIPLY : null);
        proxy.setX(deviceX / scale);
        proxy.setY(deviceY / scale);
        proxy.setFitWidth(buffer.getWidth() / scale);
        proxy.setFitHeight(buffer.getHeight() / scale);
        proxyFrom = from;
        proxy.setVisible(true);
        placeProxy();
    }

    public boolean hasProxy() {
        return proxy.isVisible();
    }

    public void clearProxy() {
        proxy.setVisible(false);
        proxy.setImage(null);
        proxyFrom = null;
    }

    /** During a drag, the bounds the selection chrome and proxy follow; null when not dragging. */
    public void setDragBounds(Rectangle2D bounds) {
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
    public void refresh() {
        place();
    }

    /**
     * An annotation's rectangle in this page's view space; axis-aligned for the 90° rotations.
     */
    public Rectangle2D viewBounds(Annotation annotation) {
        return pageToView.createTransformedShape(annotation.getUserSpaceRectangle()).getBounds2D();
    }

    /**
     * Which handle (0-7, clockwise from top-left: TL, T, TR, R, BR, B, BL, L) is at a point in page
     * view space, or -1.
     */
    public int handleAt(double x, double y) {
        if (!handlesShown || selected == null) return -1;
        for (int i = 0; i < handles.size(); i++) {
            Rectangle h = handles.get(i);
            if (x >= h.getX() - 2 && x <= h.getX() + HANDLE + 2 && y >= h.getY() - 2 && y <= h.getY() + HANDLE + 2) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Shows a validity badge on each signed field of this page; a click on one reports its status.
     *
     * @param statuses the page's signature widgets (this page's annotation objects) and their status
     */
    public void setSignatureBadges(java.util.Map<Annotation, org.icepdf.fx.signature.SignatureStatus> statuses,
                            java.util.function.Consumer<org.icepdf.fx.signature.SignatureStatus> clicked) {
        if (statuses.equals(badgeStatuses)) {
            place();
            return;
        }
        badgeStatuses = java.util.Map.copyOf(statuses);
        badges.getChildren().clear();
        badgeNodes.clear();
        for (java.util.Map.Entry<Annotation, org.icepdf.fx.signature.SignatureStatus> e : badgeStatuses.entrySet()) {
            org.icepdf.fx.signature.SignatureStatus status = e.getValue();
            Node icon = org.icepdf.fx.signature.SignatureIcons.icon(status.verdict(), BADGE);
            javafx.scene.Group badge = new javafx.scene.Group(icon);
            badge.getStyleClass().add("pdf-signature-badge");
            badge.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(badge, new Tooltip(status.summary()
                    + (status.signerName() != null ? "\n" + Messages.format("signatureBadge.signedBy", status.signerName()) : "")));
            badge.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, javafx.scene.input.MouseEvent::consume);
            badge.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, javafx.scene.input.MouseEvent::consume);
            badge.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, ev -> {
                ev.consume();
                clicked.accept(status);
            });
            badgeNodes.put(e.getKey(), badge);
            badges.getChildren().add(badge);
        }
        place();
    }

    private void place() {
        for (java.util.Map.Entry<Annotation, Node> e : badgeNodes.entrySet()) {
            e.getValue().setVisible(pageToView != null);
            if (pageToView == null) continue;
            Rectangle2D b = viewBounds(e.getKey());
            e.getValue().setLayoutX(Math.round(b.getMinX() - BADGE / 2));
            e.getValue().setLayoutY(Math.round(b.getMinY() - BADGE / 2));
        }
        boolean showHover = hovered != null && hovered != selected && pageToView != null;
        hover.setVisible(showHover);
        if (showHover) setRect(hover, viewBounds(hovered), 1);
        boolean showFocus = focused != null && pageToView != null;
        fieldFocus.setVisible(showFocus);
        if (showFocus) setRect(fieldFocus, viewBounds(focused), 2);
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
    public void updatePopups(java.util.List<org.icepdf.core.pobjects.annotations.Annotation> annotations, double zoom,
                      PopupNode.Listener listener, boolean editable) {
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
                    javafx.scene.shape.Path tail = new javafx.scene.shape.Path();
                    tail.setStroke(null);
                    glue.put(node, tail);
                    glueLines.getChildren().add(tail);
                }
                node.setEditable(editable);
                node.refreshColour();
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
    public void setPopupReshape(PopupNode node, double dx, double dy, double dw, double dh, double zoom) {
        reshaping = node;
        reshape = new double[]{dx, dy, dw, dh};
        placePopup(node, zoom);
    }

    public void clearPopupReshape(double zoom) {
        PopupNode node = reshaping;
        reshaping = null;
        reshape = null;
        if (node != null) placePopup(node, zoom);
    }

    /** The popup's top-left in this page's view space: its /Rect's view bounds' minimum. */
    public double[] popupAnchor(org.icepdf.core.pobjects.annotations.PopupAnnotation popup) {
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
        javafx.scene.shape.Path tail = glue.get(node);
        if (tail != null) {
            Rectangle2D m = viewBounds(node.getMarkup());
            double pw = Math.max(PopupNode.MIN_WIDTH, w) * zoom;
            double ph = Math.max(PopupNode.MIN_HEIGHT, h) * zoom;
            boolean apart = !m.intersects(x, y, pw, ph);
            tail.setVisible(apart);
            if (apart) {
                tail.getElements().setAll(glueTail(m.getCenterX(), m.getCenterY(), x, y, pw, ph, GLUE_BASE * zoom));
                tail.setFill(Color.color(node.colour().getRed(), node.colour().getGreen(),
                        node.colour().getBlue(), 0.5));
            }
        }
    }

    /** Width (points) where the glue tail meets the popup. */
    static final double GLUE_BASE = 14;

    /**
     * The glue between a markup annotation and its popup: a tail from the markup's centre that
     * widens, with gently concave sides, to {@code base} where it meets the popup edge facing the
     * markup.  A narrower take on the Swing viewer's sweep, which spans the whole popup edge.
     */
    static java.util.List<javafx.scene.shape.PathElement> glueTail(double tipX, double tipY, double x, double y,
                                                                 double w, double h, double base) {
        // nearest point on the popup rectangle; at a corner, the edge on the axis of greater gap.
        double ex = Math.max(x, Math.min(tipX, x + w));
        double ey = Math.max(y, Math.min(tipY, y + h));
        double gapX = Math.max(x - tipX, tipX - (x + w));
        double gapY = Math.max(y - tipY, tipY - (y + h));
        double ax, ay, bx, by;
        if (gapX >= gapY) {
            double half = Math.min(base, h) / 2;
            double cy = Math.max(y + half, Math.min(ey, y + h - half));
            ax = ex; ay = cy - half; bx = ex; by = cy + half;
        } else {
            double half = Math.min(base, w) / 2;
            double cx = Math.max(x + half, Math.min(ex, x + w - half));
            ax = cx - half; ay = ey; bx = cx + half; by = ey;
        }
        // each side's control point sits halfway between that side's midpoint and the centre
        // line's, so the sides bow inward.
        double mx = (tipX + (ax + bx) / 2) / 2;
        double my = (tipY + (ay + by) / 2) / 2;
        return java.util.List.of(
                new javafx.scene.shape.MoveTo(tipX, tipY),
                new javafx.scene.shape.QuadCurveTo((mx + (tipX + ax) / 2) / 2, (my + (tipY + ay) / 2) / 2, ax, ay),
                new javafx.scene.shape.LineTo(bx, by),
                new javafx.scene.shape.QuadCurveTo((mx + (tipX + bx) / 2) / 2, (my + (tipY + by) / 2) / 2, tipX, tipY),
                new javafx.scene.shape.ClosePath());
    }

    /** Open popup nodes, for tests and lookups. */
    public java.util.Collection<PopupNode> popupNodes() {
        return popupNodes.values();
    }

    // ---- creation ---------------------------------------------------------------------------

    private Node creationPreview;
    private javafx.scene.control.TextArea textEditor;
    private javafx.scene.layout.HBox textActions;

    /** A drawing tool's live outline (view space); null removes it. */
    public void setCreationPreview(Node node) {
        if (creationPreview != null) getChildren().remove(creationPreview);
        creationPreview = node;
        if (node != null) getChildren().add(node);
    }

    /**
     * An inline editor over a free text annotation (view space), with save and cancel buttons under
     * its corner.  Enter (or Ctrl+Enter) saves, Shift+Enter starts a new line, Esc cancels; leaving
     * it (a click elsewhere) saves too.
     *
     * @param commit told the text when it is saved
     * @param cancel run when it is cancelled
     */
    public void openTextEditor(Rectangle2D viewBounds, String text, double zoom, java.util.function.Consumer<String> commit,
                               Runnable cancel) {
        closeTextEditor();
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(text == null ? "" : text);
        area.getStyleClass().add("pdf-free-text-editor");
        area.setWrapText(true);
        area.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-size: %.1fpx;", Math.max(6, 12 * zoom)));
        area.setLayoutX(viewBounds.getX());
        area.setLayoutY(viewBounds.getY());
        area.setPrefSize(Math.max(40, viewBounds.getWidth()), Math.max(24, viewBounds.getHeight()));
        // children aren't auto-sized here (popups keep their own size).
        area.resize(area.getPrefWidth(), area.getPrefHeight());
        boolean[] done = {false};
        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            String value = area.getText();
            javafx.application.Platform.runLater(() -> {
                closeTextEditor();
                commit.accept(value);
            });
        };
        Runnable abandon = () -> {
            if (done[0]) return;
            done[0] = true;
            closeTextEditor();
            cancel.run();
        };
        area.focusedProperty().addListener((obs, was, now) -> {
            if (!now) finish.run();
        });
        area.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && e.isShiftDown()) {
                area.replaceSelection("\n");
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                finish.run();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                abandon.run();
                e.consume();
            }
        });
        area.addEventHandler(javafx.scene.input.MouseEvent.ANY, javafx.scene.input.MouseEvent::consume);

        javafx.scene.control.Button save = editorButton("M2.5 7.5 L5.5 10.5 L11.5 3.5", "freeTextEditor.save", finish);
        javafx.scene.control.Button discard = editorButton("M3.5 3.5 L10.5 10.5 M10.5 3.5 L3.5 10.5", "freeTextEditor.cancel", abandon);
        javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(2, save, discard);
        actions.getStyleClass().add("pdf-free-text-actions");
        // under the editor's bottom-right corner; the page's own clicks don't reach through.
        actions.addEventHandler(javafx.scene.input.MouseEvent.ANY, javafx.scene.input.MouseEvent::consume);
        textEditor = area;
        textActions = actions;
        getChildren().addAll(area, actions);
        actions.applyCss();
        actions.autosize();
        actions.setLayoutX(viewBounds.getX() + area.getPrefWidth() - actions.getWidth());
        actions.setLayoutY(viewBounds.getY() + area.getPrefHeight() + 2);
        area.requestFocus();
    }

    /** A small save or cancel button that leaves the caret in the editor. */
    private static javafx.scene.control.Button editorButton(String glyph, String tooltipKey, Runnable action) {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent(glyph);
        path.setFill(null);
        path.setStrokeWidth(1.5);
        path.getStyleClass().add("pdf-free-text-glyph");
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        button.setGraphic(path);
        button.setTooltip(new javafx.scene.control.Tooltip(Messages.get(tooltipKey)));
        button.setAccessibleText(Messages.get(tooltipKey));
        // not focusable: a press would take the caret from the editor, which saves it.
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    /** An inline text editor is open on this page. */
    public boolean isEditingText() {
        return textEditor != null;
    }

    /** Adds a form field editor (view space) above everything else on this page. */
    public void addFieldEditor(Node editor) {
        getChildren().add(editor);
    }

    public void removeFieldEditor(Node editor) {
        getChildren().remove(editor);
    }

    void closeTextEditor() {
        if (textEditor != null) {
            getChildren().removeAll(textEditor, textActions);
            textEditor = null;
            textActions = null;
        }
    }

    /** For tests: the editor's save and cancel buttons, or null. */
    javafx.scene.layout.HBox textActions() {
        return textActions;
    }

    /** For tests: the selection outline node. */
    Node selectionOutline() {
        return selection;
    }
}
