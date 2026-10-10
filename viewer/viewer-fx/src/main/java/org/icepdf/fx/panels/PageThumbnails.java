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
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small page renderings for a thumbnail strip: painted on a background pool with core's page
 * painter, kept in a bounded LRU, and handed back on the FX thread.  Requests for a document that
 * has since been replaced, or for a {@link #invalidate() previous version} of its pages, are dropped.
 */
final class PageThumbnails {

    private static final Logger logger = Logger.getLogger(PageThumbnails.class.getName());
    private static final int CACHE_SIZE = 256;

    private final ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "icepdf-fx-thumbnails");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Map<Key, Image> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Image> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private final Map<Key, Request> pending = new LinkedHashMap<>();
    private Document document;
    private int version;

    /** What a thumbnail was rendered for. */
    private record Key(int page, int widthPx, float rotation, int boundary, int version) {
    }

    /** A render in flight and everyone waiting on it (a list asks again as it recycles cells). */
    private static final class Request {
        final List<Consumer<Image>> waiters = new ArrayList<>();
        Future<?> future;
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
        pending.values().forEach(request -> request.future.cancel(true));
        pending.clear();
    }

    /**
     * FX thread: the thumbnail of {@code page}, {@code widthPx} device pixels wide; null when it
     * isn't ready yet, in which case {@code ready} is called with it later (on the FX thread).
     */
    Image get(int page, int widthPx, float rotation, int boundary, Consumer<Image> ready) {
        Key key = new Key(page, widthPx, rotation, boundary, version);
        Image image = cache.get(key);
        if (image != null || document == null) return image;
        Request waiting = pending.get(key);
        if (waiting != null) {
            waiting.waiters.add(ready);
            return null;
        }
        Request request = new Request();
        request.waiters.add(ready);
        pending.put(key, request);
        Document doc = document;
        request.future = pool.submit(() -> {
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
                    p.paint(g, GraphicsRenderingHints.SCREEN, boundary, rotation, zoom, true, false);
                } finally {
                    g.dispose();
                }
                pixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> pending.remove(key));
                return;
            } catch (Throwable e) {
                logger.log(Level.FINE, e, () -> "Thumbnail of page " + (page + 1) + " failed");
                Platform.runLater(() -> pending.remove(key));
                return;
            }
            int w = width, h = height;
            int[] argb = pixels;
            Platform.runLater(() -> {
                pending.remove(key);
                if (key.version() != version || doc != document) return;
                WritableImage rendered = new WritableImage(w, h);
                rendered.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbPreInstance(), argb, 0, w);
                cache.put(key, rendered);
                request.waiters.forEach(waiter -> waiter.accept(rendered));
            });
        });
        return null;
    }

    /** Stops the pool; nothing more is rendered. */
    void shutdown() {
        invalidate();
        pool.shutdownNow();
    }
}
