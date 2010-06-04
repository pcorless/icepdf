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
public class CalRGB extends PColorSpace {
    float[] whitepoint = {
            1, 1, 1
    };
    float[] gamma = {
            1, 1, 1
    };
    float[] matrix = {
            1, 0, 0, 0, 1, 0, 0, 0, 1
    };


    CalRGB(Library l, Hashtable h) {
        super(l, h);
        Vector m = (Vector) h.get("WhitePoint");
        if (m != null) {
            for (int i = 0; i < 3; i++) {
                whitepoint[i] = ((Number) m.elementAt(i)).floatValue();
            }
        }
        m = (Vector) h.get("Gamma");
        if (m != null) {
            for (int i = 0; i < 3; i++) {
                gamma[i] = ((Number) m.elementAt(i)).floatValue();
            }
        }
        m = (Vector) h.get("Matrix");
        if (m != null) {
            for (int i = 0; i < 9; i++) {
                matrix[i] = ((Number) m.elementAt(i)).floatValue();
            }
        }
    }


    public int getNumComponents() {
        return 3;
    }


    public Color getColor(float[] f) {
        if (true) {
            return new java.awt.Color(f[2], f[1], f[0]);
        }
        /*        float A = (float)Math.exp(gamma[0]*Math.log(f[2]));
         float B = (float)Math.exp(gamma[1]*Math.log(f[1]));
         float C = (float)Math.exp(gamma[2]*Math.log(f[0]));*/
        float A = (float) Math.pow(f[2], gamma[0]);
        float B = (float) Math.pow(f[1], gamma[1]);
        float C = (float) Math.pow(f[0], gamma[2]);
        float X = matrix[0] * A + matrix[3] * B + matrix[6] * C;
        float Y = matrix[1] * A + matrix[4] * B + matrix[7] * C;
        float Z = matrix[2] * A + matrix[5] * B + matrix[8] * C;
        if (X < 0) {
            X = 0;
        }
        if (Y < 0) {
            Y = 0;
        }
        if (Z < 0) {
            Z = 0;
        }
        if (X > 1) {
            X = 1;
        }
        if (Y > 1) {
            Y = 1;
        }
        if (Z > 1) {
            Z = 1;
        }
        return new Color(X, Y, Z);
        //        return  new java.awt.Color(f[2]*255/max_val, f[1]*255/max_val, f[0]*255/max_val);
    }
}



