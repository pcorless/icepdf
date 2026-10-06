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
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.*;
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
 * Requests, cancellation and delivery are all on the FX thread; only the paint runs on workers.
 */
public final class TileRenderer {

    private static final Logger logger = Logger.getLogger(TileRenderer.class.toString());

    /** Max region edge in device px: 4096² ARGB is a 64MB scratch raster per job. */
    public static final int MAX_REGION = 4096;

    /** Receives finished rasters on the FX thread. */
    public interface Sink {
        void tileReady(CacheKey.Tile key, RasterBuffer buffer);

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

    /** Whether annotation appearance streams are part of the page raster. */
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
        Document doc = document;
        if (doc == null || tiles.isEmpty()) return;
        // bucket into blocks of at most MAX_REGION px so each job's region stays bounded.
        int block = Math.max(1, MAX_REGION / grid.getTileSize());
        Map<Long, List<TileGrid.Tile>> blocks = new LinkedHashMap<>();
        for (TileGrid.Tile t : tiles) {
            CacheKey.Tile key = new CacheKey.Tile(pageIndex, params, t.column(), t.row());
            if (inFlight.containsKey(key) || failed.contains(key)) continue;
            long blockId = ((long) (t.row() / block) << 32) | (t.column() / block);
            blocks.computeIfAbsent(blockId, k -> new ArrayList<>()).add(t);
        }
        for (List<TileGrid.Tile> blockTiles : blocks.values()) {
            List<CacheKey> keys = new ArrayList<>(blockTiles.size());
            for (TileGrid.Tile t : blockTiles) keys.add(new CacheKey.Tile(pageIndex, params, t.column(), t.row()));
            Job job = new Job(keys);
            TileGrid.Region region = TileGrid.union(blockTiles);
            job.future = tileExecutor.submit(() -> renderRegion(doc, job, pageIndex, params, region, blockTiles));
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
                              TileGrid.Region region, List<TileGrid.Tile> tiles) {
        try {
            Page page = doc.getPageTree().getPage(pageIndex);
            page.init();
            BufferedImage scratch = new BufferedImage(region.width(), region.height(),
                    BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = scratch.createGraphics();
            try {
                g.setClip(0, 0, region.width(), region.height());
                g.translate(-region.x(), -region.y());
                g.scale(params.scale(), params.scale());
                page.paint(g, GraphicsRenderingHints.SCREEN, params.boundary(), params.rotation(), params.zoom(),
                        paintAnnotations, false);
            } finally {
                g.dispose();
            }
            if (Thread.currentThread().isInterrupted()) return;
            int[] pixels = RasterBuffer.pixelsOf(scratch);
            Map<CacheKey.Tile, RasterBuffer> out = new LinkedHashMap<>();
            for (TileGrid.Tile t : tiles) {
                out.put(new CacheKey.Tile(pageIndex, params, t.column(), t.row()),
                        RasterBuffer.slice(pixels, region.width(), t.x() - region.x(), t.y() - region.y(),
                                t.width(), t.height()));
            }
            Platform.runLater(() -> {
                if (!jobs.contains(job)) return; // cancelled after the paint finished
                untrack(job);
                out.forEach(sink::tileReady);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            fail(job, "page " + (pageIndex + 1) + " region " + region, e);
        }
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
                page.paint(g, GraphicsRenderingHints.SCREEN, key.boundary(), 0f, zoom, paintAnnotations, false);
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
