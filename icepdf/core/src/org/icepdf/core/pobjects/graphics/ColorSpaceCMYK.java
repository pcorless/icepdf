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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import java.awt.color.ColorSpace;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class ColorSpaceCMYK extends ColorSpace {
    private static final String[] NAMES = new String[]{"Cyan", "Magenta", "Yellow", "Black"};
    private static final ColorSpace COLOR_SPACE_sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);

    public ColorSpaceCMYK() {
        super(TYPE_CMYK, 4);
    }

    public int getNumComponents() {
        return 4;
    }

    public String getName(int index) {
        return NAMES[index];
    }

    public int getType() {
        return TYPE_CMYK;
    }

    public boolean isCS_sRGB() {
        return false;
    }

    public float[] fromRGB(float[] rgbValues) {
        float c = 1.0f - rgbValues[0];
        float m = 1.0f - rgbValues[1];
        float y = 1.0f - rgbValues[2];
        float k = Math.min(c, Math.min(m, y));
        float km = Math.max(c, Math.max(m, y));
        if (km > k)
            k = k * k * k / (km * km);

        c -= k;
        m -= k;
        y -= k;

        float[] cmykValues = new float[4];
        cmykValues[0] = c;
        cmykValues[1] = m;
        cmykValues[2] = y;
        cmykValues[3] = k;
        return cmykValues;
    }

    public float[] toRGB(float[] cmykValues) {
//System.out.println("CMYK: " + cmykValues[0] + "  " + cmykValues[1] + "  " + cmykValues[2] + "  " + cmykValues[3]);
        /*
        float c = 1.0f - cmykValues[0];
        float m = 1.0f - cmykValues[1];
        float y = 1.0f - cmykValues[2];
        float k = cmykValues[3];

        c -= k;
        m -= k;
        y -= k;

        if( c < 0.0f )
            c = 0.0f;
        if( m < 0.0f )
            m = 0.0f;
        if( y < 0.0f )
            y = 0.0f;
        */

        float c = cmykValues[0];
        float m = cmykValues[1];
        float y = cmykValues[2];
        float k = cmykValues[3];

        c += k;
        m += k;
        y += k;

        if (c < 0.0f)
            c = 0.0f;
        else if (c > 1.0f)
            c = 1.0f;
        if (m < 0.0f)
            m = 0.0f;
        else if (m > 1.0f)
            m = 1.0f;
        if (y < 0.0f)
            y = 0.0f;
        else if (y > 1.0f)
            y = 1.0f;

        c = 1.0f - c;
        m = 1.0f - m;
        y = 1.0f - y;

        float[] rgbValues = new float[4];
        rgbValues[0] = c;
        rgbValues[1] = m;
        rgbValues[2] = y;
        return cmykValues;
    }

    private float[] _rgbValues = new float[4];

    public float[] fromCIEXYZ(float[] colorvalue) {
        return fromRGB(COLOR_SPACE_sRGB.fromCIEXYZ(colorvalue));
    }

    public float[] toCIEXYZ(float[] colorvalue) {
        return COLOR_SPACE_sRGB.toCIEXYZ(toRGB(colorvalue));
    }
}
