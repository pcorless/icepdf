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
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.awt.color.ColorSpace;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * GH-617: ICCBased colours come from a lock-free cache shared by every rendering thread. A cached colour must
 * depend on its key alone: the same in any order, on any thread, and for an image sample exactly the direct
 * conversion it always was.
 */
public class ICCBasedTest {

    private static ICCBased cmykProfile() throws IOException {
        byte[] profile;
        try (InputStream in = DeviceCMYK.class.getResourceAsStream(
                "/org/icepdf/core/pobjects/graphics/res/CoatedFOGRA27.icc")) {
            profile = in.readAllBytes();
        }
        Library library = new Library();
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(new Name("N"), 4);
        return new ICCBased(library, new Stream(library, entries, profile));
    }

    /** The conversion ICCBased makes, without a cache. */
    private static int direct(ColorSpace colorSpace, float[] f) {
        float[] rgb = colorSpace.toRGB(f);
        return 0xff000000 | ((int) (rgb[0] * 255) & 0xff) << 16 | ((int) (rgb[1] * 255) & 0xff) << 8
                | ((int) (rgb[2] * 255) & 0xff);
    }

    private static float[] sample(int packed) {
        return new float[]{(packed >>> 24) / 255f, (packed >>> 16 & 0xff) / 255f, (packed >>> 8 & 0xff) / 255f,
                (packed & 0xff) / 255f};
    }

    @Test
    public void imageSamplesConvertExactlyAsBefore() throws IOException {
        ICCBased icc = cmykProfile();
        icc.init();
        Random random = new Random(617);
        for (int i = 0; i < 20_000; i++) {
            // a small range of colours, so later lookups hit the cache
            float[] f = sample(random.nextInt(3000) * 0x9E3779B1);
            assertEquals(direct(icc.getColorSpace(), f), icc.getColor(f).getRGB());
        }
    }

    @Test
    public void aColourDependsOnItsKeyNotOnWhatFilledTheSlot() throws IOException {
        // 0.5 and 0.501 share a key; whichever comes first, both get the colour of the key's own value
        float[] half = {0.5f, 0.5f, 0.5f, 0.5f};
        float[] nearHalf = {0.501f, 0.501f, 0.501f, 0.501f};
        ICCBased first = cmykProfile();
        ICCBased second = cmykProfile();
        int a = first.getColor(half).getRGB();
        assertEquals(a, first.getColor(nearHalf).getRGB());
        assertEquals(a, second.getColor(nearHalf).getRGB());
        assertEquals(a, second.getColor(half).getRGB());
        assertEquals(direct(first.getColorSpace(), sample(0x7f7f7f7f)), a, "the key 127 stands for 127/255");
    }

    @Test
    public void outOfRangeComponentsClampInsteadOfWrapping() throws IOException {
        ICCBased icc = cmykProfile();
        // (int) (1.2 * 255) & 0xff was 50: a too-large component took another colour's key
        int over = icc.getColor(new float[]{1.2f, 0, 0, 0}).getRGB();
        assertEquals(icc.getColor(new float[]{1f, 0, 0, 0}).getRGB(), over);
        assertNotEquals(icc.getColor(new float[]{50 / 255f, 0, 0, 0}).getRGB(), over);
        assertEquals(icc.getColor(new float[]{0, 0, 0, 0}).getRGB(), icc.getColor(new float[]{-0.3f, 0, 0, 0}).getRGB());
    }

    @Test
    public void threadsSharingTheCacheAgree() throws Exception {
        ICCBased icc = cmykProfile();
        int colours = 40_000;
        int[] expected = new int[colours];
        ICCBased reference = cmykProfile();
        for (int i = 0; i < colours; i++) {
            expected[i] = reference.getColor(sample(i * 0x9E3779B1)).getRGB();
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                // the first getColor on each thread races init() too
                results.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 100_000; i++) {
                        int c = random.nextInt(colours);
                        assertEquals(expected[c], icc.getColor(sample(c * 0x9E3779B1)).getRGB(), "colour " + c);
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }
    }
}
