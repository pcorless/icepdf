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
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.RasterOps.PColorSpaceRasterOp;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GH-617: {@link PColorSpaceRasterOp} remembering converted colours must not change a pixel; each is checked
 * against a direct {@code getColor} of its samples. In this package for Separation's protected constructor.
 */
public class PColorSpaceRasterOpTest {

    private static int[] convert(PColorSpace colorSpace, byte[] samples, int bands, int w, int h) {
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] offsets = new int[bands];
        for (int i = 0; i < bands; i++) {
            offsets[i] = i;
        }
        Raster src = Raster.createInterleavedRaster(
                new DataBufferByte(samples, samples.length), w, h, w * bands, bands, offsets, null);
        new PColorSpaceRasterOp(colorSpace, null).filter(src, rgb.getRaster());
        return ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
    }

    private static void assertMatchesDirect(PColorSpace colorSpace, byte[] samples, int bands, int[] got) {
        float[] value = new float[bands];
        for (int i = 0; i < got.length; i++) {
            for (int c = 0; c < bands; c++) {
                value[c] = (samples[i * bands + c] & 0xff) / 255.0f;
            }
            int expected = colorSpace.getColor(value).getRGB();
            int pixel = i;
            assertEquals(expected & 0xffffff, got[i] & 0xffffff, () -> "pixel " + pixel);
        }
    }

    private static Separation separation(Library library, Object tintTransform) {
        return new Separation(library, new DictionaryEntries(), new Name("Spot"), new Name("DeviceCMYK"),
                tintTransform);
    }

    /** A four-band raster (DeviceCMYK through getColor) threw: the sample buffer was sized for three. */
    @Test
    public void fourBandNoiseMatches() {
        int w = 120, h = 120;
        byte[] cmyk = new byte[w * h * 4];
        new Random(617).nextBytes(cmyk);
        DeviceCMYK colorSpace = new DeviceCMYK(new Library(), null);
        assertMatchesDirect(colorSpace, cmyk, 4, convert(colorSpace, cmyk, 4, w, h));
    }

    @Test
    public void separationTintsMatch() {
        Library library = new Library();
        DictionaryEntries tint = new DictionaryEntries();
        tint.put(new Name("FunctionType"), 2);
        tint.put(new Name("Domain"), Arrays.asList(0f, 1f));
        tint.put(new Name("C0"), Arrays.asList(0f, 0f, 0f, 0f));
        tint.put(new Name("C1"), Arrays.asList(0.1f, 0.8f, 0.3f, 0.05f));
        tint.put(new Name("N"), 1f);
        Separation spot = separation(library, tint);
        int w = 100, h = 100;
        byte[] tints = new byte[w * h];
        new Random(1).nextBytes(tints);
        assertMatchesDirect(spot, tints, 1, convert(spot, tints, 1, w, h));
    }

    /**
     * A one-band raster was handed to the colour space as three values, the tint and two zeros. A PostScript
     * tint transform evaluated the last zero instead of the tint, so every pixel came out as tint 0.
     */
    @Test
    public void postScriptTintSeesOnlyTheTint() {
        Library library = new Library();
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("FunctionType"), 4);
        entries.put(new Name("Domain"), Arrays.asList(0f, 1f));
        entries.put(new Name("Range"), Arrays.asList(0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f));
        byte[] program = "{ dup 0 0 4 -1 roll }".getBytes(StandardCharsets.US_ASCII);
        Separation spot = separation(library, new Stream(library, entries, program));
        byte[] tints = new byte[256];
        for (int i = 0; i < tints.length; i++) {
            tints[i] = (byte) i;
        }
        int[] got = convert(spot, tints, 1, 16, 16);
        assertMatchesDirect(spot, tints, 1, got);
        // full tint is full cyan and black, not the paper white of tint 0
        assertEquals(spot.getColor(new float[]{1f}).getRGB() & 0xffffff, got[255] & 0xffffff);
        assertEquals(false, (got[255] & 0xffffff) == (got[0] & 0xffffff), "tint 255 differs from tint 0");
    }
}
