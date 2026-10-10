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
package org.icepdf.fx.ri.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.icepdf.fx.ri.UserLayout;

/**
 * The viewer's layout: a header across the top, the document in the middle, and an
 * {@link EdgeDrawer} on each side (the tool rail and the utility panel).  A drawer's strip and a
 * docked drawer take room from the document; an overlay floats above the document without moving
 * it - the document keeps its size, so its tiles aren't rendered again.
 * <p>
 * Esc or a click on the document closes open overlays.
 */
public final class ViewerShell extends Region {

    private Node header;
    private Node center;
    private EdgeDrawer left;
    private EdgeDrawer right;

    public ViewerShell() {
        getStyleClass().add("viewer-shell");
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && closeOverlays()) e.consume();
        });
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (center != null && isInside(center, e)) closeOverlays();
        });
    }

    public void setHeader(Node header) {
        replace(this.header, header);
        this.header = header;
    }

    public Node getHeader() {
        return header;
    }

    public void setCenter(Node center) {
        replace(this.center, center);
        this.center = center;
    }

    // drawers already listened to (a drawer can be put back after changing sides).
    private final java.util.Set<EdgeDrawer> watched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Puts a drawer on its side, replacing any there; a drawer that was on the other side moves.
     */
    public void setDrawer(EdgeDrawer drawer) {
        if (left == drawer) left = null;
        if (right == drawer) right = null;
        remove(drawer);
        EdgeDrawer old = drawer.getSide() == UserLayout.Side.LEFT ? left : right;
        if (old != null) remove(old);
        if (drawer.getSide() == UserLayout.Side.LEFT) left = drawer;
        else right = drawer;
        if (drawer.getStrip() != null) getChildren().add(drawer.getStrip());
        getChildren().add(drawer.getContent());
        if (watched.add(drawer)) {
            drawer.stateProperty().addListener((o, a, b) -> {
                orderOverlays();
                requestLayout();
            });
            drawer.widthProperty().addListener((o, a, b) -> requestLayout());
        }
        orderOverlays();
        requestLayout();
    }

    private void remove(EdgeDrawer drawer) {
        getChildren().remove(drawer.getContent());
        if (drawer.getStrip() != null) getChildren().remove(drawer.getStrip());
        if (left == drawer) left = null;
        if (right == drawer) right = null;
    }

    public EdgeDrawer getDrawer(UserLayout.Side side) {
        return side == UserLayout.Side.LEFT ? left : right;
    }

    /** Closes overlays; true if any was open. */
    public boolean closeOverlays() {
        boolean closed = false;
        for (EdgeDrawer drawer : new EdgeDrawer[]{left, right}) {
            if (drawer != null && drawer.getState() == EdgeDrawer.State.OVERLAY) {
                drawer.close();
                closed = true;
            }
        }
        return closed;
    }

    /** Hides everything but the document (full screen), or shows it all again. */
    public void setChromeVisible(boolean visible) {
        for (Node n : getChildren()) {
            if (n != center) {
                n.setVisible(visible);
                n.setManaged(visible);
            }
        }
        requestLayout();
    }

    private void replace(Node old, Node next) {
        if (old != null) getChildren().remove(old);
        if (next != null) getChildren().add(0, next);
    }

    /** Overlays draw above everything else. */
    private void orderOverlays() {
        for (EdgeDrawer drawer : new EdgeDrawer[]{left, right}) {
            if (drawer != null && drawer.getState() == EdgeDrawer.State.OVERLAY) drawer.getContent().toFront();
        }
    }

    private static boolean isInside(Node node, MouseEvent e) {
        return node.isVisible() && node.localToScene(node.getBoundsInLocal()).contains(e.getSceneX(), e.getSceneY());
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double x = in.getLeft(), y = in.getTop();
        double w = getWidth() - in.getLeft() - in.getRight();
        double h = getHeight() - in.getTop() - in.getBottom();
        if (header != null && header.isManaged()) {
            double hh = header.prefHeight(w);
            header.resizeRelocate(x, y, w, hh);
            y += hh;
            h -= hh;
        }
        double leftX = x, rightX = x + w;
        // strips at the edges.
        if (left != null && left.getStrip() != null && left.getStrip().isManaged()) {
            double sw = left.getStrip().prefWidth(h);
            left.getStrip().resizeRelocate(leftX, y, sw, h);
            leftX += sw;
        }
        if (right != null && right.getStrip() != null && right.getStrip().isManaged()) {
            double sw = right.getStrip().prefWidth(h);
            rightX -= sw;
            right.getStrip().resizeRelocate(rightX, y, sw, h);
        }
        // docked content takes room; overlays sit on top of the document, collapsed ones hide.
        double centerLeft = leftX, centerRight = rightX;
        if (left != null) centerLeft = place(left, leftX, rightX, y, h, true, centerLeft);
        if (right != null) centerRight = place(right, leftX, rightX, y, h, false, centerRight);
        if (center != null) center.resizeRelocate(centerLeft, y, Math.max(0, centerRight - centerLeft), h);
    }

    /** Lays out a drawer's content; returns the document's new edge on that side. */
    private double place(EdgeDrawer drawer, double leftX, double rightX, double y, double h, boolean isLeft,
                         double edge) {
        Region content = drawer.getContent();
        if (!content.isManaged()) return edge;
        EdgeDrawer.State state = drawer.getState();
        double width = drawer.isFitContent() ? content.prefWidth(h)
                : Math.min(drawer.widthProperty().get(), Math.max(0, rightX - leftX) * 0.9);
        if (state == EdgeDrawer.State.COLLAPSED) {
            content.setVisible(false);
            return edge;
        }
        content.setVisible(true);
        double cx = isLeft ? leftX : rightX - width;
        content.resizeRelocate(cx, y, width, h);
        if (state == EdgeDrawer.State.DOCKED) return isLeft ? leftX + width : rightX - width;
        return edge;
    }
}
