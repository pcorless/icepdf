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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.fx.view.PagePoint;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.SearchHit;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A search panel for a {@link PdfView}: a query with options, and the results as a tree - page
 * text hits grouped by page with the words around each, then comments, form field values, bookmarks
 * and named destinations when those are asked for.  Picking a result goes to it; the current text
 * hit (also moved by {@link PdfView#nextSearchHit()}) is kept selected in the tree.
 * <p>
 * The page text search is the view's own ({@link PdfView#search(SearchTerm...)}), so the hits the
 * tree lists are the ones highlighted on the pages, and a search started elsewhere - a toolbar find
 * field - shows here too.  With <em>cumulative</em> on, each search adds its term to the ones
 * before instead of replacing them.  The options are properties so an application can persist them.
 */
public class SearchPanel extends BorderPane {

    private static final Logger logger = Logger.getLogger(SearchPanel.class.getName());
    /** Text hit pages start expanded while the result list is shorter than this. */
    private static final int EXPAND_LIMIT = 60;

    private final PdfView view;
    private final TextField query = new TextField();
    private final Label status = new Label();
    private final Label termsLabel = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final TreeView<Entry> tree = new TreeView<>();
    private final TreeItem<Entry> root = new TreeItem<>();

    private final BooleanProperty caseSensitive = new SimpleBooleanProperty(this, "caseSensitive");
    private final BooleanProperty wholeWord = new SimpleBooleanProperty(this, "wholeWord");
    private final BooleanProperty regex = new SimpleBooleanProperty(this, "regex");
    private final BooleanProperty foldAccents = new SimpleBooleanProperty(this, "foldAccents", true);
    private final BooleanProperty cumulative = new SimpleBooleanProperty(this, "cumulative");
    private final BooleanProperty comments = new SimpleBooleanProperty(this, "comments");
    private final BooleanProperty formFields = new SimpleBooleanProperty(this, "formFields");
    private final BooleanProperty outlines = new SimpleBooleanProperty(this, "outlines");
    private final BooleanProperty destinations = new SimpleBooleanProperty(this, "destinations");

    private final List<SearchTerm> terms = new ArrayList<>();
    // text results: the category, its page groups, and one tree item per view hit (same index).
    private TreeItem<Entry> textCategory;
    private final Map<Integer, TreeItem<Entry>> textPages = new HashMap<>();
    private final List<TreeItem<Entry>> textItems = new ArrayList<>();
    // field results by kind, then page (-1 for none).
    private final Map<DocumentFieldSearch.Kind, TreeItem<Entry>> fieldCategories =
            new EnumMap<>(DocumentFieldSearch.Kind.class);
    private final Map<DocumentFieldSearch.Kind, Map<Integer, TreeItem<Entry>>> fieldPages =
            new EnumMap<>(DocumentFieldSearch.Kind.class);
    private Thread fieldThread;
    private int generation;
    private boolean fieldSearching;
    // true while the tree selection follows the view, so the listener doesn't navigate back.
    private boolean syncing;

    // ---- tree entries ---------------------------------------------------------------------

    /** What a tree row shows. */
    sealed interface Entry permits Category, PageGroup, TextResult, FieldResult {
    }

    /** A kind of result, with its count. */
    static final class Category implements Entry {
        final String title;
        int count;

        Category(String title) {
            this.title = title;
        }
    }

    /** One page's results within a category. */
    static final class PageGroup implements Entry {
        final int pageIndex;
        int count;

        PageGroup(int pageIndex) {
            this.pageIndex = pageIndex;
        }
    }

    record TextResult(int index, SearchHit hit) implements Entry {
    }

    record FieldResult(DocumentFieldSearch.Hit hit) implements Entry {
    }

    public SearchPanel(PdfView view) {
        this.view = view;
        getStyleClass().add("search-panel");

        query.setPromptText("Search");
        query.setOnAction(e -> search(query.getText()));
        query.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) clear();
        });
        Button go = new Button("Search");
        go.setOnAction(e -> search(query.getText()));
        go.setDefaultButton(false);
        MenuButton options = new MenuButton("Options");
        options.getItems().addAll(
                check("Match case", caseSensitive), check("Whole words only", wholeWord),
                check("Ignore accents", foldAccents), check("Regular expression", regex),
                check("Add to previous terms (cumulative)", cumulative),
                new SeparatorMenuItem(),
                new CustomMenuItem(new Label("Also search:"), false),
                check("Comments", comments), check("Form fields", formFields),
                check("Bookmarks", outlines), check("Named destinations", destinations));
        Button clear = new Button("Clear");
        clear.setOnAction(e -> clear());
        HBox field = new HBox(4, query, go);
        HBox.setHgrow(query, Priority.ALWAYS);
        HBox actions = new HBox(4, options, clear);
        termsLabel.setWrapText(true);
        termsLabel.managedProperty().bind(termsLabel.visibleProperty());
        termsLabel.setVisible(false);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.managedProperty().bind(progress.visibleProperty());
        progress.setVisible(false);
        status.setWrapText(true);
        VBox top = new VBox(6, field, actions, termsLabel, progress, status);
        top.setPadding(new Insets(6));
        setTop(top);

        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setCellFactory(t -> new ResultCell());
        tree.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (!syncing && now != null) go(now.getValue());
        });
        // re-picking the selected row goes there again.
        tree.setOnMouseClicked(e -> {
            TreeItem<Entry> item = tree.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && item != null) go(item.getValue());
        });
        tree.setOnKeyPressed(e -> {
            TreeItem<Entry> item = tree.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && item != null) go(item.getValue());
        });
        setCenter(tree);
        setMinWidth(140);

        view.getSearchHits().addListener((ListChangeListener<SearchHit>) change -> onTextHits(change));
        view.currentSearchHitIndexProperty().addListener((o, a, b) -> follow(b.intValue()));
        view.searchingProperty().addListener((o, a, b) -> updateStatus());
        view.searchProgressProperty().addListener((o, a, b) -> updateStatus());
        view.documentProperty().addListener((o, a, b) -> {
            terms.clear();
            stopFieldSearch();
            resetTree();
            updateStatus();
        });
        resetTree();
        updateStatus();
    }

    // ---- options --------------------------------------------------------------------------

    public final BooleanProperty caseSensitiveProperty() {
        return caseSensitive;
    }

    public final BooleanProperty wholeWordProperty() {
        return wholeWord;
    }

    public final BooleanProperty regexProperty() {
        return regex;
    }

    /** Accent-insensitive matching ("resume" finds "résumé"); on by default.  Ignored for regex. */
    public final BooleanProperty foldAccentsProperty() {
        return foldAccents;
    }

    /** Each search adds its term to the previous ones instead of replacing them. */
    public final BooleanProperty cumulativeProperty() {
        return cumulative;
    }

    public final BooleanProperty commentsProperty() {
        return comments;
    }

    public final BooleanProperty formFieldsProperty() {
        return formFields;
    }

    public final BooleanProperty outlinesProperty() {
        return outlines;
    }

    public final BooleanProperty destinationsProperty() {
        return destinations;
    }

    /** Puts the caret in the query field. */
    public void focusQuery() {
        query.requestFocus();
        query.selectAll();
    }

    /** The terms of the current search. */
    public List<SearchTerm> getTerms() {
        return List.copyOf(terms);
    }

    // ---- searching ------------------------------------------------------------------------

    /**
     * Searches for {@code text} with the panel's options (adding it to the previous terms when
     * cumulative); an empty text clears.
     *
     * @return false if the text isn't a valid regular expression (the status says why)
     */
    public boolean search(String text) {
        if (text == null || text.isBlank()) {
            clear();
            return true;
        }
        if (!text.equals(query.getText())) query.setText(text);
        if (regex.get()) {
            try {
                Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                status.setText("Not a valid regular expression: " + e.getDescription());
                return false;
            }
        }
        SearchTerm term = new SearchTerm(text, null, caseSensitive.get(), wholeWord.get(), regex.get());
        term.setFoldDiacritics(foldAccents.get() && !regex.get());
        if (!cumulative.get()) terms.clear();
        terms.removeIf(t -> t.getTerm().equals(text));
        terms.add(term);
        run();
        return true;
    }

    /** Stops searching and removes every result and highlight. */
    public void clear() {
        terms.clear();
        stopFieldSearch();
        view.clearSearch();
        resetTree();
        updateStatus();
    }

    private void run() {
        stopFieldSearch();
        resetTree();
        // the view's search (re)fills the text results through the hit list listener.
        view.search(terms.toArray(new SearchTerm[0]));
        Document document = view.getDocument();
        boolean c = comments.get(), f = formFields.get(), o = outlines.get(), d = destinations.get();
        if (document != null && (c || f || o || d)) {
            int run = ++generation;
            DocumentFieldSearch search = new DocumentFieldSearch(terms);
            List<DocumentFieldSearch.Hit> batch = new ArrayList<>();
            fieldSearching = true;
            fieldThread = new Thread(() -> {
                try {
                    search.run(document, c, f, o, d, () -> run != generation, hit -> {
                        synchronized (batch) {
                            batch.add(hit);
                            if (batch.size() == 1) Platform.runLater(() -> drain(run, batch));
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Field search failed", e);
                } finally {
                    Platform.runLater(() -> {
                        drain(run, batch);
                        if (run == generation) {
                            fieldSearching = false;
                            updateStatus();
                        }
                    });
                }
            }, "icepdf-fx-field-search");
            fieldThread.setDaemon(true);
            fieldThread.setPriority(Thread.NORM_PRIORITY - 1);
            fieldThread.start();
        }
        updateTermsLabel();
        updateStatus();
    }

    private void drain(int run, List<DocumentFieldSearch.Hit> batch) {
        List<DocumentFieldSearch.Hit> hits;
        synchronized (batch) {
            hits = new ArrayList<>(batch);
            batch.clear();
        }
        if (run != generation) return;
        hits.forEach(this::addFieldHit);
        tree.refresh();
        updateStatus();
    }

    private void stopFieldSearch() {
        generation++;
        fieldSearching = false;
        if (fieldThread != null) {
            fieldThread.interrupt();
            fieldThread = null;
        }
    }

    // ---- the tree -------------------------------------------------------------------------

    private void resetTree() {
        textCategory = null;
        textPages.clear();
        textItems.clear();
        fieldCategories.clear();
        fieldPages.clear();
        root.getChildren().clear();
        // hits already in the view (a search started before this panel, or elsewhere).
        List<SearchHit> hits = view.getSearchHits();
        for (int i = 0; i < hits.size(); i++) addTextHit(i, hits.get(i));
    }

    private void onTextHits(ListChangeListener.Change<? extends SearchHit> change) {
        while (change.next()) {
            if (change.wasRemoved() && view.getSearchHits().isEmpty()) {
                if (textCategory != null) root.getChildren().remove(textCategory);
                textCategory = null;
                textPages.clear();
                textItems.clear();
            }
            if (change.wasAdded()) {
                int index = change.getFrom();
                for (SearchHit hit : change.getAddedSubList()) addTextHit(index++, hit);
            }
        }
        tree.refresh();
        updateStatus();
    }

    private void addTextHit(int index, SearchHit hit) {
        if (textCategory == null) {
            textCategory = new TreeItem<>(new Category("Text"));
            textCategory.setExpanded(true);
            root.getChildren().add(0, textCategory);
        }
        TreeItem<Entry> page = textPages.computeIfAbsent(hit.pageIndex(), p -> {
            TreeItem<Entry> item = new TreeItem<>(new PageGroup(p));
            item.setExpanded(textItems.size() < EXPAND_LIMIT);
            textCategory.getChildren().add(item);
            return item;
        });
        TreeItem<Entry> item = new TreeItem<>(new TextResult(index, hit));
        page.getChildren().add(item);
        ((PageGroup) page.getValue()).count++;
        ((Category) textCategory.getValue()).count++;
        textItems.add(item);
    }

    private void addFieldHit(DocumentFieldSearch.Hit hit) {
        TreeItem<Entry> category = fieldCategories.computeIfAbsent(hit.kind(), kind -> {
            TreeItem<Entry> item = new TreeItem<>(new Category(kind.label()));
            item.setExpanded(true);
            root.getChildren().add(item);
            return item;
        });
        ((Category) category.getValue()).count++;
        TreeItem<Entry> result = new TreeItem<>(new FieldResult(hit));
        // comments and fields are grouped by page; bookmarks and destinations are a flat list.
        boolean byPage = hit.kind() == DocumentFieldSearch.Kind.COMMENT || hit.kind() == DocumentFieldSearch.Kind.FORM_FIELD;
        if (!byPage || hit.pageIndex() < 0) {
            category.getChildren().add(result);
            return;
        }
        TreeItem<Entry> page = fieldPages.computeIfAbsent(hit.kind(), k -> new HashMap<>())
                .computeIfAbsent(hit.pageIndex(), p -> {
                    TreeItem<Entry> item = new TreeItem<>(new PageGroup(p));
                    item.setExpanded(true);
                    category.getChildren().add(item);
                    return item;
                });
        ((PageGroup) page.getValue()).count++;
        page.getChildren().add(result);
    }

    /** Keeps the view's current text hit selected and in sight. */
    private void follow(int index) {
        if (index < 0 || index >= textItems.size()) return;
        TreeItem<Entry> item = textItems.get(index);
        if (item.getParent() != null) item.getParent().setExpanded(true);
        syncing = true;
        try {
            tree.getSelectionModel().select(item);
        } finally {
            syncing = false;
        }
        int row = tree.getRow(item);
        if (row >= 0) tree.scrollTo(Math.max(0, row - 3));
    }

    /** Goes to a result. */
    private void go(Entry entry) {
        if (entry instanceof TextResult text) {
            view.selectSearchHit(text.index());
        } else if (entry instanceof PageGroup group) {
            view.setCurrentPageIndex(group.pageIndex);
        } else if (entry instanceof FieldResult field) {
            Object target = field.hit().target();
            if (target instanceof OutlineItem item) {
                if (item.getDest() != null) view.navigateTo(item.getDest());
                else if (item.getAction() != null) view.performAction(item.getAction());
            } else if (target instanceof Destination destination) {
                view.navigateTo(destination);
            } else if (target instanceof Annotation annotation && field.hit().pageIndex() >= 0) {
                int page = field.hit().pageIndex();
                view.setCurrentPageIndex(page);
                Rectangle2D.Float rect = annotation.getUserSpaceRectangle();
                if (rect != null) {
                    view.ensureVisible(new PagePoint(page, rect.getCenterX(), rect.getCenterY()));
                }
                if (!(annotation instanceof AbstractWidgetAnnotation)) view.selectAnnotation(annotation);
            }
        }
    }

    // ---- status ---------------------------------------------------------------------------

    private void updateStatus() {
        boolean busy = view.isSearching() || fieldSearching;
        progress.setVisible(busy);
        progress.setProgress(view.isSearching() ? view.getSearchProgress() : ProgressBar.INDETERMINATE_PROGRESS);
        if (terms.isEmpty() && view.getSearchHits().isEmpty()) {
            status.setText("");
            return;
        }
        int text = view.getSearchHits().size();
        int fields = fieldCategories.values().stream().mapToInt(c -> ((Category) c.getValue()).count).sum();
        int pages = (int) view.getSearchHits().stream().mapToInt(SearchHit::pageIndex).distinct().count();
        StringBuilder sb = new StringBuilder();
        sb.append(text == 1 ? "1 match" : text + " matches");
        if (text > 0) sb.append(" on ").append(pages == 1 ? "1 page" : pages + " pages");
        if (fields > 0) sb.append(", ").append(fields).append(" elsewhere");
        if (busy) sb.append(" (searching…)");
        status.setText(sb.toString());
    }

    private void updateTermsLabel() {
        termsLabel.setVisible(terms.size() > 1);
        termsLabel.setText("Terms: " + String.join(", ", terms.stream().map(t -> "“" + t.getTerm() + "”").toList()));
    }

    private static CheckMenuItem check(String text, BooleanProperty property) {
        CheckMenuItem item = new CheckMenuItem(text);
        item.selectedProperty().bindBidirectional(property);
        return item;
    }

    // ---- cells ----------------------------------------------------------------------------

    private static final class ResultCell extends TreeCell<Entry> {
        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            if (empty || entry == null) return;
            if (entry instanceof Category category) {
                Label label = new Label(category.title + " (" + category.count + ")");
                label.setStyle("-fx-font-weight: bold;");
                setGraphic(label);
            } else if (entry instanceof PageGroup group) {
                setText("Page " + (group.pageIndex + 1) + " (" + group.count + ")");
            } else if (entry instanceof TextResult text) {
                SearchHit hit = text.hit();
                String before = tidy(hit.before()), after = tidy(hit.after());
                setGraphic(snippet(null, clip(before, 16, false), hit.text(), after));
                setTooltip(new Tooltip(before + hit.text() + after));
            } else if (entry instanceof FieldResult field) {
                DocumentFieldSearch.Hit hit = field.hit();
                String label = hit.label();
                if ((hit.kind() == DocumentFieldSearch.Kind.OUTLINE || hit.kind() == DocumentFieldSearch.Kind.DESTINATION)
                        && hit.pageIndex() >= 0) {
                    label = "p. " + (hit.pageIndex() + 1);
                }
                String fullText = hit.text();
                if (hit.start() < 0) {
                    setGraphic(snippet(label, clip(fullText, 80, true), "", ""));
                } else {
                    String before = fullText.substring(0, hit.start());
                    String after = fullText.substring(hit.end());
                    setGraphic(snippet(label, clip(before, 16, false), fullText.substring(hit.start(), hit.end()),
                            clip(after, 50, true)));
                }
                setTooltip(new Tooltip(fullText.length() > 400 ? fullText.substring(0, 400) + "…" : fullText));
            }
        }

        private static Node snippet(String label, String before, String match, String after) {
            List<Text> parts = new ArrayList<>();
            if (label != null && !label.isEmpty()) {
                Text l = new Text(label + ": ");
                l.setStyle("-fx-opacity: 0.7;");
                parts.add(l);
            }
            parts.add(new Text(before));
            Text hit = new Text(match);
            hit.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize()));
            parts.add(hit);
            parts.add(new Text(after));
            parts.forEach(t -> t.getStyleClass().add("text"));
            // one line, never wrapped: a long snippet runs off the edge (the tooltip has it all).
            HBox line = new HBox(parts.toArray(new Text[0]));
            line.setMinWidth(Region.USE_PREF_SIZE);
            return line;
        }

        /** Dot leaders (a table of contents) shrink to an ellipsis. */
        private static String tidy(String text) {
            return text.replaceAll("(\\s?[.\u2024\u2026\u00b7]){4,}\\s?", " \u2026 ");
        }

        /** At most {@code max} characters of {@code text}, cut at the end (or start) with an ellipsis. */
        private static String clip(String text, int max, boolean keepStart) {
            if (text.length() <= max) return text;
            return keepStart ? text.substring(0, max) + "…" : "…" + text.substring(text.length() - max);
        }
    }
}
