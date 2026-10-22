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
package org.icepdf.core.util;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.logging.Logger;

/**
 * Sizes a thread pool from the machine, the one rule the core's pools and a viewer's render pool
 * share.
 * <p>
 * An explicit size property wins (a value below 1 counts as {@link #MIN_THREADS}).  Otherwise the
 * size is derived from {@link Runtime#availableProcessors()} by a pool-specific function (the core
 * pools use {@code cores - 1}), clamped to {@code [MIN_THREADS, cap]}, where the cap comes from a cap
 * property with a pool-specific default.  A cap below {@link #MIN_THREADS} counts as
 * {@link #MIN_THREADS}.
 */
public final class PoolSizing {

    private static final Logger logger = Logger.getLogger(PoolSizing.class.getName());

    /** The smallest pool an auto-sized (or nonsensically configured) pool gets. */
    public static final int MIN_THREADS = 2;

    /** Where a resolved size came from. */
    public enum Source {
        /** Derived from the core count, within the cap. */
        AUTO,
        /** Derived from the core count and cut down to the cap. */
        CAP,
        /** Set by the size property. */
        EXPLICIT
    }

    /** A resolved pool size. */
    public static final class Size {
        private final int threads;
        private final Source source;
        private final int cap;

        /**
         * @param threads the thread count, at least 1
         * @param source  where it came from
         * @param cap     the cap in force (also reported for an explicit size, which ignores it)
         */
        public Size(int threads, Source source, int cap) {
            this.threads = threads;
            this.source = source;
            this.cap = cap;
        }

        /** The thread count, at least 1. */
        public int threads() {
            return threads;
        }

        /** Where the count came from. */
        public Source source() {
            return source;
        }

        /** The cap in force (also reported for an explicit size, which ignores it). */
        public int cap() {
            return cap;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Size)) return false;
            Size other = (Size) o;
            return threads == other.threads && source == other.source && cap == other.cap;
        }

        @Override
        public int hashCode() {
            return Objects.hash(threads, source, cap);
        }

        @Override
        public String toString() {
            return threads + " (" + source.name().toLowerCase(Locale.ROOT) + ", cap " + cap + ")";
        }
    }

    private PoolSizing() {
    }

    /**
     * Resolves a pool's thread count.
     *
     * @param sizeProperty system property holding an explicit size
     * @param capProperty  system property holding the cap for an auto-sized pool
     * @param defaultCap   the cap when {@code capProperty} is unset
     * @param fromCores    the auto size as a function of the core count
     * @return the thread count, at least 1
     */
    public static int size(String sizeProperty, String capProperty, int defaultCap, IntUnaryOperator fromCores) {
        return resolve(sizeProperty, capProperty, defaultCap, fromCores).threads();
    }

    /** As {@link #size}, also saying where the count came from. */
    public static Size resolve(String sizeProperty, String capProperty, int defaultCap, IntUnaryOperator fromCores) {
        return resolve(sizeProperty, Defs.sysProperty(sizeProperty), Defs.intProperty(capProperty, defaultCap),
                fromCores, Runtime.getRuntime().availableProcessors());
    }

    /** The rule itself, with its inputs passed in rather than read from the system. */
    static Size resolve(String sizeProperty, String explicit, int cap, IntUnaryOperator fromCores, int cores) {
        cap = Math.max(MIN_THREADS, cap);
        if (explicit != null) {
            try {
                int value = Integer.parseInt(explicit.trim());
                return new Size(value < 1 ? MIN_THREADS : value, Source.EXPLICIT, cap);
            } catch (NumberFormatException e) {
                logger.warning("Invalid thread pool size for " + sizeProperty + ": '" + explicit
                        + "', falling back to CPU-based sizing.");
            }
        }
        int derived = Math.max(MIN_THREADS, fromCores.applyAsInt(cores));
        return derived > cap ? new Size(cap, Source.CAP, cap) : new Size(derived, Source.AUTO, cap);
    }
}
