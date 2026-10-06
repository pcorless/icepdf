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

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Composites one element of a <i>knockout</i> transparency group
 * (PDF 32000-1 §11.4.5.5, {@code /Group << /K true >>}).
 * <p>
 * In a knockout group every element composites with the group's <i>initial</i>
 * backdrop rather than with the accumulated result of the elements before it, so
 * a later element completely replaces earlier siblings wherever it has coverage.
 * Inside the group's (transparent) buffer an element's composite against that
 * initial backdrop is simply the element itself, which makes this composite a
 * coverage-weighted replace:
 * <pre>
 *     dst = lerp(dst, src, coverage)
 * </pre>
 * -- replacing colour <b>and</b> alpha, where SRC_OVER would have added them.
 * That difference is the whole point: a stack of semi-transparent siblings drawn
 * SRC_OVER accumulates as {@code 1 - prod(1 - a_i)} and saturates, while a
 * knockout stack shows only the topmost covering element at its own alpha.  The
 * anchor case is an Illustrator blend -- ~200 nested ellipses whose constant
 * alpha ramps 0.005 to 1.0 (Duke's nose highlight, Java Magazine Sept/Oct 2016
 * p3/p61): accumulated it is a flat saturated disc, knocked out it is a smooth
 * dome.
 * <p>
 * <b>Coverage vs alpha.</b> The source is the element's rasterised buffer, whose
 * alpha already carries both the element's constant alpha and its anti-aliased
 * edge coverage.  Replacing wherever {@code alpha > 0} would let a feathered edge
 * hard-replace an opaque sibling, so {@code elementAlpha} (the constant alpha
 * baked into the buffer, measured as its peak) is divided back out to recover
 * coverage; the interior then replaces exactly and only the edges feather.
 */
public class KnockoutComposite implements Composite {

    // Constant alpha already baked into the source buffer, used to separate the
    // element's own alpha from its per-pixel coverage.  1.0 when unknown.
    private final float elementAlpha;
    // Additional constant alpha to apply to the source, mirroring
    // AlphaComposite's alpha (the group ca, when it was not already baked in).
    private final float extraAlpha;

    /**
     * @param elementAlpha constant alpha baked into the source buffer (its peak
     *                     alpha), in 0..1; pass 1 when unknown.
     * @param extraAlpha   further constant alpha to apply to the source, in 0..1.
     */
    public KnockoutComposite(float elementAlpha, float extraAlpha) {
        this.elementAlpha = clamp(elementAlpha);
        this.extraAlpha = clamp(extraAlpha);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public float getElementAlpha() {
        return elementAlpha;
    }

    public float getExtraAlpha() {
        return extraAlpha;
    }

    @Override
    public String toString() {
        return "Knockout element=" + elementAlpha + " extra=" + extraAlpha;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel,
                                          RenderingHints hints) {
        return new KnockoutContext(elementAlpha, extraAlpha);
    }

    private static final class KnockoutContext implements CompositeContext {

        private final float elementAlpha;
        private final float extraAlpha;

        private KnockoutContext(float elementAlpha, float extraAlpha) {
            this.elementAlpha = elementAlpha;
            this.extraAlpha = extraAlpha;
        }

        public void dispose() {
        }

        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            if (src.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
                    dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT ||
                    dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
                throw new IllegalStateException("Source and destination must store pixels as INT.");
            }
            int width = Math.min(src.getWidth(), dstIn.getWidth());
            int height = Math.min(src.getHeight(), dstIn.getHeight());
            int[] srcPixels = new int[width];
            int[] dstPixels = new int[width];
            for (int y = 0; y < height; y++) {
                src.getDataElements(0, y, width, 1, srcPixels);
                dstIn.getDataElements(0, y, width, 1, dstPixels);
                for (int x = 0; x < width; x++) {
                    int sp = srcPixels[x];
                    float srcAlpha = ((sp >> 24) & 0xFF) / 255f;
                    // Coverage and alpha are separate questions.  Coverage decides
                    // WHERE this element knocks out and comes from the source's own
                    // alpha with any constant alpha already baked into it divided
                    // back out; the resulting alpha is what it knocks out WITH.
                    // Keeping them apart is what lets a fully transparent element
                    // (ca 0) still knock out -- it replaces what is underneath with
                    // nothing, punching a hole, which is exactly what the PDF
                    // 32000-1 figure's ca=0 circle demonstrates.
                    float coverage = elementAlpha > 0f ? srcAlpha / elementAlpha : srcAlpha;
                    if (coverage <= 0f) {
                        // no coverage: the element does not knock out here.
                        continue;
                    }
                    if (coverage > 1f) {
                        coverage = 1f;
                    }
                    float sa = srcAlpha * extraAlpha;
                    int dp = dstPixels[x];
                    int da = (dp >>> 24) & 0xFF;
                    // The element's composite against the group's transparent
                    // initial backdrop is the element itself: its colour at its
                    // own alpha, which replaces the destination under coverage.
                    int sAlpha = Math.round(sa * 255f);
                    dstPixels[x] =
                            (lerp(da, sAlpha, coverage) & 0xFF) << 24 |
                                    (lerp((dp >> 16) & 0xFF, (sp >> 16) & 0xFF, coverage) & 0xFF) << 16 |
                                    (lerp((dp >> 8) & 0xFF, (sp >> 8) & 0xFF, coverage) & 0xFF) << 8 |
                                    (lerp(dp & 0xFF, sp & 0xFF, coverage) & 0xFF);
                }
                dstOut.setDataElements(0, y, width, 1, dstPixels);
            }
        }

        private static int lerp(int from, int to, float t) {
            return Math.round(from + (to - from) * t);
        }
    }
}
