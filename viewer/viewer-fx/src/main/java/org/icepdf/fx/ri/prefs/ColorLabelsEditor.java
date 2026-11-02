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
package org.icepdf.fx.ri.prefs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.ColorLabels;

import java.util.List;

/**
 * Edits the colour labels: a list in the user's order, the selected label's name and colour
 * beside it, and add, remove, move up and down.  Nothing is stored until {@link #labels()} is read
 * and saved by the dialog.
 */
final class ColorLabelsEditor extends VBox {

    /** Colours a new label starts with, in turn, so consecutive labels differ. */
    private static final java.awt.Color[] STARTERS = {
            new java.awt.Color(255, 255, 0), new java.awt.Color(0, 176, 80), new java.awt.Color(0, 112, 192),
            new java.awt.Color(255, 0, 0), new java.awt.Color(255, 192, 0), new java.awt.Color(112, 48, 160)};

    private final ObservableList<ColorLabels.Label> items = FXCollections.observableArrayList();
    private final ListView<ColorLabels.Label> list = new ListView<>(items);
    private final TextField name = new TextField();
    private final ColorPicker colour = new ColorPicker();
    private final Button remove = new Button(Messages.get("preferences.labels.remove"));
    private final Button up = new Button(Messages.get("preferences.labels.up"));
    private final Button down = new Button(Messages.get("preferences.labels.down"));
    private boolean loading;

    ColorLabelsEditor(List<ColorLabels.Label> labels) {
        super(6);
        items.setAll(labels);
        list.setCellFactory(l -> new LabelCell());
        list.setPrefHeight(150);
        list.setPlaceholder(new Label(Messages.get("preferences.labels.none")));

        name.setPromptText(Messages.get("preferences.labels.name"));
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox fields = new HBox(6, name, colour);
        fields.setAlignment(Pos.CENTER_LEFT);

        Button add = new Button(Messages.get("preferences.labels.add"));
        add.setOnAction(e -> {
            items.add(new ColorLabels.Label(STARTERS[items.size() % STARTERS.length],
                    Messages.get("preferences.labels.newName")));
            list.getSelectionModel().selectLast();
            name.requestFocus();
            name.selectAll();
        });
        remove.setOnAction(e -> {
            int i = list.getSelectionModel().getSelectedIndex();
            if (i >= 0) items.remove(i);
        });
        up.setOnAction(e -> move(-1));
        down.setOnAction(e -> move(1));
        HBox buttons = new HBox(6, add, remove, up, down);

        // the fields edit the selected label as they change.
        list.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (!loading) load();
        });
        name.textProperty().addListener((o, a, b) -> store());
        colour.valueProperty().addListener((o, a, b) -> store());

        Label help = new Label(Messages.get("preferences.labels.help"));
        help.setWrapText(true);
        help.getStyleClass().add("dim-label");
        getChildren().addAll(list, fields, buttons, help);
        setPadding(new Insets(4, 0, 0, 0));
        load();
    }

    /** The labels as edited, in order. */
    List<ColorLabels.Label> labels() {
        return List.copyOf(items);
    }

    private void move(int by) {
        int i = list.getSelectionModel().getSelectedIndex();
        int j = i + by;
        if (i < 0 || j < 0 || j >= items.size()) return;
        ColorLabels.Label label = items.remove(i);
        items.add(j, label);
        list.getSelectionModel().select(j);
    }

    private void load() {
        int i = list.getSelectionModel().getSelectedIndex();
        boolean has = i >= 0;
        loading = true;
        try {
            name.setText(has ? items.get(i).name() : "");
            colour.setValue(has ? fx(items.get(i).color()) : Color.WHITE);
        } finally {
            loading = false;
        }
        name.setDisable(!has);
        colour.setDisable(!has);
        remove.setDisable(!has);
        up.setDisable(!has || i == 0);
        down.setDisable(!has || i == items.size() - 1);
    }

    private void store() {
        int i = list.getSelectionModel().getSelectedIndex();
        if (loading || i < 0 || colour.getValue() == null) return;
        Color c = colour.getValue();
        // replacing the item drops the selection for a moment; don't reload the fields being typed in.
        loading = true;
        try {
            items.set(i, new ColorLabels.Label(new java.awt.Color((float) c.getRed(), (float) c.getGreen(),
                    (float) c.getBlue()), name.getText()));
            list.getSelectionModel().select(i);
        } finally {
            loading = false;
        }
    }

    private static Color fx(java.awt.Color c) {
        return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static final class LabelCell extends ListCell<ColorLabels.Label> {
        @Override
        protected void updateItem(ColorLabels.Label label, boolean empty) {
            super.updateItem(label, empty);
            if (empty || label == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Rectangle swatch = new Rectangle(14, 14, fx(label.color()));
            swatch.setStroke(Color.gray(0, 0.35));
            setGraphic(swatch);
            setText(label.name().isEmpty() ? String.format("#%06X", label.color().getRGB() & 0xFFFFFF) : label.name());
        }
    }
}
