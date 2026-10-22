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

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.icepdf.fx.ri.UserLayout;

/**
 * A panel at one edge of the viewer that opens three ways, like the vertical tab bars in Firefox
 * and Chrome: collapsed to its {@link #getStrip() strip}, open as an {@link State#OVERLAY overlay}
 * above the document (which neither moves nor re-renders), or {@link State#DOCKED docked} beside it.
 * <ul>
 *     <li>resting the pointer on the strip opens the overlay after {@link #OPEN_DELAY} (when
 *     {@link #hoverOpenProperty()} is on); leaving strip and panel closes it after {@link #CLOSE_DELAY};</li>
 *     <li>{@link #open(boolean) open(true)} - a click - keeps the overlay until {@link #close()}:
 *     Esc, a click on the document, or the same control again;</li>
 *     <li>{@link #pinnedProperty() pinned} docks it.</li>
 * </ul>
 * The content gets a drag handle on its inner edge for its width.  {@link ViewerShell} lays it out.
 */
public final class EdgeDrawer {

    /** How the content is showing. */
    public enum State { COLLAPSED, OVERLAY, DOCKED }

    public static final Duration OPEN_DELAY = Duration.millis(300);
    public static final Duration CLOSE_DELAY = Duration.millis(500);
    private static final double HANDLE = 5;

    private UserLayout.Side side;
    private final Node strip;
    private final StackPane content;
    private final Region handle = new Region();
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.COLLAPSED);
    private final BooleanProperty pinned = new SimpleBooleanProperty(this, "pinned");
    private final BooleanProperty hoverOpen = new SimpleBooleanProperty(this, "hoverOpen", true);
    private final DoubleProperty width = new SimpleDoubleProperty(this, "width", 260);
    private final DoubleProperty minWidth = new SimpleDoubleProperty(this, "minWidth", 180);
    private final PauseTransition openLater = new PauseTransition(OPEN_DELAY);
    private final PauseTransition closeLater = new PauseTransition(CLOSE_DELAY);
    private boolean sticky;
    private boolean pointerInside;

    /**
     * @param side    edge it sits on
     * @param strip   what shows when collapsed (icons, or a thin hot edge); may be null for none
     * @param content what opens
     */
    public EdgeDrawer(UserLayout.Side side, Node strip, Node content) {
        this.side = side;
        this.strip = strip;
        this.content = new StackPane(content, handle);
        this.content.getStyleClass().add("edge-drawer");
        handle.getStyleClass().add("edge-drawer-handle");
        handle.setCursor(Cursor.H_RESIZE);
        handle.setMaxWidth(HANDLE);
        handle.setMinWidth(HANDLE);
        setSide(side);
        installResize();

        openLater.setOnFinished(e -> {
            if (state.get() == State.COLLAPSED) state.set(State.OVERLAY);
        });
        closeLater.setOnFinished(e -> {
            if (!sticky && !pointerInside && state.get() == State.OVERLAY) state.set(State.COLLAPSED);
        });
        state.addListener((o, was, now) -> {
            content.getStyleClass().remove("overlay");
            if (now == State.OVERLAY) content.getStyleClass().add("overlay");
        });
        pinned.addListener((o, was, now) -> {
            sticky = false;
            state.set(now ? State.DOCKED : State.COLLAPSED);
        });
        if (strip != null) {
            strip.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> pointerEntered());
            strip.addEventHandler(MouseEvent.MOUSE_EXITED, e -> pointerLeft());
        }
        this.content.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> pointerEntered());
        this.content.addEventHandler(MouseEvent.MOUSE_EXITED, e -> pointerLeft());
    }

    public UserLayout.Side getSide() {
        return side;
    }

    /** Moves the drawer to the other edge (put it in the shell again afterwards). */
    public void setSide(UserLayout.Side side) {
        this.side = side;
        content.getStyleClass().removeAll("edge-drawer-left", "edge-drawer-right");
        content.getStyleClass().add(side == UserLayout.Side.LEFT ? "edge-drawer-left" : "edge-drawer-right");
        StackPane.setAlignment(handle, side == UserLayout.Side.LEFT
                ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
    }

    /** Shown at the edge while collapsed (and beside the open content); may be null. */
    public Node getStrip() {
        return strip;
    }

    /** The content, with its resize handle. */
    public Region getContent() {
        return content;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public State getState() {
        return state.get();
    }

    public boolean isOpen() {
        return state.get() != State.COLLAPSED;
    }

    /** Docked beside the document. */
    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    /** Whether resting on the strip opens the overlay. */
    public BooleanProperty hoverOpenProperty() {
        return hoverOpen;
    }

    /** Content width, open or docked. */
    public DoubleProperty widthProperty() {
        return width;
    }

    public DoubleProperty minWidthProperty() {
        return minWidth;
    }

    private boolean fitContent;

    /** Sized to its content's preferred width (a tool rail) instead of {@link #widthProperty()}. */
    public void setFitContent(boolean fit) {
        fitContent = fit;
    }

    public boolean isFitContent() {
        return fitContent;
    }

    /** Whether the content has a drag handle for its width (true by default). */
    public void setResizable(boolean resizable) {
        handle.setVisible(resizable);
        handle.setManaged(resizable);
    }

    /**
     * Opens the content: docked if pinned, else as an overlay - kept open when {@code sticky} (a
     * click), else closing again when the pointer leaves.
     */
    public void open(boolean sticky) {
        openLater.stop();
        closeLater.stop();
        if (pinned.get()) {
            state.set(State.DOCKED);
            return;
        }
        this.sticky = this.sticky || sticky;
        state.set(State.OVERLAY);
    }

    /** Closes an overlay (a docked panel stays; unpin it to close it). */
    public void close() {
        openLater.stop();
        closeLater.stop();
        sticky = false;
        if (state.get() == State.OVERLAY) state.set(State.COLLAPSED);
    }

    /** Opens if closed, closes an overlay if open. */
    public void toggle() {
        if (state.get() == State.OVERLAY) close();
        else open(true);
    }

    private void pointerEntered() {
        pointerInside = true;
        closeLater.stop();
        if (state.get() == State.COLLAPSED && hoverOpen.get()) openLater.playFromStart();
    }

    private void pointerLeft() {
        pointerInside = false;
        openLater.stop();
        if (state.get() == State.OVERLAY && !sticky) closeLater.playFromStart();
    }

    private void installResize() {
        double[] start = new double[2];
        handle.setOnMousePressed(e -> {
            start[0] = e.getScreenX();
            start[1] = width.get();
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            double delta = e.getScreenX() - start[0];
            double next = start[1] + (side == UserLayout.Side.LEFT ? delta : -delta);
            Region parent = content.getParent() instanceof Region r ? r : null;
            double max = parent != null && parent.getWidth() > 0 ? parent.getWidth() * 0.6 : 800;
            width.set(Math.max(minWidth.get(), Math.min(max, next)));
            e.consume();
        });
    }
}
