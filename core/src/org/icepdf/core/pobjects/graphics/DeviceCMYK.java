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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

/**
 * Device CMYK colour space definitions. The primary purpose of this colour
 * space is to convert cymk colours to rgb.  No ICC profile is used in this
 * process and the generated rgb colour is just and approximation.
 */
public class DeviceCMYK extends PColorSpace {

    DeviceCMYK(Library l, Hashtable h) {
        super(l, h);
    }


    public int getNumComponents() {
        return 4;
    }

    /**
     * Converts a 4 component cmyk colour to rgb.  With out a valid ICC colour
     * profile this is just an approximation.
     *
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    public Color getColor(float[] f) {
        return alternative2(f);
    }

    /**
     * Ah yes the many possible ways to go from cmyk to rgb.  Everybody has
     * an opinion but no one has the solution that is 100%
     */

    /**
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color alternative1(float[] f) {

        float c = f[3];
        float m = f[2];
        float y = f[1];
        float k = f[0];

        float r = 1.0f - Math.min(1.0f, c + k);
        float g = 1.0f - Math.min(1.0f, m + k);
        float b = 1.0f - Math.min(1.0f, y + k);

        return new Color(r, g, b);
    }

    /**
     * @param f 4 component values of the cmyk, assumes components between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color alternative3(float[] f) {

        float c = f[3];
        float m = f[2];
        float y = f[1];
        float k = f[0];

        float r = 1.0f - Math.min(1.0f, (c * (1 - k)) + k);
        float g = 1.0f - Math.min(1.0f, (m * (1 - k)) + k);
        float b = 1.0f - Math.min(1.0f, (y * (1 - k)) + k);

        return new Color(r, g, b);
    }

    /**
     * Auto cad color model
     * var R=Math.round((1-C)*(1-K)*255);
     * var B=Math.round((1-Y)*(1-K)*255);
     * var G=Math.round((1-M)*(1-K)*255);
     *
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color getAutoCadColor(float[] f) {

        float c = f[3];
        float m = f[2];
        float y = f[1];
        float k = f[0];

        int red = Math.round((1.0f - c) * (1.0f - k) * 255);
        int blue = Math.round((1.0f - y) * (1.0f - k) * 255);
        int green = Math.round((1.0f - m) * (1.0f - k) * 255);

        return new Color(red, green, blue);
    }

    /**
     * GNU Ghost Script algorithm or so they say.
     * <p/>
     * rgb[0] = colors * (255 - cyan)/255;
     * rgb[1] = colors * (255 - magenta)/255;
     * rgb[2] = colors * (255 - yellow)/255;
     *
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color getGhostColor(float[] f) {

        int cyan = (int) (f[3] * 255);
        int magenta = (int) (f[2] * 255);
        int yellow = (int) (f[1] * 255);
        int black = (int) (f[0] * 255);
        float colors = 255 - black;

        float[] rgb = new float[3];
        rgb[0] = colors * (255 - cyan) / 255;
        rgb[1] = colors * (255 - magenta) / 255;
        rgb[2] = colors * (255 - yellow) / 255;

        return new Color((int) rgb[0], (int) rgb[1], (int) rgb[2]);

    }

    /**
     * Adobe photo shop algorithm or so they say.
     * <p/>
     * cyan = Math.min(255, cyan + black); //black is from K
     * magenta = Math.min(255, magenta + black);
     * yellow = Math.min(255, yellow + black);
     * rgb[0] = 255 - cyan;
     * rgb[1] = 255 - magenta;
     * rgb[2] = 255 - yellow;
     *
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color getAdobeColor(float[] f) {

        int cyan = (int) (f[3] * 255);
        int magenta = (int) (f[2] * 255);
        int yellow = (int) (f[1] * 255);
        int black = (int) (f[0] * 255);

        cyan = Math.min(255, cyan + black); //black is from K
        magenta = Math.min(255, magenta + black);
        yellow = Math.min(255, yellow + black);

        int[] rgb = new int[3];
        rgb[0] = 255 - cyan;
        rgb[1] = 255 - magenta;
        rgb[2] = 255 - yellow;

        return new Color(rgb[0], rgb[1], rgb[2]);
    }


    /**
     * Current runner for conversion that looks closest to acrobat.
     * The algorithm is a little expensive but it does the best approximation.
     * the algorithm was taken from the Xpdf project (http://www.foolabs.com/xpdf/).
     * <p/>
     * <p/>
     * #if 0	// standard/simple algorithm
     * outRed = clip01( (1.0 - inCyan) * (1.0 - inBlack) );
     * outGreen = clip01( (1.0 - inMagenta) * (1.0 - inBlack) );
     * outBlue = clip01( (1.0 - inYellow) * (1.0 - inBlack) );
     * #else	// from Xpdf
     * double c, m, y, aw, ac, am, ay, ar, ag, ab;
     * <p/>
     * c = clip01( inCyan + inBlack );
     * m = clip01( inMagenta + inBlack );
     * y = clip01( inYellow + inBlack );
     * aw = (1-c) * (1-m) * (1-y);
     * ac = c * (1-m) * (1-y);
     * am = (1-c) * m * (1-y);
     * ay = (1-c) * (1-m) * y;
     * ar = (1-c) * m * y;
     * ag = c * (1-m) * y;
     * ab = c * m * (1-y);
     * outRed = clip01(aw + 0.9137*am + 0.9961*ay + 0.9882*ar);
     * outGreen = clip01(aw + 0.6196*ac + ay + 0.5176*ag);
     * outBlue = clip01(aw + 0.7804*ac + 0.5412*am + 0.0667*ar +
     * 0.2118*ag + 0.4863*ab);
     * #endif
     *
     * @param f 4 component values of the cmyk, assumes compoents between
     *          0.0 and 1.0
     * @return valid rgb colour object.
     */
    private static Color alternative2(float[] f) {
        float inCyan = f[3];
        float inMagenta = f[2];
        float inYellow = f[1];
        float inBlack = f[0];

        double c, m, y, aw, ac, am, ay, ar, ag, ab;
        c = Math.min(1.0, inCyan + inBlack);
        m = Math.min(1.0, inMagenta + inBlack);
        y = Math.min(1.0, inYellow + inBlack);
        aw = (1 - c) * (1 - m) * (1 - y);
        ac = c * (1 - m) * (1 - y);
        am = (1 - c) * m * (1 - y);
        ay = (1 - c) * (1 - m) * y;
        ar = (1 - c) * m * y;
        ag = c * (1 - m) * y;
        ab = c * m * (1 - y);

        float outRed = (float) (aw + 0.9137 * am + 0.9961 * ay + 0.9882 * ar);
        float outGreen = (float) (aw + 0.6196 * ac + ay + 0.5176 * ag);
        float outBlue = (float) (aw + 0.7804 * ac + 0.5412 * am + 0.0667 * ar + 0.2118 * ag + 0.4863 * ab);

        return new Color(outRed, outGreen, outBlue);
    }

}
