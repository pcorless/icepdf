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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.graphics.DeviceCMYK;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GH-617: raw (Flate) DeviceCMYK images are decoded sample by sample in {@link RawDecoder}, which now remembers
 * converted colours. Every pixel is checked against a direct {@code getColor} of its samples.
 */
public class RawCmykImageTest {

    private static BufferedImage decode(byte[] data, int w, int h, int bitsPerComponent) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), w);
        entries.put(new Name("Height"), h);
        entries.put(new Name("BitsPerComponent"), bitsPerComponent);
        entries.put(new Name("ColorSpace"), new Name("DeviceCMYK"));
        GraphicsState graphicsState = new GraphicsState(new Shapes());
        graphicsState.setFillColor(Color.BLACK);
        Library library = new Library();
        BufferedImage image = new ImageStream(library, entries, data)
                .getImage(graphicsState, new Resources(library, new DictionaryEntries()));
        assertNotNull(image);
        return image;
    }

    private static void assertMatchesDirect(BufferedImage image, int[] samples, int max) {
        DeviceCMYK colorSpace = new DeviceCMYK(new Library(), null);
        float[] value = new float[4];
        int w = image.getWidth();
        for (int i = 0; i < samples.length / 4; i++) {
            for (int c = 0; c < 4; c++) {
                value[c] = samples[i * 4 + c] / (float) max;
            }
            int expected = colorSpace.getColor(value).getRGB();
            int pixel = i;
            assertEquals(expected, image.getRGB(i % w, i / w), () -> "pixel " + pixel);
        }
    }

    @Test
    public void eightBitNoiseAndRepeatsMatch() {
        int w = 96, h = 64;
        byte[] data = new byte[w * h * 4];
        Random random = new Random(617);
        random.nextBytes(data);
        // the lower half repeats a few colours out of order, so the memo hits as well as misses
        byte[][] palette = {{0, 0, 0, 0}, {0, 0, 0, (byte) 255}, {40, 60, 10, 120}, {(byte) 200, 0, 30, 0}};
        for (int i = w * h / 2; i < w * h; i++) {
            System.arraycopy(palette[random.nextInt(palette.length)], 0, data, i * 4, 4);
        }
        int[] samples = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            samples[i] = data[i] & 0xff;
        }
        assertMatchesDirect(decode(data, w, h, 8), samples, 255);
    }

    /**
     * Rows start on a byte boundary (8.9.3). Three 1-bit CMYK pixels fill 12 bits, so each row is padded to two
     * bytes; read as one continuous stream, the second and third rows started in the padding.
     */
    @Test
    public void paddedRowsStartOnAByteBoundary() {
        // per row: C, M, Y, K pixels... 0001 (black) 1000 (cyan) 0000 (white), then 4 bits of padding set to 1
        byte[] row = {(byte) 0b0001_1000, (byte) 0b0000_1111};
        int w = 3, h = 3;
        byte[] data = new byte[row.length * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(row, 0, data, y * row.length, row.length);
        }
        int[] samples = new int[w * h * 4];
        int[][] pixels = {{0, 0, 0, 1}, {1, 0, 0, 0}, {0, 0, 0, 0}};
        for (int i = 0; i < w * h; i++) {
            System.arraycopy(pixels[i % w], 0, samples, i * 4, 4);
        }
        assertMatchesDirect(decode(data, w, h, 1), samples, 1);
    }

    @Test
    public void fourBitSamplesMatch() {
        int w = 32, h = 32;
        byte[] data = new byte[w * h * 2];
        new Random(4).nextBytes(data);
        int[] samples = new int[w * h * 4];
        for (int i = 0; i < data.length; i++) {
            samples[i * 2] = (data[i] >> 4) & 0xf;
            samples[i * 2 + 1] = data[i] & 0xf;
        }
        assertMatchesDirect(decode(data, w, h, 4), samples, 15);
    }
}
