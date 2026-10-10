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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tile cache: a byte-budgeted LRU whose on-screen (pinned) tiles are never evicted.  No FX
 * toolkit needed - a RasterBuffer only creates its FX image when the scene asks for it.
 */
class TileCacheTest {

    private static final CacheKey.Params PARAMS = new CacheKey.Params(1f, 0f, 1.0, 2, 0);
    // a 16x16 ARGB tile is 1 KB
    private static final long TILE = 16 * 16 * 4;

    private static CacheKey key(int column) {
        return new CacheKey.Tile(0, PARAMS, column, 0);
    }

    private static RasterBuffer tile() {
        return new RasterBuffer(16, 16);
    }

    @DisplayName("bytes are counted on put and replace")
    @Test
    void accounting() {
        TileCache cache = new TileCache(100 * TILE);
        cache.put(key(0), tile());
        cache.put(key(1), tile());
        assertEquals(2 * TILE, cache.getUsedBytes());
        cache.put(key(1), new RasterBuffer(32, 16));
        assertEquals(TILE + 2 * TILE, cache.getUsedBytes(), "a replaced entry's bytes are given back");
        assertEquals(2, cache.size());
    }

    @DisplayName("over budget, the least recently used tile goes first")
    @Test
    void evictsLeastRecentlyUsed() {
        TileCache cache = new TileCache(3 * TILE);
        cache.put(key(0), tile());
        cache.put(key(1), tile());
        cache.put(key(2), tile());
        cache.get(key(0)); // touch: 1 is now the eldest
        cache.put(key(3), tile());
        assertFalse(cache.contains(key(1)));
        assertTrue(cache.contains(key(0)) && cache.contains(key(2)) && cache.contains(key(3)));
        assertEquals(3 * TILE, cache.getUsedBytes());
    }

    @DisplayName("pinned (on-screen) tiles are never evicted, even over budget")
    @Test
    void pinnedSurvive() {
        TileCache cache = new TileCache(2 * TILE);
        cache.put(key(0), tile());
        cache.put(key(1), tile());
        cache.pin(Set.of(key(0), key(1)));
        cache.put(key(2), tile());
        // over budget with nothing evictable but the newcomer
        assertTrue(cache.contains(key(0)) && cache.contains(key(1)));
        assertFalse(cache.contains(key(2)), "the unpinned newcomer is the one to go");

        cache.pin(Set.of(key(1)));
        cache.put(key(3), tile());
        assertFalse(cache.contains(key(0)), "unpinned tiles become evictable again");
        assertTrue(cache.contains(key(1)) && cache.contains(key(3)));
    }

    @DisplayName("shrinking the budget evicts at once; trimToPinned keeps only what is on screen")
    @Test
    void budgetAndTrim() {
        TileCache cache = new TileCache(10 * TILE);
        for (int i = 0; i < 5; i++) cache.put(key(i), tile());
        cache.setBudget(2 * TILE);
        assertEquals(2, cache.size());
        assertTrue(cache.contains(key(3)) && cache.contains(key(4)), "the two most recent stay");

        cache.setBudget(10 * TILE);
        for (int i = 0; i < 5; i++) cache.put(key(i), tile());
        cache.pin(Set.of(key(2)));
        cache.trimToPinned();
        assertEquals(1, cache.size());
        assertEquals(TILE, cache.getUsedBytes());
    }

    @DisplayName("removeIf and clear give the bytes back")
    @Test
    void removal() {
        TileCache cache = new TileCache(10 * TILE);
        for (int i = 0; i < 4; i++) cache.put(key(i), tile());
        cache.removeIf(k -> k instanceof CacheKey.Tile t && t.column() % 2 == 0);
        assertEquals(2, cache.size());
        assertEquals(2 * TILE, cache.getUsedBytes());
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.getUsedBytes());
    }

    @DisplayName("the default budget is an eighth of the heap, within 32-256 MB")
    @Test
    void defaultBudget() {
        long budget = TileCache.defaultBudget();
        assertTrue(budget >= 32L << 20 && budget <= 256L << 20, String.valueOf(budget));
    }

    @DisplayName("slicing a region render into tiles copies the right pixels")
    @Test
    void slice() {
        int[] region = new int[8 * 4];
        for (int i = 0; i < region.length; i++) region[i] = i;
        RasterBuffer piece = RasterBuffer.slice(region, 8, 2, 1, 3, 2);
        int[] back = new int[8 * 4];
        piece.copyInto(back, 8, 2, 1);
        assertEquals(region[1 * 8 + 2], back[1 * 8 + 2]);
        assertEquals(region[2 * 8 + 4], back[2 * 8 + 4]);
        assertEquals(0, back[0], "only the slice is copied back");
    }
}
