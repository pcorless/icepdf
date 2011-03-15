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
package org.icepdf.core.pobjects.functions;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Stream;

import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>This class <code>Function_0</code> represents a generic Type 0, sampled function
 * type.  Type 0 functions use a sequence of sampled values (contained in a stream)
 * to produce an approximation for function shose domains and ranges are bounded.
 * The samples are organized as an m-dimensional table in which each entry has n
 * components. </p>
 * <p/>
 * <p>Sampled functiosn are highly general and offer reasonablly accurate
 * representations of arbitrary analytic functions at low expense.  The
 * dimensionality of a sampled function is restricted only by the implementation
 * limits.</p>
 *
 * @see Function
 * @since 1.0
 */
public class Function_0 extends Function {

    private static final Logger logger =
            Logger.getLogger(Function_0.class.toString());

    // An array of m positive integers specifying the number of samples in each
    // input dimension of the sample table.
    private int size[];

    // The number of bits used to represent each sample. If the function has
    // multiple output values, each one occupies BitsPerSample bits.  Valid
    // values are 1,2,4,8,12,16,24, and 32.
    private int bitspersample;

    // The order of interpolation between samples.  Valid values are 1 and 3,
    // specifying linear and cubic spline interpolation, respectively.  Default 1
    private int order;

    // An array of 2 x m numbers specifying the linear mapping of input values
    // into the domain of the function's sample table.  Default value:
    // [0 (size<sub>0</sub>-1) 0 size<sub>1</sub> ...].
    private float encode[];

    // An array of 2 x n numbers specifying the linear mapping of sample values
    // into the range the range appropriate for the function's output values.
    // Default same as Range.
    private float decode[];

    // associated stream bytes, comes from dictionary
    private byte bytes[];

    /**
     * Creates a new instance of a type 0 function.
     *
     * @param d function's dictionary.
     */
    Function_0(Dictionary d) {
        // initiate, domain and range
        super(d);

        Vector s = (Vector) d.getObject("Size");
        // setup size array, each entry represents the number of samples for
        // each input dimension.
        size = new int[s.size()];
        for (int i = 0; i < s.size(); i++) {
            size[i] = (int) (((Number) s.elementAt(i)).floatValue());
        }
        // setup bitspersample array, each entry represents the number of bits used
        // for each sample
        bitspersample = d.getInt("BitsPerSample");

        // setup of encode table, specifies the linear mapping of input values
        // into the domain of the function's sample table.
        Vector enc = (Vector) d.getObject("Encode");
        encode = new float[size.length * 2];
        if (enc != null) {
            for (int i = 0; i < size.length * 2; i++) {
                encode[i] = ((Number) enc.elementAt(i)).floatValue();
            }
        } else {
            // encoding is optional, so fill up encode area with uniform
            // mapping of 0,size[0]-1, 0,size[1]-1, 0,size[2]-1 which is
            // the default value which is defined in the spec.
            for (int i = 0; i < size.length; i++) {
                encode[2 * i] = 0;
                encode[2 * i + 1] = size[i] - 1;
            }
        }

        // setup decode, an array of  2 x n numbers specifying the linear mapping
        // of sample values into the range appropriate for the function's output values.
        Vector dec = (Vector) d.getObject("Decode");
        decode = new float[range.length];
        if (dec != null) {
            for (int i = 0; i < range.length; i++) {
                decode[i] = ((Number) dec.elementAt(i)).floatValue();
            }
        } else {
            // deocode is optional, so we should copy range as a default values
            System.arraycopy(range, 0, decode, 0, range.length);
//            for (int i = 0; i < range.length; i++) {
//                decode[i] = range[i];
//            }
        }

        // lastly get the stream byte data if any.
        Stream stream = (Stream) d;
        bytes = stream.getBytes();
    }


    /**
     * Calculates the y values for the given x values using a sampled function.
     *
     * @param x array of input values m.
     * @return array of ouput value n.
     */
    public float[] calculate(float[] x) {
        // length of output array
        int n = range.length / 2;
        // ready output array
        float y[] = new float[n];
        // work throw all input data and store in y[]
        try {
            for (int i = 0; i < x.length; i++) {
                // clip input value appropriately for the given domain
                // xi' = min (max(xi, Domain2i), Domain2i+1)
                x[i] = Math.min(Math.max(x[i], domain[2 * i]), domain[2 * i + 1]);
                // find the encoded value
                // ei = intermolate (xi', Domain2i, Domain2i+1, Encode2i, Encode2i+1)
                float e = interpolate(x[i], domain[2 * i], domain[2 * i + 1],
                        encode[2 * i], encode[2 * i + 1]);
                // clip to the size of the sampled table in that dimension:
                // ei' = min (max(ei, 0), Sizei-1)
                e = Math.min(Math.max(e, 0), size[i] - 1);
                // pretty sure that e1 and e2 are used to for a bilinear interpolation?
                // Output values are are caculated from the nearest surrounding values
                // in the sample table in the sample table.
                int e1 = (int) Math.floor(e);
                int e2 = (int) Math.ceil(e);
                int index;
                // Calculate the final output values
                for (int j = 0; j < n; j++) {
                    //  find nearest surrounding values in the sample table
                    int b1 = ((int) bytes[(int) (e1 * n + j)]) & 255;
                    int b2 = ((int) bytes[(int) (e2 * n + j)]) & 255;
                    // get the average
                    float r = ((float) b1 + (float) b2) / 2;
                    // interplate to get output values
                    r = interpolate(r, 0f, (float) Math.pow(2, bitspersample) -
                            1, decode[2 * j], decode[2 * j + 1]);
                    // finally, decoded values are clipped ot the range
                    // yj = min(max(rj', Range2j), Range2j+1)
                    r = Math.min(Math.max(r, range[2 * j]), range[2 * j + 1]);
                    index = i * n + j;
                    // make sure we y can contain the calcualated r value
                    if (index < y.length) {
                        y[index] = r;
                    }

                }
            }
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating function 0 values", e);
        }
        return y;
    }
}
