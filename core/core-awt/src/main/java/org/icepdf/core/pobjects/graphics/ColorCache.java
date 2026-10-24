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
package org.icepdf.core.pobjects.graphics;

import java.awt.*;

/**
 * A colour space's converted colours, keyed by its components packed into an int (ICCBased quantises them,
 * Separation keys on its one tint's float bits), shared by every thread rendering with that colour space. Lock
 * free and bounded: one entry per slot, a colliding key replaces it.
 * <br>
 * The races are benign. A slot holds an immutable entry, so a reader sees a whole entry or none; a lost write
 * costs one extra conversion. That needs the cached colour to depend on the key alone, never on which input
 * filled the slot first: when keys are quantised, a caller converts the colour its key stands for, not the
 * value it was given.
 *
 * @since 7.5.0
 */
final class ColorCache {

    private static final class Entry {
        final int key;
        final Color color;

        Entry(int key, Color color) {
            this.key = key;
            this.color = color;
        }
    }

    private final Entry[] slots;
    private final int shift;

    /**
     * @param bits log2 of the slot count.
     */
    ColorCache(int bits) {
        slots = new Entry[1 << bits];
        shift = 32 - bits;
    }

    /**
     * @param key packed components.
     * @return the colour converted for this key, or null.
     */
    Color get(int key) {
        Entry entry = slots[(key * 0x9E3779B9) >>> shift];
        return entry != null && entry.key == key ? entry.color : null;
    }

    void put(int key, Color color) {
        slots[(key * 0x9E3779B9) >>> shift] = new Entry(key, color);
    }
}
