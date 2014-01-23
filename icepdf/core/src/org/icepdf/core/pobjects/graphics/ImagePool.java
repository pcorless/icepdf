/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Defs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The Image pool is a Map of the most recently used images.  The pools size
 * by default is setup to be 1/4 the heap size.  So as the pool grows it
 * will self trim to keep the memory foot print at the specified max.  The
 * max pool size can be specified by using the org.icepdf.core.views.imagePoolSize
 * system property.  The value is specified in MB.
 * <p/>
 * The pool also contains an executor pool for processing Images.  The executor
 * allows the pageInitialization thread to continue while the executor processes
 * the image data on another thread.
 * <p/>
 * Teh pool size can be set with the system property  org.icepdf.core.views.imagePoolSize
 * where the default value is 1/4 the heap size.  The pool set can be specified in
 * using a int value representing the desired size in MB.
 * <p/>
 * The pool can also be disabled using the boolean system property
 * org.icepdf.core.views.imagePoolEnabled=false.  The default state is for the
 * ImagePool to be enabled.
 *
 * @since 5.0
 */
public class ImagePool {
    private static final Logger log =
            Logger.getLogger(ImagePool.class.toString());

    // Image pool
    private final LinkedHashMap<Reference, BufferedImage> fCache;

    private static int defaultSize;

    private static boolean enabled;

    static {
        // Default size is 1/4 of heap size.
        defaultSize = (int) ((Runtime.getRuntime().maxMemory() / 1024L / 1024L) / 4L);
        defaultSize = Defs.intProperty("org.icepdf.core.views.imagePoolSize", defaultSize);
        // enable/disable the image pool all together.
        enabled = Defs.booleanProperty("org.icepdf.core.views.imagePoolEnabled", true);
    }

    public ImagePool() {
        this(defaultSize * 1024 * 1024);
    }

    public ImagePool(long maxCacheSize) {
        fCache = new MemoryImageCache(maxCacheSize);
    }

    public void put(Reference ref, BufferedImage image) {
        // create a new reference so we don't have a hard link to the page
        // which will likely keep a page from being GC'd.
        if (enabled) {
            fCache.put(new Reference(ref.getObjectNumber(), ref.getGenerationNumber()), image);
        }
    }

    public BufferedImage get(Reference ref) {
        if (enabled) {
            return fCache.get(ref);
        } else {
            return null;
        }
    }

    private static class MemoryImageCache extends LinkedHashMap<Reference, BufferedImage> {
        private final long maxCacheSize;
        private long currentCacheSize;

        public MemoryImageCache(long maxCacheSize) {
            super(16, 0.75f, true);
            this.maxCacheSize = maxCacheSize;
        }

        @Override
        public BufferedImage put(Reference key, BufferedImage value) {
            if (containsKey(key)) {
                BufferedImage removed = remove(key);
                currentCacheSize = currentCacheSize - sizeOf(removed) + sizeOf(value);
                super.put(key, value);
                return removed;
            } else {
                currentCacheSize += sizeOf(value);
                return super.put(key, value);
            }
        }

        private long sizeOf(BufferedImage image) {
            if (image == null) {
                return 0L;
            }

            DataBuffer dataBuffer = image.getRaster().getDataBuffer();
            int dataTypeSize;
            switch (dataBuffer.getDataType()) {
                case DataBuffer.TYPE_BYTE:
                    dataTypeSize = 1;
                    break;
                case DataBuffer.TYPE_SHORT:
                case DataBuffer.TYPE_USHORT:
                    dataTypeSize = 2;
                    break;
                case DataBuffer.TYPE_INT:
                case DataBuffer.TYPE_FLOAT:
                    dataTypeSize = 4;
                    break;
                case DataBuffer.TYPE_DOUBLE:
                case DataBuffer.TYPE_UNDEFINED:
                default:
                    dataTypeSize = 8;
                    break;
            }
            return dataBuffer.getSize() * dataTypeSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Reference, BufferedImage> eldest) {
            boolean remove = currentCacheSize > maxCacheSize;
            if (remove) {
                long size = sizeOf(eldest.getValue());
                currentCacheSize -= size;
            }
            return remove;
        }
    }
}
