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
import javafx.beans.value.ObservableValue;
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
    private HBox bar;
    private Region leftSpace, rightSpace;
    private final HBox searchBar;
    private final TextField searchField = new TextField();
    private final ToggleButton searchToggle;

    private final MenuButton menu;
    private PageField pageField;

    /**
     * @param search runs a search for the typed text (the viewer's search panel, so results list there)
     * @param menuIds the primary menu's actions; null for a separator
     */
    public HeaderBar(ActionControls actions, ObservableValue<PdfView> view, Consumer<String> search, String... menuIds) {
        this.actions = actions;
        getStyleClass().add("header-bar-box");
        bar = new HBox();
        bar.getStyleClass().add("header-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        title.getStyleClass().add("header-title");
        title.setMinWidth(0);
        Region left = new Region(), right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        leftSpace = left;
        rightSpace = right;

        add(bar, "document.open");
        add(bar, "document.save");
        bar.getChildren().add(left);
        bar.getChildren().add(title);
        bar.getChildren().add(right);
        if (actions.isAvailable("navigation.previous")) {
            pageField = new PageField(view);
            bar.getChildren().addAll(actions.button("navigation.previous"), pageField, actions.button("navigation.next"));
        }
        if (actions.isAvailable("view.zoom-in")) {
            bar.getChildren().addAll(actions.button("view.zoom-out"), new ZoomField(view), actions.button("view.zoom-in"));
        }
        searchToggle = new ToggleButton();
        Node searchIcon = actions.icon("search.find", 20);
        if (searchIcon != null) searchToggle.setGraphic(searchIcon);
        else searchToggle.setText(Messages.get("headerBar.find"));
        searchToggle.getStyleClass().add("icon-button");
        searchToggle.setTooltip(new Tooltip(Messages.get("headerBar.search")));
        searchToggle.setId("ui.search");
        Runnable followDocument = () -> {
            searchToggle.disableProperty().unbind();
            PdfView now = view.getValue();
            if (now != null) searchToggle.disableProperty().bind(now.documentProperty().isNull());
            else searchToggle.setDisable(true);
        };
        view.addListener((o, was, now) -> followDocument.run());
        followDocument.run();
        if (actions.isAvailable("search.find")) bar.getChildren().add(searchToggle);

        menu = new MenuButton();
        Node menuIcon = actions.icon("ui.menu", 20);
        if (menuIcon != null) menu.setGraphic(menuIcon);
        else menu.setText("☰");
        menu.getStyleClass().add("icon-button");
        menu.setTooltip(new Tooltip(Messages.get("headerBar.menu")));
        menu.setId("ui.menu");
        menu.getItems().setAll(actions.items(menuIds));
        if (!menu.getItems().isEmpty()) bar.getChildren().add(menu);

        // the search bar slides in under the header.
        searchField.setPromptText(Messages.get("headerBar.searchPrompt"));
        Accessibility.name(searchField, Messages.get("a11y.find"));
        followSearchResults(view);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(420);
        searchField.setOnAction(e -> search.accept(searchField.getText()));
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                searchToggle.setSelected(false);
                if (view.getValue() != null) view.getValue().requestFocus();
                e.consume();
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

    /**
     * Keeps "match 3 of 12" in the search field's accessible help, where a screen reader finds it
     * (JavaFX can't announce it).  Follows whichever view {@code source} holds.
     */
    private void followSearchResults(ObservableValue<PdfView> source) {
        PdfView[] following = new PdfView[1];
        javafx.beans.InvalidationListener update = o -> {
            PdfView v = following[0];
            int hits = v != null ? v.getSearchHits().size() : 0;
            int current = v != null ? v.getCurrentSearchHitIndex() : -1;
            searchField.setAccessibleHelp(v == null || v.getSearchHits().isEmpty() && !v.isSearching() ? null
                    : current >= 0 ? Messages.format("a11y.searchMatch", current + 1, hits)
                    : Messages.format("searchPanel.matches", hits));
        };
        javafx.beans.value.ChangeListener<PdfView> follow = (o, was, now) -> {
            if (was != null) {
                was.getSearchHits().removeListener(update);
                was.currentSearchHitIndexProperty().removeListener(update);
                was.searchingProperty().removeListener(update);
            }
            following[0] = now;
            if (now != null) {
                now.getSearchHits().addListener(update);
                now.currentSearchHitIndexProperty().addListener(update);
                now.searchingProperty().addListener(update);
            }
            update.invalidated(null);
        };
        source.addListener(follow);
        follow.changed(source, null, source.getValue());
    }

    /** Shows these tabs in the middle instead of the title. */
    public void setTabs(Node tabs) {
        int at = bar.getChildren().indexOf(leftSpace);
        bar.getChildren().removeAll(leftSpace, title, rightSpace);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        bar.getChildren().add(at, tabs);
    }

    /** The title in the middle (the file name). */
    public void setTitle(String text) {
        title.setText(text);
    }

    /** Shows the search bar with the caret in it. */
    /** Whether the header has the page number field. */
    public boolean hasPageField() {
        return pageField != null;
    }

    /** Puts the caret in the page number field with its text selected (Go to page). */
    public void focusPageNumber() {
        if (pageField != null) pageField.focusField();
    }

    /** Whether the search bar is showing. */
    public ObservableBooleanValue searchBarShown() {
        return searchToggle.selectedProperty();
    }

    public void showSearch() {
        searchToggle.setSelected(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void add(HBox bar, String id) {
        if (actions.isAvailable(id)) bar.getChildren().add(actions.button(id));
    }

    /** The primary menu, for an application to add its own items (Open Recent). */
    public MenuButton getMenu() {
        return menu;
    }
}
