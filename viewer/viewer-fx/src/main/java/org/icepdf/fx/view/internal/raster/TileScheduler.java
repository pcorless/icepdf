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
package org.icepdf.fx.view.internal.raster;

import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.fx.render.RenderService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Keeps the view's page tiles in step with what is on screen: shows cached tiles for the visible
 * grid cells, asks the renderer for the missing ones, and tracks each page's annotation
 * generation (bumped on every edit, part of the annotation tile keys), the annotation layers
 * found empty, and the annotations left out of the tiles while a live node shows them.  It owns
 * the view's tile cache and renderer.
 */
public final class TileScheduler {

    /** A page's tiles as drawn: content or one annotation layer. */
    public interface Tiles {
        boolean has(CacheKey key);

        /** Adds a tile at the current params; {@link RasterBuffer#EMPTY} records it with no node. */
        void add(CacheKey key, TileGrid.Tile tile, RasterBuffer buffer);

        /** Drops tile nodes not in {@code keep} (they stay in the cache). */
        void retain(Set<? extends CacheKey> keep);

        /** Drops the tiles kept from the previous params once the current ones are complete. */
        void clearStale();

        /** Removes everything, current and stale. */
        void clear();
    }

    /** A visible page's tile layers. */
    public interface TiledPage {
        Tiles content();

        Tiles annotations(CacheKey.AnnotationLayer layer, int generation);
    }

    private final TileCache cache = new TileCache(TileCache.defaultBudget());
    private final TileRenderer renderer;
    // per page, bumped on every annotation add/edit/delete; part of the annotation tile keys.
    private final Map<Integer, Integer> generations = new HashMap<>();
    // layers found to hold no annotations at a page's generation: not requested again.
    private final Map<Integer, Map<CacheKey.AnnotationLayer, Integer>> emptyLayers = new HashMap<>();
    // annotations left out of the annotation tiles while a live node (a drag, an editor) shows them.
    private final Map<Integer, Set<Annotation>> excluded = new HashMap<>();

    /** @param refresh asks the view to refresh (on the FX thread) when tiles arrive or a render fails */
    public TileScheduler(RenderService service, RenderService.Owner owner, Runnable refresh) {
        renderer = new TileRenderer(service, owner, new TileRenderer.Sink() {
            @Override
            public void tileReady(CacheKey key, RasterBuffer buffer) {
                cache.put(key, buffer);
                refresh.run();
            }

            @Override
            public void previewReady(CacheKey.Preview key, RasterBuffer buffer, float zoom) {
                cache.put(key, buffer);
                refresh.run();
            }

            @Override
            public void annotationLayerEmpty(int pageIndex, CacheKey.AnnotationLayer layer, int generation) {
                emptyLayers.computeIfAbsent(pageIndex, k -> new EnumMap<>(CacheKey.AnnotationLayer.class))
                        .put(layer, generation);
            }

            @Override
            public void failed(boolean outOfMemory, long retryAfterMs) {
                onRenderFailure.accept(outOfMemory && retryAfterMs > 0 ? Failure.LOW_MEMORY : Failure.NOT_DRAWN);
                if (outOfMemory) cache.trimToPinned();
                if (retryAfterMs > 0) {
                    // the keys stay requestable; ask again once the heap has had a moment.
                    PauseTransition retry = new PauseTransition(Duration.millis(retryAfterMs));
                    retry.setOnFinished(e -> refresh.run());
                    retry.play();
                } else {
                    refresh.run();
                }
            }
        });
    }

    /** What went wrong drawing: memory ran short (it will be tried again), or a part gave up for good. */
    public enum Failure { LOW_MEMORY, NOT_DRAWN }

    private java.util.function.Consumer<Failure> onRenderFailure = failure -> { };

    /** Told (on the FX thread) whenever a tile or preview fails. */
    public void setOnRenderFailure(java.util.function.Consumer<Failure> onRenderFailure) {
        this.onRenderFailure = onRenderFailure != null ? onRenderFailure : failure -> { };
    }

    public TileCache cache() {
        return cache;
    }

    public TileRenderer renderer() {
        return renderer;
    }

    // ---- annotation generations and exclusions

    public int generation(int pageIndex) {
        return generations.getOrDefault(pageIndex, 0);
    }

    /** Invalidates a page's annotation layers: they re-render; page content tiles are untouched. */
    public void bumpGeneration(int pageIndex) {
        generations.merge(pageIndex, 1, Integer::sum);
        emptyLayers.remove(pageIndex);
    }

    /** A new document: generations start over. */
    public void clearGenerations() {
        generations.clear();
    }

    private boolean isLayerEmpty(int pageIndex, CacheKey.AnnotationLayer layer, int generation) {
        Map<CacheKey.AnnotationLayer, Integer> empty = emptyLayers.get(pageIndex);
        return empty != null && Integer.valueOf(generation).equals(empty.get(layer));
    }

    /** Leaves an annotation out of the page's annotation tiles, or puts it back (bump to re-render). */
    public void setExcluded(int pageIndex, Annotation annotation, boolean exclude) {
        if (exclude) {
            excluded.computeIfAbsent(pageIndex, k -> new HashSet<>()).add(annotation);
        } else {
            Set<Annotation> set = excluded.get(pageIndex);
            if (set != null) set.remove(annotation);
        }
    }

    private Set<Annotation> excluded(int pageIndex) {
        return excluded.getOrDefault(pageIndex, Set.of());
    }

    // ---- reconciling

    /**
     * Shows a page's cached tiles over its visible part and requests the missing ones.
     *
     * @param x0             the visible part of the page, in page-local logical px ({@code x1 <= x0}
     *                       or {@code y1 <= y0} when none of it is visible)
     * @param wanted         collects every key on screen, for {@link #retain}
     * @param settling       a zoom is settling: show what is cached, request nothing
     * @param annotationsOn  annotations are painted as their own tile layers
     * @return true when every visible tile of the page is showing (or has failed)
     */
    public boolean update(TiledPage layer, int page, double width, double height, double x0, double y0,
                          double x1, double y1, CacheKey.Params params, Set<CacheKey> wanted, boolean settling,
                          boolean annotationsOn) {
        if (x1 <= x0 || y1 <= y0) {
            layer.content().retain(Set.of());
            if (annotationsOn) {
                for (CacheKey.AnnotationLayer kind : CacheKey.AnnotationLayer.values()) {
                    layer.annotations(kind, generation(page)).retain(Set.of());
                }
            }
            return true;
        }
        double scale = params.scale();
        TileGrid grid = new TileGrid(TileGrid.deviceSize(width, scale), TileGrid.deviceSize(height, scale),
                TileGrid.DEFAULT_TILE_SIZE);
        List<TileGrid.Tile> visible = grid.tilesIntersecting(x0 * scale, y0 * scale, (x1 - x0) * scale,
                (y1 - y0) * scale);

        boolean complete = updateSet(layer.content(), visible, wanted, settling,
                t -> new CacheKey.Tile(page, params, t.column(), t.row()),
                missing -> renderer.requestTiles(page, params, grid, missing));

        for (CacheKey.AnnotationLayer kind : CacheKey.AnnotationLayer.values()) {
            int generation = generation(page);
            Tiles set = layer.annotations(kind, generation);
            if (!annotationsOn || isLayerEmpty(page, kind, generation)) {
                set.clear();
                continue;
            }
            complete &= updateSet(set, visible, wanted, settling,
                    t -> new CacheKey.AnnotationTile(page, params, kind, t.column(), t.row(), generation),
                    missing -> renderer.requestAnnotationTiles(page, params, kind, generation, grid, missing,
                            excluded(page)));
        }
        return complete;
    }

    /**
     * Shows a tile set's cached tiles for the visible grid cells, requests the missing ones (unless a
     * zoom is settling) and drops nodes that scrolled away; stale tiles go once the set is complete.
     *
     * @return true when every visible tile is showing (or has failed)
     */
    private boolean updateSet(Tiles set, List<TileGrid.Tile> visible, Set<CacheKey> wanted, boolean settling,
                              Function<TileGrid.Tile, CacheKey> keyOf, Consumer<List<TileGrid.Tile>> request) {
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
        } else if (!settling && !missing.isEmpty()) {
            request.accept(missing);
        }
        return complete;
    }

    /** Keeps what is on screen: pins it in the cache and cancels render work for anything else. */
    public void retain(Set<CacheKey> wanted) {
        cache.pin(wanted);
        renderer.retain(wanted::contains);
    }

    /** Drops every raster and stops rendering (a view nobody can see). */
    public void release() {
        renderer.retain(key -> false);
        cache.clear();
    }

    /** Drops every raster and pending render, and the empty-layer findings. */
    public void reset() {
        emptyLayers.clear();
        renderer.cancelAll();
        cache.clear();
    }

    public void dispose() {
        renderer.cancelAll();
        cache.clear();
    }
}
