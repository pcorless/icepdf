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

/**
 * The subtractive (DeviceCMYK) {@link BlendingSpace}: four ink channels and the
 * PDF 32000-1 &sect;11.3.5 separable blend functions evaluated in CMYK.
 * <p>
 * The §11.3.5 blend functions are <i>defined</i> on additive colour values, where
 * a larger value is a brighter colour.  CMYK is subtractive: a larger value is
 * <i>more ink</i>, i.e. a darker colour -- the opposite sense.  The spec resolves
 * this (§11.3.5, note on subtractive spaces) by complementing each colorant before
 * the blend and complementing the result afterwards.  So a CMYK blend is exactly
 * the additive blend run on the ink complement:
 * <pre>
 *     B_cmyk(cb, cs) = 255 - B_rgb(255 - cb, 255 - cs)
 * </pre>
 * which lets this space reuse {@link RgbBlendingSpace}'s already-verified separable
 * math verbatim instead of re-deriving a parallel set of darkening rules.  A worked
 * check: {@code Multiply} of full ink over no ink (white) must preserve the ink
 * (multiply-with-white is identity); the complement form gives
 * {@code 255 - (0*255/255) = 255}, full ink -- correct, whereas the naive additive
 * {@code cb*cs/255 = 0} would wrongly wash it to white.  This is the whole reason a
 * DeviceCMYK group muddied when composited in RGB (GH-501).
 * <p>
 * Device conversion uses the textbook GCR sRGB&harr;CMYK transform, which is
 * exactly invertible for in-gamut colours: {@link #toSRGB}({@link #fromSRGB}(p)) ==
 * p.  That is deliberate -- the isolated group colour {@code Cs} therefore passes
 * through losslessly and <i>only</i> the backdrop interaction (the separable blend
 * against {@code Cb}) changes, keeping this strategy a surgical swap of the blend
 * math rather than a colour shift.  (An ICC-accurate device conversion, and reading
 * the group's <i>true</i> preserved CMYK samples instead of recovering them from
 * sRGB, are the GH-501 Phase 2 follow-ups; see
 * {@code ImageUtility.getCmykSamples}.)
 *
 * @see RgbBlendingSpace
 * @see BlendingSpace
 */
public final class CmykBlendingSpace implements BlendingSpace {

    public static final CmykBlendingSpace INSTANCE = new CmykBlendingSpace();

    private CmykBlendingSpace() {
    }

    @Override
    public int channelCount() {
        return 4;
    }

    /**
     * sRGB ARGB pixel -> C,M,Y,K ink (0..255) via the GCR transform
     * {@code K = 1 - max(r,g,b)}, {@code C = (1-r-K)/(1-K)} (and similarly M, Y).
     */
    @Override
    public void fromSRGB(int argb, double[] channels) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double k = 1.0 - Math.max(r, Math.max(g, b));
        double c, m, y;
        if (k >= 1.0) {
            // pure black: no chromatic ink, all key.
            c = m = y = 0.0;
        } else {
            double w = 1.0 - k;
            c = (1.0 - r - k) / w;
            m = (1.0 - g - k) / w;
            y = (1.0 - b - k) / w;
        }
        channels[0] = c * 255.0;
        channels[1] = m * 255.0;
        channels[2] = y * 255.0;
        channels[3] = k * 255.0;
    }

    /**
     * C,M,Y,K ink (0..255) + 8-bit alpha -> sRGB ARGB, the exact inverse of
     * {@link #fromSRGB}: {@code r = (1-C)(1-K)} (and similarly g, b).
     */
    @Override
    public int toSRGB(double[] channels, int alpha) {
        double c = clamp01(channels[0] / 255.0);
        double m = clamp01(channels[1] / 255.0);
        double y = clamp01(channels[2] / 255.0);
        double k = clamp01(channels[3] / 255.0);
        int r = channel8((1.0 - c) * (1.0 - k));
        int g = channel8((1.0 - m) * (1.0 - k));
        int b = channel8((1.0 - y) * (1.0 - k));
        return ((alpha & 0xFF) << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * PDF 32000-1 &sect;11.3.5 separable blend in the subtractive domain: the
     * additive blend ({@link RgbBlendingSpace}) applied to the ink complement, then
     * complemented back.  Operands and result in {@code [0,255]} ink.
     */
    @Override
    public double separable(BlendComposite.BlendingMode mode, double cb, double cs) {
        double additive = RgbBlendingSpace.INSTANCE.separable(mode, 255.0 - cb, 255.0 - cs);
        return 255.0 - additive;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static int channel8(double unit) {
        int i = (int) Math.round(unit * 255.0);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}