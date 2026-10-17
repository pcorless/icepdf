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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pool sizing rule shared by the core pools and the JavaFX render pool. */
class PoolSizingTest {

    private static final IntUnaryOperator CORE = cores -> cores - 1;
    private static final IntUnaryOperator HALF = cores -> cores / 2;

    private static PoolSizing.Size auto(int cores, int cap, IntUnaryOperator fromCores) {
        return PoolSizing.resolve("size", null, cap, fromCores, cores);
    }

    @DisplayName("auto: cores - 1 for the core pools, half the cores for the render pool, within [2, cap]")
    @Test
    void autoSizes() {
        // the plan's table: 4, 6, 16 and 32 cores at the default cap of 8.
        int[][] expected = {{4, 3, 2}, {6, 5, 3}, {16, 8, 8}, {32, 8, 8}};
        for (int[] row : expected) {
            assertEquals(row[1], auto(row[0], 8, CORE).threads(), row[0] + " cores, core pool");
            assertEquals(row[2], auto(row[0], 8, HALF).threads(), row[0] + " cores, render pool");
        }
    }

    @DisplayName("auto never goes below 2, even on one core")
    @Test
    void floor() {
        assertEquals(new PoolSizing.Size(2, PoolSizing.Source.AUTO, 8), auto(1, 8, CORE));
        assertEquals(new PoolSizing.Size(2, PoolSizing.Source.AUTO, 8), auto(2, 8, HALF));
    }

    @DisplayName("the cap cuts an auto size down and says so; a cap below 2 counts as 2")
    @Test
    void cap() {
        assertEquals(new PoolSizing.Size(8, PoolSizing.Source.CAP, 8), auto(32, 8, CORE));
        assertEquals(new PoolSizing.Size(31, PoolSizing.Source.AUTO, 64), auto(32, 64, CORE));
        assertEquals(new PoolSizing.Size(2, PoolSizing.Source.CAP, 2), auto(32, 0, CORE));
    }

    @DisplayName("an explicit size wins over the cap; below 1 counts as 2; garbage falls back to auto")
    @Test
    void explicit() {
        assertEquals(new PoolSizing.Size(12, PoolSizing.Source.EXPLICIT, 8),
                PoolSizing.resolve("size", " 12 ", 8, CORE, 4));
        assertEquals(new PoolSizing.Size(2, PoolSizing.Source.EXPLICIT, 8),
                PoolSizing.resolve("size", "0", 8, CORE, 32));
        assertEquals(new PoolSizing.Size(3, PoolSizing.Source.AUTO, 8),
                PoolSizing.resolve("size", "lots", 8, CORE, 4));
    }

    @DisplayName("Library resolves the same counts through PoolSizing as its old inline rule")
    @Test
    void librarySizesUnchanged() {
        int cores = Runtime.getRuntime().availableProcessors();
        int cap = Defs.intProperty(Library.THREAD_POOL_CAP_PROPERTY, 8);
        for (String property : new String[]{Library.COMMON_POOL_SIZE_PROPERTY, Library.IMAGE_POOL_SIZE_PROPERTY}) {
            assertEquals(oldRule(property, cores, cap), Library.resolvePoolSize(property).threads(), property);
        }
        assertEquals(Library.commonPoolThreads, Library.resolvePoolSize(Library.COMMON_POOL_SIZE_PROPERTY).threads());
        assertEquals(Library.imagePoolThreads, Library.resolvePoolSize(Library.IMAGE_POOL_SIZE_PROPERTY).threads());
    }

    /** Library.resolvePoolSize as it was before PoolSizing. */
    private static int oldRule(String sizeProperty, int cores, int cap) {
        String override = Defs.sysProperty(sizeProperty);
        if (override != null) {
            try {
                int value = Integer.parseInt(override.trim());
                return value < 1 ? 2 : value;
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        cap = Math.max(2, cap);
        return Math.min(cap, Math.max(2, cores - 1));
    }
}
