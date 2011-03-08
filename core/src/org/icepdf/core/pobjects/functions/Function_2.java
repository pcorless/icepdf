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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.pobjects.Dictionary;

import java.util.Arrays;
import java.util.Vector;

/**
 * <p>This class <code>Function_2</code> represents a generic Type 2, exponentail
 * interpolation function type.  Type 2 functions include a set of parameters that
 * define an exponential interpolation of one input value and n output values:<p>
 * <p/>
 * <ul>
 * f(x) = y<sub>0</sub>, ..., y<sub>n-1</sub>
 * </ul>
 * <p/>
 * <p>Values of <code>Domain</code> must constrain x in such a way that if
 * <code>N</code> is not an integer, all values of x must be non-negative, and if
 * <code>N</code> is negative, no value of x may be zero.  Typically,
 * <code>Domain</code> is declared as [0.0 1.0], and <code>N</code> is a postive
 * number.  The <code>Range</code> attribute is optional and can be used to clip
 * the output to a specified range.  Note that when <code>N</code> is 1, the
 * function performs a linear interpolation between <code>C0</code> and
 * <code>C1</code>; therefore, the function cna also be expressed as a sampled
 * function (type 0). </p>
 *
 * @see Function
 * @since 1.0
 */
public class Function_2 extends Function {

    // The interpolation exponent. Each input value x will return n values,
    // given by:
    // y<sub>j</sub> = CO<sub>j</sub> + x<sup>N</sup> x (C1<sub>j</sub> - C0<sub>j</sub>)
    // for 0 <= j < n
    private float N;

    // An array of n numbers defining the function result when x = 0.0.  Default
    // value is [0.0]
    private float C0[] = {0.0f};

    // An array of n number defining the function result when x = 1.0. Default
    // value is [1.0]
    private float C1[] = {1.0f};

    /**
     * Creates a new instance of a type 2 function.
     *
     * @param d function's dictionary.
     */
    Function_2(Dictionary d) {
        super(d);
        // Setup and assign N, interpolation exponent
        N = d.getFloat("N");

        // Convert C0 dictionary values.
        Vector c0 = (Vector) d.getObject("C0");
        if (c0 != null) {
            C0 = new float[c0.size()];
            for (int i = 0; i < c0.size(); i++) {
                C0[i] = ((Number) c0.elementAt(i)).floatValue();
            }
        }
        // legacy PDFGo code, guessing that setting default value should just
        // be [0.0] and not assigned for each possible entry.
        /*else {
         for (int i = 0; i < range.length/2; i++) {
         C0[i] = 0f;
         }
         }*/

        // Convert C1 dictionary values
        Vector c1 = (Vector) d.getObject("C1");
        if (c1 != null) {
            C1 = new float[c1.size()];
            for (int i = 0; i < c1.size(); i++) {
                C1[i] = ((Number) c1.elementAt(i)).floatValue();
            }
        }
        // legacy PDFGo code, guessing that setting default value should just
        // be [1.0] and not assigned for each possible entry.
        /*else {
         for (int i = 0; i < range.length/2; i++) {
         C1[i] = 1f;
         }
         }*/

    }

    /**
     * <p>Exponential Interpolation calculation.  Each input value x will return
     * n values, given by:</p>
     * <ul>
     * y<sub>j</sub> =
     * CO<sub>j</sub> + x<sup>N</sup> x (C1<sub>j</sub> - C0<sub>j</sub>), for 0 <= j < n
     * </ul>
     *
     * @param x input values m
     * @return output values n
     */
    public float[] calculate(float[] x) {
        // create output array
        float y[] = new float[x.length * C0.length];
        float yValue;
        // for each y value, apply exponential interpolation function
        for (int i = 0; i < x.length; i++) {
            // C0 and C1 should have the same length work through C0 length
            for (int j = 0; j < C0.length; j++) {
                // apply the function as defined above.
                yValue = (float) (C0[j] + Math.pow(x[i], N) * (C1[j] - C0[j]));

                // Range is optional but if present should be used to clip the output
                if (range != null)
                    yValue = Math.min(Math.max(yValue, range[2 * j]), range[2 * j + 1]);

                // finally assign the interpolation value.
                y[i * C0.length + j] = yValue;
            }
        }
        return y;
    }

    public String toString() {
        return "FunctionType: " + functionType +
                "\n    domain: " + Arrays.toString(domain) +
                "\n     range: " + Arrays.toString(range) +
                "\n         N: " + N +
                "\n        C0: " + Arrays.toString(C0) +
                "\n        C1: " + Arrays.toString(C1);
    }
}
