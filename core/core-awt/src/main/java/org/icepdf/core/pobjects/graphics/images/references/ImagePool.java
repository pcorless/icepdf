/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Reference;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

/**
 * Caches decoded images keyed by their PDF object {@link Reference}.  Values are
 * held via {@link SoftReference} so an image survives normal garbage collection
 * (letting a page repaint, or a shared XObject on another page, reuse it without
 * re-decoding) but is reclaimed automatically when the heap is under pressure; a byte
 * budget, least recently used first out, also caps it (see {@link #BUDGET_PROPERTY}).
 * <br>
 * The pool also tracks decodes currently in flight ({@link #registerInProgress})
 * so that two references to the same image de-duplicate onto a single decode
 * instead of racing on the shared {@link ImageStream} and each building their own
 * copy.  This in-flight de-duplication is required for correct concurrent
 * rendering, which is why the pool is always active (an earlier
 * {@code org.icepdf.core.views.imagePoolEnabled=false} switch, which disabled the
 * de-duplication along with the cache, was removed).
 *
 * @since 5.0
 */
public class ImagePool {

    // Image pool.  Values are held via SoftReference so a decoded image survives
    // normal garbage collection (letting a page repaint / a shared XObject on
    // another page reuse it without re-decoding) but is reclaimed when the heap is
    // genuinely under pressure.  The previous implementation used a WeakHashMap
    // keyed by a freshly-allocated Reference that nothing else strongly held, so
    // every entry was collectable on the very next GC -- under the allocation churn
    // of zooming, entries were purged as fast as they were inserted and get()
    // returned null, forcing constant re-decodes (and masking as a timing bug that
    // "went away" when a breakpoint changed GC timing).
    private final Map<Reference, SoftReference<BufferedImage>> fCache;

    // Decodes currently in flight, keyed by image object reference, so that two references to the same image
    // (e.g. the same XObject drawn on multiple pages, or an eager pre-decode racing the content parser) share a
    // single decode instead of each starting their own.
    private final Map<Reference, FutureTask<BufferedImage>> inProgress = new ConcurrentHashMap<>();

    // Soft references alone don't bound the pool: G1 clears them only when the heap is all but full,
    // so scrolling a document of scans fills the whole heap with decoded images, every collection
    // then runs against a full heap, and an allocation that fails while a native decoder holds the
    // GC locker throws OutOfMemoryError without the soft references ever being cleared.  So the
    // pool also keeps a byte budget, least recently used first out; past it an image is dropped
    // from the pool (and re-decoded if drawn again).  {@value #BUDGET_PROPERTY} sets the budget in
    // MB, an eighth of the max heap by default: GH-608 mip levels ride on top (about a third more),
    // beside the viewer's own raster cache, the document bytes and the decodes in flight.
    public static final String BUDGET_PROPERTY = "org.icepdf.core.images.poolBudget";

    private final long budgetBytes;
    // access order, eldest first; guarded by itself.
    private final LinkedHashMap<Reference, Long> sizes = new LinkedHashMap<>(64, 0.75f, true);
    private long usedBytes;

    public ImagePool() {
        this(Long.getLong(BUDGET_PROPERTY, Runtime.getRuntime().maxMemory() / 8 >> 20) << 20);
    }

    /**
     * @param budgetBytes the most decoded image bytes the pool keeps
     */
    public ImagePool(long budgetBytes) {
        this.budgetBytes = budgetBytes;
        fCache = new ConcurrentHashMap<>(50);
    }

    /** Decoded bytes of an image: its raster's data buffer. */
    static long byteSize(BufferedImage image) {
        DataBuffer buffer = image.getRaster().getDataBuffer();
        return (long) buffer.getSize() * buffer.getNumBanks() * DataBuffer.getDataTypeSize(buffer.getDataType()) / 8;
    }

    /** Decoded image bytes the pool is keeping. */
    public long getUsedBytes() {
        synchronized (sizes) {
            return usedBytes;
        }
    }

    public void put(Reference ref, BufferedImage image) {
        if (ref != null && image != null) {
            // copy the reference for the key so the map never holds the caller's
            // Reference instance (keeps parity with the previous behaviour).
            Reference key = new Reference(ref.getObjectNumber(), ref.getGenerationNumber());
            fCache.put(key, new SoftReference<>(image));
            synchronized (sizes) {
                Long old = sizes.put(key, byteSize(image));
                usedBytes += sizes.get(key) - (old != null ? old : 0);
                // the newest entry stays even if it alone is over the budget: it is being drawn.
                Iterator<Map.Entry<Reference, Long>> it = sizes.entrySet().iterator();
                while (usedBytes > budgetBytes && sizes.size() > 1 && it.hasNext()) {
                    Map.Entry<Reference, Long> eldest = it.next();
                    if (eldest.getKey().equals(key)) continue;
                    usedBytes -= eldest.getValue();
                    fCache.remove(eldest.getKey());
                    it.remove();
                }
            }
        }
    }

    public BufferedImage get(Reference ref) {
        if (ref == null) {
            return null;
        }
        SoftReference<BufferedImage> softReference = fCache.get(ref);
        if (softReference == null) {
            return null;
        }
        BufferedImage image = softReference.get();
        if (image == null) {
            // the soft reference was cleared under memory pressure; drop the dead
            // entry so the map doesn't accumulate empty holders.
            if (fCache.remove(ref, softReference)) {
                synchronized (sizes) {
                    Long size = sizes.remove(ref);
                    if (size != null) usedBytes -= size;
                }
            }
        } else {
            synchronized (sizes) {
                // a hit makes it the most recently used.
                sizes.get(ref);
            }
        }
        return image;
    }

    public boolean containsKey(Reference ref) {
        return get(ref) != null;
    }

    /**
     * Registers an in-flight decode for the given image reference if none is already running.
     *
     * @param ref  image object reference; a null reference (e.g. inline image) is never de-duplicated.
     * @param task the decode task the caller is about to run.
     * @return an existing in-flight decode for this reference to wait on instead, or null if the caller should
     * run its own {@code task} (which it has now registered).
     */
    public FutureTask<BufferedImage> registerInProgress(Reference ref, FutureTask<BufferedImage> task) {
        if (ref == null) {
            return null;
        }
        return inProgress.putIfAbsent(ref, task);
    }

    /**
     * Removes the in-flight marker for a reference once its decode has completed (success, failure or cancel).
     */
    public void removeInProgress(Reference ref) {
        if (ref != null) {
            inProgress.remove(ref);
        }
    }
}
