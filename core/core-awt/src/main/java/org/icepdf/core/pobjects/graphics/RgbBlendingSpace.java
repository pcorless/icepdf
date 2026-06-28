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
 * The additive (sRGB) {@link BlendingSpace}: three channels, identity device
 * conversion, and the PDF 32000-1 &sect;11.3.5 separable blend functions
 * evaluated directly on the RGB channels.  This is the behaviour the transparency
 * compositor has always used; it is factored out here unchanged so a DeviceCMYK
 * (subtractive) space can be added alongside it without forking the shared flow.
 */
public final class RgbBlendingSpace implements BlendingSpace {

    public static final RgbBlendingSpace INSTANCE = new RgbBlendingSpace();

    private RgbBlendingSpace() {
    }

    @Override
    public int channelCount() {
        return 3;
    }

    @Override
    public void fromSRGB(int argb, double[] channels) {
        channels[0] = (argb >> 16) & 0xFF;
        channels[1] = (argb >> 8) & 0xFF;
        channels[2] = argb & 0xFF;
    }

    @Override
    public int toSRGB(double[] channels, int alpha) {
        int r = channel8(channels[0]);
        int g = channel8(channels[1]);
        int b = channel8(channels[2]);
        return ((alpha & 0xFF) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel8(double v) {
        int i = (int) Math.round(v);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /**
     * PDF 32000-1 &sect;11.3.5 separable blend functions {@code B(cb, cs)},
     * operands and result in {@code [0,255]}.  The 0..255 domain (rather than the
     * normalised [0,1] the interface notionally prefers) is deliberate: it copies
     * the prior {@code FormDrawCmd.separableBlend} arithmetic verbatim so the
     * factored-out path is provably bit-for-bit identical (normalising introduced
     * a handful of &plusmn;1 ulp rounding differences). A subtractive CMYK space
     * uses the same 0..255 ink convention.
     */
    @Override
    public double separable(BlendComposite.BlendingMode mode, double cb, double cs) {
        switch (mode) {
            case MULTIPLY:
                return cb * cs / 255.0;
            case SCREEN:
                return cb + cs - cb * cs / 255.0;
            case OVERLAY:
                return hardLight(cs, cb);          // Overlay(b,s) = HardLight(s,b)
            case DARKEN:
                return Math.min(cb, cs);
            case LIGHTEN:
                return Math.max(cb, cs);
            case COLOR_DODGE:
                if (cb == 0) return 0;
                if (cs >= 255) return 255;
                return Math.min(255.0, cb * 255.0 / (255.0 - cs));
            case COLOR_BURN:
                if (cb >= 255) return 255;
                if (cs == 0) return 0;
                return 255.0 - Math.min(255.0, (255.0 - cb) * 255.0 / cs);
            case HARD_LIGHT:
                return hardLight(cb, cs);
            case SOFT_LIGHT: {
                double b = cb / 255.0, s = cs / 255.0;
                double d = b <= 0.25 ? ((16 * b - 12) * b + 4) * b : Math.sqrt(b);
                double r = s <= 0.5 ? b - (1 - 2 * s) * b * (1 - b) : b + (2 * s - 1) * (d - b);
                return r * 255.0;
            }
            case DIFFERENCE:
                return Math.abs(cb - cs);
            case EXCLUSION:
                return cb + cs - 2 * cb * cs / 255.0;
            default:
                return cs;                          // Normal / Compatible
        }
    }

    private static double hardLight(double cb, double cs) {
        // HardLight(Cb,Cs) = Multiply(Cb,2Cs) if Cs<=.5 else Screen(Cb,2Cs-1)
        if (cs <= 127.5) {
            return cb * (2 * cs) / 255.0;
        }
        double s2 = 2 * cs - 255.0;
        return cb + s2 - cb * s2 / 255.0;
    }
}
