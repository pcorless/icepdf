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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads pages' reading-order {@link TextSequence}s off the FX thread.
 * <p>
 * {@code page.getViewText()} initialises the page if the tile renderer hasn't yet, and building
 * the sequence runs the reading-order sort, which is not free on dense pages - neither belongs on
 * the FX thread.  Results are handed over with {@code Platform.runLater}, which also safely
 * publishes the lazily built sequence.
 * <p>
 * Only what is wanted is kept: a selection is just offsets ({@code DocumentSelection}), so a page's
 * text can be dropped when it scrolls away and reloaded when it returns - nothing needs pinning.
 * Requests, results and retention are confined to the FX thread.
 */
final class PageTextLoader {

    private static final Logger logger = Logger.getLogger(PageTextLoader.class.getName());

    interface Sink {
        /** On the FX thread; {@code sequence} is null for a page with no text layer. */
        void textReady(int pageIndex, TextSequence sequence);
    }

    private final Sink sink;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "icepdf-fx-text");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    // FX thread only.  A page with no text maps to Optional.empty(), so it isn't re-requested.
    private final Map<Integer, Optional<TextSequence>> loaded = new HashMap<>();
    private final Map<Integer, Future<?>> pending = new HashMap<>();
    private final Set<Integer> failed = new HashSet<>();
    // off-screen pages asked for explicitly (keyboard caret moves); kept through retain() so they
    // survive until used.  Small and most-recent-first.
    private static final int MAX_EXTRA = 16;
    private final LinkedHashSet<Integer> extra = new LinkedHashSet<>();
    private Document document;

    PageTextLoader(Sink sink) {
        this.sink = sink;
    }

    void setDocument(Document document) {
        clear();
        this.document = document;
    }

    /** The page's sequence if loaded, else null (also null for a page with no text). */
    TextSequence get(int pageIndex) {
        Optional<TextSequence> sequence = loaded.get(pageIndex);
        return sequence != null ? sequence.orElse(null) : null;
    }

    boolean isLoaded(int pageIndex) {
        return loaded.containsKey(pageIndex);
    }

    /** Queues the page unless it is loaded, pending or has failed. */
    void request(int pageIndex) {
        Document doc = document;
        if (doc == null || loaded.containsKey(pageIndex) || pending.containsKey(pageIndex)
                || failed.contains(pageIndex)) return;
        Future<?>[] self = new Future<?>[1];
        Future<?> future = executor.submit(() -> load(doc, pageIndex, self));
        self[0] = future;
        pending.put(pageIndex, future);
    }

    /** Like {@link #request}, and keeps the page through {@link #retain} even while off-screen. */
    void requestKept(int pageIndex) {
        extra.remove(pageIndex);
        extra.add(pageIndex);
        if (extra.size() > MAX_EXTRA) extra.remove(extra.iterator().next());
        request(pageIndex);
    }

    /** Cancels pending loads and drops loaded text for pages no longer wanted. */
    void retain(IntPredicate wanted) {
        IntPredicate keep = page -> wanted.test(page) || extra.contains(page);
        pending.entrySet().removeIf(e -> {
            if (keep.test(e.getKey())) return false;
            e.getValue().cancel(true);
            return true;
        });
        loaded.keySet().removeIf(page -> !keep.test(page));
    }

    /**
     * Extracts a selection's text on the loader's worker, loading (and initialising) every page it
     * spans - for select-all on a long document that is real work, hence asynchronous.  Runs on the
     * same single thread as page loads, so a page's sequence is never built by two threads at once.
     */
    CompletableFuture<String> extractAsync(DocumentSelection selection) {
        Document doc = document;
        if (doc == null || selection == null) return CompletableFuture.completedFuture("");
        CompletableFuture<String> result = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                result.complete(selection.extractText(page -> {
                    try {
                        if (page >= doc.getNumberOfPages()) return null;
                        PageText text = doc.getPageViewText(page);
                        return text != null ? text.getTextSequence() : null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException("interrupted");
                    }
                }));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    void clear() {
        pending.values().forEach(f -> f.cancel(true));
        pending.clear();
        loaded.clear();
        failed.clear();
        extra.clear();
    }

    void shutdown() {
        clear();
        executor.shutdownNow();
    }

    private void load(Document doc, int pageIndex, Future<?>[] self) {
        try {
            Page page = doc.getPageTree().getPage(pageIndex);
            PageText text = page != null ? page.getViewText() : null;
            TextSequence sequence = text != null ? text.getTextSequence() : null;
            if (Thread.currentThread().isInterrupted()) return;
            Platform.runLater(() -> {
                // superseded: cancelled, re-requested, or the document changed.
                if (doc != document || pending.get(pageIndex) != self[0]) return;
                pending.remove(pageIndex);
                loaded.put(pageIndex, Optional.ofNullable(sequence));
                sink.textReady(pageIndex, sequence);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed loading text of page " + (pageIndex + 1), e);
            Platform.runLater(() -> {
                if (doc != document || pending.get(pageIndex) != self[0]) return;
                pending.remove(pageIndex);
                failed.add(pageIndex);
            });
        }
    }
}
