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

    private record Entry(SidePanel panel, Node node, ObservableBooleanValue available, ToggleButton button) {
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
    private SidePanel shown;
    private SidePanel hovered;

    public UtilityPanel(UserLayout.Side side, IconProvider icons) {
        this.icons = icons;
        strip.getStyleClass().add("utility-strip");
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        pin.setGraphic(icon("ui.pin", 20));
        pin.getStyleClass().add("icon-button");
        pin.setTooltip(new Tooltip("Keep the panel open beside the document"));
        strip.getChildren().addAll(buttons, spacer, pin);

        title.getStyleClass().add("panel-title");
        Button close = new Button();
        close.setGraphic(icon("ui.close", 16));
        close.getStyleClass().add("icon-button");
        close.setTooltip(new Tooltip("Close"));
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
            if (now == EdgeDrawer.State.OVERLAY && hovered != null && shown != hovered && !isClickOpened()) show(hovered);
            if (now != EdgeDrawer.State.COLLAPSED && shown == null) showFirstAvailable();
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
        Entry entry = new Entry(panel, node, available, button);
        entries.put(panel, entry);
        available.addListener((o, was, now) -> refreshStrip());
        refreshStrip();
    }

    public EdgeDrawer getDrawer() {
        return drawer;
    }

    /** The panel showing (or shown last), or null. */
    public SidePanel getShown() {
        return shown;
    }

    /** Opens at a panel and keeps it open (as a click would). */
    public void open(SidePanel panel) {
        Entry entry = entries.get(panel);
        if (entry == null) return;
        show(panel);
        clickOpened = true;
        drawer.open(true);
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
        if (open && shown == panel && drawer.getState() == EdgeDrawer.State.OVERLAY && clickOpened) {
            clickOpened = false;
            drawer.close();
            return;
        }
        show(panel);
        clickOpened = true;
        drawer.open(true);
    }

    private void show(SidePanel panel) {
        Entry entry = entries.get(panel);
        if (entry == null) return;
        shown = panel;
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
        if (shown != null && !entries.get(shown).available().get()) {
            shown = null;
            showFirstAvailable();
        }
    }

    private void syncButtons() {
        if (!drawer.isOpen()) {
            clickOpened = false;
            group.selectToggle(null);
        } else if (shown != null) {
            group.selectToggle(entries.get(shown).button());
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
