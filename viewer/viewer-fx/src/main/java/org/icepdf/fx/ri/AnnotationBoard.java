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
package org.icepdf.fx.ri;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.panels.AnnotationSummary;
import org.icepdf.fx.panels.MarkedText;
import org.icepdf.fx.view.AnnotationProperties;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.PdfViewEvent;

import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The annotation board: a document's comments as cards in columns, one column per colour label
 * ({@link ColorLabels}), for sorting a review.  A highlight's card quotes the text it marks.
 * Picking a card goes to the comment in the view and selects it; selecting a comment in the view
 * picks its card.  The board follows the view's edits and document.
 * <p>
 * {@link #show} opens it in its own window, one per view, so it can sit on a second screen.
 */
public class AnnotationBoard extends BorderPane {

    private static final Logger logger = Logger.getLogger(AnnotationBoard.class.getName());
    private static final DateTimeFormatter DATES = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    private static final double COLUMN_WIDTH = 280;
    private static final int QUOTE_MAX = 280, CONTENTS_MAX = 400;
    private static final Map<PdfView, Stage> windows = new WeakHashMap<>();

    /** Which comments to show by privacy; offered only with private annotations enabled. */
    enum Visibility {
        ALL("commentsPanel.visibility.all"), PUBLIC("commentsPanel.visibility.public"),
        PRIVATE("commentsPanel.visibility.private");

        private final String key;

        Visibility(String key) {
            this.key = key;
        }

        boolean shows(MarkupAnnotation annotation) {
            return this == ALL || (this == PRIVATE) == annotation.getFlagPrivateContents();
        }

        @Override
        public String toString() {
            return Messages.get(key);
        }
    }

    private final PdfView view;
    private final ColorLabels labels;
    private final TextField filter = new TextField();
    private final ComboBox<AnnotationSummary.Order> order = new ComboBox<>();
    private final ComboBox<Visibility> visibility = new ComboBox<>();
    private final Label status = new Label();
    private final HBox columns = new HBox(10);
    private final ScrollPane scroller = new ScrollPane(columns);
    private final Label empty = new Label();
    private final PauseTransition rescanLater = new PauseTransition(Duration.millis(250));
    private final List<ListView<BoardModel.Card>> lists = new ArrayList<>();
    private final List<Label> counts = new ArrayList<>();
    private List<BoardModel.Column> model = List.of();
    private List<String> columnKeys = List.of();
    /** The card being dragged to another column, or null. */
    private BoardModel.Card dragged;

    private List<AnnotationSummary.Entry> entries = Collections.emptyList();
    private Map<MarkupAnnotation, String> quotes = Collections.emptyMap();
    private Thread scan;
    private int generation;
    private boolean syncing;
    private List<ColorLabels.Label> builtWith = List.of();
    private final javafx.beans.value.ChangeListener<Document> documentListener = (o, a, b) -> rescan();
    private final javafx.event.EventHandler<PdfViewEvent> changeListener = e -> rescanLater.playFromStart();
    private final javafx.beans.value.ChangeListener<org.icepdf.core.pobjects.annotations.Annotation> selectionListener =
            (o, a, b) -> follow(b);

    /**
     * @param labels the colour labels to make columns of; read again on {@link #refresh()}
     */
    public AnnotationBoard(PdfView view, ColorLabels labels) {
        this.view = view;
        this.labels = labels != null ? labels : ColorLabels.inMemory();
        getStyleClass().add("annotation-board");

        filter.setPromptText(Messages.get("commentsPanel.filter"));
        filter.textProperty().addListener((o, a, b) -> rebuild());
        HBox.setHgrow(filter, Priority.ALWAYS);
        filter.setMaxWidth(320);
        order.getItems().setAll(AnnotationSummary.Order.values());
        order.setValue(AnnotationSummary.Order.PAGE);
        order.valueProperty().addListener((o, a, b) -> rebuild());
        visibility.getItems().setAll(Visibility.values());
        visibility.setValue(Visibility.ALL);
        visibility.valueProperty().addListener((o, a, b) -> rebuild());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.getStyleClass().add("dim-label");
        HBox bar = new HBox(8, filter, new Label(Messages.get("commentsPanel.sort")), order);
        if (AnnotationSummary.privacyEnabled()) bar.getChildren().add(visibility);
        bar.getChildren().addAll(spacer, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        setTop(bar);

        columns.setPadding(new Insets(0, 8, 8, 8));
        columns.setFillHeight(true);
        scroller.setFitToHeight(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        empty.setWrapText(true);
        empty.setPadding(new Insets(16));
        setCenter(empty);

        rescanLater.setOnFinished(e -> rescan());
        view.documentProperty().addListener(documentListener);
        view.addEventHandler(PdfViewEvent.ANNOTATIONS_CHANGED, changeListener);
        view.selectedAnnotationProperty().addListener(selectionListener);
        rescan();
    }

    /**
     * Shows the board for {@code view} in its own window, or brings the open one to the front.
     *
     * @param owner the viewer's window; the board closes with it
     */
    public static Stage show(Window owner, PdfView view, ColorLabels labels) {
        Stage stage = windows.get(view);
        if (stage != null) {
            ((AnnotationBoard) stage.getScene().getRoot()).refresh();
            stage.show();
            stage.toFront();
            return stage;
        }
        AnnotationBoard board = new AnnotationBoard(view, labels);
        Stage created = new Stage();
        if (owner != null) {
            created.initOwner(owner);
            if (owner.getScene() != null) created.getIcons().setAll(owner instanceof Stage s ? s.getIcons() : List.of());
            // the viewer's theme.
            created.setOnShowing(e -> {
                if (owner.getScene() != null) board.getStylesheets().setAll(owner.getScene().getStylesheets());
            });
        }
        created.setScene(new Scene(board, 1100, 700));
        created.titleProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> Messages.format("annotationBoard.title", title(view)), view.documentProperty()));
        // labels may have been edited in Preferences meanwhile.
        created.focusedProperty().addListener((o, was, now) -> {
            if (now) board.labelsChanged();
        });
        created.setOnHidden(e -> {
            board.dispose();
            windows.remove(view);
        });
        windows.put(view, created);
        created.show();
        return created;
    }

    private static String title(PdfView view) {
        Document document = view.getDocument();
        if (document == null) return "";
        String title = document.getInfo() != null ? document.getInfo().getTitle() : null;
        if (title != null && !title.isBlank()) return title;
        String path = document.getDocumentLocation();
        return path != null ? java.nio.file.Paths.get(path).getFileName().toString() : "";
    }

    /** The columns' card lists, left to right (for tests). */
    List<ListView<BoardModel.Card>> columnLists() {
        return List.copyOf(lists);
    }

    /** Reads the document's comments again. */
    public void refresh() {
        rescan();
    }

    /** Stops the background read and lets go of the view; call when the board is thrown away. */
    public void dispose() {
        generation++;
        if (scan != null) scan.interrupt();
        rescanLater.stop();
        view.documentProperty().removeListener(documentListener);
        view.removeEventHandler(PdfViewEvent.ANNOTATIONS_CHANGED, changeListener);
        view.selectedAnnotationProperty().removeListener(selectionListener);
    }

    /** Rebuilds the columns if the labels changed since they were built (edited in Preferences). */
    void labelsChanged() {
        if (!labels.labels().equals(builtWith)) rebuild();
    }

    // ---- reading ----------------------------------------------------------------------------

    private void rescan() {
        int run = ++generation;
        if (scan != null) scan.interrupt();
        Document document = view.getDocument();
        if (document == null) {
            show(Collections.emptyList(), Collections.emptyMap());
            return;
        }
        if (entries.isEmpty()) status.setText(Messages.get("commentsPanel.reading"));
        scan = new Thread(() -> {
            try {
                List<AnnotationSummary.Entry> found = AnnotationSummary.collect(document, () -> run != generation);
                if (found == null) return;
                Map<MarkupAnnotation, String> marked = quotes(document, found, run);
                Platform.runLater(() -> {
                    if (run == generation && document == view.getDocument()) show(found, marked);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Reading the document's comments failed", e);
                String message = Messages.format("commentsPanel.readFailed", Messages.reason(e));
                Platform.runLater(() -> {
                    if (run == generation && document == view.getDocument()) status.setText(message);
                });
            }
        }, "icepdf-fx-board");
        scan.setDaemon(true);
        scan.setPriority(Thread.MIN_PRIORITY);
        scan.start();
    }

    /** The marked text of each highlight-like comment; the old quote is kept for one already read. */
    private Map<MarkupAnnotation, String> quotes(Document document, List<AnnotationSummary.Entry> found, int run)
            throws InterruptedException {
        Map<MarkupAnnotation, String> marked = new IdentityHashMap<>();
        Map<MarkupAnnotation, String> known = quotes;
        for (AnnotationSummary.Entry entry : found) {
            if (run != generation) return marked;
            if (!(entry.annotation() instanceof TextMarkupAnnotation markup)) continue;
            String quote = known.get(markup);
            if (quote == null) {
                try {
                    Page page = document.getPageTree().getPage(entry.pageIndex());
                    quote = MarkedText.of(page, markup);
                } catch (RuntimeException e) {
                    logger.log(Level.FINE, "Could not read the text under a comment", e);
                    quote = "";
                }
            }
            marked.put(markup, quote);
        }
        return marked;
    }

    private void show(List<AnnotationSummary.Entry> found, Map<MarkupAnnotation, String> marked) {
        entries = found;
        quotes = marked;
        rebuild();
    }

    // ---- the columns ------------------------------------------------------------------------

    void rebuild() {
        String needle = filter.getText();
        Visibility privacy = visibility.getValue() != null ? visibility.getValue() : Visibility.ALL;
        AnnotationSummary.Order sort = order.getValue() != null ? order.getValue() : AnnotationSummary.Order.PAGE;
        List<ColorLabels.Label> named = labels.labels();
        builtWith = named;
        model = BoardModel.columns(entries, quotes, named, sort.comparator(),
                card -> card.matches(needle) && privacy.shows(card.entry().annotation()));
        Object selected = view.getSelectedAnnotation();
        List<String> keys = model.stream().map(AnnotationBoard::key).toList();

        int shown = 0;
        if (keys.equals(columnKeys) && lists.size() == model.size()) {
            // same columns: new cards in the lists there are, so they keep their scroll positions.
            syncing = true;
            try {
                for (int i = 0; i < model.size(); i++) {
                    lists.get(i).getItems().setAll(model.get(i).cards());
                    counts.get(i).setText(Integer.toString(model.get(i).cards().size()));
                    shown += model.get(i).cards().size();
                }
            } finally {
                syncing = false;
            }
        } else {
            lists.clear();
            counts.clear();
            columns.getChildren().clear();
            for (BoardModel.Column column : model) {
                columns.getChildren().add(column(column));
                shown += column.cards().size();
            }
            columnKeys = keys;
        }
        int total = (int) entries.stream().filter(e -> e.replyTo() == null).count();
        status.setText(entries.isEmpty() ? "" : shown == total ? Messages.format("commentsPanel.count", total)
                : Messages.format("commentsPanel.shownOf", shown, Messages.format("commentsPanel.count", total)));
        if (entries.isEmpty()) {
            empty.setText(Messages.get(view.getDocument() == null ? "annotationBoard.noDocument" : "commentsPanel.empty"));
            setCenter(empty);
        } else {
            setCenter(scroller);
            setBottom(named.isEmpty() ? hint() : null);
        }
        if (selected != null) follow(selected);
    }

    /** A column's identity, for telling whether a rebuild has the same columns. */
    private static String key(BoardModel.Column column) {
        return column.kind() + (column.label() != null
                ? ":" + (column.label().color().getRGB() & 0xFFFFFF) + ":" + column.label().name() : "");
    }

    private Label hint() {
        Label hint = new Label(Messages.get("annotationBoard.noLabels"));
        hint.setWrapText(true);
        hint.getStyleClass().add("dim-label");
        hint.setPadding(new Insets(0, 8, 8, 8));
        return hint;
    }

    private VBox column(BoardModel.Column column) {
        Rectangle swatch = new Rectangle(12, 12, column.label() != null ? fx(column.label().color()) : Color.TRANSPARENT);
        swatch.setStroke(Color.gray(0.4));
        Label name = new Label(switch (column.kind()) {
            case LABEL -> column.label().name().isEmpty()
                    ? String.format("#%06X", column.label().color().getRGB() & 0xFFFFFF) : column.label().name();
            case OTHER -> Messages.get("annotationBoard.other");
            case NONE -> Messages.get("annotationBoard.noColour");
            case ALL -> Messages.get("annotationBoard.all");
        });
        name.setStyle("-fx-font-weight: bold;");
        Label count = new Label(Integer.toString(column.cards().size()));
        count.getStyleClass().add("dim-label");
        counts.add(count);
        HBox header = new HBox(6, swatch, name, count);
        if (column.kind() != BoardModel.Kind.LABEL) header.getChildren().remove(swatch);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 2, 4, 2));

        ListView<BoardModel.Card> list = new ListView<>();
        list.getItems().setAll(column.cards());
        list.setCellFactory(l -> new CardCell());
        list.setPlaceholder(new Label(Messages.get("annotationBoard.emptyColumn")));
        list.getStyleClass().add("annotation-board-column");
        list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (syncing || now == null) return;
            selectOnly(list);
            go(now.entry());
        });
        list.setOnMouseClicked(e -> {
            BoardModel.Card card = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && card != null) openNote(card.entry());
        });
        // a filter: the list's own keys (F2, Enter) would otherwise take these first.
        list.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            BoardModel.Card card = list.getSelectionModel().getSelectedItem();
            int index = lists.indexOf(list);
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
                focusColumn(index + (e.getCode() == KeyCode.LEFT ? -1 : 1));
                e.consume();
            }
            if (card == null || list.getEditingIndex() >= 0) return;
            if (e.getCode() == KeyCode.ENTER) openNote(card.entry());
            else if (e.getCode() == KeyCode.F2 && canEdit(card)) edit(list, list.getSelectionModel().getSelectedIndex());
            else if (e.getCode() == KeyCode.DELETE && canEdit(card)) delete(card);
            else if (e.isShortcutDown() && e.getCode().isDigitKey()) {
                // Shortcut+1..9 files the card under the nth label.
                int n = e.getCode().getCode() - KeyCode.DIGIT1.getCode();
                List<ColorLabels.Label> named = labels.labels();
                if (n >= 0 && n < named.size()) refile(card, named.get(n));
            } else return;
            e.consume();
        });
        if (column.kind() == BoardModel.Kind.LABEL) acceptDrops(list, column.label());
        lists.add(list);
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox box = new VBox(4, header, list);
        box.setPrefWidth(COLUMN_WIDTH);
        box.setMinWidth(COLUMN_WIDTH);
        return box;
    }

    /** Clears the selection of every other column. */
    private void selectOnly(ListView<BoardModel.Card> keep) {
        boolean was = syncing;
        syncing = true;
        try {
            for (ListView<BoardModel.Card> list : lists) {
                if (list != keep) list.getSelectionModel().clearSelection();
            }
        } finally {
            syncing = was;
        }
    }

    /** Moves the focus to column {@code index}, picking its first card if none is. */
    private void focusColumn(int index) {
        if (index < 0 || index >= lists.size()) return;
        ListView<BoardModel.Card> list = lists.get(index);
        if (list.getItems().isEmpty()) return;
        if (list.getSelectionModel().isEmpty()) list.getSelectionModel().select(0);
        list.requestFocus();
    }

    // ---- editing ----------------------------------------------------------------------------

    private boolean canEdit(BoardModel.Card card) {
        return view.isAnnotationEditingAllowed() && card.entry().annotation().allowAlterProperties()
                && !card.entry().annotation().getFlagReadOnly();
    }

    /** Files a card under a label: its colour becomes the label's, one undoable edit. */
    void refile(BoardModel.Card card, ColorLabels.Label label) {
        MarkupAnnotation annotation = card.entry().annotation();
        if (!canEdit(card) || !AnnotationProperties.hasColor(annotation)) return;
        java.awt.Color now = annotation.getColor();
        if (now != null && (now.getRGB() & 0xFFFFFF) == (label.color().getRGB() & 0xFFFFFF)) return;
        view.applyAnnotationProperties(annotation, AnnotationProperties.ofColor(label.color()));
    }

    /**
     * Edits a card's text in place.  The list is editable only for this: an editable list would
     * start editing on any click on a picked card.
     */
    private void edit(ListView<BoardModel.Card> list, int index) {
        list.setEditable(true);
        list.edit(index);
    }

    private void delete(BoardModel.Card card) {
        view.deleteAnnotation(card.entry().annotation());
    }

    private void togglePrivate(BoardModel.Card card) {
        MarkupAnnotation annotation = card.entry().annotation();
        if (!canEdit(card) || !AnnotationSummary.canBePrivate(annotation)) return;
        view.setAnnotationsPrivate(AnnotationSummary.withReplies(annotation), !annotation.getFlagPrivateContents());
    }

    /** Makes a label of a card's colour, asking its name. */
    private void newLabel(BoardModel.Card card) {
        java.awt.Color colour = card.entry().color();
        if (colour == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle(Messages.get("annotationBoard.newLabelTitle"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("preferences.labels.name"));
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.showAndWait().map(String::strip).filter(n -> !n.isEmpty()).ifPresent(name -> {
            List<ColorLabels.Label> next = new ArrayList<>(labels.labels());
            next.add(new ColorLabels.Label(colour, name));
            labels.setLabels(next);
            rebuild();
        });
    }

    /** Lets cards be dropped on a label's column, which files them under it. */
    private void acceptDrops(ListView<BoardModel.Card> list, ColorLabels.Label label) {
        list.setOnDragOver(e -> {
            if (dragged != null && canEdit(dragged) && ColorLabels.match(List.of(label), dragged.entry().color()) == null) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            e.consume();
        });
        list.setOnDragEntered(e -> {
            if (dragged != null && ColorLabels.match(List.of(label), dragged.entry().color()) == null) {
                list.setStyle("-fx-border-color: " + web(label.color()) + "; -fx-border-width: 2;");
            }
        });
        list.setOnDragExited(e -> list.setStyle(null));
        list.setOnDragDropped(e -> {
            boolean done = dragged != null;
            if (done) refile(dragged, label);
            e.setDropCompleted(done);
            e.consume();
        });
    }

    private ContextMenu menu(BoardModel.Card card) {
        MarkupAnnotation annotation = card.entry().annotation();
        boolean editable = canEdit(card);
        ContextMenu menu = new ContextMenu();
        MenuItem goTo = new MenuItem(Messages.get("commentsPanel.goTo"));
        goTo.setOnAction(e -> go(card.entry()));
        MenuItem open = new MenuItem(Messages.get("commentsPanel.openNote"));
        open.setDisable(annotation.getPopupAnnotation() == null);
        open.setOnAction(e -> openNote(card.entry()));
        MenuItem editText = new MenuItem(Messages.get("annotationBoard.editText"));
        editText.setDisable(!editable);
        editText.setOnAction(e -> {
            for (ListView<BoardModel.Card> list : lists) {
                int i = list.getItems().indexOf(card);
                if (i >= 0) edit(list, i);
            }
        });
        menu.getItems().addAll(goTo, open, editText, new SeparatorMenuItem());

        Menu file = new Menu(Messages.get("annotationBoard.label"));
        List<ColorLabels.Label> named = labels.labels();
        ColorLabels.Label current = ColorLabels.match(named, card.entry().color());
        for (int i = 0; i < named.size(); i++) {
            ColorLabels.Label label = named.get(i);
            MenuItem item = new MenuItem(label.name().isEmpty() ? web(label.color()) : label.name());
            Rectangle box = new Rectangle(12, 12, fx(label.color()));
            box.setStroke(Color.gray(0, 0.35));
            item.setGraphic(box);
            if (i < 9) item.setAccelerator(new javafx.scene.input.KeyCodeCombination(KeyCode.getKeyCode(Integer.toString(i + 1)),
                    javafx.scene.input.KeyCombination.SHORTCUT_DOWN));
            item.setDisable(!editable || !AnnotationProperties.hasColor(annotation) || label.equals(current)
                    && (label.color().getRGB() & 0xFFFFFF) == (card.entry().color().getRGB() & 0xFFFFFF));
            item.setOnAction(e -> refile(card, label));
            file.getItems().add(item);
        }
        if (current == null && card.entry().color() != null) {
            if (!file.getItems().isEmpty()) file.getItems().add(new SeparatorMenuItem());
            MenuItem make = new MenuItem(Messages.get("annotationBoard.newLabel"));
            make.setOnAction(e -> newLabel(card));
            file.getItems().add(make);
        }
        if (!file.getItems().isEmpty()) menu.getItems().add(file);
        if (AnnotationSummary.privacyEnabled() && AnnotationSummary.canBePrivate(annotation)) {
            CheckMenuItem priv = new CheckMenuItem(Messages.get("annotationMenu.private"));
            priv.setSelected(annotation.getFlagPrivateContents());
            priv.setDisable(!editable);
            priv.setOnAction(e -> togglePrivate(card));
            menu.getItems().add(priv);
        }
        if (AnnotationProperties.supports(annotation)) {
            MenuItem properties = new MenuItem(Messages.get("annotationMenu.properties"));
            properties.setDisable(!editable);
            properties.setOnAction(e -> AnnotationPropertiesDialog.show(getScene() != null ? getScene().getWindow() : null,
                    annotation).ifPresent(change -> view.applyAnnotationProperties(annotation, change)));
            menu.getItems().add(properties);
        }
        MenuItem copy = new MenuItem(Messages.get("commentsPanel.copyText"));
        String text = !card.entry().contents().isEmpty() ? card.entry().contents() : card.quote();
        copy.setDisable(text.isEmpty());
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        MenuItem delete = new MenuItem(Messages.get("commentsPanel.delete"));
        delete.setDisable(!editable);
        delete.setOnAction(e -> delete(card));
        menu.getItems().addAll(copy, new SeparatorMenuItem(), delete);
        return menu;
    }

    private static String web(java.awt.Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    /** Picks the card of the view's selected comment, if it's on the board. */
    private void follow(Object annotation) {
        if (syncing) return; // the board picked it
        for (ListView<BoardModel.Card> list : lists) {
            for (int i = 0; i < list.getItems().size(); i++) {
                if (list.getItems().get(i).entry().annotation() == annotation) {
                    int row = i;
                    syncing = true;
                    try {
                        selectOnly(list);
                        list.getSelectionModel().select(row);
                    } finally {
                        syncing = false;
                    }
                    list.scrollTo(Math.max(0, row - 1));
                    return;
                }
            }
        }
    }

    private void go(AnnotationSummary.Entry entry) {
        view.setCurrentPageIndex(entry.pageIndex());
        Rectangle2D.Float rect = entry.annotation().getUserSpaceRectangle();
        if (rect != null) view.ensureVisible(new PagePoint(entry.pageIndex(), rect.getCenterX(), rect.getCenterY()));
        syncing = true;
        try {
            view.selectAnnotation(entry.annotation());
        } finally {
            syncing = false;
        }
    }

    private void openNote(AnnotationSummary.Entry entry) {
        go(entry);
        if (entry.annotation().getPopupAnnotation() != null) view.setPopupOpen(entry.annotation(), true);
    }

    private static Color fx(java.awt.Color c) {
        return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String cut(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }

    // ---- cards ------------------------------------------------------------------------------

    private final class CardCell extends ListCell<BoardModel.Card> {
        /** The text editor while editing, else null. */
        private javafx.scene.control.TextArea editor;

        CardCell() {
            // fit the column; text wraps rather than widening it.
            setPrefWidth(0);
            setOnDragDetected(e -> {
                BoardModel.Card card = getItem();
                if (card == null || isEditing() || !canEdit(card)) return;
                dragged = card;
                javafx.scene.input.Dragboard board = startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(card.entry().contents());
                board.setContent(content);
                board.setDragView(snapshot(null, null), e.getX(), e.getY());
                e.consume();
            });
            setOnDragDone(e -> dragged = null);
            setOnContextMenuRequested(e -> {
                if (getItem() == null) return;
                getListView().getSelectionModel().select(getIndex());
                menu(getItem()).show(this, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }

        @Override
        public void startEdit() {
            BoardModel.Card card = getItem();
            if (card == null || !canEdit(card)) return;
            super.startEdit();
            javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(card.entry().contents());
            editor = area;
            area.setWrapText(true);
            area.setPrefRowCount(4);
            area.setPromptText(Messages.get("annotationBoard.editPrompt"));
            area.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    e.consume();
                } else if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) {
                    commit(card, area.getText());
                    e.consume();
                }
            });
            area.focusedProperty().addListener((o, was, now) -> {
                if (!now && isEditing()) commit(card, area.getText());
            });
            setGraphic(area);
            area.requestFocus();
        }

        private void commit(BoardModel.Card card, String text) {
            cancelEdit();
            view.setAnnotationContents(card.entry().annotation(), text);
        }

        @Override
        public void cancelEdit() {
            editor = null;
            super.cancelEdit();
            if (getListView() != null) getListView().setEditable(false);
            updateItem(getItem(), getItem() == null);
        }

        @Override
        protected void updateItem(BoardModel.Card card, boolean empty) {
            super.updateItem(card, empty);
            setText(null);
            if (isEditing() && editor != null && !empty) {
                // a layout pass while editing: keep the editor.
                setGraphic(editor);
                return;
            }
            setGraphic(null);
            if (empty || card == null) return;
            AnnotationSummary.Entry entry = card.entry();

            Label type = new Label(entry.type());
            type.setStyle("-fx-font-weight: bold;");
            type.setMinWidth(Region.USE_PREF_SIZE);
            Label page = new Label(Messages.format("annotationBoard.page", entry.pageIndex() + 1));
            page.getStyleClass().add("dim-label");
            page.setMinWidth(Region.USE_PREF_SIZE);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox heading = new HBox(6, type, page);
            if (AnnotationSummary.privacyEnabled() && entry.annotation().getFlagPrivateContents()) {
                Label lock = new Label(Messages.get("commentsPanel.privateBadge"));
                lock.getStyleClass().addAll("dim-label", "comment-private");
                lock.setMinWidth(Region.USE_PREF_SIZE);
                heading.getChildren().add(lock);
                if (canEdit(card)) {
                    lock.setTooltip(new javafx.scene.control.Tooltip(Messages.get("annotationBoard.makePublic")));
                    lock.setCursor(javafx.scene.Cursor.HAND);
                    lock.setOnMouseClicked(e -> {
                        togglePrivate(card);
                        e.consume();
                    });
                }
            }
            String date = entry.date() != null ? DATES.format(entry.date()) : "";
            Label when = new Label(date);
            when.getStyleClass().add("dim-label");
            when.setMinWidth(0);
            heading.getChildren().addAll(spacer, when);
            heading.setAlignment(Pos.CENTER_LEFT);

            VBox body = new VBox(3, heading);
            if (!entry.author().isEmpty()) {
                Label author = new Label(entry.author());
                author.getStyleClass().add("dim-label");
                author.setMinWidth(0);
                body.getChildren().add(author);
            }
            String quote = cut(card.quote(), QUOTE_MAX);
            String contents = cut(entry.contents(), CONTENTS_MAX);
            if (!quote.isEmpty()) {
                Label quoted = new Label("“" + quote + "”");
                quoted.setWrapText(true);
                quoted.setStyle("-fx-font-style: italic;");
                body.getChildren().add(quoted);
            }
            // a highlight made here keeps the text it marks as its contents: say it once.
            if (!contents.isEmpty() && !contents.equals(quote)) {
                Label text = new Label(contents);
                text.setWrapText(true);
                body.getChildren().add(text);
            }
            if (card.replies() > 0) {
                Label replies = new Label(Messages.format("annotationBoard.replies", card.replies()));
                replies.getStyleClass().add("dim-label");
                body.getChildren().add(replies);
            }
            java.awt.Color c = entry.color();
            String edge = c != null ? String.format("#%06x", c.getRGB() & 0xFFFFFF) : "transparent";
            body.setStyle("-fx-border-color: transparent transparent transparent " + edge
                    + "; -fx-border-width: 0 0 0 4; -fx-padding: 2 0 4 8;");
            body.setMinWidth(0);
            body.prefWidthProperty().bind(widthProperty().subtract(20));
            body.maxWidthProperty().bind(widthProperty().subtract(20));
            setGraphic(body);
        }
    }
}
