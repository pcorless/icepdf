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

import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Rectangle;
import org.icepdf.fx.ri.document.DocumentPane;
import org.icepdf.fx.ri.icons.IconProvider;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The document tabs, for the header bar: one tab per document with its name (a bullet while it has
 * unsaved changes) and a close button, a "+" for a new one, and a menu listing every document when
 * they no longer fit.  The tabs share the width, between {@link #MIN_TAB} and {@link #MAX_TAB}; past
 * that the row scrolls to keep the selected one in view.
 */
public final class TabStrip extends HBox {

    public static final double MIN_TAB = 96, MAX_TAB = 220;

    private final ObservableList<DocumentPane> documents;
    private final ObservableValue<DocumentPane> current;
    private final Consumer<DocumentPane> select;
    private final Consumer<DocumentPane> close;
    private final IconProvider icons;
    private final Map<DocumentPane, TabNode> tabs = new IdentityHashMap<>();
    private final TabRow row = new TabRow();
    private final MenuButton list = new MenuButton();

    public TabStrip(ObservableList<DocumentPane> documents, ObservableValue<DocumentPane> current,
                    Consumer<DocumentPane> select, Consumer<DocumentPane> close, Runnable add, IconProvider icons) {
        super(2);
        this.documents = documents;
        this.current = current;
        this.select = select;
        this.close = close;
        this.icons = icons;
        getStyleClass().add("tab-strip");
        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row, Priority.ALWAYS);

        Button plus = iconButton("ui.add", "New tab", "+");
        plus.setId("ui.add");
        plus.setOnAction(e -> add.run());
        list.getStyleClass().add("icon-button");
        Node arrow = icons.icon("ui.tabs", 16);
        if (arrow != null) list.setGraphic(arrow);
        else list.setText("\u25be");
        list.setTooltip(new Tooltip("All documents"));
        list.setId("ui.tabs");
        list.setOnShowing(e -> {
            list.getItems().clear();
            for (DocumentPane pane : documents) {
                CheckMenuItem item = new CheckMenuItem(label(pane));
                item.setSelected(pane == current.getValue());
                item.setOnAction(a -> select.accept(pane));
                list.getItems().add(item);
            }
        });
        list.setVisible(false);
        list.setManaged(false);
        getChildren().addAll(row, list, plus);

        documents.addListener((ListChangeListener<DocumentPane>) c -> rebuild());
        current.addListener((o, was, now) -> {
            tabs.forEach((pane, tab) -> tab.sync());
            row.requestLayout();
        });
        rebuild();
    }

    /** The tab for a document (for tests and drag handling). */
    public Node tabFor(DocumentPane pane) {
        return tabs.get(pane);
    }

    private static String label(DocumentPane pane) {
        return pane.getTitle().isEmpty() ? "New Tab" : pane.getTitle();
    }

    private void rebuild() {
        tabs.keySet().removeIf(p -> !documents.contains(p));
        for (DocumentPane pane : documents) tabs.computeIfAbsent(pane, TabNode::new);
        row.getChildren().setAll(documents.stream().map(tabs::get).toList());
        tabs.values().forEach(TabNode::sync);
        row.requestLayout();
    }

    private Button iconButton(String icon, String tip, String fallback) {
        Button button = new Button();
        Node graphic = icons.icon(icon, 16);
        if (graphic != null) button.setGraphic(graphic);
        else button.setText(fallback);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tip));
        return button;
    }

    /** One tab: the name, and a close button. */
    private final class TabNode extends HBox {
        final DocumentPane pane;
        final Label name = new Label();
        final Tooltip tip = new Tooltip();

        TabNode(DocumentPane pane) {
            super(4);
            this.pane = pane;
            getStyleClass().add("doc-tab");
            setAlignment(Pos.CENTER_LEFT);
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Button x = iconButton("ui.close", "Close", "\u00d7");
            x.getStyleClass().add("tab-close");
            x.setOnAction(e -> {
                close.accept(pane);
                e.consume();
            });
            getChildren().addAll(name, x);
            Tooltip.install(this, tip);
            pane.titleProperty().addListener((o, a, b) -> sync());
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) select.accept(pane);
                else if (e.getButton() == MouseButton.MIDDLE) close.accept(pane);
            });
            ContextMenu menu = new ContextMenu();
            MenuItem closeThis = new MenuItem("Close");
            closeThis.setOnAction(e -> close.accept(pane));
            MenuItem closeOthers = new MenuItem("Close Other Tabs");
            closeOthers.setOnAction(e -> {
                for (DocumentPane other : documents.toArray(new DocumentPane[0])) if (other != pane) close.accept(other);
            });
            menu.getItems().addAll(closeThis, closeOthers);
            setOnContextMenuRequested(e -> menu.show(this, e.getScreenX(), e.getScreenY()));
        }

        void sync() {
            name.setText(label(pane));
            tip.setText(pane.getSession().getFile() != null ? pane.getSession().getFile().toString() : "No document");
            getStyleClass().remove("selected");
            if (pane == current.getValue()) getStyleClass().add("selected");
        }
    }

    /** Lays the tabs out side by side, clipped, scrolled so the selected one shows. */
    private final class TabRow extends Pane {
        private final Rectangle clip = new Rectangle();

        TabRow() {
            setClip(clip);
            setMinWidth(MIN_TAB);
        }

        @Override
        protected double computePrefWidth(double height) {
            return documents.size() * MAX_TAB;
        }

        @Override
        protected void layoutChildren() {
            int n = getChildren().size();
            double w = getWidth(), h = getHeight();
            clip.setWidth(w);
            clip.setHeight(h);
            if (n == 0) return;
            double each = Math.max(MIN_TAB, Math.min(MAX_TAB, w / n));
            double x = 0, selectedX = 0;
            for (Node node : getChildren()) {
                node.resizeRelocate(x, 0, each, h);
                if (node instanceof TabNode tab && tab.pane == current.getValue()) selectedX = x;
                x += each;
            }
            boolean overflow = x > w + 0.5;
            double shift = 0;
            if (overflow) shift = Math.max(0, Math.min(x - w, selectedX + each - w));
            for (Node node : getChildren()) node.setTranslateX(-shift);
            list.setVisible(overflow);
            list.setManaged(overflow);
        }
    }
}
