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
package org.icepdf.fx.panels;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;

import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The document's comments - notes, highlights, shapes, stamps and the rest of the markup
 * annotations - for a {@link PdfView}: grouped by page (or sorted by date, author or type), replies
 * under the comment they answer, filtered by text, type or author.  Picking one goes to it and
 * selects it; double-click or Enter opens its note.  The list follows edits made in the view
 * ({@link PdfView#annotationsVersionProperty()}).
 */
public class AnnotationListPanel extends BorderPane {

    private static final Logger logger = Logger.getLogger(AnnotationListPanel.class.getName());
    private static final String ALL_TYPES = "All types";
    private static final String ALL_AUTHORS = "All authors";

    private final PdfView view;
    private final TextField filter = new TextField();
    private final ComboBox<String> types = new ComboBox<>();
    private final ComboBox<String> authors = new ComboBox<>();
    private final ComboBox<AnnotationSummary.Order> order = new ComboBox<>();
    private final Label status = new Label();
    private final TreeView<Object> tree = new TreeView<>();
    private final TreeItem<Object> root = new TreeItem<>();
    private final Label empty = new Label("This document has no comments.");
    private final ReadOnlyBooleanWrapper hasComments = new ReadOnlyBooleanWrapper(this, "hasComments", false);
    private final PauseTransition rescanLater = new PauseTransition(Duration.millis(250));

    private List<AnnotationSummary.Entry> entries = Collections.emptyList();
    private final Map<MarkupAnnotation, TreeItem<Object>> items = new IdentityHashMap<>();
    private Thread scan;
    private int generation;
    private boolean syncing;

    /** A page heading, with how many comments it holds. */
    record PageRow(int pageIndex, int count) {
    }

    public AnnotationListPanel(PdfView view) {
        this.view = view;
        getStyleClass().add("annotation-list-panel");

        filter.setPromptText("Filter comments");
        filter.textProperty().addListener((o, a, b) -> rebuild());
        types.valueProperty().addListener((o, a, b) -> rebuild());
        authors.valueProperty().addListener((o, a, b) -> rebuild());
        order.getItems().setAll(AnnotationSummary.Order.values());
        order.setValue(AnnotationSummary.Order.PAGE);
        order.valueProperty().addListener((o, a, b) -> rebuild());
        // the panel takes the width the side bar gives it, never asks for more.
        for (ComboBox<?> box : List.of(types, authors, order)) {
            box.setMaxWidth(Double.MAX_VALUE);
            box.setPrefWidth(60);
            box.setMinWidth(0);
        }
        HBox pickers = new HBox(4, types, authors);
        HBox.setHgrow(types, Priority.ALWAYS);
        HBox.setHgrow(authors, Priority.ALWAYS);
        Label sortLabel = new Label("Sort:");
        HBox sortRow = new HBox(4, sortLabel, order);
        sortRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(order, Priority.ALWAYS);
        status.setStyle("-fx-opacity: 0.75;");
        VBox top = new VBox(6, filter, pickers, sortRow, status);
        top.setPadding(new Insets(6));
        setTop(top);

        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setCellFactory(t -> new CommentCell());
        tree.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (!syncing && now != null) go(now.getValue());
        });
        tree.setOnMouseClicked(e -> {
            TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && item != null && item.getValue() instanceof AnnotationSummary.Entry entry) {
                openNote(entry);
            }
        });
        tree.setOnKeyPressed(e -> {
            TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
            if (item == null || !(item.getValue() instanceof AnnotationSummary.Entry entry)) return;
            if (e.getCode() == KeyCode.ENTER) openNote(entry);
            else if (e.getCode() == KeyCode.DELETE) delete(entry);
        });
        tree.setContextMenu(contextMenu());
        empty.setWrapText(true);
        empty.setPadding(new Insets(8));
        setCenter(empty);
        setMinWidth(140);
        setPrefWidth(240);

        rescanLater.setOnFinished(e -> rescan());
        view.documentProperty().addListener((o, a, b) -> rescan());
        view.annotationsVersionProperty().addListener((o, a, b) -> rescanLater.playFromStart());
        view.selectedAnnotationProperty().addListener((o, a, b) -> follow(b));
        rescan();
    }

    /** True when the current document has comments (known once the background scan finishes). */
    public final ReadOnlyBooleanProperty hasCommentsProperty() {
        return hasComments.getReadOnlyProperty();
    }

    public final boolean hasComments() {
        return hasComments.get();
    }

    /** Reads the document's comments again (edits the view makes are picked up by themselves). */
    public void refresh() {
        rescan();
    }

    /** Stops the background scan; call when the panel is thrown away. */
    public void dispose() {
        generation++;
        if (scan != null) scan.interrupt();
    }

    // ---- scanning -------------------------------------------------------------------------

    private void rescan() {
        int run = ++generation;
        if (scan != null) scan.interrupt();
        Document document = view.getDocument();
        if (document == null) {
            show(Collections.emptyList());
            return;
        }
        if (entries.isEmpty()) status.setText("Reading comments…");
        scan = new Thread(() -> {
            try {
                List<AnnotationSummary.Entry> found = AnnotationSummary.collect(document, () -> run != generation);
                if (found != null) Platform.runLater(() -> {
                    if (run == generation && document == view.getDocument()) show(found);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Reading the document's comments failed", e);
            }
        }, "icepdf-fx-comments");
        scan.setDaemon(true);
        scan.setPriority(Thread.MIN_PRIORITY);
        scan.start();
    }

    private void show(List<AnnotationSummary.Entry> found) {
        entries = found;
        hasComments.set(!found.isEmpty());
        // offer the types and authors there are, keeping the current pick if it's still there.
        Set<String> typeNames = new TreeSet<>();
        Set<String> authorNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AnnotationSummary.Entry entry : found) {
            typeNames.add(entry.type());
            if (!entry.author().isEmpty()) authorNames.add(entry.author());
        }
        String type = types.getValue(), author = authors.getValue();
        syncing = true;
        try {
            types.getItems().setAll(ALL_TYPES);
            types.getItems().addAll(typeNames);
            types.setValue(type != null && types.getItems().contains(type) ? type : ALL_TYPES);
            authors.getItems().setAll(ALL_AUTHORS);
            authors.getItems().addAll(authorNames);
            authors.setValue(author != null && authors.getItems().contains(author) ? author : ALL_AUTHORS);
        } finally {
            syncing = false;
        }
        rebuild();
    }

    // ---- the tree -------------------------------------------------------------------------

    private void rebuild() {
        if (syncing) return;
        String type = types.getValue(), author = authors.getValue();
        List<AnnotationSummary.Entry> shown = new ArrayList<>();
        for (AnnotationSummary.Entry entry : entries) {
            if (type != null && !ALL_TYPES.equals(type) && !type.equals(entry.type())) continue;
            if (author != null && !ALL_AUTHORS.equals(author) && !author.equalsIgnoreCase(entry.author())) continue;
            if (!entry.matches(filter.getText())) continue;
            shown.add(entry);
        }
        AnnotationSummary.Order sort = order.getValue() != null ? order.getValue() : AnnotationSummary.Order.PAGE;
        shown.sort(sort.comparator());

        // replies go under the comment they answer when it's shown too.
        Set<Reference> shownRefs = new HashSet<>();
        for (AnnotationSummary.Entry entry : shown) {
            if (entry.annotation().getPObjectReference() != null) shownRefs.add(entry.annotation().getPObjectReference());
        }
        Map<Reference, List<AnnotationSummary.Entry>> replies = AnnotationSummary.replies(shown);
        Object selected = selectedAnnotation();

        items.clear();
        root.getChildren().clear();
        Map<Integer, TreeItem<Object>> pages = new LinkedHashMap<>();
        Map<Integer, Integer> perPage = new HashMap<>();
        for (AnnotationSummary.Entry entry : shown) {
            MarkupAnnotation parent = entry.replyTo();
            if (parent != null && shownRefs.contains(parent.getPObjectReference())) continue;
            TreeItem<Object> item = item(entry, replies);
            if (sort == AnnotationSummary.Order.PAGE) {
                pages.computeIfAbsent(entry.pageIndex(), p -> {
                    TreeItem<Object> page = new TreeItem<>(new PageRow(p, 0));
                    page.setExpanded(true);
                    root.getChildren().add(page);
                    return page;
                }).getChildren().add(item);
                perPage.merge(entry.pageIndex(), 1 + countReplies(item), Integer::sum);
            } else {
                root.getChildren().add(item);
            }
        }
        pages.forEach((p, page) -> page.setValue(new PageRow(p, perPage.getOrDefault(p, 0))));

        int total = entries.size();
        status.setText(total == 0 ? "" : shown.size() == total ? count(total)
                : shown.size() + " of " + count(total));
        setCenter(total == 0 ? empty : tree);
        if (selected instanceof MarkupAnnotation markup) follow(markup);
    }

    private TreeItem<Object> item(AnnotationSummary.Entry entry, Map<Reference, List<AnnotationSummary.Entry>> replies) {
        TreeItem<Object> item = new TreeItem<>(entry);
        item.setExpanded(true);
        items.put(entry.annotation(), item);
        Reference ref = entry.annotation().getPObjectReference();
        for (AnnotationSummary.Entry reply : replies.getOrDefault(ref, List.of())) {
            if (reply.annotation() != entry.annotation()) item.getChildren().add(item(reply, replies));
        }
        return item;
    }

    private static int countReplies(TreeItem<Object> item) {
        int n = 0;
        for (TreeItem<Object> child : item.getChildren()) n += 1 + countReplies(child);
        return n;
    }

    private static String count(int n) {
        return n == 1 ? "1 comment" : n + " comments";
    }

    private Object selectedAnnotation() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof AnnotationSummary.Entry entry ? entry.annotation() : null;
    }

    /** Keeps the view's selected annotation selected here. */
    private void follow(Object annotation) {
        TreeItem<Object> item = annotation instanceof MarkupAnnotation markup ? items.get(markup) : null;
        if (item == null) return;
        syncing = true;
        try {
            tree.getSelectionModel().select(item);
        } finally {
            syncing = false;
        }
        int row = tree.getRow(item);
        if (row >= 0) tree.scrollTo(Math.max(0, row - 2));
    }

    // ---- actions --------------------------------------------------------------------------

    private void go(Object value) {
        if (value instanceof PageRow page) {
            view.setCurrentPageIndex(page.pageIndex());
        } else if (value instanceof AnnotationSummary.Entry entry) {
            view.setCurrentPageIndex(entry.pageIndex());
            Rectangle2D.Float rect = entry.annotation().getUserSpaceRectangle();
            if (rect != null) {
                view.ensureVisible(new PagePoint(entry.pageIndex(), rect.getCenterX(), rect.getCenterY()));
            }
            syncing = true;
            try {
                view.selectAnnotation(entry.annotation());
            } finally {
                syncing = false;
            }
        }
    }

    private void openNote(AnnotationSummary.Entry entry) {
        go(entry);
        if (entry.annotation().getPopupAnnotation() != null) view.setPopupOpen(entry.annotation(), true);
    }

    private void delete(AnnotationSummary.Entry entry) {
        if (!view.isAnnotationEditingAllowed()) return;
        go(entry);
        view.deleteSelectedAnnotation();
    }

    private ContextMenu contextMenu() {
        MenuItem goTo = new MenuItem("Go To");
        MenuItem open = new MenuItem("Open Note");
        MenuItem copy = new MenuItem("Copy Text");
        MenuItem delete = new MenuItem("Delete");
        ContextMenu menu = new ContextMenu(goTo, open, copy, new SeparatorMenuItem(), delete);
        menu.setOnShowing(e -> {
            AnnotationSummary.Entry entry = selectedEntry();
            boolean has = entry != null;
            goTo.setDisable(!has);
            open.setDisable(!has || entry.annotation().getPopupAnnotation() == null);
            copy.setDisable(!has || entry.contents().isEmpty());
            delete.setDisable(!has || !view.isAnnotationEditingAllowed());
        });
        goTo.setOnAction(e -> go(selectedEntry()));
        open.setOnAction(e -> {
            if (selectedEntry() != null) openNote(selectedEntry());
        });
        copy.setOnAction(e -> {
            AnnotationSummary.Entry entry = selectedEntry();
            if (entry == null) return;
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(entry.contents());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        delete.setOnAction(e -> {
            if (selectedEntry() != null) delete(selectedEntry());
        });
        return menu;
    }

    private AnnotationSummary.Entry selectedEntry() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof AnnotationSummary.Entry entry ? entry : null;
    }

    // ---- cells ----------------------------------------------------------------------------

    private static final DateTimeFormatter DATES = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

    private static final class CommentCell extends TreeCell<Object> {
        CommentCell() {
            // fit the tree's width; long text is cut with an ellipsis rather than widening the panel.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Object value, boolean empty) {
            super.updateItem(value, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            if (empty || value == null) return;
            if (value instanceof PageRow page) {
                Label label = new Label("Page " + (page.pageIndex() + 1) + " (" + page.count() + ")");
                label.setStyle("-fx-font-weight: bold;");
                setGraphic(label);
                return;
            }
            AnnotationSummary.Entry entry = (AnnotationSummary.Entry) value;
            Rectangle swatch = new Rectangle(10, 10, swatch(entry.color()));
            swatch.setStroke(Color.gray(0.4));
            Label type = new Label(entry.type());
            type.setStyle("-fx-font-weight: bold;");
            Label author = new Label(entry.author());
            Label date = new Label(entry.date() != null ? DATES.format(entry.date()) : "");
            date.setStyle("-fx-opacity: 0.7;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox heading = new HBox(6, swatch, type, author, spacer, date);
            heading.setAlignment(Pos.CENTER_LEFT);
            Node body = heading;
            if (!entry.contents().isEmpty()) {
                String text = entry.contents().replaceAll("\\s+", " ");
                Label contents = new Label(text.length() > 120 ? text.substring(0, 120) + "…" : text);
                contents.setStyle("-fx-opacity: 0.85;");
                body = new VBox(2, heading, contents);
                setTooltip(new Tooltip(entry.contents().length() > 600
                        ? entry.contents().substring(0, 600) + "…" : entry.contents()));
            }
            setGraphic(body);
        }

        private static Color swatch(java.awt.Color color) {
            if (color == null) return Color.TRANSPARENT;
            return Color.rgb(color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
