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
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders page tiles and previews off the FX thread with the ICEpdf Java2D core.
 * <p>
 * Missing tiles of a page are coalesced into regions and each region is painted with a single
 * {@code page.paint}: the display list is traversed once per region rather than once per tile
 * (RasterBench measured per-tile painting at 7-20× the cost).  The region is then sliced into
 * {@link RasterBuffer} tiles.  Regions are capped at {@link #MAX_REGION} device px a side so one
 * job's scratch raster stays bounded on huge screens; larger tile sets split into several jobs,
 * which also lets them paint in parallel.
 * <p>
 * Every paint gets a real clip, so the core's own offscreens (transparency groups, soft masks) are
 * sized to the region rather than the zoomed page.
 * <p>
 * Annotations are not part of the page tiles: they render into their own
 * {@link CacheKey.AnnotationTile}s, so editing an annotation re-renders that cheap layer and never
 * page content - the Swing viewer likewise paints page buffers without annotations.  Plain
 * appearances go on a transparent layer.  Blend-mode appearances (Multiply highlights) can't: core's
 * blend composite over a transparent backdrop multiplies by zero and paints black.  So, like the
 * Swing viewer's {@code paintBlendedAnnotation}, they are rendered over the real page pixels - the
 * cached content tiles, read-only once rendered, so still no page re-render - and shown as opaque
 * copies where blend annotations are.  {@code -Dorg.icepdf.fx.view.singlePassAnnotations=true}
 * restores the single-pass render (annotations baked into the page tiles) for A/B comparison.
 * <p>
 * Requests, cancellation and delivery are all on the FX thread; only the paint runs on workers.
 */
public final class TileRenderer {

    private static final Logger logger = Logger.getLogger(TileRenderer.class.toString());

    private static final java.util.concurrent.atomic.AtomicLong CONTENT_RENDERS = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Diagnostics: page-content region paints so far, process-wide.  Annotation edits must not move
     * it - they only re-render annotation layers.
     */
    public static long contentRenderCount() {
        return CONTENT_RENDERS.get();
    }

    /** Diagnostic: bake annotations into the page tiles instead of separate layers. */
    public static final boolean SINGLE_PASS_ANNOTATIONS = Boolean.getBoolean("org.icepdf.fx.view.singlePassAnnotations");

    /** Max region edge in device px: 4096² ARGB is a 64MB scratch raster per job. */
    public static final int MAX_REGION = 4096;

    /** Receives finished rasters on the FX thread. */
    public interface Sink {
        /** A page tile or annotation tile; {@link RasterBuffer#EMPTY} for an annotation tile with nothing on it. */
        void tileReady(CacheKey key, RasterBuffer buffer);

        /**
         * The page has no annotations in this layer at all, at this generation: no need to request
         * its tiles again at any zoom until the generation changes.
         */
        default void annotationLayerEmpty(int pageIndex, CacheKey.AnnotationLayer layer, int generation) {
        }

        /**
         * @param zoom the zoom the preview was rendered at, unrotated; its pixels are
         *             {@code PageTransforms.pageToView(page, boundary, 0, zoom)} space.
         */
        void previewReady(CacheKey.Preview key, RasterBuffer buffer, float zoom);

        /**
         * A job failed; its keys are now {@link #hasFailed failed}.
         *
         * @param outOfMemory the render ran out of heap; caches should shed what they can
         */
        void failed(boolean outOfMemory);
    }

    private final class Job {
        final List<CacheKey> keys;
        Future<?> future;

        Job(List<CacheKey> keys) {
            this.keys = keys;
        }
    }

    private final Sink sink;
    private final ExecutorService tileExecutor;
    private final ExecutorService previewExecutor;
    // FX thread only.
    private final Map<CacheKey, Job> inFlight = new HashMap<>();
    private final Set<Job> jobs = new HashSet<>();
    // keys whose render threw; not retried until the document or rasters are reset, so a page the
    // core can't paint doesn't become a render loop.
    private final Set<CacheKey> failed = new HashSet<>();
    private volatile Document document;
    private volatile boolean paintAnnotations = true;
    // annotation rendering is serialised per page: render() may lazily build appearance state.
    private final Map<Integer, Object> annotationLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * What a region paint covered.
     *
     * @param anyOnPage false if the page has nothing of this kind at all
     * @param areas     device-space areas painted (page-tile coordinates), or null for "all of it";
     *                  tiles touching none of them are delivered as {@link RasterBuffer#EMPTY}
     */
    private record PaintResult(boolean anyOnPage, List<Rectangle2D> areas) {
        static final PaintResult ALL = new PaintResult(true, null);
        static final PaintResult NOTHING = new PaintResult(false, List.of());
    }

    /** Paints one region into a prepared graphics (clipped, region-translated, device-scaled). */
    private interface RegionPainter {
        PaintResult paint(Graphics2D g, Page page) throws InterruptedException;
    }

    public TileRenderer(int threads, Sink sink) {
        this.sink = sink;
        tileExecutor = Executors.newFixedThreadPool(threads, daemonThreads("icepdf-fx-tile"));
        previewExecutor = Executors.newSingleThreadExecutor(daemonThreads("icepdf-fx-preview"));
    }

    /** Half the cores, 1 to 4: page paints are memory-bandwidth heavy and image decode shares the box. */
    public static int defaultThreads() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    /** Zoom at which a page's unrotated long side is {@code longSide} px: the preview's zoom. */
    public static float previewZoom(Page page, int boundary, int longSide) {
        PDimension unit = page.getSize(boundary, 0f, 1f);
        return (float) (longSide / Math.max(1, Math.max(unit.getWidth(), unit.getHeight())));
    }

    /** Switches document, cancelling all work for the previous one. */
    public void setDocument(Document document) {
        cancelAll();
        this.document = document;
    }

    /**
     * Whether annotation appearances are drawn, in the annotation layers (and in the page tiles
     * and previews only under {@link #SINGLE_PASS_ANNOTATIONS}).
     */
    public void setPaintAnnotations(boolean paintAnnotations) {
        this.paintAnnotations = paintAnnotations;
    }

    public boolean isPending(CacheKey key) {
        return inFlight.containsKey(key);
    }

    public boolean hasFailed(CacheKey key) {
        return failed.contains(key);
    }

    /**
     * Queues the given tiles of one page, skipping any already in flight.
     *
     * @param grid  the page's tile grid at {@code params}
     * @param tiles tiles to render (normally the visible ones missing from the cache)
     */
    public void requestTiles(int pageIndex, CacheKey.Params params, TileGrid grid, List<TileGrid.Tile> tiles) {
        boolean annotations = paintAnnotations && SINGLE_PASS_ANNOTATIONS;
        submitRegions(pageIndex, grid, tiles, t -> new CacheKey.Tile(pageIndex, params, t.column(), t.row()),
                params, (g, page) -> {
                    CONTENT_RENDERS.incrementAndGet();
                    page.paint(g, GraphicsRenderingHints.SCREEN, params.boundary(), params.rotation(),
                            params.zoom(), annotations, false);
                    return PaintResult.ALL;
                }, null, null);
    }

    /**
     * Queues tiles of one page's annotation layer: annotations only, on transparent, through the page
     * transform - no content stream, so cheap.
     *
     * @param excluded  annotations to leave out (one being dragged as a live node); may be empty
     * @param backdrops for {@link CacheKey.AnnotationLayer#BLEND}, the content tile under each requested
     *                  tile (required: blend appearances composite against the page); null for NORMAL
     */
    public void requestAnnotationTiles(int pageIndex, CacheKey.Params params, CacheKey.AnnotationLayer layer,
                                       int generation, TileGrid grid, List<TileGrid.Tile> tiles,
                                       Set<Annotation> excluded, Map<TileGrid.Tile, RasterBuffer> backdrops) {
        submitRegions(pageIndex, grid, tiles,
                t -> new CacheKey.AnnotationTile(pageIndex, params, layer, t.column(), t.row(), generation),
                params, (g, page) -> paintAnnotations(g, page, pageIndex, params, layer, excluded),
                () -> sink.annotationLayerEmpty(pageIndex, layer, generation), backdrops);
    }

    private PaintResult paintAnnotations(Graphics2D g, Page page, int pageIndex, CacheKey.Params params,
                                         CacheKey.AnnotationLayer layer, Set<Annotation> excluded) {
        List<Annotation> annotations = page.getAnnotations();
        if (annotations == null || annotations.isEmpty()) return PaintResult.NOTHING;
        boolean any = false;
        List<Rectangle2D> areas = new ArrayList<>();
        synchronized (annotationLocks.computeIfAbsent(pageIndex, k -> new Object())) {
            AffineTransform pageToView = page.getPageTransform(params.boundary(), params.rotation(), params.zoom());
            // page user space -> page device px, for the painted-area bookkeeping.
            AffineTransform toDevice = AffineTransform.getScaleInstance(params.scale(), params.scale());
            toDevice.concatenate(pageToView);
            g.transform(pageToView);
            float totalRotation = page.getTotalRotation(params.rotation());
            for (Annotation annotation : annotations) {
                if (annotation == null || annotation.isDeleted()) continue;
                boolean blend = annotation.appearanceHasBlendMode();
                if (blend != (layer == CacheKey.AnnotationLayer.BLEND)) continue;
                any = true;
                if (excluded.contains(annotation)) continue;
                annotation.render(g, GraphicsRenderingHints.SCREEN, totalRotation, params.zoom(), false);
                Rectangle2D bounds = toDevice.createTransformedShape(annotation.getUserSpaceRectangle()).getBounds2D();
                // borders and anti-aliasing reach a little past the rect; NoZoom icons further.
                double margin = 4 + (annotation.getFlagNoZoom() ? 64 * params.scale() : 0);
                areas.add(new Rectangle2D.Double(bounds.getX() - margin, bounds.getY() - margin,
                        bounds.getWidth() + 2 * margin, bounds.getHeight() + 2 * margin));
            }
        }
        return any ? new PaintResult(true, areas) : PaintResult.NOTHING;
    }

    /**
     * Runs a mutation of a page's annotations (an edit) under the lock annotation tile jobs hold
     * while painting that page, so no worker sees the list or an appearance half-changed.  Called on
     * the FX thread; waits at most for one in-progress annotation paint of that page.
     */
    public void withAnnotationLock(int pageIndex, Runnable mutation) {
        synchronized (annotationLocks.computeIfAbsent(pageIndex, k -> new Object())) {
            mutation.run();
        }
    }

    /**
     * Renders one annotation alone, for the live node that follows a move/resize drag while the
     * annotation layers re-render without it.
     *
     * @param region device-pixel region of the page to render (the annotation's bounds, clipped to
     *               the viewport so a huge annotation at deep zoom stays bounded)
     * @param ready  receives the raster on the FX thread
     */
    public void requestAnnotationProxy(int pageIndex, CacheKey.Params params, Annotation annotation,
                                       TileGrid.Region region, java.util.function.Consumer<RasterBuffer> ready) {
        Document doc = document;
        if (doc == null || region.isEmpty()) return;
        tileExecutor.submit(() -> {
            try {
                Page page = doc.getPageTree().getPage(pageIndex);
                RasterBuffer buffer = new RasterBuffer(region.width(), region.height());
                Graphics2D g = buffer.getBufferedImage().createGraphics();
                try {
                    g.setClip(0, 0, region.width(), region.height());
                    g.translate(-region.x(), -region.y());
                    g.scale(params.scale(), params.scale());
                    synchronized (annotationLocks.computeIfAbsent(pageIndex, k -> new Object())) {
                        g.transform(page.getPageTransform(params.boundary(), params.rotation(), params.zoom()));
                        annotation.render(g, GraphicsRenderingHints.SCREEN, page.getTotalRotation(params.rotation()),
                                params.zoom(), false);
                    }
                } finally {
                    g.dispose();
                }
                Platform.runLater(() -> ready.accept(buffer));
            } catch (Throwable e) {
                logger.log(Level.FINE, "Annotation proxy render failed", e);
            }
        });
    }

    /**
     * Buckets tiles into blocks of at most MAX_REGION px, one job per block: each job paints its
     * region once and slices it into tiles.
     */
    private void submitRegions(int pageIndex, TileGrid grid, List<TileGrid.Tile> tiles,
                               java.util.function.Function<TileGrid.Tile, CacheKey> keyOf, CacheKey.Params params,
                               RegionPainter painter, Runnable onNothingToPaint,
                               Map<TileGrid.Tile, RasterBuffer> backdrops) {
        Document doc = document;
        if (doc == null || tiles.isEmpty()) return;
        int block = Math.max(1, MAX_REGION / grid.getTileSize());
        Map<Long, List<TileGrid.Tile>> blocks = new LinkedHashMap<>();
        for (TileGrid.Tile t : tiles) {
            CacheKey key = keyOf.apply(t);
            if (inFlight.containsKey(key) || failed.contains(key)) continue;
            long blockId = ((long) (t.row() / block) << 32) | (t.column() / block);
            blocks.computeIfAbsent(blockId, k -> new ArrayList<>()).add(t);
        }
        for (List<TileGrid.Tile> blockTiles : blocks.values()) {
            List<CacheKey> keys = new ArrayList<>(blockTiles.size());
            for (TileGrid.Tile t : blockTiles) keys.add(keyOf.apply(t));
            Job job = new Job(keys);
            TileGrid.Region region = TileGrid.union(blockTiles);
            job.future = tileExecutor.submit(() -> renderRegion(doc, job, pageIndex, params, region, blockTiles,
                    keys, painter, onNothingToPaint, backdrops));
            track(job);
        }
    }

    /**
     * Queues a whole-page preview, rendered unrotated with its long side at {@code longSide} device
     * px; the view rotates and scales it as a node.
     */
    public void requestPreview(int pageIndex, int boundary, int longSide) {
        Document doc = document;
        CacheKey.Preview key = new CacheKey.Preview(pageIndex, boundary);
        if (doc == null || inFlight.containsKey(key) || failed.contains(key)) return;
        Job job = new Job(List.of(key));
        job.future = previewExecutor.submit(() -> renderPreview(doc, job, key, longSide));
        track(job);
    }

    /** Cancels queued or running jobs none of whose keys are still wanted. */
    public void retain(Predicate<CacheKey> stillWanted) {
        List<Job> drop = new ArrayList<>();
        for (Job job : jobs) {
            boolean wanted = false;
            for (CacheKey k : job.keys) {
                if (stillWanted.test(k)) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted) drop.add(job);
        }
        drop.forEach(this::cancel);
    }

    /** Cancels everything and forgets failures. */
    public void cancelAll() {
        new ArrayList<>(jobs).forEach(this::cancel);
        failed.clear();
    }

    public void shutdown() {
        cancelAll();
        tileExecutor.shutdownNow();
        previewExecutor.shutdownNow();
    }

    private void track(Job job) {
        jobs.add(job);
        for (CacheKey k : job.keys) inFlight.put(k, job);
    }

    private void cancel(Job job) {
        // interrupt: page.init and page.paint are interruptible.
        job.future.cancel(true);
        untrack(job);
    }

    private void untrack(Job job) {
        jobs.remove(job);
        for (CacheKey k : job.keys) inFlight.remove(k, job);
    }

    private void renderRegion(Document doc, Job job, int pageIndex, CacheKey.Params params,
                              TileGrid.Region region, List<TileGrid.Tile> tiles, List<CacheKey> keys,
                              RegionPainter painter, Runnable onNothingToPaint,
                              Map<TileGrid.Tile, RasterBuffer> backdrops) {
        try {
            Page page = doc.getPageTree().getPage(pageIndex);
            page.init();
            BufferedImage scratch = new BufferedImage(region.width(), region.height(),
                    BufferedImage.TYPE_INT_ARGB_PRE);
            if (backdrops != null) {
                // seed with the page pixels under the region (content tiles never change once rendered).
                int[] target = RasterBuffer.pixelsOf(scratch);
                for (TileGrid.Tile t : tiles) {
                    RasterBuffer backdrop = backdrops.get(t);
                    if (backdrop != null) {
                        backdrop.copyInto(target, region.width(), t.x() - region.x(), t.y() - region.y());
                    }
                }
            }
            Graphics2D g = scratch.createGraphics();
            PaintResult result;
            try {
                g.setClip(0, 0, region.width(), region.height());
                g.translate(-region.x(), -region.y());
                g.scale(params.scale(), params.scale());
                result = painter.paint(g, page);
            } finally {
                g.dispose();
            }
            if (Thread.currentThread().isInterrupted()) return;
            Map<CacheKey, RasterBuffer> out = new LinkedHashMap<>();
            int[] pixels = RasterBuffer.pixelsOf(scratch);
            for (int i = 0; i < tiles.size(); i++) {
                TileGrid.Tile t = tiles.get(i);
                out.put(keys.get(i), touches(result, t)
                        ? RasterBuffer.slice(pixels, region.width(), t.x() - region.x(), t.y() - region.y(),
                        t.width(), t.height())
                        : RasterBuffer.EMPTY);
            }
            boolean nothing = !result.anyOnPage();
            Platform.runLater(() -> {
                if (!jobs.contains(job)) return; // cancelled after the paint finished
                untrack(job);
                if (nothing && onNothingToPaint != null) onNothingToPaint.run();
                out.forEach(sink::tileReady);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            fail(job, "page " + (pageIndex + 1) + " region " + region, e);
        }
    }

    private static boolean touches(PaintResult result, TileGrid.Tile tile) {
        if (!result.anyOnPage()) return false;
        if (result.areas() == null) return true;
        for (Rectangle2D area : result.areas()) {
            if (area.intersects(tile.x(), tile.y(), tile.width(), tile.height())) return true;
        }
        return false;
    }

    private void renderPreview(Document doc, Job job, CacheKey.Preview key, int longSide) {
        try {
            Page page = doc.getPageTree().getPage(key.pageIndex());
            page.init();
            float zoom = previewZoom(page, key.boundary(), longSide);
            PDimension size = page.getSize(key.boundary(), 0f, zoom);
            int w = Math.max(1, (int) Math.ceil(size.getWidth()));
            int h = Math.max(1, (int) Math.ceil(size.getHeight()));
            RasterBuffer buffer = new RasterBuffer(w, h);
            Graphics2D g = buffer.getBufferedImage().createGraphics();
            try {
                g.setClip(0, 0, w, h);
                // without annotations: those live in their own layers, so an edit never stales a preview.
                page.paint(g, GraphicsRenderingHints.SCREEN, key.boundary(), 0f, zoom,
                        paintAnnotations && SINGLE_PASS_ANNOTATIONS, false);
            } finally {
                g.dispose();
            }
            if (Thread.currentThread().isInterrupted()) return;
            Platform.runLater(() -> {
                if (!jobs.contains(job)) return;
                untrack(job);
                sink.previewReady(key, buffer, zoom);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            fail(job, "preview of page " + (key.pageIndex() + 1), e);
        }
    }

    private void fail(Job job, String what, Throwable e) {
        if (e instanceof OutOfMemoryError) {
            logger.log(Level.WARNING, "Out of memory rendering " + what);
        } else {
            logger.log(Level.WARNING, "Failed rendering " + what, e);
        }
        Platform.runLater(() -> {
            if (!jobs.contains(job)) return;
            untrack(job);
            failed.addAll(job.keys);
            sink.failed(e instanceof OutOfMemoryError);
        });
    }
}
