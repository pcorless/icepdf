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
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-617: RawDecoder applies an image's whole Decode array (8.9.5.2), and the colour space's default when it
 * has none (Table 90).
 */
public class RawDecodeArrayTest {

    private static BufferedImage decode(Object colorSpace, int bitsPerComponent, List<Float> decode, int w,
                                        int... bytes) {
        Library library = new Library();
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("Type"), new Name("XObject"));
        entries.put(new Name("Subtype"), new Name("Image"));
        entries.put(new Name("Width"), w);
        entries.put(new Name("Height"), 1);
        entries.put(new Name("BitsPerComponent"), bitsPerComponent);
        entries.put(new Name("ColorSpace"), colorSpace);
        if (decode != null) {
            entries.put(new Name("Decode"), decode);
        }
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        GraphicsState graphicsState = new GraphicsState(new Shapes());
        graphicsState.setFillColor(Color.BLACK);
        BufferedImage image = new ImageStream(library, entries, data)
                .getImage(graphicsState, new Resources(library, new DictionaryEntries()));
        assertTrue(image != null);
        return image;
    }

    private static List<Object> separation() {
        DictionaryEntries tint = new DictionaryEntries();
        tint.put(new Name("FunctionType"), 2);
        tint.put(new Name("Domain"), Arrays.asList(0f, 1f));
        tint.put(new Name("C0"), Arrays.asList(0f, 0f, 0f, 0f));
        tint.put(new Name("C1"), Arrays.asList(0f, 0f, 0f, 1f));
        tint.put(new Name("N"), 1f);
        return Arrays.asList(new Name("Separation"), new Name("Spot"), new Name("DeviceCMYK"), tint);
    }

    private static int gray(int argb) {
        return argb & 0xff;
    }

    /** An inverted 8-bit tint used to snap to 0 or full, so a ramp came out two-tone. */
    @Test
    public void invertedEightBitTintKeepsItsLevels() {
        BufferedImage ramp = decode(separation(), 8, Arrays.asList(1f, 0f), 3, 0, 128, 255);
        int full = gray(ramp.getRGB(0, 0));
        int half = gray(ramp.getRGB(1, 0));
        int none = gray(ramp.getRGB(2, 0));
        // sample 0 decodes to tint 1 (black ink), 255 to tint 0 (paper)
        assertTrue(full < half && half < none, full + " < " + half + " < " + none);
        assertEquals(gray(decode(separation(), 8, null, 1, 127).getRGB(0, 0)), half);
    }

    /** A three-component image ignored its Decode. */
    @Test
    public void invertedRgbFlips() {
        // 4-bit RGB, one pixel: red full, green 0, blue 5 -- 12 bits padded to 2 bytes
        BufferedImage image = decode(new Name("DeviceRGB"), 4, Arrays.asList(1f, 0f, 1f, 0f, 1f, 0f), 1,
                0xF0, 0x50);
        assertEquals(0xff00ffaa, image.getRGB(0, 0));
    }

    /** A Lab image's samples are L*, a*, b* across Lab's own default ranges, in that order. */
    @Test
    public void labDecodesToItsOwnUnits() {
        List<Object> lab = Arrays.asList(new Name("Lab"), labDictionary());
        // L* 100, a* 0, b* 0 is the white point; L* 0 is black; L* 50 a mid grey
        int white = decode(lab, 8, null, 1, 255, 128, 128).getRGB(0, 0);
        int black = decode(lab, 8, null, 1, 0, 128, 128).getRGB(0, 0);
        int grey = decode(lab, 8, null, 1, 128, 128, 128).getRGB(0, 0);
        // Lab.getColor's own conversion puts the white point at (255, 254, 244)
        assertTrue(gray(white) >= 240 && ((white >> 8) & 0xff) >= 250 && ((white >> 16) & 0xff) >= 250,
                Integer.toHexString(white));
        assertTrue(gray(black) <= 5, Integer.toHexString(black));
        assertTrue(Math.abs(gray(grey) - 119) <= 4, Integer.toHexString(grey));
        // a* positive is red
        int red = decode(lab, 8, null, 1, 128, 255, 128).getRGB(0, 0);
        assertTrue(((red >> 16) & 0xff) > gray(red) + 60, Integer.toHexString(red));
    }

    private static DictionaryEntries labDictionary() {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("WhitePoint"), Arrays.asList(0.9505f, 1f, 1.089f));
        entries.put(new Name("Range"), Arrays.asList(-128f, 127f, -128f, 127f));
        return entries;
    }
}
