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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blend compositing with a transparent backdrop (an isolated group's buffer, an appearance on its own
 * layer) per PDF 32000-1 11.3.6, at constant alpha below 1 and over partly transparent backdrops, in
 * straight (TYPE_INT_ARGB), premultiplied (TYPE_INT_ARGB_PRE) and opaque (TYPE_INT_RGB) targets.
 */
public class BlendCompositeAlphaTest {

    private static final int[] TYPES = {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE};

    @AfterEach
    void clearFlag() {
        BlendComposite.setTransparentBackdrop(false);
    }

    /** A 4x4 image filled with {@code backdrop} (straight ARGB), then a fill of {@code colour}. */
    private static BufferedImage paint(int type, Color backdrop, Color colour, String mode, float alpha,
                                       boolean transparentBackdrop) {
        BufferedImage image = new BufferedImage(4, 4, type);
        Graphics2D g = image.createGraphics();
        if (backdrop != null) {
            g.setComposite(AlphaComposite.Src);
            g.setColor(backdrop);
            g.fillRect(0, 0, 4, 4);
        }
        boolean previous = BlendComposite.setTransparentBackdrop(transparentBackdrop);
        try {
            g.setComposite(BlendComposite.getInstance(new org.icepdf.core.pobjects.Name(mode), alpha));
            g.setColor(colour);
            g.fillRect(0, 0, 4, 4);
        } finally {
            BlendComposite.setTransparentBackdrop(previous);
            g.dispose();
        }
        return image;
    }

    private static void assertColour(int r, int g, int b, int a, int argb, String what) {
        Color c = new Color(argb, true);
        String detail = what + ": expected (" + r + "," + g + "," + b + "," + a + ") got " + c + " a=" + c.getAlpha();
        assertTrue(Math.abs(c.getAlpha() - a) <= 2, detail);
        // a nearly transparent pixel's straight colour is imprecise; only judge colour with coverage.
        if (a >= 32) {
            assertTrue(Math.abs(c.getRed() - r) <= 3 && Math.abs(c.getGreen() - g) <= 3
                    && Math.abs(c.getBlue() - b) <= 3, detail);
        }
    }

    @DisplayName("a half-opacity Multiply over a transparent backdrop is the source colour at half alpha")
    @Test
    void halfAlphaOverTransparent() {
        for (int type : TYPES) {
            BufferedImage image = paint(type, null, Color.YELLOW, "Multiply", 0.5f, true);
            assertColour(255, 255, 0, 128, image.getRGB(1, 1), "type " + type);
        }
    }

    @DisplayName("a half-opacity Multiply over an opaque backdrop: same in straight, premultiplied and RGB")
    @Test
    void halfAlphaOverOpaque() {
        Color grey = new Color(200, 200, 200);
        // multiply(grey, yellow) = (200,200,0); half way from grey: (200,200,100)
        for (int type : new int[]{BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_INT_RGB}) {
            for (boolean flag : new boolean[]{true, false}) {
                BufferedImage image = paint(type, grey, Color.YELLOW, "Multiply", 0.5f, flag);
                assertColour(199, 199, 100, 255, image.getRGB(1, 1), "type " + type + " flag " + flag);
            }
        }
    }

    @DisplayName("over a half-transparent backdrop the result alpha is ab + as - ab*as, colour weighted by as/ar")
    @Test
    void partialBackdrop() {
        Color halfWhite = new Color(255, 255, 255, 128);
        for (int type : TYPES) {
            // opaque source at full alpha: replaces with Cs' = (1-ab)Cs + ab*B = yellow, alpha 1.
            assertColour(255, 255, 0, 255, paint(type, halfWhite, Color.YELLOW, "Multiply", 1f, true)
                    .getRGB(1, 1), "full alpha, type " + type);
            // half alpha: ar = .5 + .5 - .25 = .75; weight .5/.75: white -> yellow two thirds of the way.
            assertColour(255, 255, 85, 191, paint(type, halfWhite, Color.YELLOW, "Multiply", 0.5f, true)
                    .getRGB(1, 1), "half alpha, type " + type);
        }
    }

    @DisplayName("a premultiplied target never stores a colour brighter than its alpha")
    @Test
    void premultipliedStaysValid() {
        BufferedImage image = paint(BufferedImage.TYPE_INT_ARGB_PRE, new Color(0, 0, 255, 60), Color.YELLOW,
                "Screen", 0.3f, true);
        int raw = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData()[5];
        int a = raw >>> 24;
        assertTrue(((raw >> 16) & 0xFF) <= a && ((raw >> 8) & 0xFF) <= a && (raw & 0xFF) <= a,
                String.format("valid premultiplied pixel: %08x", raw));
    }

    @DisplayName("an opaque source at full alpha over an opaque backdrop is the plain blend")
    @Test
    void opaqueUnchanged() {
        Color grey = new Color(200, 200, 200);
        for (boolean flag : new boolean[]{true, false}) {
            BufferedImage image = paint(BufferedImage.TYPE_INT_ARGB, grey, Color.YELLOW, "Multiply", 1f, flag);
            int c = image.getRGB(1, 1);
            assertEquals(0xFF, c >>> 24);
            assertEquals((200 * 255) >> 8, (c >> 16) & 0xFF, "multiply red, flag " + flag);
            assertEquals(0, c & 0xFF, "multiply blue, flag " + flag);
        }
    }
}
