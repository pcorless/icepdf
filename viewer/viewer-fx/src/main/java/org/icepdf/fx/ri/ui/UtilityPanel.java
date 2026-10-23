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

import org.icepdf.fx.i18n.Messages;

import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.icepdf.fx.ri.SidePanel;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.icons.IconProvider;

import java.util.EnumMap;
import java.util.Map;

/**
 * The utility panel: an icon strip (one icon per panel the document has content for) and the
 * panel itself in an {@link EdgeDrawer} - hover the strip to look, click an icon to keep it open,
 * pin to dock it beside the document.
 */
public final class UtilityPanel {

    private record Entry(SidePanel panel, Node node, ObservableBooleanValue available, ToggleButton button,
                         javafx.beans.InvalidationListener listener) {
    }

    private final IconProvider icons;
    private final Map<SidePanel, Entry> entries = new EnumMap<>(SidePanel.class);
    private final VBox strip = new VBox();
    private final VBox buttons = new VBox(2);
    private final ToggleGroup group = new ToggleGroup();
    private final Label title = new Label();
    private final StackPane body = new StackPane();
    private final ToggleButton pin = new ToggleButton();
    private final EdgeDrawer drawer;
    private final javafx.beans.property.ReadOnlyObjectWrapper<SidePanel> shownPanel =
            new javafx.beans.property.ReadOnlyObjectWrapper<>(this, "shown");
    private SidePanel hovered;

    public UtilityPanel(UserLayout.Side side, IconProvider icons) {
        this.icons = icons;
        strip.getStyleClass().add("utility-strip");
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        pin.setGraphic(icon("ui.pin", 20));
        pin.getStyleClass().add("icon-button");
        pin.setTooltip(new Tooltip(Messages.get("utilityPanel.pin")));
        strip.getChildren().addAll(buttons, spacer, pin);

        title.getStyleClass().add("panel-title");
        Button close = new Button();
        close.setGraphic(icon("ui.close", 16));
        close.getStyleClass().add("icon-button");
        close.setTooltip(new Tooltip(Messages.get("utilityPanel.close")));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(title, grow, close);
        header.getStyleClass().add("panel-header");
        BorderPane content = new BorderPane(body);
        content.setTop(header);
        content.getStyleClass().add("utility-panel");

        drawer = new EdgeDrawer(side, strip, content);
        pin.selectedProperty().bindBidirectional(drawer.pinnedProperty());
        close.setOnAction(e -> {
            drawer.pinnedProperty().set(false);
            drawer.close();
        });
        drawer.stateProperty().addListener((o, was, now) -> {
            if (now == EdgeDrawer.State.OVERLAY && hovered != null && shownPanel.get() != hovered && !isClickOpened()) show(hovered);
            if (now != EdgeDrawer.State.COLLAPSED && shownPanel.get() == null) showFirstAvailable();
            // closing with the caret in a panel: hand it back, or keys go to a field nobody sees.
            if (now == EdgeDrawer.State.COLLAPSED && focusInside()) onClosedWithFocus.run();
            syncButtons();
        });
    }

    /** Adds a panel; its icon shows only while {@code available} (the document has content for it). */
    public void addPanel(SidePanel panel, String label, Node node, ObservableBooleanValue available) {
        ToggleButton button = new ToggleButton();
        Node graphic = icon("panel." + panel.id(), 20);
        if (graphic != null) button.setGraphic(graphic);
        else button.setText(label.substring(0, 1));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(label));
        button.setId("panel." + panel.id());
        button.setUserData(label);
        button.setToggleGroup(group);
        button.setOnAction(e -> clicked(panel));
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            hovered = panel;
            // looking (hover overlay), not clicked: the hovered icon's panel shows.
            if (drawer.getState() == EdgeDrawer.State.OVERLAY && !isClickOpened()) show(panel);
        });
        javafx.beans.InvalidationListener listener = o -> refreshStrip();
        Entry entry = new Entry(panel, node, available, button, listener);
        entries.put(panel, entry);
        available.addListener(listener);
        refreshStrip();
    }

    /**
     * Swaps what a panel shows and when it is offered - another document's pages, bookmarks and so
     * on - keeping its icon, and the panel open if it is showing.
     */
    public void retarget(SidePanel panel, Node node, ObservableBooleanValue available) {
        Entry old = entries.get(panel);
        if (old == null) return;
        old.available().removeListener(old.listener());
        javafx.beans.InvalidationListener listener = o -> refreshStrip();
        entries.put(panel, new Entry(panel, node, available, old.button(), listener));
        available.addListener(listener);
        if (shownPanel.get() == panel) show(panel);
        refreshStrip();
    }

    public EdgeDrawer getDrawer() {
        return drawer;
    }

    /** The panel showing (or shown last), or null. */
    public SidePanel getShown() {
        return shownPanel.get();
    }

    /** {@link #getShown()}, to follow. */
    public javafx.beans.property.ReadOnlyObjectProperty<SidePanel> shownProperty() {
        return shownPanel.getReadOnlyProperty();
    }

    /** Opens at a panel and keeps it open (as a click would). */
    public void open(SidePanel panel) {
        Entry entry = entries.get(panel);
        if (entry == null) return;
        show(panel);
        clickOpened = true;
        drawer.open(true);
        activated(panel);
    }

    /** Opens at the panel shown last (or the first there is), as {@link #open(SidePanel)}. */
    public void openShown() {
        if (shownPanel.get() == null) showFirstAvailable();
        if (shownPanel.get() != null) open(shownPanel.get());
        else drawer.open(true);
    }

    /**
     * Called with a panel opened on purpose - its icon clicked, or {@link #open} - once it shows, to
     * put the caret in it (the search panel's query).  Not for a hover peek: passing the pointer over
     * the strip mustn't take the keys from the page.
     */
    public void setOnActivated(java.util.function.Consumer<SidePanel> onActivated) {
        this.onActivated = onActivated;
    }

    /** Called when the panel closes with the caret inside it, to put the caret back (on the page). */
    public void setOnClosedWithFocus(Runnable onClosedWithFocus) {
        this.onClosedWithFocus = onClosedWithFocus;
    }

    private java.util.function.Consumer<SidePanel> onActivated = panel -> { };
    private Runnable onClosedWithFocus = () -> { };

    private void activated(SidePanel panel) {
        // the shell shows the content at its next layout; a hidden field can't take the caret.
        Runnable activate = () -> {
            if (drawer.isOpen() && shownPanel.get() == panel) onActivated.accept(panel);
        };
        Region content = drawer.getContent();
        if (content.isVisible()) {
            javafx.application.Platform.runLater(activate);
            return;
        }
        content.visibleProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean was, Boolean now) {
                if (!now) return;
                o.removeListener(this);
                javafx.application.Platform.runLater(activate);
            }
        });
    }

    private boolean focusInside() {
        javafx.scene.Scene scene = body.getScene();
        for (Node n = scene != null ? scene.getFocusOwner() : null; n != null; n = n.getParent()) {
            if (n == body) return true;
        }
        return false;
    }

    /** Whether a panel is in this viewer and has content now. */
    public boolean isAvailable(SidePanel panel) {
        Entry entry = entries.get(panel);
        return entry != null && entry.available().get();
    }

    private boolean clickOpened;

    private boolean isClickOpened() {
        return clickOpened;
    }

    private void clicked(SidePanel panel) {
        boolean open = drawer.isOpen();
        if (open && shownPanel.get() == panel && drawer.getState() == EdgeDrawer.State.OVERLAY && clickOpened) {
            clickOpened = false;
            drawer.close();
            return;
        }
        show(panel);
        clickOpened = true;
        drawer.open(true);
        activated(panel);
    }

    private void show(SidePanel panel) {
        Entry entry = entries.get(panel);
        if (entry == null) return;
        shownPanel.set(panel);
        title.setText((String) entry.button().getUserData());
        body.getChildren().setAll(entry.node());
        syncButtons();
    }

    private void showFirstAvailable() {
        for (Entry entry : entries.values()) {
            if (entry.available().get()) {
                show(entry.panel());
                return;
            }
        }
    }

    private void refreshStrip() {
        buttons.getChildren().clear();
        for (Entry entry : entries.values()) {
            if (entry.available().get()) buttons.getChildren().add(entry.button());
        }
        if (shownPanel.get() != null && !entries.get(shownPanel.get()).available().get()) {
            shownPanel.set(null);
            showFirstAvailable();
        }
    }

    private void syncButtons() {
        if (!drawer.isOpen()) {
            clickOpened = false;
            group.selectToggle(null);
        } else if (shownPanel.get() != null) {
            group.selectToggle(entries.get(shownPanel.get()).button());
        }
    }

    private Node icon(String id, double size) {
        return icons.icon(id, size);
    }

    /** Panels currently offered in the strip, in order (for tests). */
    public java.util.List<SidePanel> availablePanels() {
        java.util.List<SidePanel> list = new java.util.ArrayList<>();
        entries.values().forEach(e -> {
            if (e.available().get()) list.add(e.panel());
        });
        return list;
    }

    /** Pre-selects a panel without opening (the last one used). */
    public void select(SidePanel panel) {
        if (entries.containsKey(panel)) show(panel);
    }
}
