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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * <p>The class <code>Function</code> is factory responsible for creating the correct
 * function type for the given "FunctionType" dicitonary entry.</p>
 * <p/>
 * <p>Functions in PDF represent static, self-contained numerical transfromations.
 * In general, a function can take any number (m) of input values and produce any
 * number (n) of output values:
 * <ul>
 * f(x<sub>0</sub>,..., x<sub>m-1</sub>) = y<sub>0</sub>, ... , y<sub>n-1</sub>
 * </ul>
 * <p>In PDF functions, all the input values and all the output values are numbers.
 * Each function definition includes a <code>domain</code>, the set of legal
 * values for the input.  Some functions also define a <code>range</code>, the
 * set of legal values for the output. Input and output values are clipped to
 * the respective <code>domain</code> and <code>range</code>.
 * </p>
 * <p/>
 * <p>This function factory currently support the following function types:</p>
 * <ul>
 * <li><b>type 0</b> (supported) - sampled function, uses a table of sample values to define the function.
 * various techniques are used to interpolate values between the sampled values.
 * </li>
 * <li><b>type 2</b> (supported) - exponential interpolation, defines a set of
 * coeffiecients for an exponential function.
 * </li>
 * <li><b>type 3</b> (not supported) - stitching function, a combination of
 * other functions, partitioned across a domain.
 * </li>
 * <li><b>type 4</b> (not supported) - calculator function, uses operators from
 * the PostScript language do describe an arithmetic expression.
 * </li>
 * </u>
 *
 * @since 1.0
 */
public abstract class Function {

    private static final Logger logger =
            Logger.getLogger(Function.class.toString());

    /**
     * An array of 2 x m numbers, where m is the number of input values.  Input
     * values outside the declared domain are clipped to the nearest boundary value.
     */
    protected float[] domain;

    /**
     * An array of 2 x n numbers, where n is the number of output values.  Output
     * values outside the declared range are clipped to the nearest boundary value.
     * If this entry is absent, no clipping is done.
     */
    protected float[] range;

    /**
     * Function type associated with this function.
     */
    protected int functionType;

    /**
     * <p>Creates a new instance of a Function object.  Possible function types
     * are:</p>
     * <ul>
     * <li>0 - sampled funciton.</li>
     * <li>2 - exponential interpolation funciton.</li>
     * </ul>
     *
     * @param l document library.
     * @param o dictionary or Hashtable containing Function type entries.
     * @return Function object for the specified function type, null if the
     *         function type is not available or not defined.
     */
    public static Function getFunction(Library l, Object o) {
        Dictionary d = null;

        if (o instanceof Reference) {
            o = l.getObject((Reference) o);
        }

        // create a dictionary out of the object if possible
        if (o instanceof Dictionary) {
            d = (Dictionary) o;
        } else if (o instanceof Hashtable) {
            d = new Dictionary(l, (Hashtable) o);
        }

        if (d != null) {
            // find out what time of function type and create the appropriate
            // function object.
            int fType = d.getInt("FunctionType");
            switch (fType) {
                // sampled function
                case 0:
                    return new Function_0(d);
                // exponential interpolation
                case 2:
                    return new Function_2(d);
                // stitching function
                case 3:
                    return new Function_3(d);
                // PostScript calculator
                case 4:
                    logger.finer("Function type 4 (PostScript calculator) is not supported");
                    break;
            }
        }
        return null;
    }

    /**
     * Creates a new instance of <code>Function</code> object.
     *
     * @param d dictionary containing a vaild function dictionary.
     */
    protected Function(Dictionary d) {
        Vector dom = (Vector) d.getObject("Domain");
        domain = new float[dom.size()];
        for (int i = 0; i < dom.size(); i++) {
            domain[i] = ((Number) dom.elementAt(i)).floatValue();
        }
        Vector r = (Vector) d.getObject("Range");
        if (r != null) {
            range = new float[r.size()];
            for (int i = 0; i < r.size(); i++) {
                range[i] = ((Number) r.elementAt(i)).floatValue();
            }
        }
    }

    /**
     * <p>Gets the function type number.
     * <ul>
     * <li><b>type 0</b> - sampled function, uses a table of sample values to define the function.
     * various techniques are used to interpolate values between the sampled values.
     * </li>
     * <li><b>type 2</b>  - exponential interpolation, defines a set of
     * coeffiecients for an exponential function.
     * </li>
     * <li><b>type 3</b>  - stitching function, a combination of
     * other functions, partitioned across a domain.
     * </li>
     * <li><b>type 4</b> - calculator function, uses operators from
     * the PostScript language do describe an arithmetic expression.
     * </li>
     * </u>
     */
    public int getFunctionType() {
        return functionType;
    }

    /**
     * <p>Interpolation function.  For the given value of x, the interpolate
     * calculates the y value on the line defined by the two points
     * (x<sub>min</sub>, y<sub>min</sub>) and (x<sub>max</sub>, y<sub>max</sub>).
     *
     * @param x    value we want to find a y value for.
     * @param xmin point 1, x value.
     * @param xmax point 2, x value.
     * @param ymin point 1, y value.
     * @param ymax oint 2, y value.
     * @return y value for the given x value on the point define by
     *         (x<sub>min</sub>, y<sub>min</sub>) and (x<sub>max</sub>, y<sub>max</sub>).
     */
    protected float interpolate(float x, float xmin, float xmax, float ymin, float ymax) {
        return ((x - xmin) * (ymax - ymin) / (xmax - xmin)) + ymin;
    }

    /**
     * <p>Evaluates the input values specified by <code>m</code>. In general, a
     * function can take any number (m) of input values and produce any
     * number (n) of output values:
     * <ul>
     * f(x<sub>0</sub>,..., x<sub>m-1</sub>) = y<sub>0</sub>, ... , y<sub>n-1</sub>
     * </ul>
     *
     * @param m input values to put through function.
     * @return n output values.
     */
    public abstract float[] calculate(float[] m);

    public float[] getDomain() {
        return domain;
    }

    public float[] getRange() {
        return range;
    }
}
