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
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.DeviceCMYK;
import org.icepdf.core.pobjects.graphics.DeviceGray;
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.RasterOps.SampleDecode;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-image pixel converters {@link RawDecoder} picks from.
 */
public class PixelConverterTest {

    /** A Decode array of [0 1] per component, or [1 0] inverted. */
    private static SampleDecode decode(int components, int bitsPerComponent, boolean inverted) {
        float[] range = new float[components * 2];
        for (int i = 0; i < components; i++) {
            range[i * 2] = inverted ? 1 : 0;
            range[i * 2 + 1] = inverted ? 0 : 1;
        }
        return new SampleDecode(range, bitsPerComponent);
    }

    private static int[] convert(PixelConverter converter, int pixels, int... bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        SampleReader in = new SampleReader(data);
        int[] argb = new int[pixels];
        for (int i = 0; i < pixels; i++) {
            argb[i] = converter.next(in);
        }
        return argb;
    }

    @Test
    public void stencilMaskPaintsTheFillWhereTheSampleDecodesToZero() {
        int fill = 0xff10c020;
        int[] normal = convert(PixelConverter.forImage(new DeviceGray(new Library(), null), 1, decode(1, 1, false),
                true, fill, 4, null), 4, 0b0110_0000);
        assertEquals(fill, normal[0]);
        assertEquals(0, normal[1]);
        assertEquals(0, normal[2]);
        assertEquals(fill, normal[3]);
        int[] inverted = convert(PixelConverter.forImage(new DeviceGray(new Library(), null), 1, decode(1, 1, true),
                true, fill, 2, null), 2, 0b0100_0000);
        assertEquals(0, inverted[0]);
        assertEquals(fill, inverted[1]);
    }

    @Test
    public void grayIsAnOpaqueRampAtEveryDepth() {
        DeviceGray gray = new DeviceGray(new Library(), null);
        assertTrue(PixelConverter.forImage(gray, 8, decode(1, 8, false), false, 0, 1, null)
                instanceof GrayPixelConverter);
        assertEquals(0xff555555, convert(PixelConverter.forImage(gray, 2, decode(1, 2, false), false, 0, 1, null),
                1, 0b0100_0000)[0]);
        // 8 bits once put the grey into the alpha channel too
        assertEquals(0xff7f7f7f, convert(PixelConverter.forImage(gray, 8, decode(1, 8, false), false, 0, 1, null),
                1, 0x7f)[0]);
        // 16 bits once came out as the raw sample, nearly transparent
        assertEquals(0xff808080, convert(PixelConverter.forImage(gray, 16, decode(1, 16, false), false, 0, 1,
                null), 1, 0x80, 0x00)[0]);
    }

    @Test
    public void colourSpaceConvertsThroughGetColorAndCapturesCmyk() {
        DeviceCMYK cmyk = new DeviceCMYK(new Library(), null);
        byte[] capture = new byte[8];
        PixelConverter converter = PixelConverter.forImage(cmyk, 8, decode(4, 8, false), false, 0, 2, capture);
        int[] argb = convert(converter, 2, 10, 20, 30, 40, 10, 20, 30, 40);
        int expected = cmyk.getColor(new float[]{10 / 255f, 20 / 255f, 30 / 255f, 40 / 255f}).getRGB();
        assertEquals(expected, argb[0]);
        assertEquals(expected, argb[1]);
        assertEquals(40, capture[7]);
    }

    /** Six 8-bit colorants pack into a 48-bit key; colours are still exactly getColor's. */
    @Test
    public void sixColorantDeviceNMatchesGetColor() {
        Library library = new Library();
        DictionaryEntries function = new DictionaryEntries();
        function.put(new Name("FunctionType"), 4);
        function.put(new Name("Domain"), Arrays.asList(0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f));
        function.put(new Name("Range"), Arrays.asList(0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f));
        // keep C M Y K, drop the two spot colorants
        Stream tint = new Stream(library, function, "{ pop pop }".getBytes(StandardCharsets.US_ASCII));
        PColorSpace deviceN = new Resources(library, new DictionaryEntries()).getColorSpace(Arrays.asList(
                new Name("DeviceN"),
                Arrays.asList(new Name("Cyan"), new Name("Magenta"), new Name("Yellow"), new Name("Black"),
                        new Name("Orange"), new Name("Green")),
                new Name("DeviceCMYK"), tint));
        assertEquals(6, deviceN.getNumComponents());
        int pixels = 400;
        Random random = new Random(617);
        int[] bytes = new int[pixels * 6];
        for (int i = 0; i < pixels; i++) {
            // 40 colours repeated out of order, so the memo hits
            int colour = random.nextInt(40);
            for (int c = 0; c < 6; c++) {
                bytes[i * 6 + c] = (colour * 37 + c * 53) & 0xff;
            }
        }
        int[] argb = convert(PixelConverter.forImage(deviceN, 8, decode(6, 8, false), false, 0, pixels, null),
                pixels, bytes);
        float[] components = new float[6];
        for (int i = 0; i < pixels; i++) {
            for (int c = 0; c < 6; c++) {
                components[c] = bytes[i * 6 + c] / 255f;
            }
            assertEquals(deviceN.getColor(components.clone()).getRGB(), argb[i], "pixel " + i);
        }
    }
}
