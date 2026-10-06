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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.Bias;
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.view.DocumentLayout.PageSlot;

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
final class PdfViewSkin extends SkinBase<PdfView> {

    private static final int PREVIEW_LONG_SIDE = 768;
    private static final Duration ZOOM_SETTLE = Duration.millis(150);
    private static final double SCROLL_UNIT = 40;
    // pages this far beyond the viewport (as a fraction of its height) get layers and previews.
    private static final double PREFETCH = 0.5;

    private final Pane viewport = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final ScrollBar vbar = new ScrollBar();
    private final ScrollBar hbar = new ScrollBar();

    private final TileCache cache = new TileCache(TileCache.defaultBudget());
    private final TileRenderer renderer;
    private final PageTextLoader textLoader = new PageTextLoader((page, sequence) -> scheduleRefresh());
    private CaretNavigator caretNavigator;
    // caret blink phase; the caret is drawn on the focus page's layer only (see updateText).
    private static final Duration CARET_BLINK = Duration.millis(500);
    private final Timeline caretBlink = new Timeline();
    private boolean caretOn = true;
    private final Map<Integer, PageLayer> layers = new HashMap<>();

    private Document document;
    // page sizes at zoom 1 for the current rotation and boundary.
    private double[] unitWidths = new double[0];
    private double[] unitHeights = new double[0];
    private DocumentLayout layout;
    // the zoom/rotation the current layout was built for; the properties may already differ when
    // a listener runs, and anchoring needs the old values.
    private double layoutZoom = 1;
    private float layoutRotation;

    private double scrollX;
    private double scrollY;
    private double viewportW;
    private double viewportH;
    private double outputScale = 1;

    private boolean zoomSettling;
    private final PauseTransition zoomSettle = new PauseTransition(ZOOM_SETTLE);
    private boolean refreshQueued;
    private boolean syncingBars;
    private boolean publishingPage;
    // viewport point a zoom should hold still (mouse position for wheel/pinch zoom), else centre.
    private Point2D zoomFocus;

    // tools: the active handler gets primary-button gestures; middle-button and Space+drag pan
    // whatever the tool.  gestureHandler owns a press-drag-release sequence from start to end.
    private final PanHandler panHandler = new PanHandler(this);
    private final TextSelectHandler textSelectHandler = new TextSelectHandler(this);
    private ToolHandler activeHandler;
    private ToolHandler gestureHandler;
    private MouseButton gestureButton;
    private boolean spaceDown;
    // a Space press that panned is not also a page-down when released.
    private boolean spacePanned;

    private final ChangeListener<Number> outputScaleListener = (obs, o, n) -> updateOutputScale();
    private final javafx.event.EventHandler<ScrollEvent> scrollHandler = this::onScroll;
    private final javafx.event.EventHandler<ZoomEvent> pinchHandler = this::onPinch;
    private final javafx.event.EventHandler<KeyEvent> keyHandler = this::onKey;
    private final javafx.event.EventHandler<KeyEvent> keyReleasedHandler = this::onKeyReleased;
    private Window window;

    PdfViewSkin(PdfView control) {
        super(control);
        renderer = new TileRenderer(TileRenderer.defaultThreads(), new TileRenderer.Sink() {
            @Override
            public void tileReady(CacheKey.Tile key, RasterBuffer buffer) {
                cache.put(key, buffer);
                scheduleRefresh();
            }

            @Override
            public void previewReady(CacheKey.Preview key, RasterBuffer buffer, float zoom) {
                cache.put(key, buffer);
                scheduleRefresh();
            }

            @Override
            public void failed(boolean outOfMemory) {
                if (outOfMemory) cache.trimToPinned();
                scheduleRefresh();
            }
        });
        renderer.setPaintAnnotations(control.isPaintAnnotations());

        viewport.setClip(viewportClip);
        viewport.setManaged(false);
        vbar.setOrientation(Orientation.VERTICAL);
        vbar.setUnitIncrement(SCROLL_UNIT);
        hbar.setUnitIncrement(SCROLL_UNIT);
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
        registerChangeListener(control.zoomProperty(), o -> onZoom());
        registerChangeListener(control.rotationProperty(), o -> onRotation());
        registerChangeListener(control.pageBoundaryProperty(), o -> onRotation());
        registerChangeListener(control.viewModeProperty(), o -> onArrangement());
        registerChangeListener(control.coverPageProperty(), o -> onArrangement());
        registerChangeListener(control.pageGapProperty(), o -> onArrangement());
        registerChangeListener(control.fitModeProperty(), o -> applyFit());
        registerChangeListener(control.currentPageIndexProperty(), o -> onCurrentPage());
        registerChangeListener(control.paintAnnotationsProperty(), o -> {
            renderer.setPaintAnnotations(control.isPaintAnnotations());
            resetRasters();
        });
        registerChangeListener(control.pageOverlayFactoryProperty(), o -> {
            layers.values().forEach(PageLayer::resetOverlay);
            refresh();
        });
        registerChangeListener(control.sceneProperty(), o -> watchWindow());
        registerChangeListener(control.toolModeProperty(), o -> {
            installTool();
            updateCaretBlink(false);
            refresh();
        });
        registerChangeListener(control.textSelectionProperty(), o -> {
            // solid again whenever the caret moves, so it is visible straight after interaction.
            caretOn = true;
            updateCaretBlink(true);
            refresh();
        });
        registerChangeListener(control.focusedProperty(), o -> {
            updateCaretBlink(false);
            refresh();
        });
        caretBlink.getKeyFrames().add(new KeyFrame(CARET_BLINK, e -> {
            caretOn = !caretOn;
            layers.values().forEach(layer -> layer.setCaretVisible(caretOn));
        }));
        caretBlink.setCycleCount(Animation.INDEFINITE);

        control.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        control.addEventHandler(ZoomEvent.ZOOM, pinchHandler);
        control.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        control.addEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        // on the viewport, not the control, so dragging a scroll bar thumb isn't a page gesture.
        viewport.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPress);
        viewport.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDrag);
        viewport.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onRelease);
        viewport.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMoved);

        installTool();
        watchWindow();
        onDocument();
    }

    @Override
    public void dispose() {
        if (window != null) window.outputScaleXProperty().removeListener(outputScaleListener);
        renderer.shutdown();
        textLoader.shutdown();
        caretBlink.stop();
        cache.clear();
        getSkinnable().removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        getSkinnable().removeEventHandler(ZoomEvent.ZOOM, pinchHandler);
        getSkinnable().removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        getSkinnable().removeEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        if (activeHandler != null) activeHandler.uninstall();
        super.dispose();
    }

    // ---- layout ---------------------------------------------------------------------------

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
            viewportW = vw;
            viewportH = vh;
            if (getSkinnable().getFitMode() != FitMode.NONE) {
                applyFit();
            }
            relayout(first ? null : captureAnchor(viewportW / 2, viewportH / 2));
        }
    }

    private void onDocument() {
        document = getSkinnable().getDocument();
        renderer.setDocument(document);
        textLoader.setDocument(document);
        caretNavigator = null;
        resetRasters();
        scrollX = 0;
        scrollY = 0;
        loadUnitSizes();
        if (getSkinnable().getFitMode() != FitMode.NONE) applyFit();
        relayout(null);
    }

    private void onZoom() {
        Point2D focus = zoomFocus != null ? zoomFocus : new Point2D(viewportW / 2, viewportH / 2);
        zoomFocus = null;
        Anchor anchor = captureAnchor(focus.getX(), focus.getY());
        // hold re-rendering until the zoom stops changing; existing tiles are scaled meanwhile.
        zoomSettling = true;
        zoomSettle.playFromStart();
        relayout(anchor);
    }

    private void onRotation() {
        Anchor anchor = captureAnchor(viewportW / 2, viewportH / 2);
        loadUnitSizes();
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
        PageSlot slot = layout.getSlot(page);
        if (slot != null) {
            double gap = getSkinnable().getPageGap();
            double x = layout.getWidth() > viewportW ? slot.x() - gap : scrollX;
            scrollTo(x, slot.y() - gap);
        }
    }

    /** Core rotation is counter-clockwise; the control's property is clockwise. */
    private float coreRotation() {
        return (float) ((360 - getSkinnable().getRotation()) % 360);
    }

    private void loadUnitSizes() {
        int count = document != null ? document.getNumberOfPages() : 0;
        unitWidths = new double[count];
        unitHeights = new double[count];
        int boundary = getSkinnable().getPageBoundary();
        float rotation = coreRotation();
        for (int i = 0; i < count; i++) {
            Page page = document.getPageTree().getPage(i);
            PDimension size = page != null ? page.getSize(boundary, rotation, 1f) : null;
            unitWidths[i] = size != null ? size.getWidth() : 612;
            unitHeights[i] = size != null ? size.getHeight() : 792;
        }
    }

    /** Rebuilds the layout for the current properties, then restores the anchor (or the top). */
    private void relayout(Anchor anchor) {
        PdfView control = getSkinnable();
        double zoom = control.getZoom();
        int count = unitWidths.length;
        double[] w = unitWidths;
        double[] h = unitHeights;
        layout = new DocumentLayout(new DocumentLayout.PageSizes() {
            public int count() {
                return count;
            }

            public double width(int i) {
                return w[i] * zoom;
            }

            public double height(int i) {
                return h[i] * zoom;
            }
        }, control.getViewMode(), control.isCoverPage(), control.getCurrentPageIndex(), control.getPageGap(),
                viewportW, viewportH);
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

    private void applyFit() {
        FitMode mode = getSkinnable().getFitMode();
        if (mode == FitMode.NONE || unitWidths.length == 0 || viewportW <= 0 || viewportH <= 0) return;
        PdfView control = getSkinnable();
        double gap = control.getPageGap();
        boolean facing = control.getViewMode().isFacing();
        int current = control.getCurrentPageIndex();
        double width;
        double height;
        if (mode == FitMode.WIDTH && control.getViewMode().isContinuous()) {
            width = 0;
            for (double uw : unitWidths) width = Math.max(width, uw);
            height = 0;
        } else {
            width = unitWidths[current];
            height = unitHeights[current];
            if (facing) {
                int partner = partnerOf(current);
                if (partner >= 0) {
                    width = Math.max(width, unitWidths[partner]);
                    height = Math.max(height, unitHeights[partner]);
                }
            }
        }
        double across = facing ? 2 * width : width;
        double gaps = facing ? 3 * gap : 2 * gap;
        double zoom = (viewportW - gaps) / Math.max(1, across);
        if (mode == FitMode.PAGE) zoom = Math.min(zoom, (viewportH - 2 * gap) / Math.max(1, height));
        control.setZoom(zoom);
    }

    private int partnerOf(int page) {
        boolean cover = getSkinnable().isCoverPage();
        int offset = cover ? page - 1 : page;
        if (cover && page == 0) return -1;
        int partner = offset % 2 == 0 ? page + 1 : page - 1;
        return partner >= 0 && partner < unitWidths.length ? partner : -1;
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

    void scrollBy(double dx, double dy) {
        scrollTo(scrollX + dx, scrollY + dy);
    }

    private void scrollTo(double x, double y) {
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

    private void scheduleRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        Platform.runLater(() -> {
            refreshQueued = false;
            refresh();
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
        PdfView control = getSkinnable();
        CacheKey.Params params = new CacheKey.Params((float) layoutZoom, layoutRotation, outputScale,
                control.getPageBoundary());
        double band = viewportH * PREFETCH;
        List<PageSlot> slots = layout.slotsIntersecting(scrollX, scrollY - band, viewportW, viewportH + 2 * band);

        Set<Integer> keep = new HashSet<>();
        Set<CacheKey> wanted = new HashSet<>();
        boolean incomplete = false;
        for (PageSlot slot : slots) {
            int index = slot.pageIndex();
            keep.add(index);
            PageLayer layer = layers.get(index);
            if (layer == null) {
                layer = new PageLayer(index, document.getPageTree().getPage(index));
                layers.put(index, layer);
                viewport.getChildren().add(layer);
            }
            layer.setParams(params, slot.width(), slot.height());
            layer.setLayoutX(snap(slot.x() - scrollX));
            layer.setLayoutY(snap(slot.y() - scrollY));
            layer.ensureOverlay(control.getPageOverlayFactory());

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
            incomplete |= !updateTiles(layer, slot, params, wanted);
            updateText(layer);
        }
        layers.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            viewport.getChildren().remove(e.getValue());
            return true;
        });
        cache.pin(wanted);
        textLoader.retain(needsText() ? keep::contains : page -> false);
        renderer.retain(wanted::contains);
        control.setRendering(zoomSettling || incomplete);
        publishCurrentPage();
    }

    /** @return true when every visible tile of the page is showing (or has failed) */
    private boolean updateTiles(PageLayer layer, PageSlot slot, CacheKey.Params params, Set<CacheKey> wanted) {
        double scale = params.scale();
        // the part of the page inside the viewport, in page-local logical px.
        double x0 = Math.max(slot.x(), scrollX) - slot.x();
        double y0 = Math.max(slot.y(), scrollY) - slot.y();
        double x1 = Math.min(slot.maxX(), scrollX + viewportW) - slot.x();
        double y1 = Math.min(slot.maxY(), scrollY + viewportH) - slot.y();
        Set<CacheKey.Tile> keys = new HashSet<>();
        if (x1 <= x0 || y1 <= y0) {
            layer.retainTiles(keys);
            return true;
        }
        TileGrid grid = new TileGrid(TileGrid.deviceSize(slot.width(), scale),
                TileGrid.deviceSize(slot.height(), scale), TileGrid.DEFAULT_TILE_SIZE);
        List<TileGrid.Tile> missing = new ArrayList<>();
        boolean complete = true;
        for (TileGrid.Tile tile : grid.tilesIntersecting(x0 * scale, y0 * scale, (x1 - x0) * scale,
                (y1 - y0) * scale)) {
            CacheKey.Tile key = new CacheKey.Tile(slot.pageIndex(), params, tile.column(), tile.row());
            keys.add(key);
            wanted.add(key);
            if (layer.hasTile(key) || renderer.hasFailed(key)) continue;
            RasterBuffer buffer = cache.get(key);
            if (buffer != null) {
                layer.addTile(key, tile, buffer);
            } else {
                complete = false;
                if (!renderer.isPending(key)) missing.add(tile);
            }
        }
        layer.retainTiles(keys);
        if (complete) {
            layer.clearStale();
        } else if (!zoomSettling && !missing.isEmpty()) {
            renderer.requestTiles(slot.pageIndex(), params, grid, missing);
        }
        return complete;
    }

    private void publishCurrentPage() {
        if (!getSkinnable().getViewMode().isContinuous()) return;
        int current = layout.currentPage(scrollX, scrollY, viewportW, viewportH);
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

    /** Page text is needed to select, or to draw a selection. */
    private boolean needsText() {
        return getSkinnable().getToolMode() == ToolMode.TEXT_SELECT || getSkinnable().getTextSelection() != null;
    }

    private void updateText(PageLayer layer) {
        if (!needsText()) {
            layer.setSelection(null, null);
            layer.setCaret(null);
            return;
        }
        int index = layer.getPageIndex();
        textLoader.request(index);
        TextSequence sequence = textLoader.get(index);
        DocumentSelection selection = getSkinnable().getTextSelection();
        layer.setSelection(sequence, selection != null ? selection.rangeForPage(index, sequence) : null);
        // the caret sits at the focus end, as in the Swing viewer.
        boolean caretHere = isCaretActive() && sequence != null && selection.getFocusPage() == index;
        layer.setCaret(caretHere ? sequence.caretRect(
                new Caret(Math.min(selection.getFocusOffset(), sequence.length()), Bias.FORWARD)) : null);
        layer.setCaretVisible(caretOn);
    }

    /** A caret shows in the select tool, while focused, when there is a selection. */
    private boolean isCaretActive() {
        PdfView control = getSkinnable();
        return control.getToolMode() == ToolMode.TEXT_SELECT && control.isFocused()
                && control.getTextSelection() != null;
    }

    /** Runs the blink while a caret is active; {@code restart} resets the cycle (caret moved). */
    private void updateCaretBlink(boolean restart) {
        if (!isCaretActive()) {
            caretBlink.stop();
            caretOn = true;
        } else if (restart || caretBlink.getStatus() != Animation.Status.RUNNING) {
            caretBlink.playFromStart();
        }
    }

    /** The page's loaded text, or null if it isn't loaded (or has none). */
    TextSequence textSequence(int pageIndex) {
        return textLoader.get(pageIndex);
    }

    /** Keyboard caret movement over the loader's text; rebuilt per document. */
    CaretNavigator caretNavigator() {
        if (caretNavigator == null) {
            caretNavigator = new CaretNavigator(new CaretNavigator.Texts() {
                public TextSequence get(int pageIndex) {
                    return textLoader.get(pageIndex);
                }

                public boolean isLoaded(int pageIndex) {
                    return textLoader.isLoaded(pageIndex);
                }

                public void request(int pageIndex) {
                    textLoader.requestKept(pageIndex);
                }
            }, unitWidths.length);
        }
        return caretNavigator;
    }

    /** Text of a selection, extracted off the FX thread. */
    java.util.concurrent.CompletableFuture<String> selectedTextAsync(DocumentSelection selection) {
        return textLoader.extractAsync(selection);
    }

    /**
     * Brings the selection's focus caret into view, switching page first in the non-continuous
     * modes.  Needs the focus page's text to be loaded, which it is after any caret move.
     */
    void revealCaret() {
        DocumentSelection selection = getSkinnable().getTextSelection();
        if (selection == null) return;
        int page = selection.getFocusPage();
        if (!getSkinnable().getViewMode().isContinuous() && layout != null && layout.getSlot(page) == null) {
            getSkinnable().setCurrentPageIndex(page);
        }
        TextSequence sequence = textLoader.get(page);
        if (sequence == null) return;
        java.awt.geom.Rectangle2D.Double caret = sequence.caretRect(
                new Caret(Math.min(selection.getFocusOffset(), sequence.length()), Bias.FORWARD));
        ensureVisible(new PagePoint(page, caret.getCenterX(), caret.getCenterY()), 48);
    }

    /** True if the viewport point is over a glyph of a page whose text is loaded. */
    boolean isOverText(double vx, double vy) {
        PagePoint point = pageAtViewport(vx, vy);
        if (point == null) return false;
        TextSequence sequence = textLoader.get(point.pageIndex());
        return sequence != null && sequence.hitsText(point.toAwt());
    }

    private void clearLayers() {
        viewport.getChildren().clear();
        layers.clear();
    }

    private void resetRasters() {
        renderer.cancelAll();
        cache.clear();
        clearLayers();
        refresh();
    }

    // ---- HiDPI ----------------------------------------------------------------------------

    private void watchWindow() {
        Scene scene = getSkinnable().getScene();
        if (scene == null) return;
        scene.windowProperty().addListener((obs, o, n) -> attachWindow(n));
        attachWindow(scene.getWindow());
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

    // ---- input ----------------------------------------------------------------------------

    private void onScroll(ScrollEvent e) {
        if (layout == null) return;
        if (e.isShortcutDown()) {
            if (e.getDeltaY() == 0) return;
            zoomAround(e.getX(), e.getY(), Math.pow(1.1, e.getDeltaY() / 40));
        } else {
            double dx = e.getDeltaX();
            double dy = e.getDeltaY();
            if (e.isShiftDown() && dx == 0) {
                dx = dy;
                dy = 0;
            }
            scrollTo(scrollX - dx, scrollY - dy);
        }
        e.consume();
    }

    private void onPinch(ZoomEvent e) {
        zoomAround(e.getX(), e.getY(), e.getZoomFactor());
        e.consume();
    }

    /** Zooms by {@code factor} keeping the document point under (x, y) (control coords) still. */
    private void zoomAround(double x, double y, double factor) {
        PdfView control = getSkinnable();
        Point2D local = viewport.parentToLocal(x, y);
        zoomFocus = local;
        control.setFitMode(FitMode.NONE);
        control.setZoom(control.getZoom() * factor);
        zoomFocus = null;
    }

    // ---- tools ----------------------------------------------------------------------------

    private void installTool() {
        ToolHandler next = getSkinnable().getToolMode() == ToolMode.PAN ? panHandler : textSelectHandler;
        if (next == activeHandler) return;
        if (activeHandler != null) activeHandler.uninstall();
        activeHandler = next;
        gestureHandler = null;
        activeHandler.install();
        restoreViewportCursor();
    }

    void setViewportCursor(Cursor cursor) {
        viewport.setCursor(cursor);
    }

    /** The cursor for the current state: hand while Space is held, else the active tool's. */
    void restoreViewportCursor() {
        viewport.setCursor(spaceDown ? Cursor.OPEN_HAND : activeHandler.idleCursor());
    }

    private void onPress(MouseEvent e) {
        getSkinnable().requestFocus();
        if (gestureHandler != null) return; // a second button during a gesture
        if (e.getButton() == MouseButton.MIDDLE || (spaceDown && e.getButton() == MouseButton.PRIMARY)) {
            gestureHandler = panHandler;
            spacePanned |= spaceDown;
        } else if (e.getButton() == MouseButton.PRIMARY) {
            gestureHandler = activeHandler;
        } else {
            return;
        }
        gestureButton = e.getButton();
        gestureHandler.pressed(e);
        e.consume();
    }

    private void onDrag(MouseEvent e) {
        if (gestureHandler == null) return;
        gestureHandler.dragged(e);
        e.consume();
    }

    private void onRelease(MouseEvent e) {
        // only the button that started the gesture ends it.
        if (gestureHandler == null || e.getButton() != gestureButton) return;
        ToolHandler handler = gestureHandler;
        gestureHandler = null;
        handler.released(e);
        restoreViewportCursor();
        e.consume();
    }

    private void onMoved(MouseEvent e) {
        if (!spaceDown) activeHandler.moved(e);
    }

    // ---- hit testing ----------------------------------------------------------------------

    /**
     * The page and PDF user-space point under a point in viewport coordinates, or null over the
     * gaps between pages, outside the document, or before a document is laid out.
     */
    PagePoint pageAtViewport(double vx, double vy) {
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
    PagePoint nearestPageAtViewport(double vx, double vy) {
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
    }

    double getViewportWidth() {
        return viewportW;
    }

    double getViewportHeight() {
        return viewportH;
    }

    /** {@link #pageAtViewport} for a point in the control's own coordinates. */
    PagePoint pageAt(double x, double y) {
        Point2D local = viewport.parentToLocal(x, y);
        return pageAtViewport(local.getX(), local.getY());
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.SPACE) {
            // hold Space to pan with the primary button; a tap without a drag pages down on release.
            if (!spaceDown) {
                spaceDown = true;
                spacePanned = false;
                if (gestureHandler == null) restoreViewportCursor();
            }
            e.consume();
            return;
        }
        if (activeHandler.keyPressed(e)) {
            e.consume();
            return;
        }
        PdfView control = getSkinnable();
        boolean continuous = control.getViewMode().isContinuous();
        switch (e.getCode()) {
            case UP -> scrollTo(scrollX, scrollY - SCROLL_UNIT);
            case DOWN -> scrollTo(scrollX, scrollY + SCROLL_UNIT);
            case LEFT -> scrollTo(scrollX - SCROLL_UNIT, scrollY);
            case RIGHT -> scrollTo(scrollX + SCROLL_UNIT, scrollY);
            case PAGE_UP -> {
                if (continuous) scrollTo(scrollX, scrollY - viewportH * 0.9);
                else control.previousPage();
            }
            case PAGE_DOWN -> pageDown();
            case HOME -> {
                if (continuous) scrollTo(scrollX, 0);
                else control.setCurrentPageIndex(0);
            }
            case END -> {
                if (continuous) scrollTo(scrollX, Double.MAX_VALUE);
                else control.setCurrentPageIndex(control.getPageCount() - 1);
            }
            case PLUS, EQUALS, ADD -> {
                if (!e.isShortcutDown()) return;
                control.zoomIn();
            }
            case MINUS, SUBTRACT -> {
                if (!e.isShortcutDown()) return;
                control.zoomOut();
            }
            case DIGIT0, NUMPAD0 -> {
                if (!e.isShortcutDown()) return;
                control.setFitMode(FitMode.NONE);
                control.setZoom(1);
            }
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void onKeyReleased(KeyEvent e) {
        if (e.getCode() != KeyCode.SPACE || !spaceDown) return;
        spaceDown = false;
        if (!spacePanned) pageDown();
        if (gestureHandler == null) restoreViewportCursor();
        e.consume();
    }

    private void pageDown() {
        if (getSkinnable().getViewMode().isContinuous()) scrollTo(scrollX, scrollY + viewportH * 0.9);
        else getSkinnable().nextPage();
    }
}
