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
package org.icepdf.core.pobjects.graphics.images.references;

import org.icepdf.core.pobjects.Reference;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins {@link ImagePool} retention semantics after the zoom-time image-drop fix.
 * The pool previously used a {@link java.util.WeakHashMap} keyed by a
 * freshly-allocated {@link Reference} that nothing else held strongly, so entries
 * were collectable on the very next GC and vanished under the allocation churn of
 * zooming.  The pool now holds decoded images behind {@link java.lang.ref.SoftReference}
 * values keyed by a value-equal {@code Reference}, so an image survives a normal GC
 * and is found again by an equal (but not identical) lookup reference.
 */
public class ImagePoolTest {

    private static BufferedImage img() {
        return new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    public void survivesNormalGc() {
        ImagePool pool = new ImagePool();
        Reference key = new Reference(12, 0);
        BufferedImage image = img();
        pool.put(key, image);

        // a normal GC must not clear a soft reference while the heap is healthy.
        System.gc();

        // lookup with an equal-but-distinct reference (the real call site uses the
        // ImageReference's own Reference field, never the map's internal copy).
        BufferedImage found = pool.get(new Reference(12, 0));
        assertSame(image, found, "image should survive a normal GC and be found by an equal reference");
        assertTrue(pool.containsKey(new Reference(12, 0)));
    }

    @Test
    public void distinctReferencesAreIndependent() {
        ImagePool pool = new ImagePool();
        BufferedImage a = img();
        BufferedImage b = img();
        pool.put(new Reference(1, 0), a);
        pool.put(new Reference(2, 0), b);

        assertSame(a, pool.get(new Reference(1, 0)));
        assertSame(b, pool.get(new Reference(2, 0)));
        assertNull(pool.get(new Reference(3, 0)));
        assertFalse(pool.containsKey(new Reference(3, 0)));
    }

    @Test
    public void nullArgumentsAreIgnored() {
        ImagePool pool = new ImagePool();
        pool.put(null, img());          // no key -> no-op, no NPE
        pool.put(new Reference(9, 0), null); // no image -> no-op, no NPE
        assertNull(pool.get(null));
        assertNull(pool.get(new Reference(9, 0)));
    }
}
