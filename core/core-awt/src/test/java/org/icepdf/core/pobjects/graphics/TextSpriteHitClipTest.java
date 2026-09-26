/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The draw commands cull shapes with {@link TextSprite#hitClip}, so a wrong "miss" drops content from the page.
 */
public class TextSpriteHitClipTest {

    private Graphics2D g;

    @BeforeEach
    public void setUp() {
        g = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB).createGraphics();
        g.setClip(0, 0, 600, 800);
    }

    @AfterEach
    public void tearDown() {
        g.dispose();
    }

    @DisplayName("a shape inside the clip hits, one outside misses")
    @Test
    public void insideAndOutside() {
        assertTrue(TextSprite.hitClip(g, new Rectangle2D.Double(10.5, 10.5, 20, 20)));
        assertFalse(TextSprite.hitClip(g, new Rectangle2D.Double(700, 900, 20, 20)));
    }

    @DisplayName("a sub-pixel shape at the clip edge hits")
    @Test
    public void subPixelShape() {
        assertTrue(TextSprite.hitClip(g, new Rectangle2D.Double(599.2, 799.2, 0.1, 0.1)));
    }

    @DisplayName("a huge 'whole page' rectangle hits, rather than overflowing its width and being culled")
    @Test
    public void hugeRectangle() {
        // generators paint backgrounds as e.g. -1e10 -1e10 2e10 2e10 re f
        for (double extent : new double[]{1.2e9, 1e10, 1e300}) {
            assertTrue(TextSprite.hitClip(g, new Rectangle2D.Double(-extent, -extent, 2 * extent, 2 * extent)),
                    "extent " + extent);
        }
    }

    @DisplayName("NaN bounds hit, erring on the side of drawing")
    @Test
    public void nanBounds() {
        assertTrue(TextSprite.hitClip(g, new Rectangle2D.Double(Double.NaN, 0, 10, 10)));
    }
}
