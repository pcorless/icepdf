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
package org.icepdf.fx.ri.prefs;

import org.icepdf.core.util.Library;
import org.icepdf.core.util.PoolSizing;
import org.icepdf.fx.render.RenderService;

/**
 * Thread counts from the viewer's settings: the page render pool every view shares, and core's
 * common and image pools.  Each is "automatic" (sized from the CPU) unless the user set a number.
 * <p>
 * The pools are sized once, when first used, so a count is handed over as its system property
 * before anything renders or loads a document - {@link #apply} runs first thing at start - and a
 * change takes effect on restart.  A property already given on the command line wins over a
 * saved count.
 */
public final class PerformanceSettings {

    /** A pool the settings size. */
    public enum Pool {
        /** Pages, previews, page text and thumbnails of every view. */
        RENDER(ViewerPreferences.RENDER_THREADS, RenderService.THREADS_PROPERTY),
        /** Core's common pool: parsing, fonts, page initialisation. */
        CORE(ViewerPreferences.CORE_THREADS, Library.COMMON_POOL_SIZE_PROPERTY),
        /** Core's image pool: image decoding. */
        IMAGE(ViewerPreferences.IMAGE_THREADS, Library.IMAGE_POOL_SIZE_PROPERTY);

        private final String key;
        private final String property;

        Pool(String key, String property) {
            this.key = key;
            this.property = property;
        }

        /** The settings key. */
        public String key() {
            return key;
        }

        /** The system property the pool is sized from. */
        public String property() {
            return property;
        }
    }

    /** The largest count the settings accept. */
    public static final int MAX_THREADS = 64;

    private PerformanceSettings() {
    }

    /** Hands the saved counts to the pools, as system properties; call before anything renders. */
    public static void apply(ViewerPreferences preferences) {
        for (Pool pool : Pool.values()) {
            Integer threads = saved(preferences, pool);
            if (threads != null && System.getProperty(pool.property()) == null) {
                System.setProperty(pool.property(), String.valueOf(threads));
            }
        }
    }

    /** The saved count, or null for automatic (also for a malformed or out-of-range value). */
    public static Integer saved(ViewerPreferences preferences, Pool pool) {
        String value = preferences.get(pool.key(), null);
        if (value == null) return null;
        try {
            int threads = Integer.parseInt(value.trim());
            return threads >= 1 && threads <= MAX_THREADS ? threads : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Saves a count; null for automatic. */
    public static void save(ViewerPreferences preferences, Pool pool, Integer threads) {
        preferences.put(pool.key(), threads != null ? String.valueOf(threads) : null);
    }

    /** The pool's size in this process and where it came from. */
    public static PoolSizing.Size inEffect(Pool pool) {
        return switch (pool) {
            case RENDER -> RenderService.shared().size();
            // core sized its pools from these properties when Library loaded.
            case CORE, IMAGE -> Library.resolvePoolSize(pool.property());
        };
    }
}
