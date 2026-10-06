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

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the compositing rule for an element of a knockout transparency group
 * (PDF 32000-1 §11.4.5.5, {@code /Group << /K true >>}): the element composites
 * with the group's <i>initial</i> backdrop rather than with the elements before
 * it, so where it has coverage it <i>replaces</i> them -- colour and alpha both.
 * <p>
 * The behaviour these tests exist to protect is the difference from SRC_OVER: a
 * stack of semi-transparent siblings composited over one another accumulates as
 * {@code 1 - prod(1 - a_i)} and saturates to opaque (Duke's nose rendering as a
 * flat red disc instead of a shaded sphere, Java Magazine Sept/Oct 2016 p3).
 */
public class KnockoutCompositeTest {

    /** Runs the composite over a single pixel and returns the resulting ARGB. */
    private static int compose(KnockoutComposite composite, int srcArgb, int dstArgb) {
        BufferedImage src = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage dst = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(0, 0, srcArgb);
        dst.setRGB(0, 0, dstArgb);
        WritableRaster out = dst.getRaster().createCompatibleWritableRaster();
        CompositeContext context = composite.createContext(src.getColorModel(), dst.getColorModel(), null);
        context.compose(src.getRaster(), dst.getRaster(), out);
        context.dispose();
        BufferedImage result = new BufferedImage(dst.getColorModel(), out, false, null);
        return result.getRGB(0, 0);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /**
     * The whole point: a half-opaque element lands on an opaque sibling as its own
     * half-opaque self.  Composited SRC_OVER the destination would stay opaque and
     * the colours would mix; knocked out the sibling is simply gone.
     */
    @Test
    public void semiTransparentElementReplacesTheSiblingBeneathIt() {
        // source buffer already carries the element's ca (0.5), as a group buffer does.
        int result = compose(new KnockoutComposite(0.5f, 1f),
                argb(128, 0, 0, 255), argb(255, 255, 0, 0));

        assertEquals(128, alpha(result), "alpha is the element's own, not an accumulation");
        assertEquals(argb(128, 0, 0, 255), result, "the element replaces the sibling outright");
    }

    /**
     * A fully transparent element ({@code ca 0}) still knocks out: it replaces what
     * is underneath with nothing, punching a hole through to the group's backdrop.
     * This is the case the old {@code AlphaComposite.SRC} approximation got wrong in
     * the other direction, painting a black disc (transparent_groups.pdf).
     */
    @Test
    public void fullyTransparentElementPunchesAHole() {
        // a fill: the source raster carries coverage, the constant alpha is 0.
        int result = compose(new KnockoutComposite(1f, 0f),
                argb(255, 255, 255, 255), argb(255, 255, 0, 0));

        assertEquals(0, alpha(result), "a ca=0 element knocks the sibling out to transparent");
    }

    /**
     * Outside the element there is no coverage, so earlier siblings survive.
     */
    @Test
    public void noCoverageLeavesTheDestinationAlone() {
        int dst = argb(255, 255, 0, 0);
        int result = compose(new KnockoutComposite(1f, 1f), argb(0, 0, 0, 255), dst);

        assertEquals(dst, result, "a pixel the element does not cover is not knocked out");
    }

    /**
     * An anti-aliased edge is partial coverage, not a partial constant alpha, so it
     * feathers between the sibling and the replacing element instead of replacing
     * outright -- otherwise every element's soft edge would cut a hard step into the
     * one below it.
     */
    @Test
    public void antiAliasedEdgeFeathersBetweenSiblingAndElement() {
        // element alpha 1.0, source alpha 128 => half coverage.
        int result = compose(new KnockoutComposite(1f, 1f),
                argb(128, 0, 0, 0), argb(255, 255, 255, 255));

        int red = (result >> 16) & 0xFF;
        assertTrue(red > 100 && red < 155, "edge pixel should be about half way, got " + red);
        assertTrue(alpha(result) > 150, "half coverage keeps most of the opaque sibling's alpha");
    }

    /**
     * The constant alpha still applies to what is knocked out: with the element's
     * alpha supplied separately (a path fill, whose raster carries only coverage),
     * the result takes that alpha.
     */
    @Test
    public void constantAlphaBecomesTheKnockedOutAlpha() {
        int result = compose(new KnockoutComposite(1f, 0.25f),
                argb(255, 0, 0, 255), argb(255, 255, 0, 0));

        assertEquals(64, alpha(result), 1, "result carries the element's constant alpha");
        assertEquals(255, result & 0xFF, "and the element's colour");
    }
}
