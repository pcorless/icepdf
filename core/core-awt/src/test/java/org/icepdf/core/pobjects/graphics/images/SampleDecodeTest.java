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
package org.icepdf.core.pobjects.graphics.images;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleDecodeTest {

    @Test
    public void defaultAndInvertedAreExactFractions() {
        SampleDecode normal = new SampleDecode(new float[]{0, 1}, 8);
        SampleDecode inverted = new SampleDecode(new float[]{1, 0}, 8);
        for (int sample = 0; sample < 256; sample++) {
            assertEquals(sample / 255f, normal.value(0, sample));
            assertEquals((255 - sample) / 255f, inverted.value(0, sample));
        }
    }

    @Test
    public void mapsEachComponentOntoItsOwnRange() {
        // Lab's default: L* 0..100, a* and b* -128..127
        SampleDecode lab = new SampleDecode(new float[]{0, 100, -128, 127, -128, 127}, 8);
        assertEquals(100f, lab.value(0, 255));
        assertEquals(0f, lab.value(1, 128));
        assertEquals(-128f, lab.value(2, 0));
        // an Indexed image's default: the sample is the index
        SampleDecode indexed = new SampleDecode(new float[]{0, 15}, 4);
        assertEquals(9f, indexed.value(0, 9));
        // a 2-bit [0.2 0.8]
        SampleDecode partial = new SampleDecode(new float[]{0.2f, 0.8f}, 2);
        assertArrayEquals(new float[]{0.2f, 0.4f, 0.6f, 0.8f},
                new float[]{partial.value(0, 0), partial.value(0, 1), partial.value(0, 2), partial.value(0, 3)},
                1e-6f);
    }

    @Test
    public void toByteRoundsAndClamps() {
        assertEquals(0, SampleDecode.toByte(-0.2f));
        assertEquals(128, SampleDecode.toByte(0.5f));
        assertEquals(255, SampleDecode.toByte(1.4f));
    }
}
