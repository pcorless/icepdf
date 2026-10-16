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
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.fx.render.RenderService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small page renderings for a thumbnail strip: painted with core's page painter as
 * {@link RenderService.Kind#THUMBNAIL THUMBNAIL} tasks on the shared render pool, behind the view's
 * own tiles, kept in a byte-bounded LRU, and handed back on the FX thread.  Requests for a document
 * that has since been replaced, or for a {@link #invalidate() previous version} of its pages, are
 * dropped; a request whose every waiter has {@link #cancel cancelled} (its cells scrolled away) is
 * stopped, so flinging through a long strip doesn't queue a paint for every page passed.
 */
final class PageThumbnails {

    private static final Logger logger = Logger.getLogger(PageThumbnails.class.getName());
    private static final long BUDGET_BYTES = 32L << 20;

    private final RenderService service;
    private final RenderService.Owner owner;
    private final Map<Key, Image> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long cacheBytes;
    private final Map<Key, Request> pending = new LinkedHashMap<>();
    // which request each waiter is on, so a recycled cell can withdraw.
    private final Map<Consumer<Image>, Key> waiting = new IdentityHashMap<>();
    private Document document;
    private int version;

    /** What a thumbnail was rendered for; {@code rotation} is core's (counter-clockwise). */
    private record Key(int page, int widthPx, float rotation, int boundary, int version) {
    }

    /** A render in flight and everyone waiting on it (a list asks again as it recycles cells). */
    private static final class Request {
        final List<Consumer<Image>> waiters = new ArrayList<>();
        Future<?> future;
    }

    /** @param owner the strip's view, whose foreground status its thumbnails share */
    PageThumbnails(RenderService service, RenderService.Owner owner) {
        this.service = service;
        this.owner = owner;
    }

    /** FX thread: switches document, dropping everything of the old one. */
    void setDocument(Document document) {
        this.document = document;
        invalidate();
    }

    /** FX thread: forgets every rendering - the pages draw differently now (a layer toggled). */
    void invalidate() {
        version++;
        cache.clear();
        cacheBytes = 0;
        pending.values().forEach(request -> service.cancel(request.future));
        pending.clear();
        waiting.clear();
    }

    /**
     * FX thread: the thumbnail of {@code page}, {@code widthPx} device pixels wide; null when it
     * isn't ready yet, in which case {@code ready} is called with it later (on the FX thread) unless
     * it is {@link #cancel cancelled} first.
     *
     * @param rotation core's rotation (counter-clockwise degrees)
     */
    Image get(int page, int widthPx, float rotation, int boundary, Consumer<Image> ready) {
        Key key = new Key(page, widthPx, rotation, boundary, version);
        Image image = cache.get(key);
        if (image != null || document == null) return image;
        waiting.put(ready, key);
        Request inFlight = pending.get(key);
        if (inFlight != null) {
            inFlight.waiters.add(ready);
            return null;
        }
        Request request = new Request();
        request.waiters.add(ready);
        pending.put(key, request);
        Document doc = document;
        request.future = service.submit(owner, RenderService.Kind.THUMBNAIL, "p" + (page + 1) + " thumbnail", () -> {
            int[] pixels;
            int width, height;
            try {
                Page p = doc.getPageTree().getPage(page);
                p.init();
                PDimension size = p.getSize(boundary, rotation, 1f);
                float zoom = (float) (widthPx / Math.max(1, size.getWidth()));
                width = Math.max(1, widthPx);
                height = Math.max(1, (int) Math.ceil(size.getHeight() * zoom));
                BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = buffer.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, width, height);
                    g.setClip(0, 0, width, height);
                    service.paint(() -> {
                        p.paint(g, GraphicsRenderingHints.SCREEN, boundary, rotation, zoom, true, false);
                        return null;
                    });
                } finally {
                    g.dispose();
                }
                pixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> pending.remove(key, request));
                return;
            } catch (Throwable e) {
                logger.log(Level.FINE, e, () -> "Thumbnail of page " + (page + 1) + " failed");
                Platform.runLater(() -> pending.remove(key, request));
                return;
            }
            int w = width, h = height;
            int[] argb = pixels;
            Platform.runLater(() -> {
                if (!pending.remove(key, request)) return; // cancelled or invalidated meanwhile
                if (key.version() != version || doc != document) return;
                // zero-copy: the image shares the painted int[].
                WritableImage rendered = new WritableImage(new PixelBuffer<>(w, h, IntBuffer.wrap(argb),
                        PixelFormat.getIntArgbPreInstance()));
                put(key, rendered, (long) argb.length * Integer.BYTES);
                for (Consumer<Image> waiter : request.waiters) {
                    waiting.remove(waiter, key);
                    waiter.accept(rendered);
                }
            });
        });
        return null;
    }

    /** FX thread: {@code ready} no longer wants its thumbnail; the render stops if nobody else does. */
    void cancel(Consumer<Image> ready) {
        Key key = waiting.remove(ready);
        Request request = key != null ? pending.get(key) : null;
        if (request == null) return;
        request.waiters.remove(ready);
        if (request.waiters.isEmpty()) {
            service.cancel(request.future);
            pending.remove(key);
        }
    }

    private void put(Key key, Image image, long bytes) {
        cache.put(key, image);
        cacheBytes += bytes;
        // access order: least recently used first.
        Iterator<Map.Entry<Key, Image>> it = cache.entrySet().iterator();
        while (cacheBytes > BUDGET_BYTES && cache.size() > 1 && it.hasNext()) {
            Image eldest = it.next().getValue();
            cacheBytes -= (long) eldest.getWidth() * (long) eldest.getHeight() * Integer.BYTES;
            it.remove();
        }
    }

    /** Cancels everything queued; the pool's threads are shared and stay. */
    void dispose() {
        invalidate();
    }
}
