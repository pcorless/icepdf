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

import java.util.*;
import java.util.function.Predicate;

/**
 * Byte-budgeted LRU of rendered rasters.
 * <p>
 * The budget is a hard number rather than SoftReferences: soft refs only give memory back under GC
 * pressure, and the Swing viewer's GH-495 work showed that is exactly when a re-render storm hurts
 * most.  Keys in the pinned set (what is on screen now) are never evicted, so the budget can be
 * exceeded by at most one screenful.
 * <p>
 * Confined to the FX application thread; no locking.
 */
final class TileCache {

    private final LinkedHashMap<CacheKey, RasterBuffer> entries = new LinkedHashMap<>(256, 0.75f, true);
    private Set<CacheKey> pinned = Set.of();
    private long budgetBytes;
    private long usedBytes;

    public TileCache(long budgetBytes) {
        this.budgetBytes = budgetBytes;
    }

    /**
     * An eighth of the max heap, clamped to 32-256MB.  Deliberately modest: the core's own caches
     * (decoded images, fonts, display lists) dominate the heap on heavy documents - 1.pdf retains
     * ~460MB in the core alone - and a screenful of tiles is only ~4MB/megapixel.
     */
    public static long defaultBudget() {
        long eighth = Runtime.getRuntime().maxMemory() / 8;
        return Math.max(32L << 20, Math.min(256L << 20, eighth));
    }

    /** Drops everything not on screen, e.g. after a render ran out of memory. */
    public void trimToPinned() {
        removeIf(key -> !pinned.contains(key));
    }

    public RasterBuffer get(CacheKey key) {
        return entries.get(key);
    }

    public boolean contains(CacheKey key) {
        return entries.containsKey(key);
    }

    public void put(CacheKey key, RasterBuffer buffer) {
        RasterBuffer old = entries.put(key, buffer);
        if (old != null) usedBytes -= old.byteSize();
        usedBytes += buffer.byteSize();
        evict();
    }

    /** Replaces the pinned set; previously pinned entries become evictable. */
    public void pin(Set<CacheKey> keys) {
        pinned = keys;
        evict();
    }

    public void removeIf(Predicate<CacheKey> predicate) {
        Iterator<Map.Entry<CacheKey, RasterBuffer>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CacheKey, RasterBuffer> e = it.next();
            if (predicate.test(e.getKey())) {
                usedBytes -= e.getValue().byteSize();
                it.remove();
            }
        }
    }

    public void clear() {
        entries.clear();
        usedBytes = 0;
    }

    public void setBudget(long budgetBytes) {
        this.budgetBytes = budgetBytes;
        evict();
    }

    public long getBudget() {
        return budgetBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public int size() {
        return entries.size();
    }

    private void evict() {
        if (usedBytes <= budgetBytes) return;
        // access order: eldest (least recently used) first.
        Iterator<Map.Entry<CacheKey, RasterBuffer>> it = entries.entrySet().iterator();
        while (usedBytes > budgetBytes && it.hasNext()) {
            Map.Entry<CacheKey, RasterBuffer> e = it.next();
            if (!pinned.contains(e.getKey())) {
                usedBytes -= e.getValue().byteSize();
                it.remove();
            }
        }
    }
}
