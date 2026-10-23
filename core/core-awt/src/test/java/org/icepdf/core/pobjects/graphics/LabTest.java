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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lab to sRGB against reference values (sRGB primaries in Lab, IEC 61966-2-1), for the D65 and the far more
 * common D50 white point.
 */
public class LabTest {

    private static final float[] D65 = {0.95047f, 1f, 1.08883f};
    private static final float[] D50 = {0.9642f, 1f, 0.8249f};

    private static Lab lab(float[] whitePoint) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("WhitePoint"), Arrays.asList(whitePoint[0], whitePoint[1], whitePoint[2]));
        entries.put(new Name("Range"), Arrays.asList(-128f, 127f, -128f, 127f));
        return new Lab(new Library(), entries);
    }

    private static void assertColour(int r, int g, int b, Color actual) {
        assertTrue(Math.abs(actual.getRed() - r) <= 2 && Math.abs(actual.getGreen() - g) <= 2
                        && Math.abs(actual.getBlue() - b) <= 2,
                "expected (" + r + ", " + g + ", " + b + ") but was " + actual);
    }

    @Test
    public void whiteIsWhiteForEitherWhitePoint() {
        // D65's Z of 1.089 was clamped to 1, which came out (255, 254, 244); D50 was converted as if it were D65
        assertColour(255, 255, 255, lab(D65).getColor(new float[]{100, 0, 0}));
        assertColour(255, 255, 255, lab(D50).getColor(new float[]{100, 0, 0}));
        assertColour(0, 0, 0, lab(D50).getColor(new float[]{0, 0, 0}));
        assertColour(119, 119, 119, lab(D50).getColor(new float[]{50, 0, 0}));
    }

    @Test
    public void srgbPrimaries() {
        assertColour(255, 0, 0, lab(D65).getColor(new float[]{53.24f, 80.09f, 67.20f}));
        assertColour(0, 0, 255, lab(D65).getColor(new float[]{32.30f, 79.19f, -107.86f}));
        // the same primaries relative to D50 (Bradford)
        assertColour(255, 0, 0, lab(D50).getColor(new float[]{54.29f, 80.80f, 69.89f}));
        assertColour(0, 255, 0, lab(D50).getColor(new float[]{87.82f, -79.29f, 80.99f}));
    }

    @Test
    public void normalisedComponentsAreLThenAThenB() {
        // L* and b* used to land in each other's place
        float[] out = new float[3];
        lab(D50).normaliseComponentsToFloats(new int[]{255, 128, 0}, out, 255);
        assertArrayEquals(new float[]{100f, 0f, -128f}, out, 0.01f);
    }
}
