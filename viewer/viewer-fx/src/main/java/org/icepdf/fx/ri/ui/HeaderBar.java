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

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.view.PdfView;

import java.util.function.Consumer;

/**
 * The header bar, GNOME style: the open command, the document's name in the middle, page and zoom
 * controls, a search button that reveals a search bar underneath, and one menu for the rest.
 * Everything it shows comes from the action registry, so what a product leaves out isn't here.
 */
public final class HeaderBar extends VBox {

    private final ActionControls actions;
    private final Label title = new Label();
    private final HBox searchBar;
    private final TextField searchField = new TextField();
    private final ToggleButton searchToggle;

    /**
     * @param search runs a search for the typed text (the viewer's search panel, so results list there)
     * @param menuIds the primary menu's actions; null for a separator
     */
    public HeaderBar(ActionControls actions, PdfView view, Consumer<String> search, String... menuIds) {
        this.actions = actions;
        getStyleClass().add("header-bar-box");
        HBox bar = new HBox();
        bar.getStyleClass().add("header-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        title.getStyleClass().add("header-title");
        title.setMinWidth(0);
        Region left = new Region(), right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        add(bar, "document.open");
        add(bar, "document.save");
        bar.getChildren().add(left);
        bar.getChildren().add(title);
        bar.getChildren().add(right);
        if (actions.isAvailable("navigation.previous")) {
            bar.getChildren().addAll(actions.button("navigation.previous"), new PageField(view), actions.button("navigation.next"));
        }
        if (actions.isAvailable("view.zoom-in")) {
            bar.getChildren().addAll(actions.button("view.zoom-out"), new ZoomField(view), actions.button("view.zoom-in"));
        }
        searchToggle = new ToggleButton();
        Node searchIcon = actions.icon("search.find", 20);
        if (searchIcon != null) searchToggle.setGraphic(searchIcon);
        else searchToggle.setText("Find");
        searchToggle.getStyleClass().add("icon-button");
        searchToggle.setTooltip(new Tooltip("Search"));
        searchToggle.setId("ui.search");
        searchToggle.disableProperty().bind(view.documentProperty().isNull());
        if (actions.isAvailable("search.find")) bar.getChildren().add(searchToggle);

        MenuButton menu = new MenuButton();
        Node menuIcon = actions.icon("ui.menu", 20);
        if (menuIcon != null) menu.setGraphic(menuIcon);
        else menu.setText("☰");
        menu.getStyleClass().add("icon-button");
        menu.setTooltip(new Tooltip("Menu"));
        menu.setId("ui.menu");
        menu.getItems().setAll(actions.items(menuIds));
        if (!menu.getItems().isEmpty()) bar.getChildren().add(menu);

        // the search bar slides in under the header.
        searchField.setPromptText("Search the document");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(420);
        searchField.setOnAction(e -> search.accept(searchField.getText()));
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                searchToggle.setSelected(false);
                view.requestFocus();
            }
        });
        searchBar = new HBox(searchField);
        searchBar.getStyleClass().add("search-bar");
        searchBar.setAlignment(Pos.CENTER);
        for (String id : new String[]{"search.previous", "search.next", "search.panel"}) {
            if (actions.isAvailable(id)) searchBar.getChildren().add(actions.button(id));
        }
        searchBar.visibleProperty().bind(searchToggle.selectedProperty());
        searchBar.managedProperty().bind(searchToggle.selectedProperty());
        searchToggle.selectedProperty().addListener((o, was, now) -> {
            if (now) {
                searchField.requestFocus();
                searchField.selectAll();
            }
        });
        getChildren().addAll(bar, searchBar);
    }

    /** The title in the middle (the file name). */
    public void setTitle(String text) {
        title.setText(text);
    }

    /** Shows the search bar with the caret in it. */
    public void showSearch() {
        searchToggle.setSelected(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void add(HBox bar, String id) {
        if (actions.isAvailable(id)) bar.getChildren().add(actions.button(id));
    }
}
