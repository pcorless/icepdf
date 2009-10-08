/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * put your documentation comment here
 */
public class Lab extends PColorSpace {
    private float[] whitePoint = {
            0.95047f, 1.0f, 1.08883f
    };
    private float[] blackPoint = {
            0f, 0f, 0f
    };
    private float[] range = {
            -100, 100, -100, 100
    };
    private float lBase;
    private float lSpread;
    private float aBase;
    private float aSpread;
    private float bBase;
    private float bSpread;

    private float xBase;
    private float xSpread;
    private float yBase;
    private float ySpread;
    private float zBase;
    private float zSpread;

    /**
     * @param l
     * @param h
     */
    Lab(Library l, Hashtable h) {
        super(l, h);
        Vector v = (Vector) l.getObject(h, "WhitePoint");
        if (v != null) {
            whitePoint[0] = ((Number) v.elementAt(0)).floatValue();
            whitePoint[1] = ((Number) v.elementAt(1)).floatValue();
            whitePoint[2] = ((Number) v.elementAt(2)).floatValue();
        }
        v = (Vector) l.getObject(h, "Range");
        if (v != null) {
            range[0] = ((Number) v.elementAt(0)).floatValue();
            range[1] = ((Number) v.elementAt(1)).floatValue();
            range[2] = ((Number) v.elementAt(2)).floatValue();
            range[3] = ((Number) v.elementAt(3)).floatValue();
        }

        lBase = 0.0f;
        lSpread = 100.0f;
        aBase = range[0];
        aSpread = range[1] - aBase;
        bBase = range[2];
        bSpread = range[3] - bBase;

        xBase = blackPoint[0];
        xSpread = whitePoint[0] - xBase;
        yBase = blackPoint[1];
        ySpread = whitePoint[1] - yBase;
        zBase = blackPoint[2];
        zSpread = whitePoint[2] - zBase;
    }

    /**
     * @return
     */
    public int getNumComponents() {
        return 3;
    }

    /**
     * @param x
     * @return
     */
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

    public void normaliseComponentsToFloats(int[] in, float[] out, float maxval) {
        super.normaliseComponentsToFloats(in, out, maxval);
        out[2] = lBase + (lSpread * out[2]); // L
        out[1] = aBase + (aSpread * out[1]); // a
        out[0] = bBase + (bSpread * out[0]); // b
    }

    /**
     * @param f
     * @return
     */
    public Color getColor(float[] f) {
        double cie_b = f[0];
        double cie_a = f[1];
        double cie_L = f[2];

        double var_Y = (cie_L + 16.0) / (116.0);
        double var_X = var_Y + (cie_a * 0.002);
        double var_Z = var_Y - (cie_b * 0.005);
        double X = g(var_X);
        double Y = g(var_Y);
        double Z = g(var_Z);
        X = xBase + X * xSpread;
        Y = yBase + Y * ySpread;
        Z = zBase + Z * zSpread;
        X = Math.max(0, Math.min(1, X));
        Y = Math.max(0, Math.min(1, Y));
        Z = Math.max(0, Math.min(1, Z));

        /*
         * Algorithm from online
        double r = X *  3.2406 + Y * -1.5372 + Z * -0.4986;
        double g = X * -0.9689 + Y *  1.8758 + Z *  0.0415;
        double b = X *  0.0557 + Y * -0.2040 + Z *  1.0570;
        */
        double r = X * 3.241 + Y * -1.5374 + Z * -0.4986;
        double g = X * -0.9692 + Y * 1.876 + Z * 0.0416;
        double b = X * 0.0556 + Y * -0.204 + Z * 1.057;
        r = gg(r);
        g = gg(g);
        b = gg(b);
        int ir = (int) (r * 255.0);
        int ig = (int) (g * 255.0);
        int ib = (int) (b * 255.0);
        ir = Math.max(0, Math.min(255, ir));
        ig = Math.max(0, Math.min(255, ig));
        ib = Math.max(0, Math.min(255, ib));
        return new Color(ir, ig, ib);
    }
}
