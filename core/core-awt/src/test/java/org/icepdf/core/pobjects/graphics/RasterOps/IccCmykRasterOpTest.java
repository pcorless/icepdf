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

import org.icepdf.core.pobjects.graphics.DeviceCMYK;
import org.junit.jupiter.api.Test;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GH-617: the colour cache must not change a single pixel. Every case is checked against a direct
 * {@code toRGB} of each pixel, the conversion the op made before it remembered colours.
 */
public class IccCmykRasterOpTest {

    private static int[] convert(byte[] cmyk, int w, int h) {
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Raster src = Raster.createInterleavedRaster(
                new DataBufferByte(cmyk, cmyk.length), w, h, w * 4, 4,
                new int[]{0, 1, 2, 3}, null);
        new IccCmykRasterOp(null).filter(src, rgb.getRaster());
        return ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
    }

    private static void assertMatchesDirect(byte[] cmyk, int[] got) {
        ColorSpace cs = DeviceCMYK.getIccCmykColorSpace();
        float[] value = new float[4];
        for (int i = 0; i < got.length; i++) {
            for (int c = 0; c < 4; c++) {
                value[c] = (cmyk[i * 4 + c] & 0xff) / 255.0f;
            }
            float[] rgb = cs.toRGB(value);
            int expected = (((int) (rgb[0] * 255) & 0xff) << 16) |
                    (((int) (rgb[1] * 255) & 0xff) << 8) |
                    ((int) (rgb[2] * 255) & 0xff);
            int pixel = i;
            assertEquals(expected, got[i] & 0xffffff, () -> "pixel " + pixel);
            assertEquals(0xff, got[i] >>> 24, () -> "pixel " + pixel + " alpha");
        }
    }

    /** Noise: nearly every pixel is a new colour, so the cache fills and the rest are converted uncached. */
    @Test
    public void noiseOverflowsTheCacheAndStillMatches() {
        int w = 200, h = 200;
        byte[] cmyk = new byte[w * h * 4];
        new Random(617).nextBytes(cmyk);
        assertMatchesDirect(cmyk, convert(cmyk, w, h));
    }

    /** A few colours repeated out of order: cache hits, the previous-pixel shortcut, and white (all zero). */
    @Test
    public void repeatedColoursMatch() {
        int w = 64, h = 32;
        byte[][] palette = {
                {0, 0, 0, 0},
                {0, 0, 0, (byte) 255},
                {(byte) 255, 0, 0, 0},
                {40, 60, 10, 120},
                {0, 0, (byte) 200, 30},
        };
        Random random = new Random(1);
        byte[] cmyk = new byte[w * h * 4];
        for (int i = 0; i < w * h; i++) {
            // runs of 1-3 pixels so neighbours both repeat and change
            byte[] colour = palette[(i / (1 + random.nextInt(3))) % palette.length];
            System.arraycopy(colour, 0, cmyk, i * 4, 4);
        }
        assertMatchesDirect(cmyk, convert(cmyk, w, h));
    }

    /** All white: the first pixel's key is 0, the same as an empty slot's. */
    @Test
    public void allWhiteMatches() {
        byte[] cmyk = new byte[16 * 16 * 4];
        assertMatchesDirect(cmyk, convert(cmyk, 16, 16));
    }
}
