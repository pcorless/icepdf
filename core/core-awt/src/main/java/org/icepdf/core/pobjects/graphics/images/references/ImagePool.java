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
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

/**
 * Caches decoded images keyed by their PDF object {@link Reference}.  Values are
 * held via {@link SoftReference} so an image survives normal garbage collection
 * (letting a page repaint, or a shared XObject on another page, reuse it without
 * re-decoding) but is reclaimed automatically when the heap is under pressure --
 * so the pool self-limits its memory footprint without an explicit size cap.
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

    public ImagePool() {
        fCache = new ConcurrentHashMap<>(50);
    }

    public void put(Reference ref, BufferedImage image) {
        if (ref != null && image != null) {
            // copy the reference for the key so the map never holds the caller's
            // Reference instance (keeps parity with the previous behaviour).
            fCache.put(new Reference(ref.getObjectNumber(), ref.getGenerationNumber()),
                    new SoftReference<>(image));
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
            fCache.remove(ref, softReference);
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
