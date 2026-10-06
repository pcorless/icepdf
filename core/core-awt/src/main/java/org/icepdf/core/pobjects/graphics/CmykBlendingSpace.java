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
 * <b>This space must be fed TRUE CMYK ink</b> (the preserved samples from
 * {@code ImageUtility.getCmykSamples}), not CMYK recovered from sRGB.  The recovery
 * convenience {@link #fromSRGB} uses a no-black (pure C,M,Y, K=0) transform that
 * round-trips losslessly, but a verified finding makes clear it is only a stand-in:
 * subtractive blending of K=0 ink reduces <i>exactly</i> to the additive RGB blend
 * for every white-preserving mode (Multiply/Screen/Overlay/...), because the
 * fromSRGB / separable / toSRGB complements cancel.  The black channel is the sole
 * carrier of genuinely subtractive behaviour, and it cannot be recovered from sRGB
 * once an image has decoded -- attempting to fabricate it (an earlier GCR
 * {@code K=1-max(rgb)} variant) shifted {@code pattern_and_CYMK_jpeg.pdf} from its
 * reference-correct orange to green (Acrobat, Chrome and Firefox all show orange).
 * <p>
 * Consequently this strategy is <b>staged, not yet wired</b> into the sRGB-based
 * {@code FormDrawCmd.composeContribution} path (which stays additive); it becomes
 * active when the group is composited at the raster level from its preserved CMYK
 * samples -- the GH-501 Phase 2 raster-renderer step (see the 3a/n finding).  The
 * {@link #separable} math here is the piece that path consumes.
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
     * sRGB ARGB pixel -> C,M,Y ink (0..255) with no black generation (K=0):
     * {@code C = 255 - r} (and similarly M, Y).  Lossless and exactly inverted by
     * {@link #toSRGB}.  This is a recovery stand-in only -- it carries no real black
     * channel (see the class note), so blending the result reduces to an additive
     * RGB blend.  The raster-renderer path supplies true K via the preserved CMYK
     * samples instead of calling this.
     */
    @Override
    public void fromSRGB(int argb, double[] channels) {
        channels[0] = 255.0 - ((argb >> 16) & 0xFF);
        channels[1] = 255.0 - ((argb >> 8) & 0xFF);
        channels[2] = 255.0 - (argb & 0xFF);
        channels[3] = 0.0;
    }

    /**
     * C,M,Y,K ink (0..255) + 8-bit alpha -> sRGB ARGB: {@code r = (1-C)(1-K)} (and
     * similarly g, b).  Inverts {@link #fromSRGB} exactly, and is the real sink for
     * true-sample CMYK (where K is non-zero) at the final convert-to-device step.
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