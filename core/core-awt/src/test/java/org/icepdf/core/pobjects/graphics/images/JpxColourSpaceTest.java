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
import org.icepdf.core.pobjects.graphics.PColorSpace;
import org.icepdf.core.pobjects.graphics.images.jpx.Jpeg2000Fixtures;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GH-617: a JPXDecode image in a colour space the decoder had no branch for (DeviceN, CalGray, CalRGB, Lab) was
 * drawn as the reader's raster, as if grey or RGB. Each pixel must be its colour space's colour for the
 * reference samples.
 */
public class JpxColourSpaceTest {

    private static final Library LIBRARY = new Library();

    private static PColorSpace resolve(Object colourSpace) {
        return new Resources(LIBRARY, new DictionaryEntries()).getColorSpace(colourSpace);
    }

    private static void assertColours(String fixture, String reference, Object colourSpace) {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        BufferedImage image = JpxDecoderTest.decodeFixture(fixture, 64, 64, colourSpace);
        assertNotNull(image);
        PColorSpace space = resolve(colourSpace);
        float[] range = space.getDefaultDecode(8);
        List<Jpeg2000Fixtures.Component> c = JpxDecoderTest.reference(reference);
        int bad = 0;
        String first = null;
        for (int p = 0; p < 64 * 64; p++) {
            float[] values = new float[c.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = range[2 * i] + (range[2 * i + 1] - range[2 * i]) * c.get(i).samples[p] / 255f;
            }
            int want = space.getColor(values).getRGB() & 0xffffff;
            int got = image.getRGB(p % 64, p / 64) & 0xffffff;
            if (Math.abs((want >> 16) - (got >> 16)) > 1 || Math.abs((want >> 8 & 0xff) - (got >> 8 & 0xff)) > 1
                    || Math.abs((want & 0xff) - (got & 0xff)) > 1) {
                if (first == null) {
                    first = "pixel " + p + " want " + Integer.toHexString(want) + " got " + Integer.toHexString(got);
                }
                bad++;
            }
        }
        assertEquals(0, bad, fixture + " as " + colourSpace + ": " + first);
    }

    @Test
    public void calGrayAppliesItsGamma() {
        DictionaryEntries calGray = new DictionaryEntries();
        calGray.put(new Name("WhitePoint"), Arrays.asList(0.9505f, 1f, 1.089f));
        calGray.put(new Name("Gamma"), 2.2f);
        assertColours("rev53-gray.j2k", "rev53-gray", Arrays.asList(new Name("CalGray"), calGray));
    }

    @Test
    public void calRgb() {
        DictionaryEntries calRgb = new DictionaryEntries();
        calRgb.put(new Name("WhitePoint"), Arrays.asList(0.9505f, 1f, 1.089f));
        assertColours("rev53-rgb.j2k", "rev53-rgb", Arrays.asList(new Name("CalRGB"), calRgb));
    }

    @Test
    public void labIsInLabUnits() {
        DictionaryEntries lab = new DictionaryEntries();
        lab.put(new Name("WhitePoint"), Arrays.asList(0.9642f, 1f, 0.8249f));
        lab.put(new Name("Range"), Arrays.asList(-128f, 127f, -128f, 127f));
        assertColours("rev53-rgb.j2k", "rev53-rgb", Arrays.asList(new Name("Lab"), lab));
    }

    @Test
    public void deviceNRunsItsTintTransform() {
        DictionaryEntries function = new DictionaryEntries();
        function.put(new Name("FunctionType"), 4);
        function.put(new Name("Domain"), Arrays.asList(0f, 1f, 0f, 1f, 0f, 1f));
        function.put(new Name("Range"), Arrays.asList(0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f));
        // three spot inks onto C, M and K
        Stream tint = new Stream(LIBRARY, function,
                "{ 3 1 roll 0 3 1 roll 4 -1 roll }".getBytes(StandardCharsets.US_ASCII));
        assertColours("rev53-rgb.j2k", "rev53-rgb", Arrays.asList(new Name("DeviceN"),
                Arrays.asList(new Name("Spot1"), new Name("Spot2"), new Name("Spot3")), new Name("DeviceCMYK"),
                tint));
    }

    /** DeviceGray reads back (getRGB, as a soft mask is read) as its samples; on CS_GRAY it was gamma-lifted. */
    @Test
    public void deviceGrayReadsBackAsItsSamples() {
        assumeTrue(JpxDecoder.isDecoderAvailable(), "no JPEG 2000 reader installed");
        BufferedImage image = JpxDecoderTest.decodeFixture("rev53-gray.j2k", 64, 64, "DeviceGray");
        assertNotNull(image);
        int[] samples = JpxDecoderTest.reference("rev53-gray").get(0).samples;
        int bad = 0;
        for (int p = 0; p < samples.length; p++) {
            if ((image.getRGB(p % 64, p / 64) & 0xffffff) != samples[p] * 0x010101) {
                bad++;
            }
        }
        assertEquals(0, bad, "pixels not reading back as their sample");
    }
}
