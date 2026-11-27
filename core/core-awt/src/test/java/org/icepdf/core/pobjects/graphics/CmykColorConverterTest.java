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

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.color.ICC_ColorSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-647: DeviceCMYK converts through the bundled SWOP TR003 profile with black-point compensation. The expected
 * colours are Ghostscript 10.02's for the same profile ({@code -sDefaultCMYKProfile=SWOP_TR003_coated_3.icc}, its
 * default relative colorimetric with BPC), probed at the centre of a filled square.
 */
public class CmykColorConverterTest {

    // C, M, Y, K, then Ghostscript's R, G, B
    private static final float[][] GHOSTSCRIPT = {
            {1, 0, 0, 0, 0, 170, 234},
            {0, 1, 0, 0, 233, 0, 132},
            {0, 0, 1, 0, 255, 242, 0},
            {0, 0, 0, 1, 38, 38, 38},
            {0, 0, 0, 0.5f, 149, 152, 156},
            {1, 0.5f, 0, 0, 0, 113, 185},
            {0, 1, 1, 0, 231, 22, 32},
            {1, 1, 0, 0, 51, 54, 143},
            {0.6f, 0.4f, 0.4f, 1, 3, 11, 15},
            {1, 1, 1, 1, 0, 0, 0},
            {0.2f, 0.1f, 0.1f, 0, 206, 217, 222},
            {0, 0, 0, 0.2f, 211, 213, 216},
    };

    private static final int TOLERANCE = 4;

    private static CmykColorConverter converter() {
        CmykColorConverter converter = DeviceCMYK.getCmykColorConverter();
        assertNotNull(converter, "the bundled CMYK profile loads");
        return converter;
    }

    @Test
    public void deviceCmykMatchesGhostscript() {
        CmykColorConverter converter = converter();
        DeviceCMYK deviceCmyk = new DeviceCMYK(null, null);
        for (float[] row : GHOSTSCRIPT) {
            float[] cmyk = {row[0], row[1], row[2], row[3]};
            Color expected = new Color((int) row[4], (int) row[5], (int) row[6]);
            assertClose(expected, new Color(converter.toArgb(cmyk.clone())), cmyk);
            assertClose(expected, deviceCmyk.getColor(cmyk.clone(), true), cmyk);
        }
    }

    @Test
    public void paperIsWhiteAndRichBlackIsBlack() {
        CmykColorConverter converter = converter();
        assertEquals(0xffffffff, converter.toArgb(new float[]{0, 0, 0, 0}));
        // without compensation the profile's black is a dark grey (30,33,35): the press's ink, not black
        Color rich = new Color(converter.toArgb(new float[]{0.6f, 0.4f, 0.4f, 1}));
        assertTrue(rich.getRed() + rich.getGreen() + rich.getBlue() < 40, "rich black " + rich);
    }

    @Test
    public void withoutCompensationItIsThePlainProfileConversion() {
        ICC_ColorSpace profile = converter().getColorSpace();
        CmykColorConverter plain = new CmykColorConverter(profile, false);
        float[] cmyk = {0.6f, 0.4f, 0.4f, 1};
        float[] rgb = profile.toRGB(cmyk.clone());
        Color expected = new Color((int) (rgb[0] * 255), (int) (rgb[1] * 255), (int) (rgb[2] * 255));
        assertEquals(expected.getRGB(), plain.toArgb(cmyk));
    }

    @Test
    public void darkerInkIsNeverLighter() {
        // compensation scales toward black; it must not fold a ramp back on itself
        CmykColorConverter converter = converter();
        int last = Integer.MAX_VALUE;
        for (int k = 0; k <= 255; k++) {
            Color c = new Color(converter.toArgb(new float[]{0, 0, 0, k / 255f}));
            int sum = c.getRed() + c.getGreen() + c.getBlue();
            assertTrue(sum <= last, "K " + k + " is lighter than K " + (k - 1));
            last = sum;
        }
    }

    private static void assertClose(Color expected, Color actual, float[] cmyk) {
        String what = String.format("cmyk %.2f %.2f %.2f %.2f: expected %s got %s",
                cmyk[0], cmyk[1], cmyk[2], cmyk[3], expected, actual);
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= TOLERANCE
                && Math.abs(expected.getGreen() - actual.getGreen()) <= TOLERANCE
                && Math.abs(expected.getBlue() - actual.getBlue()) <= TOLERANCE, what);
    }
}
