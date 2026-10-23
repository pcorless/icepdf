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
package org.icepdf.core.pobjects.graphics.RasterOps;

/**
 * Remembers the ARGB an image's colour conversion gave for each packed sample, so every distinct colour is
 * converted once. Images reuse far fewer colours than they have pixels (Mass 3D 8.pdf: 161k colours in 12.8M
 * pixels), and a conversion such as an ICC {@code toRGB} or a tint transform costs far more than a lookup.
 * <br>
 * An open-addressed table, half full at most; once full, later colours are not remembered rather than
 * evicting. Keys of up to 32 bits are held as ints, wider ones (DeviceN with five to eight 8-bit colorants) as
 * longs. A memo is meant for one image on one thread, it is not thread safe.
 * <pre>
 * int slot = memo.slot(key);
 * int argb = memo.argb(slot);
 * if (argb == 0) {
 *     argb = convert(key);
 *     memo.put(slot, key, argb);
 * }
 * </pre>
 *
 * @since 7.5.0
 */
public final class ColourMemo {
    // slots: a small table for small images, at most 1M slots (8 MB, or 12 MB with wide keys) for the largest
    private static final int MIN_CAPACITY = 1 << 10;
    private static final int MAX_CAPACITY = 1 << 20;

    // one of the two, by key width
    private final int[] keys;
    private final long[] wideKeys;
    // 0 marks an empty slot, so an ARGB of 0 (transparent black) is never remembered
    private final int[] argbs;
    private final int shift;
    private final int mask;
    private final int limit;
    private int count;

    /**
     * @param pixelCount pixels in the image, which bounds how many colours it can use.
     * @param keyBits    bits in a packed key, which also bounds them: an 8-bit Separation uses at most 256.
     */
    public ColourMemo(int pixelCount, int keyBits) {
        int colours = keyBits < 31 ? Math.min(pixelCount, 1 << keyBits) : pixelCount;
        int capacity = Integer.highestOneBit(Math.max(MIN_CAPACITY, Math.min(colours, MAX_CAPACITY / 2)) * 2 - 1) * 2;
        keys = keyBits <= 32 ? new int[capacity] : null;
        wideKeys = keyBits <= 32 ? null : new long[capacity];
        argbs = new int[capacity];
        shift = Integer.numberOfLeadingZeros(capacity) + 1;
        mask = capacity - 1;
        limit = capacity / 2;
    }

    /**
     * Whether a key of this many bits can be packed into a long, the widest key a memo takes.
     *
     * @param bitsPerComponent bits per colour component.
     * @param components       colour components per pixel.
     * @return true if the packed key fits.
     */
    public static boolean fits(int bitsPerComponent, int components) {
        return bitsPerComponent * components <= 64;
    }

    /**
     * @param key packed sample.
     * @return the slot holding this key, or the empty slot it would go in.
     */
    public int slot(int key) {
        if (keys == null) {
            return slot((long) key);
        }
        int slot = (key * 0x9E3779B9) >>> shift;
        while (argbs[slot] != 0 && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    /**
     * @param key packed sample, more than 32 bits wide.
     * @return the slot holding this key, or the empty slot it would go in.
     */
    public int slot(long key) {
        if (wideKeys == null) {
            return slot((int) key);
        }
        int slot = ((int) (key ^ (key >>> 32)) * 0x9E3779B9) >>> shift;
        while (argbs[slot] != 0 && wideKeys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    /**
     * @param slot a slot from {@link #slot(int)}.
     * @return the remembered ARGB, or 0 if the key has not been converted yet.
     */
    public int argb(int slot) {
        return argbs[slot];
    }

    /**
     * Remembers a conversion, unless the memo is full.
     *
     * @param slot the empty slot {@link #slot(int)} gave for the key.
     * @param key  packed sample.
     * @param argb its converted colour.
     */
    public void put(int slot, int key, int argb) {
        put(slot, (long) key, argb);
    }

    /**
     * Remembers a conversion, unless the memo is full.
     *
     * @param slot the empty slot {@link #slot(long)} gave for the key.
     * @param key  packed sample.
     * @param argb its converted colour.
     */
    public void put(int slot, long key, int argb) {
        if (count < limit && argb != 0) {
            if (keys != null) {
                keys[slot] = (int) key;
            } else {
                wideKeys[slot] = key;
            }
            argbs[slot] = argb;
            count++;
        }
    }
}
