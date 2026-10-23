/*
 * Copyright 2026 Patrick Corless
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
package org.icepdf.core.pobjects.graphics.RasterOps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-617: the memo must hand back exactly what was put, never another key's colour.
 */
public class ColourMemoTest {

    private static int lookup(ColourMemo memo, int key) {
        return memo.argb(memo.slot(key));
    }

    private static void remember(ColourMemo memo, int key, int argb) {
        int slot = memo.slot(key);
        assertEquals(0, memo.argb(slot), "key " + key + " already remembered");
        memo.put(slot, key, argb);
    }

    @Test
    public void remembersEveryKeyItsKeySpaceAllows() {
        // an 8-bit key has 256 values; all fit, including key 0 and a negative packed key
        ColourMemo memo = new ColourMemo(1_000_000, 8);
        for (int key = 0; key < 256; key++) {
            remember(memo, key, 0xff000000 | key * 7);
        }
        for (int key = 0; key < 256; key++) {
            assertEquals(0xff000000 | key * 7, lookup(memo, key));
        }
        ColourMemo wide = new ColourMemo(100, 32);
        remember(wide, 0xff102030, 0xff445566);
        assertEquals(0xff445566, lookup(wide, 0xff102030));
        assertEquals(0, lookup(wide, 0x00102030));
    }

    @Test
    public void stopsRememberingWhenFullAndNeverMisreports() {
        // at least 2048 slots, half full at most, so keys after the 1024th are not remembered
        ColourMemo memo = new ColourMemo(10, 32);
        for (int key = 1; key <= 2000; key++) {
            int slot = memo.slot(key);
            memo.put(slot, key, 0xff000000 | key);
        }
        int remembered = 0;
        for (int key = 1; key <= 2000; key++) {
            int argb = lookup(memo, key);
            if (argb != 0) {
                assertEquals(0xff000000 | key, argb);
                remembered++;
            }
        }
        assertEquals(1024, remembered);
    }

    @Test
    public void transparentBlackIsNotRemembered() {
        ColourMemo memo = new ColourMemo(10, 8);
        remember(memo, 5, 0);
        assertEquals(0, lookup(memo, 5));
    }

    @Test
    public void fits() {
        assertTrue(ColourMemo.fits(8, 4));
        assertTrue(ColourMemo.fits(16, 4));
        assertTrue(ColourMemo.fits(8, 8));
        assertFalse(ColourMemo.fits(16, 5));
        assertFalse(ColourMemo.fits(8, 9));
    }

    /** DeviceN with five to eight 8-bit colorants: keys wider than an int, distinct in their upper bits. */
    @Test
    public void wideKeysKeepTheirUpperBits() {
        ColourMemo memo = new ColourMemo(1_000, 48);
        long low = 0x0000_1122_3344L;
        long high = 0x5500_1122_3344L;
        int slot = memo.slot(low);
        memo.put(slot, low, 0xff010203);
        assertEquals(0, memo.argb(memo.slot(high)), "keys differing only above bit 32 must not collide");
        memo.put(memo.slot(high), high, 0xff040506);
        assertEquals(0xff010203, memo.argb(memo.slot(low)));
        assertEquals(0xff040506, memo.argb(memo.slot(high)));
    }
}
