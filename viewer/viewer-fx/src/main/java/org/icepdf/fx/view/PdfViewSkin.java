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

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SkinBase;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.render.NodeOwner;
import org.icepdf.fx.render.RenderService;
import org.icepdf.fx.view.DocumentLayout.PageSlot;
import org.icepdf.fx.view.internal.annot.AnnotationController;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.icepdf.fx.view.internal.annot.AnnotationGeometry;
import org.icepdf.fx.view.internal.annot.AnnotationHit;
import org.icepdf.fx.view.internal.annot.AnnotationUiLayer;
import org.icepdf.fx.view.internal.form.FormFields;
import org.icepdf.fx.view.internal.form.SignatureFields;
import org.icepdf.fx.view.internal.raster.CacheKey;
import org.icepdf.fx.view.internal.raster.PageTransforms;
import org.icepdf.fx.view.internal.raster.RasterBuffer;
import org.icepdf.fx.view.internal.raster.TileCache;
import org.icepdf.fx.view.internal.raster.TileGrid;
import org.icepdf.fx.view.internal.raster.TileRenderer;
import org.icepdf.fx.view.internal.raster.TileScheduler;
import org.icepdf.fx.view.internal.text.CaretNavigator;
import org.icepdf.fx.view.internal.text.PageTextLoader;
import org.icepdf.fx.view.internal.text.TextLayers;
import org.icepdf.fx.view.internal.tool.InputRouter;

import java.awt.geom.AffineTransform;
import java.util.*;

/**
 * Default skin for {@link PdfView}.
 * <p>
 * It owns the viewport: scroll position is a pair of doubles in document space (see
 * {@link DocumentLayout}), so the visible rectangle is always known exactly - the piece a JavaFX
 * ScrollPane hides and the reason the earlier experiments stalled.  Only pages intersecting the
 * viewport get a {@link PageLayer}, positioned relative to the viewport (never at absolute
 * document coordinates, which reach tens of millions of px at deep zoom and would lose precision in
 * Prism's float vertices).  Each visible page asks for just its on-screen tiles.
 */
final class PdfViewSkin extends SkinBase<PdfView> implements InputRouter.Host {

    private static final int PREVIEW_LONG_SIDE = 768;
    private static final Duration ZOOM_SETTLE = Duration.millis(150);
    // pages this far beyond the viewport (as a fraction of its height) get layers and previews.
    private static final double PREFETCH = 0.5;

    private final Pane viewport = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final ScrollBar vbar = new ScrollBar();
    private final ScrollBar hbar = new ScrollBar();

    // this view's place in the shared render pool's queue: ahead while its window is focused.  Coming
    // to the front re-ranks what is queued and refreshes, so anything retain() dropped is asked for.
    private final NodeOwner renderOwner = new NodeOwner(getSkinnable(), RenderService.shared(), this::scheduleRefresh,
            this::renderStats);
    // tiles on screen, the cache and the renderer behind them, and annotation tile bookkeeping.
    private final TileScheduler tiles;
    private final TileRenderer renderer;
    private final TileCache cache;
    // page text: loading, selection, search hits and the caret on each page.
    private final TextLayers text = new TextLayers(getSkinnable(),
            new PageTextLoader(RenderService.shared(), renderOwner, (page, sequence) -> onTextReady(page)),
            page -> getSkinnable().isShowSearchHits() ? getSkinnable().searchHitsOnPage(page) : List.of(), () -> this.sizes.count(), () -> this.layers.values());
    // what a screen reader sees: set in the constructor.
    private PdfViewAccessibility accessibility;
    private final Map<Integer, PageLayer> layers = new HashMap<>();
    // page layers (clipped to their pages) under the annotation UI layers (not clipped: popups and
    // chrome may extend past a page edge, as Swing's document-level popup layer allows).
    private final Group shadowGroup = new Group();
    private final Group pagesGroup = new Group();
    private final Group uiGroup = new Group();
    private final Map<Integer, AnnotationUiLayer> uiLayers = new HashMap<>();

    private Document document;
    // page sizes at zoom 1 for the current rotation and boundary; most are estimates until shown.
    private final PageSizes sizes = new PageSizes();
    private DocumentLayout layout;
    // the zoom/rotation the current layout was built for; the properties may already differ when
    // a listener runs, and anchoring needs the old values.
    private double layoutZoom = 1;
    private float layoutRotation;

    private double scrollX;
    private double scrollY;
    // set by dispose: work queued for later (a refresh) finds the skin gone and does nothing.
    private boolean disposed;
    // a Page Up/Down scroll in flight, and where it ends.
    private AnimationTimer pageScroll;
    private double pageScrollTargetY;
    private static final long PAGE_SCROLL_NANOS = 150_000_000L;
    private double viewportW;
    private double viewportH;
    private double outputScale = 1;

    private boolean zoomSettling;
    private final PauseTransition zoomSettle = new PauseTransition(ZOOM_SETTLE);
    // a view out of the scene (a background tab) gives its rasters back after this long; the grace
    // covers a view moved between parents (full screen) so it doesn't repaint.
    private static final Duration RELEASE_HIDDEN = Duration.seconds(3);
    private final PauseTransition releaseHidden = new PauseTransition(RELEASE_HIDDEN);
    private boolean refreshQueued;
    private boolean syncingBars;
    private boolean publishingPage;
    // the page last navigated to (go to page, search hit, caret, link...) and the scroll position it
    // was shown at: it stays the current page until the view scrolls, even when a neighbour is more
    // visible.  -1 when none.
    private int pinnedPage = -1;
    private double pinnedX, pinnedY;
    // viewport point a zoom should hold still (mouse position for wheel/pinch zoom), else centre.
    private Point2D zoomFocus;

    // what the view does with annotations, form fields and signatures, on the skin's primitives.
    private final AnnotationController annotations = new AnnotationController(this);
    private final FormFields fields = new FormFields(this);
    private final SignatureFields signatures = new SignatureFields(this);
    // mouse, wheel and key input, routed to the active tool's handler.
    private final InputRouter input = new InputRouter(this);

    private final ChangeListener<Number> outputScaleListener = (obs, o, n) -> updateOutputScale();
    private final javafx.event.EventHandler<ScrollEvent> scrollHandler = input::onScroll;
    private final javafx.event.EventHandler<ZoomEvent> pinchHandler = input::onPinch;
    private final javafx.event.EventHandler<KeyEvent> keyHandler = input::onKey;
    private final javafx.event.EventHandler<KeyEvent> keyReleasedHandler = input::onKeyReleased;
    private Window window;
    private Scene watchedScene;
    private final ChangeListener<Window> windowListener = (obs, o, n) -> attachWindow(n);

    // what the tools want the viewport's cursor to be; the wait cursor overrides it while waiting.
    private Cursor viewportCursor = Cursor.DEFAULT;
    private boolean firstPaintPending;

    PdfViewSkin(PdfView control) {
        super(control);
        tiles = new TileScheduler(RenderService.shared(), renderOwner, this::scheduleRefresh);
        renderer = tiles.renderer();
        cache = tiles.cache();
        tiles.setOnRenderFailure(failure -> control.renderTrouble(failure == TileScheduler.Failure.LOW_MEMORY));
        renderer.setPaintAnnotations(control.isPaintAnnotations());

        viewport.setClip(viewportClip);
        viewport.getChildren().addAll(shadowGroup, pagesGroup, uiGroup);
        viewport.setManaged(false);
        vbar.setOrientation(Orientation.VERTICAL);
        vbar.setUnitIncrement(InputRouter.SCROLL_UNIT);
        hbar.setUnitIncrement(InputRouter.SCROLL_UNIT);
        getChildren().addAll(viewport, vbar, hbar);

        vbar.valueProperty().addListener((obs, o, n) -> {
            if (!syncingBars) scrollTo(scrollX, n.doubleValue());
        });
        hbar.valueProperty().addListener((obs, o, n) -> {
            if (!syncingBars) scrollTo(n.doubleValue(), scrollY);
        });
        zoomSettle.setOnFinished(e -> {
            zoomSettling = false;
            refresh();
        });

        registerChangeListener(control.documentProperty(), o -> onDocument());
        registerChangeListener(control.busyProperty(), o -> applyCursor());
        registerChangeListener(control.zoomProperty(), o -> onZoom());
        registerChangeListener(control.rotationProperty(), o -> onRotation());
        registerChangeListener(control.pageBoundaryProperty(), o -> onRotation());
        registerChangeListener(control.viewModeProperty(), o -> onArrangement());
        registerChangeListener(control.coverPageProperty(), o -> onArrangement());
        registerChangeListener(control.pageGapProperty(), o -> onArrangement());
        registerChangeListener(control.fitModeProperty(), o -> onFitMode());
        registerChangeListener(control.currentPageIndexProperty(), o -> onCurrentPage());
        // after the page settles, so a caret placed for a screen reader doesn't fight the scroll.
        registerChangeListener(control.currentPageIndexProperty(),
                o -> Platform.runLater(() -> {
                    if (!disposed) accessibility.currentPageChanged();
                }));
        registerChangeListener(control.paintAnnotationsProperty(), o -> {
            renderer.setPaintAnnotations(control.isPaintAnnotations());
            resetRasters();
        });
        // the pages draw differently (a layer toggled): new tiles, the old ones shown until they come;
        // previews are rebuilt too.
        registerChangeListener(control.contentVersionProperty(), o -> {
            cache.removeIf(key -> key instanceof CacheKey.Preview);
            layers.values().forEach(PageLayer::clearPreview);
            refresh();
        });
        registerChangeListener(control.pageOverlayFactoryProperty(), o -> {
            layers.values().forEach(PageLayer::resetOverlay);
            refresh();
        });
        registerChangeListener(control.sceneProperty(), o -> {
            watchWindow();
            onSceneChanged();
        });
        releaseHidden.setOnFinished(e -> {
            if (getSkinnable().getScene() == null) releaseRasters();
        });
        registerChangeListener(control.toolModeProperty(), o -> {
            installTool();
            text.updateCaretBlink(false);
            refresh();
        });
        registerListChangeListener(control.getSearchHits(), c -> scheduleRefresh());
        registerChangeListener(control.showSearchHitsProperty(), o -> scheduleRefresh());
        registerListChangeListener(control.getSignatures(), c -> {
            signatures.statusesChanged(control.getSignatures());
            scheduleRefresh();
        });
        registerChangeListener(control.selectedAnnotationProperty(), o -> annotations.updateChrome());
        registerChangeListener(control.focusedFieldProperty(), o -> {
            annotations.updateChrome();
            fields.focusChanged();
        });
        registerChangeListener(control.highlightFormFieldsProperty(), o -> refresh());
        registerChangeListener(control.formFieldsEditableProperty(), o -> {
            if (!control.isFormFieldsEditable()) control.clearFieldFocus();
            refresh();
        });
        registerChangeListener(control.textSelectionProperty(), o -> {
            text.selectionChanged();
            refresh();
            accessibility.selectionChanged();
        });
        // focus only decides whether the caret shows: update the text layers, not the whole view - a full
        // refresh here ran in the middle of a click on a header control, as the view lost focus to it.
        registerChangeListener(control.focusedProperty(), o -> {
            if (control.isFocused()) accessibility.focusGained();
            text.updateCaretBlink(false);
            layers.values().forEach(layer -> text.update(layer, false));
        });

        control.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        control.addEventHandler(ZoomEvent.ZOOM, pinchHandler);
        control.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        control.addEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        // on the viewport, not the control, so dragging a scroll bar thumb isn't a page gesture.
        viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, input::onPress);
        viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, input::onDrag);
        viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, input::onRelease);
        viewport.addEventHandler(MouseEvent.MOUSE_MOVED, input::onMoved);
        viewport.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, input::onContextMenu);

        accessibility = new PdfViewAccessibility(this);
        installTool();
        watchWindow();
        onDocument();
    }

    @Override
    public void dispose() {
        disposed = true;
        stopPageScroll();
        if (watchedScene != null) watchedScene.windowProperty().removeListener(windowListener);
        if (window != null) window.outputScaleXProperty().removeListener(outputScaleListener);
        zoomSettle.stop();
        releaseHidden.stop();
        // the pool's threads are shared: cancelling this view's work is all there is to stop.
        renderOwner.dispose();
        tiles.dispose();
        text.dispose();
        getSkinnable().removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        getSkinnable().removeEventHandler(ZoomEvent.ZOOM, pinchHandler);
        getSkinnable().removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        getSkinnable().removeEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        input.dispose();
        super.dispose();
    }

    // ---- layout ---------------------------------------------------------------------------

    // Sizing is independent of content.  SkinBase's default derives the pref size from where the
    // children sit, and the horizontal bar sits below the viewport: each layout pass then grew the
    // pref height by a scroll bar, which a parent like BorderPane honoured, re-laying out without
    // end - thousands of refreshes rendering ever more "visible" pages until the heap was gone.
    // A document view, like a ScrollPane, takes the space it is given.
    private static final double PREF_WIDTH = 600;
    private static final double PREF_HEIGHT = 800;
    private static final double MIN_SIZE = 50;

    @Override
    protected double computePrefWidth(double height, double top, double right, double bottom, double left) {
        return PREF_WIDTH + left + right;
    }

    @Override
    protected double computePrefHeight(double width, double top, double right, double bottom, double left) {
        return PREF_HEIGHT + top + bottom;
    }

    @Override
    protected double computeMinWidth(double height, double top, double right, double bottom, double left) {
        return MIN_SIZE + left + right;
    }

    @Override
    protected double computeMinHeight(double width, double top, double right, double bottom, double left) {
        return MIN_SIZE + top + bottom;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        // the vertical bar is always shown so fit-width can't oscillate against its appearance.
        double vbarW = vbar.prefWidth(-1);
        boolean needH = layout != null && layout.getWidth() > w - vbarW + 0.5;
        double hbarH = needH ? hbar.prefHeight(-1) : 0;
        double vw = Math.max(0, w - vbarW);
        double vh = Math.max(0, h - hbarH);
        viewport.resizeRelocate(x, y, vw, vh);
        viewportClip.setWidth(vw);
        viewportClip.setHeight(vh);
        vbar.resizeRelocate(x + vw, y, vbarW, vh);
        hbar.setVisible(needH);
        hbar.resizeRelocate(x, y + vh, vw, hbarH);
        if (vw != viewportW || vh != viewportH) {
            boolean first = viewportW == 0 && viewportH == 0;
            int page = getSkinnable().getCurrentPageIndex();
            viewportW = vw;
            viewportH = vh;
            if (getSkinnable().getFitMode() != FitMode.NONE) {
                applyFit();
            }
            relayout(first ? null : captureAnchor(viewportW / 2, viewportH / 2));
            // a resize (a scroll bar coming or going included) re-fits; fit page keeps showing its page.
            if (!first && fitsCurrentPage()) showPage(page);
        }
    }

    private void onDocument() {
        fields.documentChanged();
        tiles.clearGenerations();
        document = getSkinnable().getDocument();
        // the wait cursor until refresh finds the first screenful painted.
        firstPaintPending = document != null;
        applyCursor();
        renderer.setDocument(document);
        text.documentChanged(document);
        accessibility.documentChanged();
        // drop the previous document's layout before anything refreshes: its slots index pages the
        // new document may not have.
        layout = null;
        scrollX = 0;
        scrollY = 0;
        resetRasters();
        loadUnitSizes(false);
        if (getSkinnable().getFitMode() != FitMode.NONE) applyFit();
        relayout(null);
    }

    private void onZoom() {
        Point2D focus = zoomFocus != null ? zoomFocus : new Point2D(viewportW / 2, viewportH / 2);
        zoomFocus = null;
        Anchor anchor = captureAnchor(focus.getX(), focus.getY());
        // hold re-rendering until the zoom stops changing; existing tiles are scaled meanwhile.  With
        // nothing on screen to scale (a document just opened, fitted), there is nothing to wait for.
        if (!layers.isEmpty()) {
            zoomSettling = true;
            zoomSettle.playFromStart();
        }
        relayout(anchor);
    }

    private void onRotation() {
        Anchor anchor = captureAnchor(viewportW / 2, viewportH / 2);
        loadUnitSizes(true);
        if (getSkinnable().getFitMode() != FitMode.NONE) applyFit();
        relayout(anchor);
    }

    private void onArrangement() {
        Anchor anchor = captureAnchor(viewportW / 2, viewportH / 2);
        if (getSkinnable().getFitMode() != FitMode.NONE) applyFit();
        relayout(anchor);
    }

    private void onCurrentPage() {
        if (publishingPage || layout == null) return;
        int page = getSkinnable().getCurrentPageIndex();
        if (!getSkinnable().getViewMode().isContinuous()) {
            if (getSkinnable().getFitMode() == FitMode.PAGE) applyFit();
            relayout(null);
            return;
        }
        showPage(page);
    }

    /** Scrolls a continuous layout so the page's top edge (and left, when wider than the viewport) shows. */
    private void showPage(int page) {
        PageSlot slot = layout.getSlot(page);
        if (slot != null) {
            double gap = getSkinnable().getPageGap();
            double x = layout.getWidth() > viewportW ? slot.x() - gap : scrollX;
            scrollTo(x, slot.y() - gap);
            pinCurrentPage(page);
        }
    }

    /** Core rotation is counter-clockwise; the control's property is clockwise. */
    private float coreRotation() {
        return (float) ((360 - getSkinnable().getRotation()) % 360);
    }

    /** Reads the page sizes for the current boundary and rotation (see {@link PageSizes#load}). */
    private void loadUnitSizes(boolean keepResolved) {
        int boundary = getSkinnable().getPageBoundary();
        float rotation = coreRotation();
        Document doc = document;
        sizes.load(doc != null ? doc.getNumberOfPages() : 0, i -> {
            Page page = doc.getPageTree().getPage(i);
            return page != null ? page.getSize(boundary, rotation, 1f) : null;
        }, keepResolved);
    }

    /**
     * Reads the real size of the estimated pages among {@code slots} (the ones about to show) and,
     * when one differs from its estimate, lays the document out again around what's on screen.
     *
     * @return true if the layout was rebuilt, so the slots are stale
     */
    private boolean resolveSizes(List<PageSlot> slots) {
        if (slots.stream().noneMatch(slot -> sizes.isEstimated(slot.pageIndex()))) return false;
        // the anchor is taken before the sizes change under it.
        Anchor anchor = captureAnchor(viewportW / 2, viewportH / 2);
        PageSizes.Resolution resolved = sizes.resolve(slots.stream().map(PageSlot::pageIndex).toList(),
                getSkinnable().getCurrentPageIndex());
        if (!resolved.changed()) return false;
        // a fit to the current page follows its real size; a continuous fit width doesn't re-fit
        // as each wider page scrolls in.
        PdfView control = getSkinnable();
        boolean continuousWidth = control.getFitMode() == FitMode.WIDTH && control.getViewMode().isContinuous();
        if (resolved.currentChanged() && control.getFitMode() != FitMode.NONE && !continuousWidth) applyFit();
        relayout(anchor);
        return true;
    }

    /** Rebuilds the layout for the current properties, then restores the anchor (or the top). */
    private void relayout(Anchor anchor) {
        PdfView control = getSkinnable();
        double zoom = control.getZoom();
        layout = new DocumentLayout(sizes.atZoom(zoom), control.getViewMode(), control.isCoverPage(),
                control.getCurrentPageIndex(), control.getPageGap(), viewportW, viewportH);
        layoutZoom = zoom;
        layoutRotation = coreRotation();
        if (anchor != null && !applyAnchor(anchor)) anchor = null;
        if (anchor == null && !control.getViewMode().isContinuous()) {
            scrollX = 0;
            scrollY = 0;
        }
        getSkinnable().requestLayout();
        scrollTo(scrollX, scrollY);
    }

    // ---- fit ------------------------------------------------------------------------------

    /**
     * Fit page means the whole current page in view.  The zoom change keeps the viewport centre still,
     * which in a continuous layout leaves the page partly scrolled off, so it is brought into view
     * after the fit (also when the zoom was already the fit zoom).  Fit width keeps the centre still.
     */
    private void onFitMode() {
        PdfView control = getSkinnable();
        int page = control.getCurrentPageIndex();
        applyFit();
        if (fitsCurrentPage()) showPage(page);
    }

    /** Fit page in a continuous layout, where the current page has to be scrolled into view. */
    private boolean fitsCurrentPage() {
        PdfView control = getSkinnable();
        return control.getFitMode() == FitMode.PAGE && control.getViewMode().isContinuous() && layout != null;
    }

    private void applyFit() {
        PdfView control = getSkinnable();
        double zoom = sizes.fitZoom(control.getFitMode(), control.getViewMode(), control.isCoverPage(),
                control.getCurrentPageIndex(), control.getPageGap(), viewportW, viewportH);
        if (!Double.isNaN(zoom)) control.setZoom(zoom);
    }

    // ---- anchoring ------------------------------------------------------------------------

    /** A PDF user-space point on a page that should stay under a viewport point across a relayout. */
    private record Anchor(int pageIndex, double userX, double userY, double viewportX, double viewportY) {
    }

    private Anchor captureAnchor(double vx, double vy) {
        if (layout == null || document == null) return null;
        double dx = scrollX + vx;
        double dy = scrollY + vy;
        List<PageSlot> hit = layout.slotsIntersecting(dx, dy, 1e-3, 1e-3);
        PageSlot slot = hit.isEmpty() ? layout.getSlot(layout.currentPage(scrollX, scrollY, viewportW, viewportH))
                : hit.get(0);
        if (slot == null) return null;
        Page page = document.getPageTree().getPage(slot.pageIndex());
        AffineTransform old = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(), layoutRotation,
                (float) layoutZoom);
        Point2D user = PageTransforms.viewToPage(old, dx - slot.x(), dy - slot.y());
        return user == null ? null : new Anchor(slot.pageIndex(), user.getX(), user.getY(), vx, vy);
    }

    private boolean applyAnchor(Anchor anchor) {
        PageSlot slot = layout.getSlot(anchor.pageIndex());
        if (slot == null) return false;
        Page page = document.getPageTree().getPage(anchor.pageIndex());
        AffineTransform now = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(), layoutRotation,
                (float) layoutZoom);
        Point2D view = PageTransforms.pageToView(now, anchor.userX(), anchor.userY());
        scrollX = slot.x() + view.getX() - anchor.viewportX();
        scrollY = slot.y() + view.getY() - anchor.viewportY();
        return true;
    }

    // ---- scrolling ------------------------------------------------------------------------

    @Override
    public void scrollBy(double dx, double dy) {
        scrollTo(scrollX + dx, scrollY + dy);
    }

    @Override
    public void scrollTo(double x, double y) {
        stopPageScroll();
        applyScroll(x, y);
    }

    /**
     * Page Up/Down in a continuous mode: onto the next page edge or a screen on (see
     * {@link DocumentLayout#pageDownTarget}), eased over a moment so the eye can follow the text.
     * A key pressed again mid-scroll steps on from where the first was going.
     */
    @Override
    public void scrollByPage(boolean down) {
        if (layout == null) return;
        double from = pageScroll != null ? pageScrollTargetY : scrollY;
        double gap = getSkinnable().getPageGap();
        double target = down ? layout.pageDownTarget(from, viewportH, gap) : layout.pageUpTarget(from, viewportH, gap);
        target = clamp(target, 0, Math.max(0, layout.getHeight() - viewportH));
        stopPageScroll();
        double start = scrollY;
        if (start == target) return;
        double end = target;
        pageScrollTargetY = end;
        pageScroll = new AnimationTimer() {
            private long begin = -1;

            @Override
            public void handle(long now) {
                if (begin < 0) begin = now;
                double t = Math.min(1, (now - begin) / (double) PAGE_SCROLL_NANOS);
                // ease out: quick to start, settling onto the target.
                applyScroll(scrollX, start + (end - start) * (1 - Math.pow(1 - t, 3)));
                if (t >= 1) stopPageScroll();
            }
        };
        pageScroll.start();
    }

    /** The current layout, or null before a document is laid out. */
    DocumentLayout layout() {
        return layout;
    }

    @Override
    public double maxScrollY() {
        return layout == null ? 0 : Math.max(0, layout.getHeight() - viewportH);
    }

    private void stopPageScroll() {
        if (pageScroll == null) return;
        pageScroll.stop();
        pageScroll = null;
    }

    private void applyScroll(double x, double y) {
        if (layout == null) return;
        scrollX = clamp(x, 0, Math.max(0, layout.getWidth() - viewportW));
        scrollY = clamp(y, 0, Math.max(0, layout.getHeight() - viewportH));
        syncBars();
        refresh();
    }

    private void syncBars() {
        syncingBars = true;
        try {
            setBar(vbar, layout.getHeight(), viewportH, scrollY);
            setBar(hbar, layout.getWidth(), viewportW, scrollX);
        } finally {
            syncingBars = false;
        }
    }

    private static void setBar(ScrollBar bar, double content, double visible, double value) {
        double max = Math.max(0, content - visible);
        bar.setMin(0);
        bar.setMax(max);
        bar.setVisibleAmount(content > 0 ? max * visible / content : 0);
        bar.setBlockIncrement(visible * 0.9);
        bar.setValue(value);
        bar.setDisable(max <= 0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- rendering ------------------------------------------------------------------------

    @Override
    public void scheduleRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        Platform.runLater(() -> {
            refreshQueued = false;
            // the view may have been closed (the skin disposed) while this waited.
            if (!disposed) refresh();
        });
    }

    /**
     * Reconciles the scene graph with the viewport: creates/positions/drops page layers, shows
     * cached tiles, requests missing ones, pins what's on screen and cancels what isn't.
     */
    private void refresh() {
        if (layout == null || document == null || viewportW <= 0 || viewportH <= 0) {
            clearLayers();
            getSkinnable().setRendering(false);
            return;
        }
        // out of the scene nothing is seen: render nothing (see releaseRasters).
        if (getSkinnable().getScene() == null) {
            getSkinnable().setRendering(false);
            return;
        }
        PdfView control = getSkinnable();
        CacheKey.Params params = new CacheKey.Params((float) layoutZoom, layoutRotation, outputScale,
                control.getPageBoundary(), control.getContentVersion());
        double band = viewportH * PREFETCH;
        List<PageSlot> slots = layout.slotsIntersecting(scrollX, scrollY - band, viewportW, viewportH + 2 * band);
        // relayout scrolls, which refreshes again with the new slots.
        if (resolveSizes(slots)) return;

        Set<Integer> keep = new HashSet<>();
        Set<CacheKey> wanted = new HashSet<>();
        boolean incomplete = false;
        for (PageSlot slot : slots) {
            int index = slot.pageIndex();
            keep.add(index);
            PageLayer layer = layers.get(index);
            if (layer == null) {
                layer = new PageLayer(index, document.getPageTree());
                layers.put(index, layer);
                shadowGroup.getChildren().add(layer.getShadow());
                pagesGroup.getChildren().add(layer);
            }
            layer.setParams(params, slot.width(), slot.height());
            layer.setLayoutX(snap(slot.x() - scrollX));
            layer.setLayoutY(snap(slot.y() - scrollY));
            layer.ensureOverlay(control.getPageOverlayFactory());
            AnnotationUiLayer ui = uiLayers.get(index);
            if (ui == null) {
                ui = new AnnotationUiLayer(index);
                uiLayers.put(index, ui);
                uiGroup.getChildren().add(ui);
            }
            ui.setLayoutX(layer.getLayoutX());
            ui.setLayoutY(layer.getLayoutY());
            ui.setPageToView(layer.getPageToView());
            annotations.updateChrome(ui);
            ui.setSignatureBadges(signatures.badges(layer.getPage()), signatures::clicked);
            layer.setFieldHighlights(control.isHighlightFormFields() && fields.editable()
                    ? fields.fieldRects(layer.getPage()) : List.of());
            if (layer.getPage() != null && layer.getPage().isInitiated() && control.isPaintAnnotations()) {
                ui.updatePopups(layer.getPage().getAnnotations(), layoutZoom, annotations.popupListener,
                        control.isAnnotationEditingAllowed());
            } else {
                ui.updatePopups(null, layoutZoom, annotations.popupListener, false);
            }

            CacheKey.Preview previewKey = new CacheKey.Preview(index, params.boundary());
            wanted.add(previewKey);
            if (!layer.hasPreview()) {
                RasterBuffer preview = cache.get(previewKey);
                if (preview != null) {
                    layer.setPreview(preview, TileRenderer.previewZoom(layer.getPage(), params.boundary(),
                            PREVIEW_LONG_SIDE));
                } else {
                    renderer.requestPreview(index, params.boundary(), PREVIEW_LONG_SIDE);
                }
            }
            boolean pageComplete = updateTiles(layer, slot, params, wanted);
            incomplete |= !pageComplete;
            if (pageComplete && ui.hasProxy() && (drag == null || drag.hit().pageIndex() != index)) {
                ui.clearProxy();
                ui.refresh();
            }
            text.update(layer, slot.y() < scrollY + viewportH && slot.y() + slot.height() > scrollY);
        }
        layers.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            shadowGroup.getChildren().remove(e.getValue().getShadow());
            pagesGroup.getChildren().remove(e.getValue());
            return true;
        });
        uiLayers.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            uiGroup.getChildren().remove(e.getValue());
            return true;
        });
        text.retain(keep::contains);
        tiles.retain(wanted);
        control.setRendering(zoomSettling || incomplete);
        if (firstPaintPending && !zoomSettling && !incomplete) {
            firstPaintPending = false;
            applyCursor();
        }
        publishCurrentPage();
    }

    /** @return true when every visible tile of the page is showing (or has failed) */
    private boolean updateTiles(PageLayer layer, PageSlot slot, CacheKey.Params params, Set<CacheKey> wanted) {
        // the part of the page inside the viewport, in page-local logical px.
        double x0 = Math.max(slot.x(), scrollX) - slot.x();
        double y0 = Math.max(slot.y(), scrollY) - slot.y();
        double x1 = Math.min(slot.maxX(), scrollX + viewportW) - slot.x();
        double y1 = Math.min(slot.maxY(), scrollY + viewportH) - slot.y();
        boolean annotationsOn = getSkinnable().isPaintAnnotations() && !TileRenderer.SINGLE_PASS_ANNOTATIONS;
        return tiles.update(layer, slot.pageIndex(), slot.width(), slot.height(), x0, y0, x1, y1, params, wanted,
                zoomSettling, annotationsOn);
    }

    // ---- annotation move/resize -------------------------------------------------------------

    /** An annotation being moved (handle -1) or resized, with its view bounds when the drag began. */
    private record Drag(AnnotationHit hit, int handle, java.awt.geom.Rectangle2D from) {
    }

    private Drag drag;

    /** View bounds of an annotation in its page's view space, or null if the page isn't visible. */
    java.awt.geom.Rectangle2D annotationViewBounds(AnnotationHit hit) {
        AnnotationUiLayer ui = uiLayers.get(hit.pageIndex());
        return ui != null ? ui.viewBounds(hit.annotation()) : null;
    }

    /** Converts a viewport point into a page's view space. */
    @Override
    public Point2D toPageView(int pageIndex, double vx, double vy) {
        PageSlot slot = layout != null ? layout.getSlot(pageIndex) : null;
        return slot == null ? null : new Point2D(scrollX + vx - slot.x(), scrollY + vy - slot.y());
    }

    /** The selected annotation's handle under a viewport point, or -1. */
    @Override
    public int handleAtViewport(double vx, double vy) {
        Annotation selected = getSkinnable().getSelectedAnnotation();
        if (selected == null) return -1;
        for (AnnotationUiLayer ui : uiLayers.values()) {
            Point2D p = toPageView(ui.getPageIndex(), vx, vy);
            if (p != null) {
                int handle = ui.handleAt(p.getX(), p.getY());
                if (handle >= 0) return handle;
            }
        }
        return -1;
    }

    void requestRefresh() {
        scheduleRefresh();
    }

    /**
     * Starts a move (handle -1) or resize: the annotation leaves its tiles and a live node rendered
     * from it follows the pointer until the drag ends.
     */
    @Override
    public void beginAnnotationDrag(AnnotationHit hit, int handle) {
        java.awt.geom.Rectangle2D from = annotationViewBounds(hit);
        AnnotationUiLayer ui = uiLayers.get(hit.pageIndex());
        PageLayer layer = layers.get(hit.pageIndex());
        if (from == null || ui == null || layer == null) return;
        drag = new Drag(hit, handle, from);
        // render the live node first; the tiles drop the annotation only once it can show.
        CacheKey.Params params = layer.getParams();
        double scale = params.scale();
        PageSlot slot = layout.getSlot(hit.pageIndex());
        double vx0 = Math.max(from.getMinX() - 8, scrollX - slot.x());
        double vy0 = Math.max(from.getMinY() - 8, scrollY - slot.y());
        double vx1 = Math.min(from.getMaxX() + 8, scrollX + viewportW - slot.x());
        double vy1 = Math.min(from.getMaxY() + 8, scrollY + viewportH - slot.y());
        if (vx1 <= vx0 || vy1 <= vy0) return;
        TileGrid.Region region = new TileGrid.Region((int) Math.floor(vx0 * scale), (int) Math.floor(vy0 * scale),
                (int) Math.ceil((vx1 - vx0) * scale) + 1, (int) Math.ceil((vy1 - vy0) * scale) + 1);
        Drag started = drag;
        renderer.requestAnnotationProxy(hit.pageIndex(), params, hit.annotation(), region, buffer -> {
            if (disposed || drag != started) return;
            ui.setProxy(buffer, region.x(), region.y(), scale, from, TileRenderer.multiplies(hit.annotation()));
            tiles.setExcluded(hit.pageIndex(), hit.annotation(), true);
            bumpAnnotationGeneration(hit.pageIndex());
        });
    }

    /** Follows the pointer: (dx, dy) is the total view-space movement since the drag began. */
    @Override
    public void updateAnnotationDrag(double dx, double dy) {
        if (drag == null) return;
        AnnotationUiLayer ui = uiLayers.get(drag.hit().pageIndex());
        PageSlot slot = layout.getSlot(drag.hit().pageIndex());
        if (ui == null || slot == null) return;
        java.awt.geom.Rectangle2D page = new java.awt.geom.Rectangle2D.Double(0, 0, slot.width(), slot.height());
        ui.setDragBounds(drag.handle() < 0 ? AnnotationGeometry.move(drag.from(), dx, dy, page)
                : AnnotationGeometry.resize(drag.from(), drag.handle(), dx, dy, page));
    }

    /** Ends the drag; commits it through core unless {@code cancel}, and returns the edit (or null). */
    @Override
    public AnnotationEdits.Edit endAnnotationDrag(double dx, double dy, boolean cancel) {
        Drag ended = drag;
        drag = null;
        if (ended == null) return null;
        int pageIndex = ended.hit().pageIndex();
        AnnotationUiLayer ui = uiLayers.get(pageIndex);
        AnnotationEdits.Edit edit = null;
        PageSlot slot = layout.getSlot(pageIndex);
        if (!cancel && slot != null && (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5)) {
            java.awt.geom.Rectangle2D page = new java.awt.geom.Rectangle2D.Double(0, 0, slot.width(), slot.height());
            java.awt.geom.Rectangle2D to = ended.handle() < 0 ? AnnotationGeometry.move(ended.from(), dx, dy, page)
                    : AnnotationGeometry.resize(ended.from(), ended.handle(), dx, dy, page);
            Page p = document.getPageTree().getPage(pageIndex);
            AffineTransform toPageSpace = p.getToPageSpaceTransform(getSkinnable().getPageBoundary(),
                    layoutRotation, (float) layoutZoom);
            // the gesture's effective delta (after clamping), as Swing passes the mouse delta.
            double edx = to.getX() - ended.from().getX();
            double edy = to.getY() - ended.from().getY();
            edit = AnnotationEdits.reshape(renderer::withAnnotationLock, p, pageIndex, ended.hit().annotation(),
                    to, edx, edy, toPageSpace);
        }
        tiles.setExcluded(pageIndex, ended.hit().annotation(), false);
        if (ui != null) {
            ui.setDragBounds(null);
            // keep the live node until the re-rendered tiles cover it (see refresh), so no flash.
            if (!ui.hasProxy()) ui.refresh();
        }
        bumpAnnotationGeneration(pageIndex);
        return edit;
    }

    // ---- controllers and what they ask of the skin ----------------------------------------------

    @Override
    public AnnotationController annotations() {
        return annotations;
    }

    @Override
    public FormFields fields() {
        return fields;
    }

    @Override
    public SignatureFields signatures() {
        return signatures;
    }

    @Override
    public Document document() {
        return document;
    }

    @Override
    public double zoom() {
        return layoutZoom;
    }

    @Override
    public void addSignatureStatus(org.icepdf.fx.signature.SignatureStatus status) {
        getSkinnable().addSignatureStatus(status);
    }

    @Override
    public void setExcluded(int pageIndex, Annotation annotation, boolean exclude) {
        tiles.setExcluded(pageIndex, annotation, exclude);
        bumpAnnotationGeneration(pageIndex);
    }

    @Override
    public void reveal(PagePoint point) {
        if (!getSkinnable().getViewMode().isContinuous() && layout != null && layout.getSlot(point.pageIndex()) == null) {
            getSkinnable().setCurrentPageIndex(point.pageIndex());
        }
        ensureVisible(point, 48);
    }

    @Override
    public java.util.Collection<AnnotationUiLayer> uiLayers() {
        return uiLayers.values();
    }

    @Override
    public AffineTransform pageToView(Page page) {
        return PageTransforms.pageToView(page, getSkinnable().getPageBoundary(), layoutRotation, (float) layoutZoom);
    }

    @Override
    public AffineTransform toPageSpace(Page page) {
        return page.getToPageSpaceTransform(getSkinnable().getPageBoundary(), layoutRotation, (float) layoutZoom);
    }

    @Override
    public AnnotationEdits.Locker annotationLocker() {
        return renderer::withAnnotationLock;
    }

    @Override
    public void refreshAnnotationChrome() {
        uiLayers.values().forEach(ui -> {
            annotations.updateChrome(ui);
            ui.refresh();
        });
    }

    /** Invalidates a page's annotation layers: they re-render; page content tiles are untouched. */
    void bumpAnnotationGeneration(int pageIndex) {
        tiles.bumpGeneration(pageIndex);
        scheduleRefresh();
    }

    // ---- current page, text and caret -------------------------------------------------------

    private void publishCurrentPage() {
        if (!getSkinnable().getViewMode().isContinuous()) return;
        if (pinnedPage >= 0) {
            if (pinStillHolds()) {
                publish(pinnedPage);
                return;
            }
            pinnedPage = -1;
        }
        publish(layout.currentPage(scrollX, scrollY, viewportW, viewportH));
    }

    /**
     * Makes a page the current one because the view was taken to it, not scrolled past it: it stays
     * current, though a neighbour may show more of itself, until the view scrolls.
     */
    private void pinCurrentPage(int page) {
        if (layout == null || !getSkinnable().getViewMode().isContinuous()) return;
        pinnedPage = page;
        pinnedX = scrollX;
        pinnedY = scrollY;
        publish(page);
    }

    private boolean pinStillHolds() {
        PageSlot slot = layout.getSlot(pinnedPage);
        return slot != null && Math.abs(scrollX - pinnedX) < 0.5 && Math.abs(scrollY - pinnedY) < 0.5
                && slot.maxY() > scrollY && slot.y() < scrollY + viewportH
                && slot.maxX() > scrollX && slot.x() < scrollX + viewportW;
    }

    private void publish(int current) {
        if (current < 0 || current == getSkinnable().getCurrentPageIndex()) return;
        publishingPage = true;
        try {
            getSkinnable().setCurrentPageIndex(current);
        } finally {
            publishingPage = false;
        }
    }

    private double snap(double v) {
        return Math.round(v * outputScale) / outputScale;
    }

    /** The page's loaded text, or null if it isn't loaded (or has none). */
    @Override
    public TextSequence textSequence(int pageIndex) {
        return text.sequence(pageIndex);
    }

    /** True once the page's text is loaded, even if it has none. */
    boolean isTextLoaded(int pageIndex) {
        return text.isLoaded(pageIndex);
    }

    /** Loads a page's text, keeping it while off-screen (for the screen reader's page). */
    void requestText(int pageIndex) {
        text.requestKept(pageIndex);
    }

    private void onTextReady(int pageIndex) {
        scheduleRefresh();
        if (accessibility != null) accessibility.textReady(pageIndex);
    }

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        Object value = accessibility.query(attribute, parameters);
        return value != null ? value : super.queryAccessibleAttribute(attribute, parameters);
    }

    @Override
    protected void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (!accessibility.execute(action, parameters)) super.executeAccessibleAction(action, parameters);
    }

    /** A rectangle in a page's user space, in screen coordinates; null if the page isn't laid out. */
    Bounds pageRectToScreen(int pageIndex, java.awt.geom.Rectangle2D rect) {
        if (layout == null || document == null) return null;
        PageSlot slot = layout.getSlot(pageIndex);
        if (slot == null) return null;
        java.awt.geom.Rectangle2D view = pageToView(document.getPageTree().getPage(pageIndex))
                .createTransformedShape(rect).getBounds2D();
        return viewport.localToScreen(new BoundingBox(slot.x() + view.getX() - scrollX,
                slot.y() + view.getY() - scrollY, view.getWidth(), view.getHeight()));
    }

    /** Keyboard caret movement over the loaded text; rebuilt per document. */
    @Override
    public CaretNavigator caretNavigator() {
        return text.caretNavigator();
    }

    /** Text of a selection, extracted off the FX thread. */
    java.util.concurrent.CompletableFuture<String> selectedTextAsync(DocumentSelection selection) {
        return text.loader().extractAsync(selection);
    }

    /**
     * Brings the selection's focus caret into view, switching page first in the non-continuous
     * modes.  Needs the focus page's text to be loaded, which it is after any caret move.
     */
    @Override
    public void revealCaret() {
        DocumentSelection selection = getSkinnable().getTextSelection();
        if (selection == null) return;
        int page = selection.getFocusPage();
        if (!getSkinnable().getViewMode().isContinuous() && layout != null && layout.getSlot(page) == null) {
            getSkinnable().setCurrentPageIndex(page);
        }
        TextSequence sequence = text.sequence(page);
        if (sequence == null) return;
        java.awt.geom.Rectangle2D.Double caret = TextLayers.caretRect(sequence, selection);
        ensureVisible(new PagePoint(page, caret.getCenterX(), caret.getCenterY()), 48);
    }

    /** True if the viewport point is over a glyph of a page whose text is loaded. */
    @Override
    public boolean isOverText(double vx, double vy) {
        return text.isOverText(pageAtViewport(vx, vy));
    }

    private void clearLayers() {
        shadowGroup.getChildren().clear();
        pagesGroup.getChildren().clear();
        layers.clear();
        uiGroup.getChildren().clear();
        uiLayers.clear();
    }

    // ---- lookups, visibility and rasters ---------------------------------------------------

    AnnotationHit annotationAt(double x, double y) {
        Point2D local = viewport.parentToLocal(x, y);
        return annotations.annotationAtViewport(local.getX(), local.getY());
    }

    /** The UI layer of a visible page, or null. */
    @Override
    public AnnotationUiLayer uiLayer(int pageIndex) {
        return uiLayers.get(pageIndex);
    }

    /** Scrolls so a page point sits at the viewport's top (and left edge if the page is wider). */
    void alignTop(PagePoint point) {
        if (layout == null || document == null) return;
        PageSlot slot = layout.getSlot(point.pageIndex());
        if (slot == null) return;
        Page page = document.getPageTree().getPage(point.pageIndex());
        AffineTransform pageToView = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(),
                layoutRotation, (float) layoutZoom);
        Point2D view = PageTransforms.pageToView(pageToView, point.x(), point.y());
        double x = layout.getWidth() > viewportW ? slot.x() + view.getX() - 8 : scrollX;
        scrollTo(x, slot.y() + view.getY() - 8);
    }

    /** Out of the scene: give the rasters back after a grace period.  Back in: paint again. */
    private void onSceneChanged() {
        if (getSkinnable().getScene() == null) {
            releaseHidden.playFromStart();
        } else {
            releaseHidden.stop();
            scheduleRefresh();
        }
    }

    /**
     * Drops the tiles, previews and page text of a view nobody can see, and stops rendering for it;
     * the page layers stay (an open field editor or a selection lives on them).
     */
    private void releaseRasters() {
        tiles.release();
        layers.values().forEach(PageLayer::releaseRasters);
        text.loader().retain(page -> false);
    }

    /** This view for the render pool's diagnostics (the devtools window).  FX thread. */
    private RenderService.ViewStats renderStats() {
        PdfView view = getSkinnable();
        String name = "";
        if (document != null && document.getDocumentOrigin() != null) {
            name = java.nio.file.Paths.get(document.getDocumentOrigin()).getFileName().toString();
        }
        return new RenderService.ViewStats(name, document != null ? view.getPageCount() : 0, view.getZoom(),
                view.getCurrentPageIndex() + 1, cache.getUsedBytes(), cache.getBudget(), cache.size(), layers.size(),
                renderer.jobCount(), renderer.failedCount(), text.loader().loadedCount(), renderOwner.isForeground());
    }

    /** Whether this view's render work is ahead in the shared queue; for the smoke runs. */
    boolean isRenderForeground() {
        return renderOwner.isForeground();
    }

    /** Bytes of rasters cached; for the smoke runs. */
    long cachedBytes() {
        return cache.getUsedBytes();
    }

    private void resetRasters() {
        tiles.reset();
        clearLayers();
        refresh();
    }

    // ---- HiDPI ----------------------------------------------------------------------------

    private void watchWindow() {
        Scene scene = getSkinnable().getScene();
        if (watchedScene != null) watchedScene.windowProperty().removeListener(windowListener);
        watchedScene = scene;
        if (scene != null) scene.windowProperty().addListener(windowListener);
        attachWindow(scene != null ? scene.getWindow() : null);
    }

    private void attachWindow(Window w) {
        if (window == w) return;
        if (window != null) window.outputScaleXProperty().removeListener(outputScaleListener);
        window = w;
        if (window != null) window.outputScaleXProperty().addListener(outputScaleListener);
        updateOutputScale();
    }

    private void updateOutputScale() {
        double scale = window != null ? window.getOutputScaleX() : 1;
        if (scale <= 0 || scale == outputScale) return;
        outputScale = scale;
        refresh();
    }

    // ---- the control and its undo history -------------------------------------------------

    @Override
    public PdfView view() {
        return getSkinnable();
    }

    @Override
    public void recordEdit(AnnotationEdits.Edit edit) {
        getSkinnable().recordEdit(edit);
    }

    // ---- what the input router asks of the skin ----------------------------------------------

    @Override
    public boolean hasLayout() {
        return layout != null;
    }

    @Override
    public double scrollX() {
        return scrollX;
    }

    @Override
    public double scrollY() {
        return scrollY;
    }

    @Override
    public javafx.scene.Node viewportNode() {
        return viewport;
    }

    /** Zooms by {@code factor} keeping the document point under a viewport point still. */
    @Override
    public void zoomAround(double vx, double vy, double factor) {
        PdfView control = getSkinnable();
        zoomFocus = new Point2D(vx, vy);
        control.setFitMode(FitMode.NONE);
        control.setZoom(control.getZoom() * factor);
        zoomFocus = null;
    }

    // ---- tools ----------------------------------------------------------------------------

    private void installTool() {
        input.installTool(getSkinnable().getToolMode());
    }

    @Override
    public void setViewportCursor(Cursor cursor) {
        viewportCursor = cursor;
        applyCursor();
    }

    /** The cursor for the current state: hand while Space is held, else the active tool's. */
    @Override
    public void restoreViewportCursor() {
        setViewportCursor(input.idleCursor());
    }

    /** The tool's cursor, or the wait cursor while the application is busy or the first paint is pending. */
    private void applyCursor() {
        boolean waiting = getSkinnable().isBusy() || firstPaintPending;
        viewport.setCursor(waiting ? Cursor.WAIT : viewportCursor);
    }

    // ---- hit testing ----------------------------------------------------------------------

    /**
     * The page and PDF user-space point under a point in viewport coordinates, or null over the
     * gaps between pages, outside the document, or before a document is laid out.
     */
    @Override
    public PagePoint pageAtViewport(double vx, double vy) {
        if (layout == null || document == null) return null;
        double dx = scrollX + vx;
        double dy = scrollY + vy;
        List<PageSlot> hit = layout.slotsIntersecting(dx, dy, 1e-6, 1e-6);
        if (hit.isEmpty()) return null;
        PageSlot slot = hit.get(0);
        Page page = document.getPageTree().getPage(slot.pageIndex());
        AffineTransform pageToView = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(),
                layoutRotation, (float) layoutZoom);
        Point2D user = PageTransforms.viewToPage(pageToView, dx - slot.x(), dy - slot.y());
        return user == null ? null : new PagePoint(slot.pageIndex(), user.getX(), user.getY());
    }

    /**
     * Like {@link #pageAtViewport}, but a point between pages or outside the viewport snaps to the
     * nearest page edge: what a selection drag across a page gap or past the viewport edge means.
     * Null only without a layout or pages.
     */
    @Override
    public PagePoint nearestPageAtViewport(double vx, double vy) {
        PagePoint direct = pageAtViewport(vx, vy);
        if (direct != null || layout == null || document == null) return direct;
        double dx = scrollX + vx;
        double dy = scrollY + vy;
        double reach = Math.max(viewportH, 4 * getSkinnable().getPageGap());
        PageSlot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PageSlot slot : layout.slotsIntersecting(0, dy - reach, layout.getWidth(), 2 * reach)) {
            double ox = Math.max(0, Math.max(slot.x() - dx, dx - slot.maxX()));
            double oy = Math.max(0, Math.max(slot.y() - dy, dy - slot.maxY()));
            double distance = ox * ox + oy * oy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }
        if (best == null) return null;
        // clamp into the slot, a hair inside so the inverse transform lands on the page.
        double cx = clamp(dx, best.x() + 1e-3, best.maxX() - 1e-3);
        double cy = clamp(dy, best.y() + 1e-3, best.maxY() - 1e-3);
        Page page = document.getPageTree().getPage(best.pageIndex());
        AffineTransform pageToView = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(),
                layoutRotation, (float) layoutZoom);
        Point2D user = PageTransforms.viewToPage(pageToView, cx - best.x(), cy - best.y());
        return user == null ? null : new PagePoint(best.pageIndex(), user.getX(), user.getY());
    }

    /**
     * Scrolls the least distance that brings a page point at least {@code margin} logical px inside
     * the viewport; does nothing if it is already there or the page isn't laid out (a
     * non-continuous mode showing another page - change the current page first).
     */
    void ensureVisible(PagePoint point, double margin) {
        if (layout == null || document == null) return;
        PageSlot slot = layout.getSlot(point.pageIndex());
        if (slot == null) return;
        Page page = document.getPageTree().getPage(point.pageIndex());
        AffineTransform pageToView = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(),
                layoutRotation, (float) layoutZoom);
        Point2D view = PageTransforms.pageToView(pageToView, point.x(), point.y());
        double dx = slot.x() + view.getX();
        double dy = slot.y() + view.getY();
        double m = Math.min(margin, Math.min(viewportW, viewportH) / 2);
        double x = scrollX;
        double y = scrollY;
        if (dx < scrollX + m) x = dx - m;
        else if (dx > scrollX + viewportW - m) x = dx - viewportW + m;
        if (dy < scrollY + m) y = dy - m;
        else if (dy > scrollY + viewportH - m) y = dy - viewportH + m;
        if (x != scrollX || y != scrollY) scrollTo(x, y);
        pinCurrentPage(point.pageIndex());
    }

    @Override
    public double getViewportWidth() {
        return viewportW;
    }

    @Override
    public double getViewportHeight() {
        return viewportH;
    }

    /** {@link #pageAtViewport} for a point in the control's own coordinates. */
    PagePoint pageAt(double x, double y) {
        Point2D local = viewport.parentToLocal(x, y);
        return pageAtViewport(local.getX(), local.getY());
    }

}
