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
import java.util.function.BiConsumer;
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
    private static final javafx.scene.input.DataFormat TAB = new javafx.scene.input.DataFormat("application/x-icepdf-tab");
    private final javafx.scene.layout.Region indicator = new javafx.scene.layout.Region();
    private BiConsumer<DocumentPane, Integer> onMove = (pane, index) -> { };
    // the tab being dragged, in any window: one drag at a time, all on the FX thread.
    private static DocumentPane dragging;
    private static TabStrip dragSource;
    private java.util.function.Predicate<DocumentPane> detach = pane -> false;
    private BiConsumer<DocumentPane, Integer> onAdopt = (pane, index) -> { };
    private BiConsumer<DocumentPane, javafx.geometry.Point2D> onTearOff = (pane, at) -> { };
    private java.util.function.BooleanSupplier canTearOff = () -> false;
    private BiConsumer<DocumentPane, javafx.scene.input.Dragboard> onDropOnTab = (pane, board) -> { };
    private java.util.function.Predicate<javafx.scene.input.Dragboard> accepts = board -> false;

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

        Button plus = iconButton("ui.add", Messages.get("tabStrip.newTab"), "+");
        plus.setId("ui.add");
        plus.setOnAction(e -> add.run());
        list.getStyleClass().add("icon-button");
        Node arrow = icons.icon("ui.tabs", 16);
        if (arrow != null) list.setGraphic(arrow);
        else list.setText("\u25be");
        list.setTooltip(new Tooltip(Messages.get("tabStrip.allDocuments")));
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
        indicator.getStyleClass().add("tab-drop-indicator");
        indicator.setManaged(false);
        indicator.setVisible(false);
        indicator.setMouseTransparent(true);
        row.setOnDragOver(e -> {
            if (dragging == null || !e.getDragboard().hasContent(TAB)) return;
            e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            showIndicator(insertionIndex(e.getSceneX()));
            e.consume();
        });
        row.setOnDragExited(e -> indicator.setVisible(false));
        row.setOnDragDropped(e -> {
            if (dragging == null) return;
            DocumentPane moved = dragging;
            TabStrip from = dragSource;
            int index = insertionIndex(e.getSceneX());
            indicator.setVisible(false);
            if (from == TabStrip.this) {
                onMove.accept(moved, index);
            } else if (from != null && from.detach.test(moved)) {
                // from another window: it leaves there and joins here.
                dragging = null;
                dragSource = null;
                onAdopt.accept(moved, index);
            }
            e.setDropCompleted(true);
            e.consume();
        });
        getChildren().addAll(row, list, plus);

        documents.addListener((ListChangeListener<DocumentPane>) c -> rebuild());
        current.addListener((o, was, now) -> {
            tabs.forEach((pane, tab) -> tab.sync());
            row.requestLayout();
        });
        rebuild();
    }

    /**
     * Files dropped on a tab: {@code accepts} says whether the dragboard is something to open, and
     * {@code onDrop} gets the tab it landed on.  Elsewhere on the strip the drop passes through to
     * the viewer (a new tab).
     */
    public void setOnFilesDropped(java.util.function.Predicate<javafx.scene.input.Dragboard> accepts,
                                  BiConsumer<DocumentPane, javafx.scene.input.Dragboard> onDrop) {
        this.accepts = accepts;
        this.onDropOnTab = onDrop;
    }

    /**
     * Moving tabs between windows.  {@code detach} takes a tab out of this strip's viewer (false if
     * it can't leave); {@code adopt} puts a tab from another window in at an index; {@code tearOff} gets
     * a tab dropped outside every window (with the pointer's screen position, null from the menu),
     * and {@code canTearOff} says whether that is on offer now.
     */
    public void setOnTransfer(java.util.function.Predicate<DocumentPane> detach, BiConsumer<DocumentPane, Integer> adopt,
                              BiConsumer<DocumentPane, javafx.geometry.Point2D> tearOff,
                              java.util.function.BooleanSupplier canTearOff) {
        this.detach = detach;
        this.onAdopt = adopt;
        this.onTearOff = tearOff;
        this.canTearOff = canTearOff;
    }

    /** A tab dragged to a new place: the document and its index among the others (without it). */
    public void setOnMove(BiConsumer<DocumentPane, Integer> onMove) {
        this.onMove = onMove;
    }

    // a cancelled drag (Escape) looks like a drop on nothing: only a pointer outside the window tears off.
    private boolean outsideWindow(double screenX, double screenY) {
        javafx.stage.Window w = getScene() != null ? getScene().getWindow() : null;
        return w == null || screenX < w.getX() || screenY < w.getY() || screenX > w.getX() + w.getWidth()
                || screenY > w.getY() + w.getHeight();
    }

    /** Where the dragged tab would land: the number of other tabs left of the pointer. */
    private int insertionIndex(double sceneX) {
        int index = 0;
        for (DocumentPane pane : documents) {
            TabNode tab = tabs.get(pane);
            if (pane == dragging || tab == null) continue;
            javafx.geometry.Bounds b = tab.localToScene(tab.getBoundsInLocal());
            if (b.getMinX() + b.getWidth() / 2 < sceneX) index++;
        }
        return index;
    }

    private void showIndicator(int index) {
        double x = 0;
        int seen = 0;
        for (DocumentPane pane : documents) {
            TabNode tab = tabs.get(pane);
            if (pane == dragging || tab == null) continue;
            if (seen == index) {
                x = tab.getLayoutX() + tab.getTranslateX();
                break;
            }
            x = tab.getLayoutX() + tab.getTranslateX() + tab.getWidth();
            seen++;
        }
        indicator.resizeRelocate(Math.max(0, Math.min(x, row.getWidth() - 2)) - 1, 2, 2, Math.max(0, row.getHeight() - 4));
        indicator.setVisible(true);
        indicator.toFront();
    }

    /** The tab for a document (for tests and drag handling). */
    public Node tabFor(DocumentPane pane) {
        return tabs.get(pane);
    }

    private static String label(DocumentPane pane) {
        return pane.getTitle().isEmpty() ? Messages.get("tabStrip.newTabTitle") : pane.getTitle();
    }

    private void rebuild() {
        tabs.keySet().removeIf(p -> !documents.contains(p));
        for (DocumentPane pane : documents) tabs.computeIfAbsent(pane, TabNode::new);
        java.util.List<Node> nodes = new java.util.ArrayList<>(documents.stream().map(tabs::get).toList());
        nodes.add(indicator);
        row.getChildren().setAll(nodes);
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
            Button x = iconButton("ui.close", Messages.get("tabStrip.close"), "\u00d7");
            x.getStyleClass().add("tab-close");
            x.setOnAction(e -> {
                close.accept(pane);
                e.consume();
            });
            getChildren().addAll(name, x);
            Tooltip.install(this, tip);
            pane.titleProperty().addListener((o, a, b) -> sync());
            setOnDragDetected(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                dragging = pane;
                dragSource = TabStrip.this;
                javafx.scene.input.Dragboard db = startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.put(TAB, "tab");
                db.setContent(content);
                db.setDragView(snapshot(null, null));
                getStyleClass().add("dragging");
                e.consume();
            });
            setOnDragDone(e -> {
                boolean dropped = e.isDropCompleted();
                boolean ours = dragging == pane && dragSource == TabStrip.this;
                dragging = null;
                dragSource = null;
                getStyleClass().remove("dragging");
                indicator.setVisible(false);
                // let go outside every window: the tab gets a window of its own.
                if (ours && !dropped && canTearOff.getAsBoolean() && outsideWindow(e.getScreenX(), e.getScreenY())) {
                    onTearOff.accept(pane, new javafx.geometry.Point2D(e.getScreenX(), e.getScreenY()));
                }
            });
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) select.accept(pane);
                else if (e.getButton() == MouseButton.MIDDLE) close.accept(pane);
            });
            setOnDragOver(e -> {
                if (accepts.test(e.getDragboard())) {
                    e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                    e.consume();
                }
            });
            setOnDragEntered(e -> {
                if (accepts.test(e.getDragboard())) getStyleClass().add("drop-target");
            });
            setOnDragExited(e -> getStyleClass().remove("drop-target"));
            setOnDragDropped(e -> {
                getStyleClass().remove("drop-target");
                if (!accepts.test(e.getDragboard())) return;
                onDropOnTab.accept(pane, e.getDragboard());
                e.setDropCompleted(true);
                e.consume();
            });
            ContextMenu menu = new ContextMenu();
            MenuItem closeThis = new MenuItem(Messages.get("tabStrip.close"));
            closeThis.setOnAction(e -> close.accept(pane));
            MenuItem closeOthers = new MenuItem(Messages.get("tabStrip.closeOthers"));
            closeOthers.setOnAction(e -> {
                for (DocumentPane other : documents.toArray(new DocumentPane[0])) if (other != pane) close.accept(other);
            });
            MenuItem toWindow = new MenuItem(Messages.get("tabStrip.moveToWindow"));
            toWindow.setOnAction(e -> onTearOff.accept(pane, null));
            menu.setOnShowing(e -> toWindow.setDisable(!canTearOff.getAsBoolean()));
            menu.getItems().addAll(closeThis, closeOthers, new javafx.scene.control.SeparatorMenuItem(), toWindow);
            setOnContextMenuRequested(e -> menu.show(this, e.getScreenX(), e.getScreenY()));
        }

        void sync() {
            name.setText(label(pane));
            tip.setText(pane.getSession().getFile() != null ? pane.getSession().getFile().toString() : Messages.get("tabStrip.noDocument"));
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
            java.util.List<Node> managed = getManagedChildren();
            int n = managed.size();
            double w = getWidth(), h = getHeight();
            clip.setWidth(w);
            clip.setHeight(h);
            if (n == 0) return;
            double each = Math.max(MIN_TAB, Math.min(MAX_TAB, w / n));
            double x = 0, selectedX = 0;
            for (Node node : managed) {
                node.resizeRelocate(x, 0, each, h);
                if (node instanceof TabNode tab && tab.pane == current.getValue()) selectedX = x;
                x += each;
            }
            boolean overflow = x > w + 0.5;
            double shift = 0;
            if (overflow) shift = Math.max(0, Math.min(x - w, selectedX + each - w));
            for (Node node : managed) node.setTranslateX(-shift);
            list.setVisible(overflow);
            list.setManaged(overflow);
        }
    }
}
