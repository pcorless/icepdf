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
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GH-617: Separation colours come from a lock-free cache keyed on the exact tint, so a cached colour is the
 * one its tint converts to, whatever came first and on whichever thread.
 */
public class SeparationCacheTest {

    private static DictionaryEntries tintTransform() {
        DictionaryEntries tint = new DictionaryEntries();
        tint.put(new Name("FunctionType"), 2);
        tint.put(new Name("Domain"), Arrays.asList(0f, 1f));
        tint.put(new Name("C0"), Arrays.asList(0f, 0f, 0f, 0f));
        tint.put(new Name("C1"), Arrays.asList(0.1f, 0.8f, 0.3f, 0.05f));
        tint.put(new Name("N"), 1f);
        return tint;
    }

    private static Separation spot(Library library) {
        return new Separation(library, new DictionaryEntries(), new Name("Spot"), new Name("DeviceCMYK"),
                tintTransform());
    }

    /** The conversion Separation makes, without a cache. */
    private static int direct(Library library, float tint) {
        Function function = Function.getFunction(library, tintTransform());
        return new DeviceCMYK(library, null).getColor(function.calculate(new float[]{tint})).getRGB();
    }

    @Test
    public void tintsConvertExactlyWhateverCameFirst() {
        Library library = new Library();
        Separation spot = spot(library);
        Random random = new Random(617);
        for (int i = 0; i < 5000; i++) {
            // image samples and arbitrary fill tints, repeated so later lookups hit the cache
            float tint = i % 2 == 0 ? random.nextInt(256) / 255f : random.nextInt(2000) / 1999f;
            assertEquals(direct(library, tint), spot.getColor(new float[]{tint}).getRGB(), "tint " + tint);
        }
    }

    @Test
    public void namedColourTintsMatchAcrossThreads() throws Exception {
        Separation red = new Separation(new Library(), new DictionaryEntries(), new Name("Red"),
                new Name("DeviceRGB"), null);
        int tints = 3000;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                results.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 50_000; i++) {
                        float tint = random.nextInt(tints) / (float) (tints - 1);
                        int light = Math.round((1 - tint) * 255);
                        assertEquals(0xff000000 | 0xff0000 | light << 8 | light,
                                red.getColor(new float[]{tint}, true).getRGB(), "tint " + tint);
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
