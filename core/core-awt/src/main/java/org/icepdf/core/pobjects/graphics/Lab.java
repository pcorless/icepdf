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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.List;

/**
 * A CIE L*a*b* colour space (8.6.5.4): L* 0..100 and a*, b* across the dictionary's Range, relative to its
 * WhitePoint, converted to sRGB.
 */
public class Lab extends PColorSpace {

    public static final Name LAB_KEY = new Name("Lab");
    public static final Name WHITE_POINT_KEY = new Name("WhitePoint");
    public static final Name RANGE_KEY = new Name("Range");

    private final float lBase;
    private final float lSpread;
    private final float aBase;
    private final float aSpread;
    private final float bBase;
    private final float bSpread;

    private final float xBase;
    private final float xSpread;
    private final float yBase;
    private final float ySpread;
    private final float zBase;
    private final float zSpread;

    // Bradford adaptation (white point to D65) followed by XYZ to linear sRGB, row major
    private final double[] xyzToRgb;

    // linear sRGB from D65 XYZ (IEC 61966-2-1)
    private static final double[] SRGB = {
            3.2406, -1.5372, -0.4986,
            -0.9689, 1.8758, 0.0415,
            0.0557, -0.2040, 1.0570};
    private static final double[] BRADFORD = {
            0.8951, 0.2664, -0.1614,
            -0.7502, 1.7135, 0.0367,
            0.0389, -0.0685, 1.0296};
    private static final double[] BRADFORD_INVERSE = {
            0.9869929, -0.1470543, 0.1599627,
            0.4323053, 0.5183603, 0.0492912,
            -0.0085287, 0.0400428, 0.9684867};
    private static final double[] D65 = {0.95047, 1.0, 1.08883};

    Lab(Library l, DictionaryEntries h) {
        super(l, h);
        List v = (java.util.List) l.getObject(h, WHITE_POINT_KEY);
        float[] whitePoint = {
                0.95047f, 1.0f, 1.08883f
        };
        if (v != null) {
            whitePoint[0] = ((Number) v.get(0)).floatValue();
            whitePoint[1] = ((Number) v.get(1)).floatValue();
            whitePoint[2] = ((Number) v.get(2)).floatValue();
        }
        v = (List) l.getObject(h, RANGE_KEY);
        float[] range = {
                -100, 100, -100, 100
        };
        if (v != null) {
            range[0] = ((Number) v.get(0)).floatValue();
            range[1] = ((Number) v.get(1)).floatValue();
            range[2] = ((Number) v.get(2)).floatValue();
            range[3] = ((Number) v.get(3)).floatValue();
        }

        lBase = 0.0f;
        lSpread = 100.0f;
        aBase = range[0];
        aSpread = range[1] - aBase;
        bBase = range[2];
        bSpread = range[3] - bBase;

        float[] blackPoint = {
                0f, 0f, 0f
        };
        xBase = blackPoint[0];
        xSpread = whitePoint[0] - xBase;
        yBase = blackPoint[1];
        ySpread = whitePoint[1] - yBase;
        zBase = blackPoint[2];
        zSpread = whitePoint[2] - zBase;

        xyzToRgb = multiply(SRGB, adaptation(whitePoint));
    }

    /**
     * The Bradford transform taking XYZ relative to this white point to XYZ relative to D65, the white sRGB
     * expects.  Most Lab spaces declare D50; without it their white comes out yellow.
     */
    private static double[] adaptation(float[] whitePoint) {
        double[] source = apply(BRADFORD, whitePoint[0], whitePoint[1], whitePoint[2]);
        double[] destination = apply(BRADFORD, D65[0], D65[1], D65[2]);
        double[] scale = new double[9];
        for (int i = 0; i < 3; i++) {
            scale[i * 4] = destination[i] / source[i];
        }
        return multiply(BRADFORD_INVERSE, multiply(scale, BRADFORD));
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] product = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                for (int k = 0; k < 3; k++) {
                    product[row * 3 + column] += a[row * 3 + k] * b[k * 3 + column];
                }
            }
        }
        return product;
    }

    private static double[] apply(double[] m, double x, double y, double z) {
        return new double[]{
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z};
    }

    public int getNumComponents() {
        return 3;
    }

    private double g(double x) {
        if (x < 0.2069F)
            x = 0.12842 * (x - 0.13793);
        else
            x = x * x * x;
        return x;
    }

    private double gg(double r) {
        if (r > 0.0031308)
            r = 1.055 * Math.pow(r, (1.0 / 2.4)) - 0.055;
        else
            r *= 12.92;
        return r;
    }

    /**
     * {@code [0 100 amin amax bmin bmax]}: L*, a* and b* in their own units (Table 90).
     */
    @Override
    public float[] getDefaultDecode(int bitsPerComponent) {
        return new float[]{lBase, lBase + lSpread, aBase, aBase + aSpread, bBase, bBase + bSpread};
    }

    public void normaliseComponentsToFloats(int[] in, float[] out, float maxval) {
        super.normaliseComponentsToFloats(in, out, maxval);
        out[0] = lBase + (lSpread * out[0]); // L
        out[1] = aBase + (aSpread * out[1]); // a
        out[2] = bBase + (bSpread * out[2]); // b
    }

    public Color getColor(float[] f, boolean fillAndStroke) {
        double cie_b = f[2];
        double cie_a = f[1];
        double cie_L = f[0];

        double var_Y = (cie_L + 16.0) / (116.0);
        double var_X = var_Y + (cie_a * 0.002);
        double var_Z = var_Y - (cie_b * 0.005);
        double X = g(var_X);
        double Y = g(var_Y);
        double Z = g(var_Z);
        X = xBase + X * xSpread;
        Y = yBase + Y * ySpread;
        Z = zBase + Z * zSpread;

        // XYZ is not clamped to 1: the white point's own Z is 1.089 for D65, and clamping it tinted white
        double[] m = xyzToRgb;
        double r = gg(m[0] * X + m[1] * Y + m[2] * Z);
        double g = gg(m[3] * X + m[4] * Y + m[5] * Z);
        double b = gg(m[6] * X + m[7] * Y + m[8] * Z);
        int ir = (int) Math.round(r * 255.0);
        int ig = (int) Math.round(g * 255.0);
        int ib = (int) Math.round(b * 255.0);
        ir = Math.max(0, Math.min(255, ir));
        ig = Math.max(0, Math.min(255, ig));
        ib = Math.max(0, Math.min(255, ib));
        return new Color(ir, ig, ib);
    }
}
