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
package org.icepdf.fx.view;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.NamedAction;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.search.SearchTerm;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A JavaFX control that displays a PDF {@link Document} rendered by the ICEpdf core.
 * <p>
 * Drop it into any scene graph and drive it through properties:
 * <pre>{@code
 * Document document = new Document();
 * document.setFile("report.pdf");
 * PdfView view = new PdfView();
 * view.setDocument(document);
 * view.setFitMode(FitMode.WIDTH);
 * }</pre>
 * Pages are rendered off the FX thread by Java2D into tiles sized to the screen, not the zoom, so
 * memory stays bounded at any zoom up to {@link #MAX_ZOOM}.  Only visible pages have nodes.  The
 * control does not own the document: the caller disposes it after removing it from the view.
 * <p>
 * Styleable with the {@code pdf-view} style class.
 */
public class PdfView extends Control {

    public static final double MIN_ZOOM = 0.05;
    public static final double MAX_ZOOM = 64;
    private static final double[] ZOOM_STEPS = {0.05, 0.1, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4, 6, 8, 12, 16,
            24, 32, 40, 48, 64};

    private final ObjectProperty<Document> document = new SimpleObjectProperty<>(this, "document");
    private final IntegerProperty currentPageIndex = new SimpleIntegerProperty(this, "currentPageIndex", 0) {
        @Override
        public void set(int value) {
            super.set(Math.max(0, Math.min(value, Math.max(0, getPageCount() - 1))));
        }
    };
    private final DoubleProperty zoom = new SimpleDoubleProperty(this, "zoom", 1) {
        @Override
        public void set(double value) {
            super.set(Double.isNaN(value) ? 1 : Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value)));
        }
    };
    private final DoubleProperty rotation = new SimpleDoubleProperty(this, "rotation", 0) {
        @Override
        public void set(double value) {
            double normalised = value % 360;
            super.set(normalised < 0 ? normalised + 360 : normalised);
        }
    };
    private final ObjectProperty<ViewMode> viewMode =
            new SimpleObjectProperty<>(this, "viewMode", ViewMode.CONTINUOUS);
    private final BooleanProperty coverPage = new SimpleBooleanProperty(this, "coverPage", false);
    private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(this, "fitMode", FitMode.NONE);
    private final DoubleProperty pageGap = new SimpleDoubleProperty(this, "pageGap", 8);
    private final IntegerProperty pageBoundary =
            new SimpleIntegerProperty(this, "pageBoundary", Page.BOUNDARY_CROPBOX);
    private final BooleanProperty paintAnnotations = new SimpleBooleanProperty(this, "paintAnnotations", true);
    private final ObjectProperty<PageOverlayFactory> pageOverlayFactory =
            new SimpleObjectProperty<>(this, "pageOverlayFactory");
    private final ReadOnlyIntegerWrapper pageCount = new ReadOnlyIntegerWrapper(this, "pageCount", 0);
    private final ReadOnlyBooleanWrapper rendering = new ReadOnlyBooleanWrapper(this, "rendering", false);
    // search: hits arrive progressively from a worker; hitsByPage indexes them for drawing.
    private final ObservableList<SearchHit> searchHits = FXCollections.observableArrayList();
    private final ObservableList<SearchHit> searchHitsView = FXCollections.unmodifiableObservableList(searchHits);
    private final Map<Integer, List<SearchHit>> hitsByPage = new HashMap<>();
    private final ReadOnlyIntegerWrapper currentSearchHitIndex =
            new ReadOnlyIntegerWrapper(this, "currentSearchHitIndex", -1);
    private final ReadOnlyBooleanWrapper searching = new ReadOnlyBooleanWrapper(this, "searching", false);
    private final ReadOnlyDoubleWrapper searchProgress = new ReadOnlyDoubleWrapper(this, "searchProgress", 0);
    private Thread searchThread;
    private int searchGeneration;
    private boolean searchAutoSelect;
    private int searchStartPage;

    private final ReadOnlyObjectWrapper<Annotation> selectedAnnotation =
            new ReadOnlyObjectWrapper<>(this, "selectedAnnotation");
    private final ObjectProperty<Consumer<AnnotationActionEvent>> onAnnotationAction =
            new SimpleObjectProperty<>(this, "onAnnotationAction");
    private final AnnotationEdits.History history = new AnnotationEdits.History();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo", false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo", false);

    private final ObjectProperty<DocumentSelection> textSelection =
            new SimpleObjectProperty<>(this, "textSelection");
    private final ObjectProperty<ToolMode> toolMode = new SimpleObjectProperty<>(this, "toolMode",
            ToolMode.TEXT_SELECT) {
        @Override
        public void set(ToolMode value) {
            super.set(value == null ? ToolMode.TEXT_SELECT : value);
        }
    };

    public PdfView() {
        getStyleClass().add("pdf-view");
        setFocusTraversable(true);
        document.addListener((obs, old, doc) -> {
            clearSearch();
            selectedAnnotation.set(null);
            history.clear();
            updateHistoryState();
            setTextSelection(null);
            pageCount.set(doc != null ? doc.getNumberOfPages() : 0);
            setCurrentPageIndex(0);
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PdfViewSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return PdfView.class.getResource("pdf-view.css").toExternalForm();
    }

    // ---- actions --------------------------------------------------------------------------

    /** Next step up the zoom ladder; leaves fit mode. */
    public void zoomIn() {
        setFitMode(FitMode.NONE);
        double z = getZoom();
        for (double step : ZOOM_STEPS) {
            if (step > z + 1e-6) {
                setZoom(step);
                return;
            }
        }
        setZoom(MAX_ZOOM);
    }

    /** Next step down the zoom ladder; leaves fit mode. */
    public void zoomOut() {
        setFitMode(FitMode.NONE);
        double z = getZoom();
        for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
            if (ZOOM_STEPS[i] < z - 1e-6) {
                setZoom(ZOOM_STEPS[i]);
                return;
            }
        }
        setZoom(MIN_ZOOM);
    }

    public void rotateClockwise() {
        setRotation(getRotation() + 90);
    }

    public void rotateCounterClockwise() {
        setRotation(getRotation() - 90);
    }

    /**
     * Scrolls the viewport by logical px (positive = right/down), clamped to the document.  Does
     * nothing before the control has a skin.
     */
    public void scrollBy(double dx, double dy) {
        if (getSkin() instanceof PdfViewSkin skin) skin.scrollBy(dx, dy);
    }

    /**
     * The page and PDF user-space point under a point in this control's coordinates (for example a
     * mouse event's {@code getX()/getY()}), or empty over the gaps between pages or before the
     * control is shown.
     */
    public Optional<PagePoint> pageAt(double x, double y) {
        return getSkin() instanceof PdfViewSkin skin ? Optional.ofNullable(skin.pageAt(x, y)) : Optional.empty();
    }

    /**
     * Scrolls the least distance needed to bring a page point comfortably into view (e.g. a caret or
     * search hit).  In single-page and facing modes the point's page must be the one shown; set
     * {@link #currentPageIndexProperty()} first.
     */
    public void ensureVisible(PagePoint point) {
        if (point != null && getSkin() instanceof PdfViewSkin skin) skin.ensureVisible(point, 48);
    }

    // ---- annotations ----------------------------------------------------------------------

    /** The annotation selected for editing (outline and handles), or null. */
    public final ReadOnlyObjectProperty<Annotation> selectedAnnotationProperty() {
        return selectedAnnotation.getReadOnlyProperty();
    }

    public final Annotation getSelectedAnnotation() {
        return selectedAnnotation.get();
    }

    /** Selects an annotation as a click would; null clears.  Popups and form widgets aren't selectable. */
    public void selectAnnotation(Annotation annotation) {
        selectedAnnotation.set(annotation);
    }

    public void clearAnnotationSelection() {
        selectedAnnotation.set(null);
    }

    /**
     * The topmost visible annotation under a point in this control's coordinates (popups and form
     * widgets excluded), or empty.  Only pages already rendered are searched.
     */
    public Optional<Annotation> annotationAt(double x, double y) {
        if (!(getSkin() instanceof PdfViewSkin skin)) return Optional.empty();
        PdfViewSkin.AnnotationHit hit = skin.annotationAt(x, y);
        return hit != null ? Optional.of(hit.annotation()) : Optional.empty();
    }

    /**
     * Opens or closes a markup annotation's popup note; the state is saved in the document (/Open).
     * No-op for an annotation without a popup.
     */
    public void setPopupOpen(MarkupAnnotation markup, boolean open) {
        PopupAnnotation popup = markup != null ? markup.getPopupAnnotation() : null;
        if (popup == null || !(getSkin() instanceof PdfViewSkin skin)) return;
        PdfViewSkin.AnnotationHit hit = skin.hitOf(markup);
        if (hit == null) return;
        Page page = getDocument().getPageTree().getPage(hit.pageIndex());
        skin.annotationLocker().withAnnotationLock(hit.pageIndex(), () -> {
            popup.setOpen(open);
            page.updateAnnotation(popup);
        });
        skin.refreshAnnotationChrome();
        skin.requestRefresh();
    }

    /** Deletes the selected annotation (and its popup); undoable.  No-op if none or it's locked. */
    public void deleteSelectedAnnotation() {
        if (!(getSkin() instanceof PdfViewSkin skin)) return;
        PdfViewSkin.AnnotationHit hit = skin.selectedHit();
        if (hit == null || !PdfViewSkin.isEditable(hit.annotation())) return;
        Page page = getDocument().getPageTree().getPage(hit.pageIndex());
        recordEdit(AnnotationEdits.delete(skin.annotationLocker(), page, hit.pageIndex(), hit.annotation()));
        clearAnnotationSelection();
    }

    /** Undoes the last annotation edit (move, resize, delete, add). */
    public void undo() {
        afterHistory(history.undo());
    }

    /** Redoes the last undone annotation edit. */
    public void redo() {
        afterHistory(history.redo());
    }

    public final ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public final ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    /** Records an edit already applied; re-renders that page's annotation layers. */
    void recordEdit(AnnotationEdits.Edit edit) {
        if (edit == null) return;
        history.push(edit);
        afterHistory(edit);
    }

    private void afterHistory(AnnotationEdits.Edit edit) {
        if (edit != null && getSkin() instanceof PdfViewSkin skin) {
            skin.bumpAnnotationGeneration(edit.pageIndex());
            skin.refreshAnnotationChrome();
        }
        updateHistoryState();
    }

    private void updateHistoryState() {
        canUndo.set(history.canUndo());
        canRedo.set(history.canRedo());
    }

    /**
     * Receives annotation actions the view doesn't perform itself (URI, launch, remote GoTo,
     * JavaScript, ...).  In-document navigation is handled by the view.  Null ignores them.
     */
    public final ObjectProperty<Consumer<AnnotationActionEvent>> onAnnotationActionProperty() {
        return onAnnotationAction;
    }

    public final void setOnAnnotationAction(Consumer<AnnotationActionEvent> handler) {
        onAnnotationAction.set(handler);
    }

    public final Consumer<AnnotationActionEvent> getOnAnnotationAction() {
        return onAnnotationAction.get();
    }

    /**
     * Performs an annotation's action, as a click on it does: GoTo destinations and the page named
     * actions navigate; anything else goes to {@link #onAnnotationActionProperty()}.  A link with a
     * /Dest and no action navigates to it.
     */
    public void performAnnotationAction(Annotation annotation) {
        if (annotation == null) return;
        Action action = annotation.getAction();
        if (action instanceof GoToAction goTo) {
            navigateTo(goTo.getDestination());
        } else if (action instanceof NamedAction named && named.getNamedAction() != null) {
            Name name = named.getNamedAction();
            if (NamedAction.FIRST_PAGE_KEY.equals(name)) setCurrentPageIndex(0);
            else if (NamedAction.LAST_PAGE_KEY.equals(name)) setCurrentPageIndex(getPageCount() - 1);
            else if (NamedAction.NEXT_PAGE_KEY.equals(name)) setCurrentPageIndex(getCurrentPageIndex() + 1);
            else if (NamedAction.PREV_PAGE_KEY.equals(name)) setCurrentPageIndex(getCurrentPageIndex() - 1);
            else dispatchAction(annotation, action);
        } else if (action != null) {
            dispatchAction(annotation, action);
        } else if (annotation instanceof LinkAnnotation link && link.getDestination() != null) {
            navigateTo(link.getDestination());
        }
    }

    private void dispatchAction(Annotation annotation, Action action) {
        Consumer<AnnotationActionEvent> handler = getOnAnnotationAction();
        if (handler != null) handler.accept(new AnnotationActionEvent(annotation, action));
    }

    /**
     * Shows a destination: its page, and its top/left when the destination gives them (otherwise the
     * page's top).  Unresolvable destinations are ignored.
     */
    public void navigateTo(Destination destination) {
        Document doc = getDocument();
        if (destination == null || doc == null || destination.getPageReference() == null) return;
        int page = doc.getPageTree().getPageNumber(destination.getPageReference());
        if (page < 0 || page >= getPageCount()) return;
        setCurrentPageIndex(page);
        if (destination.getTop() != null && getSkin() instanceof PdfViewSkin skin) {
            Float left = destination.getLeft();
            skin.alignTop(new PagePoint(page, left != null ? left : 0, destination.getTop()));
        }
    }

    // ---- search ---------------------------------------------------------------------------

    /** Case-insensitive search for a phrase; see {@link #search(SearchTerm...)}. */
    public void search(String text) {
        search(new SearchTerm(text, null, false, false, false));
    }

    /**
     * Searches the whole document on a background thread, replacing any previous search.  Hits
     * appear in {@link #getSearchHits()} page by page as they are found and are highlighted on the
     * pages; the first hit at or after the current page is selected and scrolled into view as soon
     * as it is found.  Every page is loaded to be searched, so a long document takes a while; watch
     * {@link #searchingProperty()} and {@link #searchProgressProperty()}.
     */
    public void search(SearchTerm... terms) {
        clearSearch();
        Document doc = getDocument();
        List<SearchTerm> list = new ArrayList<>();
        for (SearchTerm term : terms) {
            if (term != null && term.getTerm() != null && !term.getTerm().isEmpty()) list.add(term);
        }
        if (doc == null || list.isEmpty()) return;
        int generation = searchGeneration;
        int pages = getPageCount();
        searchAutoSelect = true;
        searchStartPage = getCurrentPageIndex();
        searching.set(true);
        searchThread = new Thread(() -> DocumentSearch.run(page -> {
            try {
                PageText text = doc.getPageViewText(page);
                return text != null ? text.getTextSequence() : null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }, list, pages, () -> Thread.currentThread().isInterrupted(), (page, hits, done, total) ->
                Platform.runLater(() -> {
                    if (generation != searchGeneration) return;
                    if (!hits.isEmpty()) {
                        hitsByPage.put(page, List.copyOf(hits));
                        searchHits.addAll(hits);
                    }
                    searchProgress.set(done / (double) total);
                    if (done == total) searching.set(false);
                    autoSelectSearchHit(done == total);
                })), "icepdf-fx-search");
        searchThread.setDaemon(true);
        searchThread.setPriority(Thread.NORM_PRIORITY - 1);
        searchThread.start();
    }

    /** Selects the first hit at or after the page the search started from, once one is known. */
    private void autoSelectSearchHit(boolean finished) {
        if (!searchAutoSelect || searchHits.isEmpty()) return;
        for (int i = 0; i < searchHits.size(); i++) {
            if (searchHits.get(i).pageIndex() >= searchStartPage) {
                searchAutoSelect = false;
                selectSearchHit(i);
                return;
            }
        }
        if (finished) {
            searchAutoSelect = false;
            selectSearchHit(0);
        }
    }

    /** Stops any running search and removes all hits; the text selection is left alone. */
    public void clearSearch() {
        searchGeneration++;
        if (searchThread != null) {
            searchThread.interrupt();
            searchThread = null;
        }
        searchAutoSelect = false;
        hitsByPage.clear();
        searchHits.clear();
        currentSearchHitIndex.set(-1);
        searching.set(false);
        searchProgress.set(0);
    }

    /** Selects and reveals the next hit, wrapping; the first hit from the current page if none is current. */
    public void nextSearchHit() {
        searchAutoSelect = false;
        selectSearchHit(DocumentSearch.next(searchHits, getCurrentSearchHitIndex(), getCurrentPageIndex()));
    }

    /** Selects and reveals the previous hit, wrapping. */
    public void previousSearchHit() {
        searchAutoSelect = false;
        selectSearchHit(DocumentSearch.previous(searchHits, getCurrentSearchHitIndex(), getCurrentPageIndex()));
    }

    /**
     * Makes hit {@code index} current: it becomes the text selection (so it can be copied) and is
     * scrolled into view, switching page first in the non-continuous modes.
     */
    public void selectSearchHit(int index) {
        if (index < 0 || index >= searchHits.size()) return;
        SearchHit hit = searchHits.get(index);
        currentSearchHitIndex.set(index);
        setTextSelection(DocumentSelection.of(hit.pageIndex(), hit.range().getStart(),
                hit.pageIndex(), hit.range().getEnd()));
        if (!getViewMode().isContinuous()) setCurrentPageIndex(hit.pageIndex());
        ensureVisible(hit.centre());
    }

    /** All hits of the current search in document order; grows while {@link #isSearching()}. */
    public final ObservableList<SearchHit> getSearchHits() {
        return searchHitsView;
    }

    /** Hits on one page, for drawing. */
    List<SearchHit> searchHitsOnPage(int pageIndex) {
        return hitsByPage.getOrDefault(pageIndex, List.of());
    }

    /** Index into {@link #getSearchHits()} of the current hit, or -1. */
    public final ReadOnlyIntegerProperty currentSearchHitIndexProperty() {
        return currentSearchHitIndex.getReadOnlyProperty();
    }

    public final int getCurrentSearchHitIndex() {
        return currentSearchHitIndex.get();
    }

    public final ReadOnlyBooleanProperty searchingProperty() {
        return searching.getReadOnlyProperty();
    }

    public final boolean isSearching() {
        return searching.get();
    }

    /** Fraction of pages searched, 0 to 1. */
    public final ReadOnlyDoubleProperty searchProgressProperty() {
        return searchProgress.getReadOnlyProperty();
    }

    public final double getSearchProgress() {
        return searchProgress.get();
    }

    // ---- selection ------------------------------------------------------------------------

    /** Selects every page's text; no-op without a document. */
    public void selectAll() {
        if (getPageCount() > 0) setTextSelection(DocumentSelection.all(getPageCount()));
    }

    public void clearSelection() {
        setTextSelection(null);
    }

    /**
     * The selected text, paragraph-formatted, extracted off the FX thread (pages the selection
     * spans are loaded as needed).  Completes with "" when nothing is selected or before the control
     * is shown; complete on a background thread.
     */
    public CompletableFuture<String> selectedTextAsync() {
        DocumentSelection selection = getTextSelection();
        if (selection == null || selection.isCollapsed() || !(getSkin() instanceof PdfViewSkin skin)) {
            return CompletableFuture.completedFuture("");
        }
        return skin.selectedTextAsync(selection);
    }

    /** Copies the selected text to the system clipboard once extracted; no-op with nothing selected. */
    public void copySelection() {
        selectedTextAsync().thenAccept(text -> {
            if (text.isEmpty()) return;
            Platform.runLater(() -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            });
        });
    }

    public void nextPage() {
        setCurrentPageIndex(getCurrentPageIndex() + (getViewMode().isFacing() ? 2 : 1));
    }

    public void previousPage() {
        setCurrentPageIndex(getCurrentPageIndex() - (getViewMode().isFacing() ? 2 : 1));
    }

    // ---- properties -----------------------------------------------------------------------

    /** The document shown; not disposed by the view. */
    public final ObjectProperty<Document> documentProperty() {
        return document;
    }

    public final Document getDocument() {
        return document.get();
    }

    public final void setDocument(Document value) {
        document.set(value);
    }

    /** Zero-based page the user is looking at; setting it scrolls that page into view. */
    public final IntegerProperty currentPageIndexProperty() {
        return currentPageIndex;
    }

    public final int getCurrentPageIndex() {
        return currentPageIndex.get();
    }

    public final void setCurrentPageIndex(int value) {
        currentPageIndex.set(value);
    }

    /** 1.0 = 100% (one PDF point per logical pixel), clamped to [MIN_ZOOM, MAX_ZOOM]. */
    public final DoubleProperty zoomProperty() {
        return zoom;
    }

    public final double getZoom() {
        return zoom.get();
    }

    public final void setZoom(double value) {
        zoom.set(value);
    }

    /** Clockwise user rotation in degrees, normalised to [0, 360); applied on top of the page's /Rotate. */
    public final DoubleProperty rotationProperty() {
        return rotation;
    }

    public final double getRotation() {
        return rotation.get();
    }

    public final void setRotation(double value) {
        rotation.set(value);
    }

    public final ObjectProperty<ViewMode> viewModeProperty() {
        return viewMode;
    }

    public final ViewMode getViewMode() {
        return viewMode.get();
    }

    public final void setViewMode(ViewMode value) {
        viewMode.set(value == null ? ViewMode.CONTINUOUS : value);
    }

    /** In facing modes, show the first page alone on the right, like a book cover. */
    public final BooleanProperty coverPageProperty() {
        return coverPage;
    }

    public final boolean isCoverPage() {
        return coverPage.get();
    }

    public final void setCoverPage(boolean value) {
        coverPage.set(value);
    }

    public final ObjectProperty<FitMode> fitModeProperty() {
        return fitMode;
    }

    public final FitMode getFitMode() {
        return fitMode.get();
    }

    public final void setFitMode(FitMode value) {
        fitMode.set(value == null ? FitMode.NONE : value);
    }

    /** Space between pages and around the document, logical px. */
    public final DoubleProperty pageGapProperty() {
        return pageGap;
    }

    public final double getPageGap() {
        return pageGap.get();
    }

    public final void setPageGap(double value) {
        pageGap.set(value);
    }

    /** Page box to display, one of the {@code Page.BOUNDARY_*} constants; crop box by default. */
    public final IntegerProperty pageBoundaryProperty() {
        return pageBoundary;
    }

    public final int getPageBoundary() {
        return pageBoundary.get();
    }

    public final void setPageBoundary(int value) {
        pageBoundary.set(value);
    }

    /**
     * Whether annotation appearances are drawn.  They render in their own layers above the page
     * content, so annotation changes never re-render the page.
     */
    public final BooleanProperty paintAnnotationsProperty() {
        return paintAnnotations;
    }

    public final boolean isPaintAnnotations() {
        return paintAnnotations.get();
    }

    public final void setPaintAnnotations(boolean value) {
        paintAnnotations.set(value);
    }

    /**
     * The text selection, null for none.  Offsets index each page's reading-order
     * {@code TextSequence}; set it to select programmatically (e.g. a search hit).
     */
    public final ObjectProperty<DocumentSelection> textSelectionProperty() {
        return textSelection;
    }

    public final DocumentSelection getTextSelection() {
        return textSelection.get();
    }

    public final void setTextSelection(DocumentSelection value) {
        textSelection.set(value);
    }

    /** What a primary-button drag does; text selection by default. */
    public final ObjectProperty<ToolMode> toolModeProperty() {
        return toolMode;
    }

    public final ToolMode getToolMode() {
        return toolMode.get();
    }

    public final void setToolMode(ToolMode value) {
        toolMode.set(value);
    }

    /** Supplies native overlay content per visible page; see {@link PageOverlayFactory}. */
    public final ObjectProperty<PageOverlayFactory> pageOverlayFactoryProperty() {
        return pageOverlayFactory;
    }

    public final PageOverlayFactory getPageOverlayFactory() {
        return pageOverlayFactory.get();
    }

    public final void setPageOverlayFactory(PageOverlayFactory value) {
        pageOverlayFactory.set(value);
    }

    public final ReadOnlyIntegerProperty pageCountProperty() {
        return pageCount.getReadOnlyProperty();
    }

    public final int getPageCount() {
        return pageCount.get();
    }

    /**
     * True while visible tiles are still being rendered (or a zoom is settling); false once the
     * viewport is fully painted at the current zoom.  Bind a busy indicator to it.
     */
    public final ReadOnlyBooleanProperty renderingProperty() {
        return rendering.getReadOnlyProperty();
    }

    public final boolean isRendering() {
        return rendering.get();
    }

    void setRendering(boolean value) {
        rendering.set(value);
    }
}
