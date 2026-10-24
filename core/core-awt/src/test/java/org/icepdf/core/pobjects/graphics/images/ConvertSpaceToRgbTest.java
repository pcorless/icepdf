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
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-617: the DCT and JPX colour path ({@link ImageUtility#convertSpaceToRgb}) decodes a colour space in its own
 * units, such as Lab, instead of handing it 0..1.
 */
public class ConvertSpaceToRgbTest {

    private static Raster raster(int... samples) {
        byte[] data = new byte[samples.length];
        for (int i = 0; i < samples.length; i++) {
            data[i] = (byte) samples[i];
        }
        int pixels = samples.length / 3;
        return Raster.createInterleavedRaster(new DataBufferByte(data, data.length), pixels, 1, pixels * 3, 3,
                new int[]{0, 1, 2}, null);
    }

    private static PColorSpace lab() {
        Library library = new Library();
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("WhitePoint"), Arrays.asList(0.9642f, 1f, 0.8249f));
        entries.put(new Name("Range"), Arrays.asList(-128f, 127f, -128f, 127f));
        return new Resources(library, new DictionaryEntries()).getColorSpace(Arrays.asList(new Name("Lab"), entries));
    }

    private static float[] decode(PColorSpace colorSpace) {
        // ImageParams.getDecode() with no Decode: [0, 1/255] per component, whatever the space
        float[] decode = new float[colorSpace.getNumComponents() * 2];
        for (int i = 1; i < decode.length; i += 2) {
            decode[i] = 1 / 255f;
        }
        return decode;
    }

    @Test
    public void labSamplesAreLabUnits() {
        PColorSpace lab = lab();
        BufferedImage image = ImageUtility.convertSpaceToRgb(raster(255, 128, 128, 0, 128, 128, 128, 128, 128),
                lab, decode(lab), lab.getDefaultDecode(8));
        // white, black and L* 50 grey; as 0..1 values all three came out black
        assertEquals(0xffffff, image.getRGB(0, 0) & 0xffffff);
        assertEquals(0x000000, image.getRGB(1, 0) & 0xffffff);
        int grey = image.getRGB(2, 0) & 0xff;
        assertTrue(Math.abs(grey - 119) <= 2, "grey " + grey);
    }

    @Test
    public void unitSpacesAreUnchanged() {
        PColorSpace rgb = new Resources(new Library(), new DictionaryEntries()).getColorSpace(new Name("DeviceRGB"));
        BufferedImage image = ImageUtility.convertSpaceToRgb(raster(10, 20, 30), rgb, decode(rgb),
                rgb.getDefaultDecode(8));
        assertEquals(0x0a141e, image.getRGB(0, 0) & 0xffffff);
    }
}
