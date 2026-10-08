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
import javafx.scene.Group;
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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.actions.FormAction;
import org.icepdf.core.pobjects.actions.ResetFormAction;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
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
    // page layers (clipped to their pages) under the annotation UI layers (not clipped: popups and
    // chrome may extend past a page edge, as Swing's document-level popup layer allows).
    private final Group pagesGroup = new Group();
    private final Group uiGroup = new Group();
    private final Map<Integer, AnnotationUiLayer> uiLayers = new HashMap<>();
    // the annotation under the pointer, for the hover outline.
    private AnnotationHit hovered;

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
    private final AnnotationCreateHandler createHandler = new AnnotationCreateHandler(this);
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
            public void tileReady(CacheKey key, RasterBuffer buffer) {
                cache.put(key, buffer);
                scheduleRefresh();
            }

            @Override
            public void previewReady(CacheKey.Preview key, RasterBuffer buffer, float zoom) {
                cache.put(key, buffer);
                scheduleRefresh();
            }

            @Override
            public void annotationLayerEmpty(int pageIndex, CacheKey.AnnotationLayer layer, int generation) {
                emptyAnnotationLayers.computeIfAbsent(pageIndex, k -> new EnumMap<>(CacheKey.AnnotationLayer.class))
                        .put(layer, generation);
            }

            @Override
            public void failed(boolean outOfMemory, long retryAfterMs) {
                if (outOfMemory) cache.trimToPinned();
                if (retryAfterMs > 0) {
                    // the keys stay requestable; ask again once the heap has had a moment.
                    PauseTransition retry = new PauseTransition(Duration.millis(retryAfterMs));
                    retry.setOnFinished(e -> scheduleRefresh());
                    retry.play();
                } else {
                    scheduleRefresh();
                }
            }
        });
        renderer.setPaintAnnotations(control.isPaintAnnotations());

        viewport.setClip(viewportClip);
        viewport.getChildren().addAll(pagesGroup, uiGroup);
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
        control.getSearchHits().addListener((javafx.collections.ListChangeListener<SearchHit>) c -> scheduleRefresh());
        registerChangeListener(control.selectedAnnotationProperty(), o ->
                uiLayers.values().forEach(ui -> updateAnnotationChrome(ui, null)));
        registerChangeListener(control.focusedFieldProperty(), o -> {
            uiLayers.values().forEach(ui -> updateAnnotationChrome(ui, null));
            onFieldFocusChanged();
        });
        registerChangeListener(control.highlightFormFieldsProperty(), o -> refresh());
        registerChangeListener(control.formFieldsEditableProperty(), o -> {
            if (!control.isFormFieldsEditable()) control.clearFieldFocus();
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
            viewportW = vw;
            viewportH = vh;
            if (getSkinnable().getFitMode() != FitMode.NONE) {
                applyFit();
            }
            relayout(first ? null : captureAnchor(viewportW / 2, viewportH / 2));
        }
    }

    private void onDocument() {
        fieldEditor = null;
        editing = null;
        annotationGenerations.clear();
        document = getSkinnable().getDocument();
        renderer.setDocument(document);
        textLoader.setDocument(document);
        caretNavigator = null;
        // drop the previous document's layout before anything refreshes: its slots index pages the
        // new document may not have.
        layout = null;
        scrollX = 0;
        scrollY = 0;
        resetRasters();
        loadUnitSizes();
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
            updateAnnotationChrome(ui, layer.getPage());
            layer.setFieldHighlights(control.isHighlightFormFields() && control.isFormFieldsEditable()
                    ? fieldRects(layer.getPage()) : List.of());
            if (layer.getPage() != null && layer.getPage().isInitiated() && control.isPaintAnnotations()) {
                ui.updatePopups(layer.getPage().getAnnotations(), layoutZoom, popupListener);
            } else {
                ui.updatePopups(null, layoutZoom, popupListener);
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
            updateText(layer);
        }
        layers.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            pagesGroup.getChildren().remove(e.getValue());
            return true;
        });
        uiLayers.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            uiGroup.getChildren().remove(e.getValue());
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
        boolean annotationsOn = getSkinnable().isPaintAnnotations() && !TileRenderer.SINGLE_PASS_ANNOTATIONS;
        if (x1 <= x0 || y1 <= y0) {
            layer.content().retain(Set.of());
            if (annotationsOn) {
                for (CacheKey.AnnotationLayer kind : CacheKey.AnnotationLayer.values()) {
                    layer.annotations(kind, annotationGeneration(slot.pageIndex())).retain(Set.of());
                }
            }
            return true;
        }
        TileGrid grid = new TileGrid(TileGrid.deviceSize(slot.width(), scale),
                TileGrid.deviceSize(slot.height(), scale), TileGrid.DEFAULT_TILE_SIZE);
        List<TileGrid.Tile> visible = grid.tilesIntersecting(x0 * scale, y0 * scale, (x1 - x0) * scale,
                (y1 - y0) * scale);
        int page = slot.pageIndex();

        boolean complete = updateTileSet(layer.content(), visible, wanted,
                t -> new CacheKey.Tile(page, params, t.column(), t.row()),
                missing -> renderer.requestTiles(page, params, grid, missing));

        for (CacheKey.AnnotationLayer kind : CacheKey.AnnotationLayer.values()) {
            int generation = annotationGeneration(page);
            PageLayer.TileSet set = layer.annotations(kind, generation);
            if (!annotationsOn || isAnnotationLayerEmpty(page, kind, generation)) {
                set.clear();
                continue;
            }
            boolean blend = kind == CacheKey.AnnotationLayer.BLEND;
            complete &= updateTileSet(set, visible, wanted,
                    t -> new CacheKey.AnnotationTile(page, params, kind, t.column(), t.row(), generation),
                    missing -> {
                        Map<TileGrid.Tile, RasterBuffer> backdrops = null;
                        if (blend) {
                            // blend appearances composite against the page: wait for its content tiles.
                            backdrops = new HashMap<>();
                            List<TileGrid.Tile> ready = new ArrayList<>();
                            for (TileGrid.Tile t : missing) {
                                RasterBuffer content = cache.get(new CacheKey.Tile(page, params, t.column(), t.row()));
                                if (content == null || content == RasterBuffer.EMPTY) continue;
                                backdrops.put(t, content);
                                ready.add(t);
                            }
                            missing = ready;
                        }
                        if (!missing.isEmpty()) {
                            renderer.requestAnnotationTiles(page, params, kind, generation, grid, missing,
                                    excludedAnnotations(page), backdrops);
                        }
                    });
        }
        return complete;
    }

    /**
     * Shows a tile set's cached tiles for the visible grid cells, requests the missing ones (unless a
     * zoom is settling) and drops nodes that scrolled away; stale tiles go once the set is complete.
     *
     * @return true when every visible tile is showing (or has failed)
     */
    private boolean updateTileSet(PageLayer.TileSet set, List<TileGrid.Tile> visible, Set<CacheKey> wanted,
                                  java.util.function.Function<TileGrid.Tile, CacheKey> keyOf,
                                  java.util.function.Consumer<List<TileGrid.Tile>> request) {
        Set<CacheKey> keys = new HashSet<>();
        List<TileGrid.Tile> missing = new ArrayList<>();
        boolean complete = true;
        for (TileGrid.Tile tile : visible) {
            CacheKey key = keyOf.apply(tile);
            keys.add(key);
            wanted.add(key);
            if (set.has(key) || renderer.hasFailed(key)) continue;
            RasterBuffer buffer = cache.get(key);
            if (buffer != null) {
                set.add(key, tile, buffer);
            } else {
                complete = false;
                if (!renderer.isPending(key)) missing.add(tile);
            }
        }
        set.retain(keys);
        if (complete) {
            set.clearStale();
        } else if (!zoomSettling && !missing.isEmpty()) {
            request.accept(missing);
        }
        return complete;
    }

    // ---- annotation generations ------------------------------------------------------------

    /** Per page, bumped on every annotation add/edit/delete; part of the annotation tile keys. */
    private final Map<Integer, Integer> annotationGenerations = new HashMap<>();
    // layers found to hold no annotations at a page's generation: not requested again.
    private final Map<Integer, Map<CacheKey.AnnotationLayer, Integer>> emptyAnnotationLayers = new HashMap<>();

    int annotationGeneration(int pageIndex) {
        return annotationGenerations.getOrDefault(pageIndex, 0);
    }

    // ---- annotation move/resize -------------------------------------------------------------

    /** An annotation being moved (handle -1) or resized, with its view bounds when the drag began. */
    private record Drag(AnnotationHit hit, int handle, java.awt.geom.Rectangle2D from) {
    }

    private Drag drag;
    // drag-excluded annotations per page: left out of the annotation tiles while a live node shows them.
    private final Map<Integer, Set<Annotation>> excluded = new HashMap<>();

    private Set<Annotation> excludedAnnotations(int pageIndex) {
        return excluded.getOrDefault(pageIndex, Set.of());
    }

    /** True if the annotation may be moved/resized by the user. */
    static boolean isEditable(Annotation annotation) {
        return annotation != null && !(annotation instanceof LinkAnnotation)
                && annotation.allowAlterProperties() && !annotation.getFlagReadOnly();
    }

    /** View bounds of an annotation in its page's view space, or null if the page isn't visible. */
    java.awt.geom.Rectangle2D annotationViewBounds(AnnotationHit hit) {
        AnnotationUiLayer ui = uiLayers.get(hit.pageIndex());
        return ui != null ? ui.viewBounds(hit.annotation()) : null;
    }

    /** Converts a viewport point into a page's view space. */
    Point2D toPageView(int pageIndex, double vx, double vy) {
        PageSlot slot = layout != null ? layout.getSlot(pageIndex) : null;
        return slot == null ? null : new Point2D(scrollX + vx - slot.x(), scrollY + vy - slot.y());
    }

    /** The selected annotation's handle under a viewport point, or -1. */
    int handleAtViewport(double vx, double vy) {
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

    // ---- form fields -------------------------------------------------------------------------

    /** Fillable widgets of an initialised page, in /Annots order. */
    private List<AbstractWidgetAnnotation> fillable(Page page) {
        List<AbstractWidgetAnnotation> out = new ArrayList<>();
        if (page == null || !page.isInitiated() || page.getAnnotations() == null) return out;
        for (Annotation a : page.getAnnotations()) {
            if (a instanceof AbstractWidgetAnnotation w && !w.isDeleted() && !w.getFlagHidden()
                    && FormController.isFillable(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private List<java.awt.geom.Rectangle2D> fieldRects(Page page) {
        List<java.awt.geom.Rectangle2D> rects = new ArrayList<>();
        for (AbstractWidgetAnnotation w : fillable(page)) rects.add(w.getUserSpaceRectangle());
        return rects;
    }

    /** The fillable widget under a viewport point (top-most), or null; never parses on the FX thread. */
    AnnotationHit fieldAtViewport(double vx, double vy) {
        if (!getSkinnable().isFormFieldsEditable()) return null;
        PagePoint point = pageAtViewport(vx, vy);
        if (point == null) return null;
        List<AbstractWidgetAnnotation> widgets = fillable(document.getPageTree().getPage(point.pageIndex()));
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).getUserSpaceRectangle().contains(point.x(), point.y())) {
                return new AnnotationHit(point.pageIndex(), widgets.get(i));
            }
        }
        return null;
    }

    /** Cursor over a fillable field: an I-beam for text, a hand for buttons and lists. */
    static Cursor fieldCursor(AbstractWidgetAnnotation widget) {
        FormController.FieldKind kind = FormController.kindOf(widget);
        return kind == FormController.FieldKind.TEXT || kind == FormController.FieldKind.PASSWORD
                ? Cursor.TEXT : Cursor.HAND;
    }

    /**
     * All fillable fields of the document in tab order: pages in order, each page's fields by its
     * /Tabs entry (see {@link FieldOrder}).  Reads annotations of pages not yet shown (annotation
     * dictionaries only, not content); empty for a document without an AcroForm.
     */
    List<FormController.Located> fieldsInTabOrder() {
        List<FormController.Located> out = new ArrayList<>();
        if (document == null || document.getCatalog().getInteractiveForm() == null) return out;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            List<Annotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            List<AbstractWidgetAnnotation> widgets = new ArrayList<>();
            for (Annotation a : annotations) {
                if (a instanceof AbstractWidgetAnnotation w && !w.isDeleted() && !w.getFlagHidden()
                        && FormController.isFillable(w)) {
                    widgets.add(w);
                }
            }
            Object tabs = page.getEntries().get(new org.icepdf.core.pobjects.Name("Tabs"));
            for (AbstractWidgetAnnotation w : FieldOrder.order(widgets, AbstractWidgetAnnotation::getUserSpaceRectangle,
                    tabs instanceof org.icepdf.core.pobjects.Name n ? n.getName() : null)) {
                out.add(new FormController.Located(i, page, w));
            }
        }
        return out;
    }

    /** The field after (or before) {@code current} in tab order, wrapping; null if there are none. */
    AbstractWidgetAnnotation adjacentField(AbstractWidgetAnnotation current, boolean backwards) {
        List<FormController.Located> fields = fieldsInTabOrder();
        int index = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).widget() == current) index = i;
        }
        int next = FieldOrder.next(fields.size(), index, backwards);
        return next < 0 ? null : fields.get(next).widget();
    }

    /** Scrolls a field into view, switching page in the non-continuous modes. */
    void revealField(AbstractWidgetAnnotation widget) {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            List<Annotation> annotations = page.isInitiated() || page.getAnnotations() != null ? page.getAnnotations() : null;
            if (annotations != null && annotations.contains(widget)) {
                if (!getSkinnable().getViewMode().isContinuous() && layout != null && layout.getSlot(i) == null) {
                    getSkinnable().setCurrentPageIndex(i);
                }
                java.awt.geom.Rectangle2D r = widget.getUserSpaceRectangle();
                ensureVisible(new PagePoint(i, r.getCenterX(), r.getCenterY()), 48);
                return;
            }
        }
    }

    private FormController forms;
    // the open text field editor, and which field it edits.
    private FieldEditor fieldEditor;
    private FormController.Located editing;
    // a click (not Tab) is focusing a field: a combo opens its drop-down.
    private boolean openPopupOnFocus;

    FormController forms() {
        if (forms == null) forms = new FormController(renderer::withAnnotationLock);
        return forms;
    }

    /** The located form field (page and widget) of a widget, among the document's pages. */
    FormController.Located locate(AbstractWidgetAnnotation widget) {
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            Page page = document.getPageTree().getPage(i);
            if (page.getAnnotations() != null && page.getAnnotations().contains(widget)) {
                return new FormController.Located(i, page, widget);
            }
        }
        return null;
    }

    /** The focused field changed: commit an open editor elsewhere; open one on a text or choice field. */
    private void onFieldFocusChanged() {
        AbstractWidgetAnnotation focused = getSkinnable().getFocusedField();
        if (fieldEditor != null && (editing == null || editing.widget() != focused)) {
            fieldEditor.commitNow();
        }
        if (focused != null && fieldEditor == null) openEditor(focused, false);
    }

    /**
     * A click on a field: focuses it (opening its editor), toggles a check box or radio, or runs a
     * push button's action.  A click on the focused text field reopens its editor after an Esc.
     */
    void pressField(AnnotationHit field, javafx.scene.input.MouseEvent e) {
        AbstractWidgetAnnotation widget = (AbstractWidgetAnnotation) field.annotation();
        FormController.FieldKind kind = FormController.kindOf(widget);
        if (getSkinnable().getFocusedField() != widget) {
            openPopupOnFocus = true;
            try {
                getSkinnable().focusField(widget);
            } finally {
                openPopupOnFocus = false;
            }
        } else if (fieldEditor == null) {
            openEditor(widget, true);
        }
        actuate(widget, kind);
    }

    void reopenEditor(AbstractWidgetAnnotation widget) {
        if (fieldEditor == null) openEditor(widget, false);
    }

    /** Check boxes and radios toggle, push buttons act; as a click or Space does. */
    private void actuate(AbstractWidgetAnnotation widget, FormController.FieldKind kind) {
        if (kind != FormController.FieldKind.CHECK && kind != FormController.FieldKind.RADIO
                && kind != FormController.FieldKind.PUSH) {
            return;
        }
        FormController.Located located = locate(widget);
        if (located == null) return;
        switch (kind) {
            case CHECK -> getSkinnable().recordEdit(forms().toggleCheck(located, toPageSpaceNow(located.page())));
            case RADIO -> {
                String name = FormController.fieldNameOf(widget);
                List<FormController.Located> siblings = new ArrayList<>();
                for (FormController.Located f : fieldsInTabOrder()) {
                    if (FormController.kindOf(f.widget()) == FormController.FieldKind.RADIO
                            && Objects.equals(name, FormController.fieldNameOf(f.widget()))) {
                        siblings.add(f);
                    }
                }
                if (siblings.stream().noneMatch(f -> f.widget() == widget)) siblings.add(located);
                getSkinnable().recordEdit(forms().selectRadio(located, siblings, toPageSpaceNow(located.page())));
            }
            default -> push(located);
        }
    }

    /**
     * A push button: a ResetForm action resets the form in the view, honouring its /Fields
     * include/exclude list as core's ResetFormAction does, but through FormController so the reset
     * is one undoable edit with regenerated appearances (core's button reset() leaves the model to
     * a listening Swing component).  Anything else (SubmitForm, JavaScript, URI, ...) goes where a
     * link's action goes.
     */
    private void push(FormController.Located button) {
        if (button.widget().getAction() instanceof ResetFormAction reset) {
            resetFields(fieldsForReset(reset));
        } else {
            getSkinnable().performAnnotationAction(button.widget());
        }
    }

    /** Resets fields to their defaults as one undoable edit; every fillable field when null. */
    void resetFields(List<FormController.Located> fields) {
        if (fields == null) fields = fieldsInTabOrder();
        // buttons and signatures have no value to reset (and their appearances must not be redrawn).
        fields = fields.stream().filter(f -> switch (FormController.kindOf(f.widget())) {
            case PUSH, SIGNATURE, OTHER -> false;
            default -> true;
        }).toList();
        if (fields.isEmpty()) return;
        // a reset discards what is being typed (synchronously: a pending commit would land after it).
        if (fieldEditor != null) fieldEditor.closeNow(false);
        // the transform is only used to regenerate appearances, which are page-local per field.
        getSkinnable().recordEdit(forms().reset(fields, toPageSpaceNow(fields.get(0).page())));
    }

    /** Closes an open field editor at once, committing or discarding what is typed. */
    void closeFieldEditor(boolean commit) {
        if (fieldEditor != null) fieldEditor.closeNow(commit);
    }

    AffineTransform toPageSpace(Page page) {
        return toPageSpaceNow(page);
    }

    /** The fields a ResetForm action names: all, the /Fields listed, or all but those (Flags bit 1). */
    private List<FormController.Located> fieldsForReset(ResetFormAction reset) {
        List<FormController.Located> all = fieldsInTabOrder();
        Object listed = reset.getEntries().get(FormAction.FIELDS_KEY);
        if (listed instanceof Reference ref) listed = document.getCatalog().getLibrary().getObject(ref);
        if (!(listed instanceof List<?> entries) || entries.isEmpty()) return all;
        Set<String> names = new HashSet<>();
        for (Object entry : entries) {
            if (entry instanceof Reference ref) entry = document.getCatalog().getLibrary().getObject(ref);
            if (entry instanceof AbstractWidgetAnnotation w) names.add(FormController.fieldNameOf(w));
            else if (entry instanceof FieldDictionary f) names.add(f.getFullyQualifiedFieldName());
            else if (entry instanceof StringObject str) names.add(str.getDecryptedLiteralString(
                    document.getCatalog().getLibrary().getSecurityManager()));
            else if (entry instanceof String str) names.add(str);
        }
        boolean exclude = reset.isIncludeExclude();
        List<FormController.Located> out = new ArrayList<>();
        for (FormController.Located f : all) {
            String name = FormController.fieldNameOf(f.widget());
            boolean named = names.stream().anyMatch(n -> name.equals(n) || name.startsWith(n + "."));
            if (named != exclude) out.add(f);
        }
        return out;
    }

    /**
     * Opens the native editor over a text, password, combo or list field; the widget leaves the
     * tiles meanwhile.  Buttons have no editor.
     */
    private void openEditor(AbstractWidgetAnnotation widget, boolean openPopup) {
        FormController.FieldKind kind = FormController.kindOf(widget);
        boolean text = kind == FormController.FieldKind.TEXT || kind == FormController.FieldKind.PASSWORD;
        boolean choice = kind == FormController.FieldKind.COMBO || kind == FormController.FieldKind.LIST;
        if (!text && !choice) return;
        FormController.Located located = locate(widget);
        if (located == null) return;
        AnnotationUiLayer ui = uiLayers.get(located.pageIndex());
        if (ui == null) return;
        FieldEditor[] holder = new FieldEditor[1];
        FieldEditor.Listener listener = new FieldEditor.Listener() {
            public void commit(Object value, int advance) {
                editorListener(holder[0], located).commit(value, advance);
            }

            public void cancel() {
                editorListener(holder[0], located).cancel();
            }
        };
        FieldEditor editor = text
                ? FieldEditor.text((TextWidgetAnnotation) widget, FormController.textOf((TextWidgetAnnotation) widget),
                ui.viewBounds(widget), layoutZoom, listener)
                : FieldEditor.choice((ChoiceWidgetAnnotation) widget, ui.viewBounds(widget), layoutZoom,
                openPopup || openPopupOnFocus, listener);
        holder[0] = editor;
        fieldEditor = editor;
        editing = located;
        excluded.computeIfAbsent(located.pageIndex(), k -> new HashSet<>()).add(widget);
        bumpAnnotationGeneration(located.pageIndex());
        ui.addFieldEditor(editor.control());
        editor.control().requestFocus();
        if (editor.control() instanceof javafx.scene.control.TextInputControl input) input.selectAll();
    }

    /** Closes an editor (once): commit applies the value as an undoable edit; the widget re-renders. */
    private FieldEditor.Listener editorListener(FieldEditor editor, FormController.Located located) {
        return new FieldEditor.Listener() {
            public void commit(Object value, int advance) {
                close(editor, located);
                AnnotationEdits.Edit edit = null;
                AffineTransform toPage = toPageSpaceNow(located.page());
                if (located.widget() instanceof TextWidgetAnnotation textWidget) {
                    if (!value.equals(FormController.textOf(textWidget))) {
                        edit = forms().setText(located, (String) value, toPage);
                    }
                } else if (located.widget() instanceof ChoiceWidgetAnnotation choice) {
                    ChoiceFieldDictionary field = choice.getFieldDictionary();
                    if (value instanceof List<?> indexes) {
                        List<Integer> current = FormController.selectedIndexes(field);
                        if (!indexes.equals(current)) {
                            @SuppressWarnings("unchecked") List<Integer> chosen = (List<Integer>) indexes;
                            edit = forms().chooseIndexes(located, chosen, toPage);
                        }
                    } else if (value instanceof String typed) {
                        Object current = field.getFieldValue();
                        if (!typed.equals(current instanceof String s ? s : current != null ? current.toString() : "")) {
                            edit = forms().choose(located, typed, toPage);
                        }
                    }
                }
                getSkinnable().recordEdit(edit);
                if (advance > 0) getSkinnable().focusNextField();
                else if (advance < 0) getSkinnable().focusPreviousField();
            }

            public void cancel() {
                close(editor, located);
            }
        };
    }

    private void close(FieldEditor editor, FormController.Located located) {
        AnnotationUiLayer ui = uiLayers.get(located.pageIndex());
        if (ui != null) ui.removeFieldEditor(editor.control());
        if (fieldEditor == editor) {
            fieldEditor = null;
            editing = null;
        }
        Set<Annotation> set = excluded.get(located.pageIndex());
        if (set != null) set.remove(located.widget());
        bumpAnnotationGeneration(located.pageIndex());
        // keyboard focus back to the view, so Tab and the arrows work after an editor closes.
        if (!getSkinnable().isFocused() && getSkinnable().getScene() != null
                && getSkinnable().getScene().getFocusOwner() == null) {
            getSkinnable().requestFocus();
        }
    }

    /** The page an annotation is on (among visible pages), or null. */
    AnnotationHit hitOf(Annotation annotation) {
        for (AnnotationUiLayer ui : uiLayers.values()) {
            if (isOnPage(annotation, ui.getPageIndex())) return new AnnotationHit(ui.getPageIndex(), annotation);
        }
        return null;
    }

    void requestRefresh() {
        scheduleRefresh();
    }

    /** The page the selected annotation is on, or null. */
    AnnotationHit selectedHit() {
        Annotation selected = getSkinnable().getSelectedAnnotation();
        if (selected == null) return null;
        for (AnnotationUiLayer ui : uiLayers.values()) {
            if (isOnPage(selected, ui.getPageIndex())) return new AnnotationHit(ui.getPageIndex(), selected);
        }
        return null;
    }

    /**
     * Starts a move (handle -1) or resize: the annotation leaves its tiles and a live node rendered
     * from it follows the pointer until the drag ends.
     */
    void beginAnnotationDrag(AnnotationHit hit, int handle) {
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
            if (drag != started) return;
            ui.setProxy(buffer, region.x(), region.y(), scale, from);
            excluded.computeIfAbsent(hit.pageIndex(), k -> new HashSet<>()).add(hit.annotation());
            bumpAnnotationGeneration(hit.pageIndex());
        });
    }

    /** Follows the pointer: (dx, dy) is the total view-space movement since the drag began. */
    void updateAnnotationDrag(double dx, double dy) {
        if (drag == null) return;
        AnnotationUiLayer ui = uiLayers.get(drag.hit().pageIndex());
        PageSlot slot = layout.getSlot(drag.hit().pageIndex());
        if (ui == null || slot == null) return;
        java.awt.geom.Rectangle2D page = new java.awt.geom.Rectangle2D.Double(0, 0, slot.width(), slot.height());
        ui.setDragBounds(drag.handle() < 0 ? AnnotationGeometry.move(drag.from(), dx, dy, page)
                : AnnotationGeometry.resize(drag.from(), drag.handle(), dx, dy, page));
    }

    /** Ends the drag; commits it through core unless {@code cancel}, and returns the edit (or null). */
    AnnotationEdits.Edit endAnnotationDrag(double dx, double dy, boolean cancel) {
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
        Set<Annotation> set = excluded.get(pageIndex);
        if (set != null) set.remove(ended.hit().annotation());
        if (ui != null) {
            ui.setDragBounds(null);
            // keep the live node until the re-rendered tiles cover it (see refresh), so no flash.
            if (!ui.hasProxy()) ui.refresh();
        }
        bumpAnnotationGeneration(pageIndex);
        return edit;
    }

    boolean isDraggingAnnotation() {
        return drag != null;
    }

    // ---- popups ----------------------------------------------------------------------------

    private final PopupNode.Listener popupListener = new PopupNode.Listener() {
        @Override
        public void reshaping(PopupNode node, double dx, double dy, double dw, double dh) {
            AnnotationUiLayer ui = uiLayerOf(node);
            if (ui != null) ui.setPopupReshape(node, dx, dy, dw, dh, layoutZoom);
        }

        @Override
        public void reshaped(PopupNode node, double dx, double dy, double dw, double dh) {
            AnnotationUiLayer ui = uiLayerOf(node);
            if (ui == null) return;
            ui.clearPopupReshape(layoutZoom);
            if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5 && Math.abs(dw) < 0.5 && Math.abs(dh) < 0.5) return;
            int pageIndex = ui.getPageIndex();
            java.awt.geom.Rectangle2D.Float rect = node.getPopup().getUserSpaceRectangle();
            double w = Math.max(PopupNode.MIN_WIDTH, rect.getWidth() + dw / layoutZoom);
            double h = Math.max(PopupNode.MIN_HEIGHT, rect.getHeight() + dh / layoutZoom);
            double[] anchor = ui.popupAnchor(node.getPopup());
            Page page = document.getPageTree().getPage(pageIndex);
            java.awt.geom.Rectangle2D target = placeUpright(page, anchor[0] + dx, anchor[1] + dy, w, h);
            getSkinnable().recordEdit(AnnotationEdits.popupRect(renderer::withAnnotationLock, page, pageIndex,
                    node.getPopup(), target));
        }

        @Override
        public void contentsEdited(PopupNode node, String contents) {
            AnnotationUiLayer ui = uiLayerOf(node);
            String current = node.getMarkup().getContents();
            if (ui == null || contents.equals(current == null ? "" : current)) return;
            Page page = document.getPageTree().getPage(ui.getPageIndex());
            getSkinnable().recordEdit(AnnotationEdits.contents(renderer::withAnnotationLock, page,
                    ui.getPageIndex(), node.getMarkup(), contents));
        }

        @Override
        public void minimised(PopupNode node) {
            getSkinnable().setPopupOpen(node.getMarkup(), false);
        }
    };

    private AnnotationUiLayer uiLayerOf(PopupNode node) {
        for (AnnotationUiLayer ui : uiLayers.values()) {
            if (ui.popupNodes().contains(node)) return ui;
        }
        return null;
    }

    /**
     * The user-space rectangle of size (w, h) points whose view-space bounds have their top-left at
     * (viewX, viewY) - for an upright popup at any page rotation.
     */
    private java.awt.geom.Rectangle2D placeUpright(Page page, double viewX, double viewY, double w, double h) {
        AffineTransform pageToView = PageTransforms.pageToView(page, getSkinnable().getPageBoundary(),
                layoutRotation, (float) layoutZoom);
        java.awt.geom.Rectangle2D origin = pageToView.createTransformedShape(
                new java.awt.geom.Rectangle2D.Double(0, 0, w, h)).getBounds2D();
        // shift the origin-placed rect so its view bounds start at (viewX, viewY), in user space.
        try {
            java.awt.geom.Point2D shift = pageToView.createInverse().deltaTransform(
                    new java.awt.geom.Point2D.Double(viewX - origin.getMinX(), viewY - origin.getMinY()), null);
            return new java.awt.geom.Rectangle2D.Double(shift.getX(), shift.getY(), w, h);
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return new java.awt.geom.Rectangle2D.Double(0, 0, w, h);
        }
    }

    /** All open popup nodes on visible pages; for tests. */
    List<PopupNode> openPopupNodes() {
        List<PopupNode> nodes = new ArrayList<>();
        uiLayers.values().forEach(ui -> nodes.addAll(ui.popupNodes()));
        return nodes;
    }

    // ---- creating annotations -----------------------------------------------------------------

    java.util.Collection<AnnotationUiLayer> uiLayers() {
        return uiLayers.values();
    }

    private AffineTransform pageToViewNow(Page page) {
        return PageTransforms.pageToView(page, getSkinnable().getPageBoundary(), layoutRotation, (float) layoutZoom);
    }

    private AffineTransform toPageSpaceNow(Page page) {
        return page.getToPageSpaceTransform(getSkinnable().getPageBoundary(), layoutRotation, (float) layoutZoom);
    }

    private AnnotationCreator.Style style(ToolMode mode) {
        PdfView control = getSkinnable();
        javafx.scene.paint.Color fx = control.getAnnotationColor();
        java.awt.Color color = fx != null
                ? new java.awt.Color((float) fx.getRed(), (float) fx.getGreen(), (float) fx.getBlue())
                : switch (mode) {
            case HIGHLIGHT, NOTE -> new java.awt.Color(255, 255, 0);
            case UNDERLINE -> new java.awt.Color(0, 102, 255);
            case FREE_TEXT -> java.awt.Color.BLACK;
            default -> new java.awt.Color(230, 0, 0);
        };
        int opacity = mode == ToolMode.HIGHLIGHT ? TextMarkupAnnotation.HIGHLIGHT_ALPHA : 255;
        return new AnnotationCreator.Style(control.getAnnotationAuthor(), color, opacity, 2f);
    }

    /** Adds a built annotation (with a popup for markup) as one undoable edit and selects it. */
    private void addCreated(int pageIndex, Page page, Annotation annotation, boolean popupOpen) {
        PopupAnnotation popup = annotation instanceof MarkupAnnotation markup
                && !(annotation instanceof FreeTextAnnotation)
                ? AnnotationCreator.popup(page.getLibrary(), markup, popupOpen, toPageSpaceNow(page)) : null;
        getSkinnable().recordEdit(AnnotationEdits.add(renderer::withAnnotationLock, page, pageIndex, annotation, popup));
        getSkinnable().selectAnnotation(annotation);
    }

    void createNote(int pageIndex, double viewX, double viewY) {
        Page page = document.getPageTree().getPage(pageIndex);
        TextAnnotation note = AnnotationCreator.note(page.getLibrary(), viewX, viewY, pageToViewNow(page),
                toPageSpaceNow(page), style(ToolMode.NOTE));
        addCreated(pageIndex, page, note, true);
        scheduleRefresh();
    }

    void createShape(int pageIndex, boolean ellipse, java.awt.geom.Rectangle2D viewRect) {
        Page page = document.getPageTree().getPage(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.shape(page.getLibrary(), ellipse, viewRect, toPageSpaceNow(page),
                style(ellipse ? ToolMode.ELLIPSE : ToolMode.RECTANGLE)), false);
    }

    void createLine(int pageIndex, Point2D start, Point2D end) {
        Page page = document.getPageTree().getPage(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.line(page.getLibrary(),
                new java.awt.geom.Point2D.Double(start.getX(), start.getY()),
                new java.awt.geom.Point2D.Double(end.getX(), end.getY()), toPageSpaceNow(page), style(ToolMode.LINE)), false);
    }

    void createInk(int pageIndex, java.awt.geom.GeneralPath viewPath) {
        Page page = document.getPageTree().getPage(pageIndex);
        addCreated(pageIndex, page, AnnotationCreator.ink(page.getLibrary(), viewPath, toPageSpaceNow(page),
                style(ToolMode.INK)), false);
    }

    void createFreeText(int pageIndex, double viewX, double viewY) {
        Page page = document.getPageTree().getPage(pageIndex);
        FreeTextAnnotation freeText = AnnotationCreator.freeText(page.getLibrary(), viewX, viewY, layoutZoom,
                toPageSpaceNow(page), style(ToolMode.FREE_TEXT));
        addCreated(pageIndex, page, freeText, false);
        editFreeText(new AnnotationHit(pageIndex, freeText));
    }

    /** Opens the inline editor over a free text annotation; the text commits as an undoable edit. */
    void editFreeText(AnnotationHit hit) {
        if (!(hit.annotation() instanceof FreeTextAnnotation freeText)) return;
        AnnotationUiLayer ui = uiLayers.get(hit.pageIndex());
        if (ui == null) return;
        Page page = document.getPageTree().getPage(hit.pageIndex());
        ui.openTextEditor(ui.viewBounds(freeText), freeText.getContents(), layoutZoom, text -> {
            String current = freeText.getContents() == null ? "" : freeText.getContents();
            if (text.equals(current)) return;
            getSkinnable().recordEdit(AnnotationEdits.freeTextContents(renderer::withAnnotationLock, page,
                    hit.pageIndex(), freeText, text, toPageSpaceNow(page)));
        });
    }

    /**
     * Turns the current text selection into highlight/underline/strike-out annotations, one per page
     * it spans (pages whose text isn't loaded are skipped), and clears the selection.
     *
     * @return the number created
     */
    int markupSelection(org.icepdf.core.pobjects.Name subtype) {
        DocumentSelection selection = getSkinnable().getTextSelection();
        if (selection == null || selection.isCollapsed() || document == null) return 0;
        ToolMode mode = TextMarkupAnnotation.SUBTYPE_HIGHLIGHT.equals(subtype) ? ToolMode.HIGHLIGHT
                : TextMarkupAnnotation.SUBTYPE_UNDERLINE.equals(subtype) ? ToolMode.UNDERLINE : ToolMode.STRIKE_OUT;
        int created = 0;
        Annotation last = null;
        for (int pageIndex = selection.startPage(); pageIndex <= selection.endPage(); pageIndex++) {
            TextSequence sequence = textLoader.get(pageIndex);
            org.icepdf.core.pobjects.graphics.text.OffsetRange range = selection.rangeForPage(pageIndex, sequence);
            if (range == null || range.isEmpty()) continue;
            Page page = document.getPageTree().getPage(pageIndex);
            AffineTransform pageToView = pageToViewNow(page);
            List<java.awt.geom.Rectangle2D> viewRects = new ArrayList<>();
            for (java.awt.geom.Rectangle2D r : sequence.rectsFor(range)) {
                viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
            }
            if (viewRects.isEmpty()) continue;
            TextMarkupAnnotation markup = AnnotationCreator.textMarkup(page.getLibrary(), subtype, viewRects,
                    sequence.extractText(range), toPageSpaceNow(page), style(mode));
            addCreated(pageIndex, page, markup, false);
            last = markup;
            created++;
        }
        getSkinnable().setTextSelection(null);
        if (last != null) getSkinnable().selectAnnotation(last);
        return created;
    }

    AnnotationEdits.Locker annotationLocker() {
        return renderer::withAnnotationLock;
    }

    void refreshAnnotationChrome() {
        uiLayers.values().forEach(ui -> {
            updateAnnotationChrome(ui, null);
            ui.refresh();
        });
    }

    /** Invalidates a page's annotation layers: they re-render; page content tiles are untouched. */
    void bumpAnnotationGeneration(int pageIndex) {
        annotationGenerations.merge(pageIndex, 1, Integer::sum);
        emptyAnnotationLayers.remove(pageIndex);
        scheduleRefresh();
    }

    private boolean isAnnotationLayerEmpty(int pageIndex, CacheKey.AnnotationLayer layer, int generation) {
        Map<CacheKey.AnnotationLayer, Integer> empty = emptyAnnotationLayers.get(pageIndex);
        return empty != null && Integer.valueOf(generation).equals(empty.get(layer));
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

    /** Page text is needed to select, or to draw a selection or search hits. */
    private boolean needsText() {
        PdfView control = getSkinnable();
        return control.getToolMode().selectsText() || control.getTextSelection() != null
                || !control.getSearchHits().isEmpty();
    }

    private void updateText(PageLayer layer) {
        if (!needsText()) {
            layer.setSelection(null, null);
            layer.setSearchHits(null, List.of());
            layer.setCaret(null);
            return;
        }
        int index = layer.getPageIndex();
        textLoader.request(index);
        TextSequence sequence = textLoader.get(index);
        DocumentSelection selection = getSkinnable().getTextSelection();
        layer.setSelection(sequence, selection != null ? selection.rangeForPage(index, sequence) : null);
        layer.setSearchHits(sequence, getSkinnable().searchHitsOnPage(index));
        // the caret sits at the focus end, as in the Swing viewer.
        boolean caretHere = isCaretActive() && sequence != null && selection.getFocusPage() == index;
        layer.setCaret(caretHere ? sequence.caretRect(
                new Caret(Math.min(selection.getFocusOffset(), sequence.length()), Bias.FORWARD)) : null);
        layer.setCaretVisible(caretOn);
    }

    /** A caret shows in the select tool, while focused, when there is a selection. */
    private boolean isCaretActive() {
        PdfView control = getSkinnable();
        return control.getToolMode().selectsText() && control.isFocused()
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
        pagesGroup.getChildren().clear();
        layers.clear();
        uiGroup.getChildren().clear();
        uiLayers.clear();
    }

    // ---- annotations ----------------------------------------------------------------------

    /** An annotation and the page it is on. */
    record AnnotationHit(int pageIndex, Annotation annotation) {
    }

    /**
     * The topmost annotation under a viewport point that the view lets you interact with: visible,
     * not a popup (popups are nodes) and not a form widget (a later phase).  Only pages already
     * initialised by rendering are searched, so this never parses on the FX thread.
     */
    AnnotationHit annotationAtViewport(double vx, double vy) {
        PagePoint point = pageAtViewport(vx, vy);
        if (point == null) return null;
        Page page = document.getPageTree().getPage(point.pageIndex());
        if (page == null || !page.isInitiated()) return null;
        List<Annotation> annotations = page.getAnnotations();
        if (annotations == null) return null;
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation a = annotations.get(i);
            // MarkupGlueAnnotation is core's synthetic popup connector for printing; its rect spans
            // the markup and its popup, so it would swallow clicks meant for the markup.
            if (a == null || a.isDeleted() || a instanceof PopupAnnotation || a instanceof AbstractWidgetAnnotation
                    || a instanceof org.icepdf.core.pobjects.annotations.MarkupGlueAnnotation) continue;
            if (!a.allowScreenNormalMode() || a.getFlagHidden()) continue;
            java.awt.geom.Rectangle2D.Float r = a.getUserSpaceRectangle();
            if (r != null && r.contains(point.x(), point.y())) return new AnnotationHit(point.pageIndex(), a);
        }
        return null;
    }

    AnnotationHit annotationAt(double x, double y) {
        Point2D local = viewport.parentToLocal(x, y);
        return annotationAtViewport(local.getX(), local.getY());
    }

    /** Sets the hover outline (null clears). */
    void setHovered(AnnotationHit hit) {
        if (java.util.Objects.equals(hit, hovered)) return;
        hovered = hit;
        uiLayers.values().forEach(ui -> updateAnnotationChrome(ui, null));
    }

    private void updateAnnotationChrome(AnnotationUiLayer ui, Page page) {
        int index = ui.getPageIndex();
        AbstractWidgetAnnotation field = getSkinnable().getFocusedField();
        ui.setFocusedField(field != null && isOnPage(field, index) ? field : null);
        ui.setHovered(hovered != null && hovered.pageIndex() == index ? hovered.annotation() : null);
        Annotation selected = getSkinnable().getSelectedAnnotation();
        Annotation onPage = selected != null && isOnPage(selected, index) ? selected : null;
        ui.setSelected(onPage, isEditable(onPage));
    }

    private boolean isOnPage(Annotation annotation, int pageIndex) {
        Page page = document.getPageTree().getPage(pageIndex);
        List<Annotation> annotations = page != null && page.isInitiated() ? page.getAnnotations() : null;
        return annotations != null && !annotation.isDeleted() && annotations.contains(annotation);
    }

    /** The UI layer of a visible page, or null. */
    AnnotationUiLayer uiLayer(int pageIndex) {
        return uiLayers.get(pageIndex);
    }

    /** Follows the link (or action) under a viewport point; true if there was one. */
    boolean activateLinkAtViewport(double vx, double vy) {
        AnnotationHit hit = annotationAtViewport(vx, vy);
        if (hit == null || !isActionable(hit.annotation())) return false;
        getSkinnable().performAnnotationAction(hit.annotation());
        return true;
    }

    static boolean isActionable(Annotation annotation) {
        return annotation instanceof LinkAnnotation
                || (annotation.getAction() != null && !(annotation instanceof MarkupAnnotation));
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

    private void resetRasters() {
        emptyAnnotationLayers.clear();
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
        ToolMode mode = getSkinnable().getToolMode();
        textSelectHandler.setMarkupSubtype(switch (mode) {
            case HIGHLIGHT -> TextMarkupAnnotation.SUBTYPE_HIGHLIGHT;
            case UNDERLINE -> TextMarkupAnnotation.SUBTYPE_UNDERLINE;
            case STRIKE_OUT -> TextMarkupAnnotation.SUBTYPE_STRIKE_OUT;
            default -> null;
        });
        createHandler.setMode(mode);
        ToolHandler next = mode == ToolMode.PAN ? panHandler : mode.selectsText() ? textSelectHandler : createHandler;
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
        if (gestureHandler != null) {
            if (e.getButton() != gestureButton) return; // a second button during a gesture
            // the same button again: its release went elsewhere (a popup the press opened, such as a
            // combo field's drop-down, takes it), so that gesture is over.
            gestureHandler = null;
        }
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
        // typing in a popup note (or any text input inside the view) is not a view command.
        if (e.getTarget() instanceof javafx.scene.control.TextInputControl) return;
        AbstractWidgetAnnotation focusedField = getSkinnable().getFocusedField();
        if (focusedField != null && fieldEditor == null
                && (e.getCode() == KeyCode.SPACE || (e.getCode() == KeyCode.ENTER
                && FormController.kindOf(focusedField) == FormController.FieldKind.PUSH))) {
            // Space toggles a focused check box or radio and presses a focused button (Enter too).
            FormController.FieldKind kind = FormController.kindOf(focusedField);
            if (kind == FormController.FieldKind.CHECK || kind == FormController.FieldKind.RADIO
                    || kind == FormController.FieldKind.PUSH) {
                actuate(focusedField, kind);
                e.consume();
                return;
            }
        }
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
        if (e.getCode() == KeyCode.TAB && getSkinnable().isFormFieldsEditable()
                && getSkinnable().getToolMode() != null && !getSkinnable().getToolMode().createsAnnotations()
                && !fieldsInTabOrder().isEmpty()) {
            if (e.isShiftDown()) getSkinnable().focusPreviousField();
            else getSkinnable().focusNextField();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE && getSkinnable().getFocusedField() != null) {
            getSkinnable().clearFieldFocus();
            e.consume();
            return;
        }
        if (e.isShortcutDown() && (e.getCode() == KeyCode.Z || e.getCode() == KeyCode.Y)) {
            // undo/redo annotation edits, in any tool
            if (e.getCode() == KeyCode.Y || e.isShiftDown()) getSkinnable().redo();
            else getSkinnable().undo();
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
