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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.icepdf.fx.ri.ToolArrangement;
import org.icepdf.fx.ri.ToolArrangement.Zone;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;

import java.util.ArrayList;
import java.util.List;

/**
 * Arranges the tools: three lists - on the rail, in its "more" menu, hidden - that tools drag
 * between and within (the buttons do the same from the keyboard), and the rail's and the panel's
 * options.  Works on a copy; {@link #apply()} writes it to the {@link UserLayout} in one change.
 * The pane knows nothing of any one viewer: every viewer sharing the layout updates.
 */
public final class CustomiseToolsPane extends VBox {

    private final ActionControls actions;
    private final ViewerFeatures features;
    private final UserLayout layout;
    private final List<String> available = new ArrayList<>();
    private ToolArrangement model;
    private final java.util.Map<Zone, ListView<String>> lists = new java.util.EnumMap<>(Zone.class);
    private final ComboBox<UserLayout.Side> railSide = new ComboBox<>();
    private final CheckBox autoShow = new CheckBox("Show the rail only when the pointer reaches its edge");
    private final CheckBox hoverOpen = new CheckBox("Open the side panel when the pointer rests on its icons");
    private String dragging;

    public CustomiseToolsPane(ActionControls actions, ViewerFeatures features, UserLayout layout) {
        this.actions = actions;
        this.features = features;
        this.layout = layout;
        actions.registry().all().forEach(a -> {
            if (actions.isAvailable(a.id())) available.add(a.id());
        });
        getStyleClass().add("customise-tools");
        setSpacing(12);
        setPadding(new Insets(12));

        HBox columns = new HBox(10, column(Zone.RAIL, "On the rail"), buttons(),
                new VBox(10, column(Zone.MORE, "In the More menu"), column(Zone.HIDDEN, "Hidden")));
        HBox.setHgrow(columns.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(columns.getChildren().get(2), Priority.ALWAYS);

        railSide.getItems().setAll(UserLayout.Side.values());
        railSide.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(UserLayout.Side side) {
                return side == UserLayout.Side.LEFT ? "Left" : "Right";
            }

            @Override
            public UserLayout.Side fromString(String s) {
                return null;
            }
        });
        Button reset = new Button("Reset to Defaults");
        reset.setOnAction(e -> {
            model = ToolArrangement.defaults(features, available);
            railSide.setValue(UserLayout.Side.LEFT);
            autoShow.setSelected(false);
            hoverOpen.setSelected(true);
            refresh();
        });
        Label hint = new Label("Drag tools between the lists, or select one and use the buttons.");
        hint.getStyleClass().add("hint");
        getChildren().addAll(hint, columns,
                new HBox(8, new Label("Tool rail on the"), railSide, new Label("side")), autoShow, hoverOpen, reset);
        VBox.setVgrow(columns, Priority.ALWAYS);
        load();
    }

    /** Starts over from what the layout has now. */
    public void load() {
        model = ToolArrangement.of(features, layout, available);
        railSide.setValue(layout.getRailSide());
        autoShow.setSelected(layout.isRailAutoShow());
        hoverOpen.setSelected(layout.isPanelHoverOpen());
        refresh();
    }

    /** The arrangement being edited (for tests). */
    public ToolArrangement model() {
        return model;
    }

    public ListView<String> list(Zone zone) {
        return lists.get(zone);
    }

    /** Writes the arrangement and options to the layout, which tells every viewer once. */
    public void apply() {
        layout.update(() -> {
            model.apply(layout, features, available);
            layout.setRailSide(railSide.getValue());
            layout.setRailAutoShow(autoShow.isSelected());
            layout.setPanelHoverOpen(hoverOpen.isSelected());
        });
    }

    /**
     * Shows the pane in a dialog over {@code owner}'s window; OK applies it.  {@code styleFrom}
     * lends its stylesheets and theme classes so the dialog looks like the viewer.
     */
    public static void showDialog(Window owner, Node styleFrom, ActionControls actions, ViewerFeatures features,
                                  UserLayout layout) {
        CustomiseToolsPane pane = new CustomiseToolsPane(actions, features, layout);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Customise Tools");
        dialog.setResizable(true);
        if (owner != null) dialog.initOwner(owner);
        DialogPane dp = dialog.getDialogPane();
        dp.setContent(pane);
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (styleFrom != null && styleFrom.getScene() != null) {
            dp.getStylesheets().addAll(styleFrom.getScene().getRoot().getStylesheets());
        }
        if (styleFrom instanceof javafx.scene.Parent p) {
            dp.getStylesheets().addAll(p.getStylesheets());
            dp.getStyleClass().addAll(styleFrom.getStyleClass());
        }
        dp.setPrefSize(640, 460);
        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> pane.apply());
    }

    // ---- build ----------------------------------------------------------------------------------

    private VBox column(Zone zone, String title) {
        ListView<String> list = new ListView<>(FXCollections.observableArrayList());
        list.setCellFactory(v -> new ToolCell(zone));
        list.setPrefHeight(zone == Zone.RAIL ? 360 : 170);
        list.setPrefWidth(220);
        list.setId("tools." + zone.name().toLowerCase());
        list.setOnDragOver(e -> accept(e));
        list.setOnDragDropped(e -> dropped(e, zone, -1));
        lists.put(zone, list);
        VBox box = new VBox(4, new Label(title), list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    private Node buttons() {
        Button up = new Button("Up");
        Button down = new Button("Down");
        Button toRail = new Button("To Rail");
        Button toMore = new Button("To More");
        Button hide = new Button("Hide");
        up.setOnAction(e -> nudge(-1));
        down.setOnAction(e -> nudge(1));
        toRail.setOnAction(e -> send(Zone.RAIL));
        toMore.setOnAction(e -> send(Zone.MORE));
        hide.setOnAction(e -> send(Zone.HIDDEN));
        VBox box = new VBox(6, up, down, new Separator(), toRail, toMore, hide);
        box.setAlignment(Pos.CENTER);
        for (Node n : box.getChildren()) if (n instanceof Button b) b.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private String selected() {
        for (ListView<String> list : lists.values()) {
            String id = list.getSelectionModel().getSelectedItem();
            if (id != null && list.isFocused()) return id;
        }
        for (ListView<String> list : lists.values()) {
            String id = list.getSelectionModel().getSelectedItem();
            if (id != null) return id;
        }
        return null;
    }

    private void nudge(int by) {
        String id = selected();
        if (id == null) return;
        model.nudge(id, by);
        refresh();
        select(id);
    }

    private void send(Zone zone) {
        String id = selected();
        if (id == null) return;
        model.move(id, zone, -1);
        refresh();
        select(id);
    }

    private void select(String id) {
        Zone zone = model.zoneOf(id);
        if (zone == null) return;
        lists.values().forEach(l -> l.getSelectionModel().clearSelection());
        lists.get(zone).getSelectionModel().select(id);
    }

    private void refresh() {
        for (Zone zone : Zone.values()) lists.get(zone).getItems().setAll(model.get(zone));
    }

    private static void accept(DragEvent e) {
        if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
        e.consume();
    }

    private void dropped(DragEvent e, Zone zone, int index) {
        if (dragging != null && model.zoneOf(dragging) != null) {
            String id = dragging;
            model.move(id, zone, index);
            refresh();
            select(id);
            e.setDropCompleted(true);
        }
        dragging = null;
        e.consume();
    }

    /** A tool in a list: its icon and name; dragging it moves it. */
    private final class ToolCell extends ListCell<String> {
        ToolCell(Zone zone) {
            setOnDragDetected(e -> {
                if (getItem() == null) return;
                dragging = getItem();
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(getItem());
                db.setContent(content);
                e.consume();
            });
            setOnDragOver(CustomiseToolsPane::accept);
            setOnDragDropped(e -> dropped(e, zone, getItem() != null ? getIndex() : -1));
        }

        @Override
        protected void updateItem(String id, boolean empty) {
            super.updateItem(id, empty);
            if (empty || id == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(actions.registry().get(id).label());
            setGraphic(actions.icon(id, 18));
        }
    }
}
