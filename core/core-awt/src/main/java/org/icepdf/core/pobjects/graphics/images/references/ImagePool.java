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
import org.icepdf.core.util.Defs;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

/**
 * The Image pool is a Map of the most recently used images.  The pools size
 * by default is setup to be 1/4 the heap size.  So as the pool grows it
 * will self trim to keep the memory foot print at the specified max.  The
 * max pool size can be specified by using the org.icepdf.core.views.imagePoolSize
 * system property.  The value is specified in MB.
 * <br>
 * The pool also contains an executor pool for processing Images.  The executor
 * allows the pageInitialization thread to continue while the executor processes
 * the image data on another thread.
 * <br>
 * Teh pool size can be set with the system property  org.icepdf.core.views.imagePoolSize
 * where the default value is 1/4 the heap size.  The pool set can be specified in
 * using a int value representing the desired size in MB.
 * <br>
 * The pool can also be disabled using the boolean system property
 * org.icepdf.core.views.imagePoolEnabled=false.  The default state is for the
 * ImagePool to be enabled.
 *
 * @since 5.0
 */
public class ImagePool {
    private static final Logger log =
            Logger.getLogger(ImagePool.class.getName());

    // Image pool
    private final Map<Reference, BufferedImage> fCache;

    // Decodes currently in flight, keyed by image object reference, so that two references to the same image
    // (e.g. the same XObject drawn on multiple pages, or an eager pre-decode racing the content parser) share a
    // single decode instead of each starting their own.
    private final Map<Reference, FutureTask<BufferedImage>> inProgress = new ConcurrentHashMap<>();


    private static final boolean enabled;
    static {
        // enable/disable the image pool all together.
        enabled = Defs.booleanProperty("org.icepdf.core.views.imagePoolEnabled", true);
    }


    public ImagePool() {
        fCache = Collections.synchronizedMap(new WeakHashMap<>(50));
    }

    public void put(Reference ref, BufferedImage image) {
        // create a new reference so we don't have a hard link to the page
        // which will likely keep a page from being GC'd.
        if (enabled) {
//            synchronized (fCache) {
                fCache.put(new Reference(ref.getObjectNumber(), ref.getGenerationNumber()), image);
//            }
        }
    }

    public BufferedImage get(Reference ref) {
        if (enabled) {
                return fCache.get(ref);
        } else {
            return null;
        }
    }

    public boolean containsKey(Reference ref) {
        return enabled && fCache.containsKey(ref);
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
        if (!enabled || ref == null) {
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
