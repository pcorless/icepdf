/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the subtractive-blend contract of {@link CmykBlendingSpace} (GH-501): the
 * §11.3.5 blend math run on the ink complement, and a device conversion that
 * round-trips losslessly so the isolated group colour survives the composite.
 */
public class CmykBlendingSpaceTest {

    private static final CmykBlendingSpace CMYK = CmykBlendingSpace.INSTANCE;
    private static final RgbBlendingSpace RGB = RgbBlendingSpace.INSTANCE;

    /** The round-trip must be the identity for in-gamut colours, so an isolated
     *  group colour Cs is unchanged and only the backdrop blend alters a pixel. */
    @Test
    public void srgbRoundTripIsLossless() {
        int[] samples = {
                0xFFFFFFFF, // white
                0xFF000000, // black
                0xFFFF0000, // red
                0xFF00FF00, // green
                0xFF0000FF, // blue
                0xFF7F3D12, // an arbitrary mid colour
                0xFF12AB9C,
        };
        double[] ch = new double[4];
        for (int argb : samples) {
            CMYK.fromSRGB(argb, ch);
            int back = CMYK.toSRGB(ch, 0xFF);
            assertEquals(argb & 0xFFFFFF, back & 0xFFFFFF,
                    () -> String.format("round-trip lost colour: %06X -> %06X",
                            argb & 0xFFFFFF, back & 0xFFFFFF));
        }
    }

    /** Multiply with white (no ink) is the identity in CMYK: full ink stays full
     *  ink.  The naive additive form (cb*cs/255) would wrongly wash it to zero --
     *  this is the bug that muddied DeviceCMYK groups blended in RGB. */
    @Test
    public void multiplyWithWhitePreservesInk() {
        double fullInk = 255.0, noInk = 0.0;
        assertEquals(fullInk,
                CMYK.separable(BlendComposite.BlendingMode.MULTIPLY, fullInk, noInk), 1e-9);
        assertEquals(fullInk,
                CMYK.separable(BlendComposite.BlendingMode.MULTIPLY, noInk, fullInk), 1e-9);
    }

    /** Multiply of two inked colorants darkens (adds ink) rather than lightening. */
    @Test
    public void multiplyDarkensInCmyk() {
        double half = 127.5;
        double result = CMYK.separable(BlendComposite.BlendingMode.MULTIPLY, half, half);
        // additive multiply of the complements (127.5*127.5/255 = 63.75), complemented
        // back to 191.25 ink -- strictly MORE ink than either operand.
        assertEquals(191.25, result, 1e-9);
        assertTrue(result > half, "subtractive multiply must increase ink (darken)");
    }

    /** Darken in a subtractive space picks the colour with MORE ink (the darker
     *  one), i.e. the max ink -- the complement of additive Darken's min. */
    @Test
    public void darkenSelectsMoreInk() {
        double lo = 40, hi = 200;
        assertEquals(hi, CMYK.separable(BlendComposite.BlendingMode.DARKEN, lo, hi), 1e-9);
        assertEquals(hi, CMYK.separable(BlendComposite.BlendingMode.DARKEN, hi, lo), 1e-9);
    }

    /** Every separable mode must satisfy the complement relationship to the
     *  verified additive math, so CmykBlendingSpace never re-derives blend rules. */
    @Test
    public void separableIsComplementOfAdditive() {
        double[] operands = {0, 40, 127.5, 200, 255};
        for (BlendComposite.BlendingMode mode : BlendComposite.BlendingMode.values()) {
            for (double cb : operands) {
                for (double cs : operands) {
                    double expected = 255.0 - RGB.separable(mode, 255.0 - cb, 255.0 - cs);
                    assertEquals(expected, CMYK.separable(mode, cb, cs), 1e-9,
                            () -> "mode " + mode + " broke the complement identity");
                }
            }
        }
    }

    @Test
    public void hasFourChannels() {
        assertEquals(4, CMYK.channelCount());
    }
}