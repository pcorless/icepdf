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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.pobjects.Dictionary;

import java.util.Vector;

/**
 * <p>Type 3 Function (PDF 1.3) defines a stitching of the subdomains of
 * serveral 1-input functionsto produce a single new 1-input function.</p>
 *
 * @author ICEsoft Technologies Inc.
 * @since 3.0
 */
public class Function_3 extends Function {

    // An array of k-1 numbers that, in combination with Domain, define the
    // intervals to which each function from the Functions array applies. Bounds
    // must be in order of increasing value and each value must be with in the
    // domain defined by Domain
    private float bounds[];

    // An array of 2xk numbers that, taken in pairs, cMap each subset of the
    // domain defined by Domain and the bounds array to the domain of the
    // corresponding function.
    private float encode[];

    // An array of k 1--input functions making up the stitching function. The
    // output dimensionality of all functions must be the same, and compatible
    // with the values of the Range if Range is represent.
    private Function functions[];

    /**
     * Creates a new instance of a type 2 function.
     *
     * @param d function's dictionary.
     */
    Function_3(Dictionary d) {
        super(d);

        // Convert bounds dictionary values.
        Vector boundTemp = (Vector) d.getObject("Bounds");
        if (boundTemp != null) {
            bounds = new float[boundTemp.size()];
            for (int i = 0; i < boundTemp.size(); i++) {
                bounds[i] = ((Number) boundTemp.elementAt(i)).floatValue();
            }
        }

        // convert encode dictionary.
        Vector encodeTemp = (Vector) d.getObject("Encode");
        if (encodeTemp != null) {
            encode = new float[encodeTemp.size()];
            for (int i = 0; i < encodeTemp.size(); i++) {
                encode[i] = ((Number) encodeTemp.elementAt(i)).floatValue();
            }
        }

        Vector functionTemp = (Vector) d.getObject("Functions");
        if (encodeTemp != null) {
            functions = new Function[functionTemp.size()];
            for (int i = 0; i < functionTemp.size(); i++) {
                functions[i] = Function.getFunction(d.getLibrary(), functionTemp.get(i));
//                System.out.println("Function " + functions[i].toString());
            }
        }

    }

    /**
     * <p>Puts the value x thought the function type 3 algorithm.
     *
     * @param x input values m
     * @return output values n
     */
    public float[] calculate(float[] x) {

        int k = functions.length;

        if (k == 1 && bounds.length == 0) {
            if (domain[0] <= x[0] && x[0] <= domain[1]) {
                return encode(x, functions[0], 0);
            }
        }

        // Find where x finds into the following range:
        // Domain0 < Bounds0 < Bounds1 < ... < Boundsk-2 < Domain1
        // where k = functions length.  The found bound is the equivalent function
        // to use to encode the x value.
        for (int b = 0; b < bounds.length; b++) {
            // first sub domain
            if (b == 0) {
                // check if domain0 <= x < bounds0, return function if true
                if (domain[0] <= x[0] && x[0] < bounds[b]) {
                    return encode(x, functions[b], b);
                }
            }
            // last sub domain
            if (b == k - 2) {
                // check if bounds k-2 <= x <= domain 0, return function if true
                if (bounds[b] <= x[0] && x[0] <= domain[1]) {
                    return encode(x, functions[k - 1], k - 1);
                }
            }
            // bounds <= x < bounds b + 1, return function if true
            if (bounds[b] <= x[0] && x[0] < bounds[b + 1]) {
                return encode(x, functions[b], b);
            }
        }

        return null;
    }

    /**
     * Utility method to apply the interpolation rules and finally calculate
     * the return value using the selected function.  The method also checks
     * to see if the values fall in the specified range and makes the
     * appropriate adjustments, if range is present.
     *
     * @param x        one element array,
     * @param function function to be applied can be of any type.
     * @param i        i th subdomain, selected subdomain.
     * @return n length array of calculated values.  n length is defined by the
     *         colour space component count.
     */
    private float[] encode(float[] x, Function function, int i) {
        int k = functions.length;

        if (i <= 0 && i < k && bounds.length > 0) {

            float b1;
            float b2;
            if (i - 1 == -1) {
                // domain 0
                b1 = domain[0];
            } else {
                b1 = bounds[i - 1];
            }
            if (i == k - 1) {
                // domain 1
                b2 = domain[1];
            } else {
                b2 = bounds[i];
            }

            if (k - 2 < bounds.length && bounds[k - 2] == domain[1]) {
                x[0] = encode[2 * i];
            }

            x[0] = interpolate(x[0], b1, b2, encode[2 * i], encode[2 * i + 1]);

            x = function.calculate(x);
        } else {
            x[0] = interpolate(x[0], domain[0], domain[1], encode[2 * i], encode[2 * i + 1]);
            x = function.calculate(x);
        }


        return validateAgainstRange(x);

    }

    /**
     * Utility method to check if the values fall within the functions range.
     *
     * @param values values to test against range.
     * @return correct values that fall within the functions range.
     */
    private float[] validateAgainstRange(float[] values) {

        // Range is an array of 2xn numbers, where n is the number of output
        // values.

        for (int j = 0, max = values.length; j < max; j++) {
            if (range != null && values[j] < range[2 * j]) {
                values[j] = range[2 * j];
            } else if (range != null && values[j] > range[(2 * j) + 1]) {
                values[j] = range[(2 * j) + 1];
            } else if (values[j] < 0) {
                values[j] = 0.0f;
            }
        }
        return values;
    }
}
