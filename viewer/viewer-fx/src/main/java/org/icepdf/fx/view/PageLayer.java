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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Affine;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * The scene graph for one visible page, in page view space (logical px, origin top-left):
 * <ol>
 *     <li>paper - a plain white rectangle, so an unrendered page is never a hole;</li>
 *     <li>preview - the low-res whole-page render, mapped onto the current zoom/rotation;</li>
 *     <li>stale tiles - tiles from the previous zoom/rotation, mapped the same way, until the
 *     current tiles cover the viewport;</li>
 *     <li>tiles - current tiles at 1:1 device pixels;</li>
 *     <li>blend-annotation tiles - opaque copies of the page with its blend-mode appearances
 *     (Multiply highlights) composited in by core, only where those annotations are;</li>
 *     <li>annotation tiles - plain appearances on transparent, over everything below;</li>
 *     <li>overlay, in PDF user space: the text selection highlight, then the application's
 *     {@link PageOverlayFactory} content;</li>
 *     <li>caret, in page view space so it stays one device pixel wide at any zoom.</li>
 * </ol>
 * Selection and caret are plain fills and lines with no blend mode: Prism renders a blended or
 * effected node through an intermediate texture sized to its bounds, which for a page-sized
 * selection at deep zoom is the O(zoom²) trap again.
 * Nothing here is sized by zoom into a texture: no effects, no node caching, so a 4000% page costs
 * only the tiles on screen.  Effects or {@code setCache(true)} on a page-sized node would rasterise
 * the whole zoomed page - the same O(zoom²) trap the Swing viewer hit in GH-495.
 */
final class PageLayer extends Group {

    private final int pageIndex;
    private final Page page;
    private final Rectangle paper = new Rectangle();
    private final Rectangle clip = new Rectangle();
    private final ImageView preview = new ImageView();
    private final Affine previewTransform = new Affine();
    private final TileSet content = new TileSet();
    private final TileSet annotations = new TileSet();
    private final TileSet blendAnnotations = new TileSet();
    private final Group overlay = new Group();
    private final Affine overlayTransform = new Affine();
    private final Path fieldHighlights = new Path();
    private final Path searchHits = new Path();
    private final Path selection = new Path();
    private final Group appOverlay = new Group();
    private final Line caret = new Line();

    private CacheKey.Params params;
    private AffineTransform pageToView;
    private AffineTransform previewPageToView;
    private boolean hasPreview;
    private boolean overlayCreated;
    // what the selection path currently shows, to rebuild only on change.
    private TextSequence selectionSequence;
    private OffsetRange selectionRange;
    private Rectangle2D.Double caretRect;
    private TextSequence searchSequence;
    private List<SearchHit> searchHitList = List.of();

    PageLayer(int pageIndex, Page page) {
        this.pageIndex = pageIndex;
        this.page = page;
        setAutoSizeChildren(false);
        paper.setFill(Color.WHITE);
        preview.getTransforms().add(previewTransform);
        preview.setSmooth(true);
        preview.setVisible(false);
        overlay.getTransforms().add(overlayTransform);
        java.awt.Color c = Page.selectionColor;
        selection.setFill(Color.rgb(c.getRed(), c.getGreen(), c.getBlue(), Page.SELECTION_ALPHA));
        selection.setStroke(null);
        selection.setMouseTransparent(true);
        java.awt.Color h = Page.highlightColor;
        searchHits.setFill(Color.rgb(h.getRed(), h.getGreen(), h.getBlue(), Page.SELECTION_ALPHA));
        searchHits.setStroke(null);
        searchHits.setMouseTransparent(true);
        fieldHighlights.setFill(Color.rgb(204, 215, 255, 0.55));
        fieldHighlights.setStroke(null);
        fieldHighlights.setMouseTransparent(true);
        overlay.getChildren().addAll(fieldHighlights, searchHits, selection, appOverlay);
        caret.setStroke(Color.BLACK);
        caret.setMouseTransparent(true);
        caret.setVisible(false);
        getChildren().addAll(paper, preview, content.root, blendAnnotations.root, annotations.root, overlay, caret);
        setClip(clip);
    }

    int getPageIndex() {
        return pageIndex;
    }

    Page getPage() {
        return page;
    }

    CacheKey.Params getParams() {
        return params;
    }

    AffineTransform getPageToView() {
        return pageToView;
    }

    /**
     * Moves the layer to new render parameters.  Current tiles become the stale layer (mapped onto
     * the new transform) unless there are none, in which case the existing stale tiles are kept and
     * re-mapped - so a continuous pinch-zoom keeps showing the last real tiles.
     */
    void setParams(CacheKey.Params newParams, double width, double height) {
        paper.setWidth(width);
        paper.setHeight(height);
        clip.setWidth(width);
        clip.setHeight(height);
        if (newParams.equals(params)) return;
        params = newParams;
        pageToView = PageTransforms.pageToView(page, newParams.boundary(), newParams.rotation(), newParams.zoom());
        content.setToken(newParams, newParams);
        annotations.setToken(new AnnotationToken(newParams, annotations.generation), newParams);
        blendAnnotations.setToken(new AnnotationToken(newParams, blendAnnotations.generation), newParams);
        if (previewPageToView != null) {
            PageTransforms.setFx(previewTransform, PageTransforms.between(previewPageToView, pageToView));
        }
        PageTransforms.setFx(overlayTransform, pageToView);
        caret.setStrokeWidth(1 / params.scale());
        placeCaret();
    }

    TileSet content() {
        return content;
    }

    /** The annotation tile set for a layer, moved to {@code generation} (older tiles go stale). */
    TileSet annotations(CacheKey.AnnotationLayer layer, int generation) {
        TileSet set = layer == CacheKey.AnnotationLayer.BLEND ? blendAnnotations : annotations;
        if (set.generation != generation) {
            set.generation = generation;
            set.setToken(new AnnotationToken(params, generation), params);
        }
        return set;
    }

    private record AnnotationToken(CacheKey.Params params, int generation) {
    }

    /**
     * One kind of tile on this page: the current tiles, plus the previous set kept as "stale" -
     * mapped onto the current transform - until the current set covers the viewport, so a zoom, a
     * rotation or an annotation edit never flashes a hole.
     */
    final class TileSet {
        final Group root = new Group();
        private final Group current = new Group();
        private final Group stale = new Group();
        private final Affine staleTransform = new Affine();
        // present keys; the view is null for an EMPTY tile (nothing to draw there).
        private final Map<CacheKey, ImageView> views = new HashMap<>();
        private Object token;
        private CacheKey.Params staleParams;
        int generation;

        TileSet() {
            stale.getTransforms().add(staleTransform);
            root.getChildren().addAll(stale, current);
        }

        /** Current tiles become stale if the token (params, plus generation for annotations) changes. */
        void setToken(Object newToken, CacheKey.Params newParams) {
            if (newToken.equals(token)) return;
            CacheKey.Params oldParams = token == null ? null
                    : token instanceof AnnotationToken a ? a.params() : (CacheKey.Params) token;
            if (!current.getChildren().isEmpty()) {
                stale.getChildren().setAll(current.getChildren());
                current.getChildren().clear();
                staleParams = oldParams;
            }
            views.clear();
            token = newToken;
            if (staleParams != null && newParams != null) {
                AffineTransform from = PageTransforms.pageToView(page, staleParams.boundary(),
                        staleParams.rotation(), staleParams.zoom());
                PageTransforms.setFx(staleTransform, PageTransforms.between(from, pageToView));
            }
        }

        boolean has(CacheKey key) {
            return views.containsKey(key);
        }

        /** Adds a tile at the current params; {@link RasterBuffer#EMPTY} records it with no node. */
        void add(CacheKey key, TileGrid.Tile tile, RasterBuffer buffer) {
            if (views.containsKey(key)) return;
            if (buffer == RasterBuffer.EMPTY) {
                views.put(key, null);
                return;
            }
            ImageView view = new ImageView(buffer.getImage());
            double scale = params.scale();
            view.setX(tile.x() / scale);
            view.setY(tile.y() / scale);
            view.setFitWidth(tile.width() / scale);
            view.setFitHeight(tile.height() / scale);
            // 1:1 device pixels: filtering would only blur.
            view.setSmooth(false);
            views.put(key, view);
            current.getChildren().add(view);
        }

        /** Drops tile nodes not in {@code keep} (they stay in the cache). */
        void retain(Set<? extends CacheKey> keep) {
            Iterator<Map.Entry<CacheKey, ImageView>> it = views.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<CacheKey, ImageView> e = it.next();
                if (!keep.contains(e.getKey())) {
                    if (e.getValue() != null) current.getChildren().remove(e.getValue());
                    it.remove();
                }
            }
        }

        void clearStale() {
            stale.getChildren().clear();
            staleParams = null;
        }

        /** Removes everything, current and stale (annotations switched off). */
        void clear() {
            views.clear();
            current.getChildren().clear();
            clearStale();
        }
    }

    boolean hasPreview() {
        return hasPreview;
    }

    void setPreview(RasterBuffer buffer, float previewZoom) {
        previewPageToView = PageTransforms.pageToView(page, params.boundary(), 0f, previewZoom);
        PageTransforms.setFx(previewTransform, PageTransforms.between(previewPageToView, pageToView));
        preview.setImage(buffer.getImage());
        preview.setVisible(true);
        hasPreview = true;
    }

    /** Installs the overlay once per layer; the factory isn't re-called on zoom or rotation. */
    void ensureOverlay(PageOverlayFactory factory) {
        if (overlayCreated || factory == null) return;
        overlayCreated = true;
        Node node = factory.createOverlay(pageIndex, page);
        if (node != null) appOverlay.getChildren().setAll(node);
    }

    void resetOverlay() {
        appOverlay.getChildren().clear();
        overlayCreated = false;
    }

    /**
     * Shows {@code range} of the page's text highlighted; null or empty clears.  Rebuilt only when
     * the sequence or range changes - zoom and rotation are carried by the overlay transform.
     */
    void setSelection(TextSequence sequence, OffsetRange range) {
        if (range != null && range.isEmpty()) range = null;
        if (sequence == null) range = null;
        if (Objects.equals(range, selectionRange) && (range == null || sequence == selectionSequence)) return;
        selectionSequence = sequence;
        selectionRange = range;
        List<PathElement> elements = new ArrayList<>();
        if (range != null) {
            for (Rectangle2D.Double r : sequence.rectsFor(range)) {
                elements.add(new MoveTo(r.x, r.y));
                elements.add(new LineTo(r.x + r.width, r.y));
                elements.add(new LineTo(r.x + r.width, r.y + r.height));
                elements.add(new LineTo(r.x, r.y + r.height));
                elements.add(new ClosePath());
            }
        }
        selection.getElements().setAll(elements);
    }

    /**
     * Highlights search hits, under the selection (the current hit is the selection).  Rebuilt only
     * when the hit list or sequence changes.
     */
    void setSearchHits(TextSequence sequence, List<SearchHit> hits) {
        if (sequence == null) hits = List.of();
        if (hits == searchHitList && (hits.isEmpty() || sequence == searchSequence)) return;
        searchSequence = sequence;
        searchHitList = hits;
        List<PathElement> elements = new ArrayList<>();
        for (SearchHit hit : hits) {
            for (Rectangle2D.Double r : sequence.rectsFor(hit.range())) {
                elements.add(new MoveTo(r.x, r.y));
                elements.add(new LineTo(r.x + r.width, r.y));
                elements.add(new LineTo(r.x + r.width, r.y + r.height));
                elements.add(new LineTo(r.x, r.y + r.height));
                elements.add(new ClosePath());
            }
        }
        searchHits.getElements().setAll(elements);
    }

    private List<java.awt.geom.Rectangle2D> fieldRects = List.of();

    /** Tints these user-space rectangles (fillable form fields); empty clears.  Rebuilt on change only. */
    void setFieldHighlights(List<java.awt.geom.Rectangle2D> rects) {
        if (rects.equals(fieldRects)) return;
        fieldRects = rects;
        List<PathElement> elements = new ArrayList<>();
        for (java.awt.geom.Rectangle2D r : rects) {
            elements.add(new MoveTo(r.getX(), r.getY()));
            elements.add(new LineTo(r.getMaxX(), r.getY()));
            elements.add(new LineTo(r.getMaxX(), r.getMaxY()));
            elements.add(new LineTo(r.getX(), r.getMaxY()));
            elements.add(new ClosePath());
        }
        fieldHighlights.getElements().setAll(elements);
    }

    /** True if the layer currently highlights something; for tests and diagnostics. */
    boolean hasSelection() {
        return selectionRange != null;
    }

    /**
     * Places the caret at a user-space caret rectangle (from {@code TextSequence.caretRect}); null
     * hides it.  Drawn as the rectangle's centre line, mapped to view space.
     */
    void setCaret(Rectangle2D.Double userRect) {
        caretRect = userRect;
        placeCaret();
    }

    /** Blink phase; the caret only shows if one is placed. */
    void setCaretVisible(boolean visible) {
        caret.setVisible(visible && caretRect != null);
    }

    private void placeCaret() {
        if (caretRect == null || pageToView == null) {
            caret.setVisible(false);
            return;
        }
        // the long axis is the line's height for horizontal text, its width for vertical text.
        boolean tall = caretRect.height >= caretRect.width;
        double cx = caretRect.getCenterX();
        double cy = caretRect.getCenterY();
        javafx.geometry.Point2D a = tall
                ? PageTransforms.pageToView(pageToView, cx, caretRect.getMinY())
                : PageTransforms.pageToView(pageToView, caretRect.getMinX(), cy);
        javafx.geometry.Point2D b = tall
                ? PageTransforms.pageToView(pageToView, cx, caretRect.getMaxY())
                : PageTransforms.pageToView(pageToView, caretRect.getMaxX(), cy);
        caret.setStartX(a.getX());
        caret.setStartY(a.getY());
        caret.setEndX(b.getX());
        caret.setEndY(b.getY());
    }
}
