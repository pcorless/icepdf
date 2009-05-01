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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.util.Library;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>This class represents an External Graphics State (ExtGState) object.  An
 * ExtGState is an extension of the individual operators which sets a graphics
 * state, q, Q, cm, w, J, j, M, d, ri, i and gs.  The gs operand points to a
 * named ExtGState resource which contains a dictionary whose contents specify
 * the values of one or more graphics state parameters.  All the entries in this
 * dictionary are summarized in the following table. An ExtGSate dictionary
 * does not need to specify all entries and the results of gs operations are
 * cumulative.</p>
 * <p/>
 * <table border="1" >
 * <tr>
 * <td><b> Key </b></td>
 * <td><b> Type</b></td>
 * <td><b> Desription</b></td>
 * </tr>
 * <tr>
 * <td valign="top" >Type</td>
 * <td valign="top" >name</td>
 * <td>(Optional) The type of PDF object that this dictionary describes;
 * must beExtGState for a graphics state parameter dictionary.</td>
 * </tr>
 * <tr>
 * <td valign="top" >LW</td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.3) The line width</td>
 * </tr>
 * <tr>
 * <td valign="top" ><LC/td>
 * <td valign="top" >integer</td>
 * <td>(Optional; PDF 1.3) The line cap style.</td>
 * </tr>
 * <tr>
 * <td valign="top" >LJ</td>
 * <td valign="top" >integer</td>
 * <td>(Optional; PDF 1.3) The line join styl.</td>
 * </tr>
 * <tr>
 * <td valign="top" >ML</td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.3) The miter limit.</td>
 * </tr>
 * <tr>
 * <td valign="top" >D</td>
 * <td valign="top" >array</td>
 * <td>(Optional; PDF 1.3) The line dash pattern, expressed as an array of
 * the form [dashArray dashPhase], where dashArray is itself an array
 * and dashPhase is an integer</td>
 * </tr>
 * <tr>
 * <td valign="top" >RI</td>
 * <td valign="top" >name</td>
 * <td>(Optional; PDF 1.3) The name of the rendering intent.</td>
 * </tr>
 * <tr>
 * <td valign="top" >OP</td>
 * <td valign="top" >boolean</td>
 * <td>(Optional) A flag specifying whether to apply overprint. In PDF 1.2
 * and earlier, there is a single overprint parameter that applies to
 * all painting operations. Beginning with PDF 1.3, there are two
 * separate overprint parameters: one for stroking and one for all other
 * painting operations. Specifying an OP entry sets both parameters
 * unless there is also an op entry in the same graphics state parameter
 * dictionary, in which case the OP entry sets only the overprint
 * parameter for stroking.</td>
 * </tr>
 * <tr>
 * <td valign="top" >op</td>
 * <td valign="top" >boolean</td>
 * <td>(Optional; PDF 1.3) A flag specifying whether to apply overprint for
 * painting operations other than stroking. If this entry is absent,
 * the OP entry, if any, sets this parameter.</td>
 * </tr>
 * <tr>
 * <td valign="top" >OPM</td>
 * <td valign="top" >integer</td>
 * <td>(Optional; PDF 1.3) The overprint mode</td>
 * </tr>
 * <tr>
 * <td valign="top" >Font</td>
 * <td valign="top" >array</td>
 * <td>(Optional; PDF 1.3) An array of the form [font size], where font is
 * an indirect reference to a font dictionary and size is a number
 * expressed in text space units. These two objects correspond to the
 * operands of the Tf operator; however, the first operand is an
 * indirect object reference instead of a resource name.</td>
 * </tr>
 * <tr>
 * <td valign="top" >BG</td>
 * <td valign="top" >function</td>
 * <td>(Optional) The black-generation function, which maps the interval
 * [0.0 1.0] to the interval [0.0 1.0]</td>
 * </tr>
 * <tr>
 * <td valign="top" >BG2</td>
 * <td valign="top" >function or name</td>
 * <td>(Optional; PDF 1.3) Same as BG except that the value may also be the
 * name Default, denoting the black-generation function that was in effect at the
 * start of the page. If both BG and BG2 are present in the same graphics state
 * parameter dictionary, BG2 takes precedence.</td>
 * </tr>
 * <tr>
 * <td valign="top" >UCR</td>
 * <td valign="top" >function</td>
 * <td>(Optional) The undercolor-removal function, which maps the interval
 * [0.0 1.0] to the interval [?1.0 1.0] (see Section 6.2.3, “Conversion
 * from DeviceRGB to DeviceCMYK”).</td>
 * </tr>
 * <tr>
 * <td valign="top" >UCR2</td>
 * <td valign="top" >function or name</td>
 * <td>(Optional; PDF 1.3) Same as UCR except that the value may also be
 * the name Default, denoting the undercolor-removal function that was
 * in effect at the start of the page. If both UCR and UCR2 are present
 * in the same graphics state parameter dictionary, UCR2 takes precedence.</td>
 * </tr>
 * <tr>
 * <td valign="top" >TR</td>
 * <td valign="top" >function, array, or name</td>
 * <td>(Optional) The transfer function, which maps the interval [0.0 1.0]
 * to the interval [0.0 1.0] (see Section 6.3, “Transfer Functions”).
 * The value is either a single function (which applies to all process
 * colorants) or an array of four functions (which apply to the process
 * colorants individually). The name Identity may be used to represent
 * the identity function.</td>
 * </tr>
 * <tr>
 * <td valign="top" >TR2</td>
 * <td valign="top" >function, array, or name</td>
 * <td>(Optional; PDF 1.3) Same as TR except that the value may also be the name
 * Default, denoting the transfer function that was in effect at the start of the
 * page. If both TR and TR2 are present in the same graphics state parameter dictionary,
 * TR2 takes precedence.</td>
 * </tr>
 * <tr>
 * <td valign="top" >HT</td>
 * <td valign="top" >dictionary, stream, or name</td>
 * <td>(Optional) The halftone dictionary or stream (see Section 6.4, “Halftones”) or
 * the name Default, denoting the halftone that was in effect at the start of the
 * page.</td>
 * </tr>
 * <tr>
 * <td valign="top" >FL</td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.3) The flatness tolerance</td>
 * </tr>
 * <tr>
 * <td valign="top" >SM</td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.3) The smoothness tolerance</td>
 * </tr>
 * <tr>
 * <td valign="top" >SA</td>
 * <td valign="top" >boolean</td>
 * <td>(Optional) A flag specifying whether to apply automatic stroke adjustment
 * (see Section 6.5.4, “Automatic Stroke Adjustment”).</td>
 * </tr>
 * <tr>
 * <td valign="top" >BM</td>
 * <td valign="top" >name or array</td>
 * <td>(Optional; PDF 1.4) The current blend mode to be used in the
 * transparent imaging model.</td>
 * </tr>
 * <tr>
 * <td valign="top" >SMask</td>
 * <td valign="top" >dictionary or name</td>
 * <td>(Optional; PDF 1.4) The current soft mask, specifying the mask shape
 * or mask opacity values to be used in the transparent imaging model</td>
 * </tr>
 * <tr>
 * <td valign="top" >CA </td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.4) The current stroking alpha constant,
 * specifying the constant shape or constant opacity value to be used for
 * stroking operations in the transparent imaging model.</td>
 * </tr>
 * <tr>
 * <td valign="top" >ca</td>
 * <td valign="top" >number</td>
 * <td>(Optional; PDF 1.4) Same as CA, but for nonstroking operations.</td>
 * </tr>
 * </table>
 * <p/>
 * <p>An <code>ExtGState</code> object is is referenced by a named resource of
 * the type ExtGSate.  The Resources class method getExtGState() will try and
 * return an <code>ExtGState</code> object of the specified name.  If successful
 * the <code>ExtGState</code> object should be concatenated with the current
 * <code>GraphicsState</code> object.</p>
 * <p/>
 * <p><b>Note: </b>many of the external graphics state parameters have not
 * yet been been implemented in the context of the content parser.
 * The <code>GraphicsSate</code> object and other relevant rendering pipeline
 * classes can be updated as needed.</p>
 *
 * @since 1.4
 */
public class ExtGState extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(ExtGState.class.toString());

    /**
     * Creates a a new Graphics State object.
     *
     * @param library       document object library
     * @param graphicsState dictionary containing entries from teh graphcis
     *                      state parameters dictionary.
     */
    public ExtGState(Library library, Hashtable graphicsState) {
        super(library, graphicsState);
    }

    /**
     * Gets the line width specified by the external graphics state.
     *
     * @return the line width with Number value.  If the line width was not
     *         specified in the dictionary null is returned.
     */
    Number getLineWidth() {
        return getNumber("LW");
    }

    /**
     * Gets the line cap style specified by the external graphics state.
     *
     * @return the line cap style Number value.  If the cap style was not
     *         specified in the dictionary null is returned.
     */
    Number getLineCapStyle() {
        return getNumber("LC");
    }

    /**
     * Gets the line join style specified by the external graphics state.
     *
     * @return the line join style Number value.  If the join style was not
     *         specified in the dictionary null is returned.
     */
    Number getLineJoinStyle() {
        return getNumber("LJ");
    }

    /**
     * Gets the miter limit specified by the external graphics state.
     *
     * @return the miter limit Number value.  If the miter limit was not
     *         specified in the dictionary null is returned.
     */
    Number getMiterLimit() {
        return getNumber("ML");
    }

    /**
     * Gets the line dash pattern specified by the external graphics state.
     *
     * @return the line dash array [dashArray dashPhase].  If the dash pattern
     *         is not specified the dictionary null is returned.
     */
    Vector getLineDashPattern() {
        Vector dashPattern = null;
        Number dashPhase = new Float(0f);
        float[] dashArray = null;
        if (entries.containsKey("D")) {
            try {
                Vector dashData = (Vector) entries.get("D");
                // pop dashPhase off the stack
                dashPhase = (Number) dashData.elementAt(1);
                // pop the dashVector of the stack
                Vector dashVector = (Vector) dashData.elementAt(0);
                // if the dash vector size is zero we have a default none dashed
                // line and thus we skip out
                if (dashVector.size() > 0) {
                    // conver dash vector to a array of floats
                    Enumeration dashEnum = dashVector.elements();
                    dashArray = new float[dashVector.size()];
                    int count = 0;
                    while (dashEnum.hasMoreElements()) {
                        dashArray[count++] = ((Number) dashEnum.nextElement()).floatValue();
                    }
                }
                // default to standard black line
                else {
                    dashPhase = new Float(0f);
                    dashArray = null;
                }
                dashPattern = new Vector(2);
                dashPattern.add(dashArray);
                dashPattern.add(dashPhase);
            }
            catch (ClassCastException e) {
                logger.log(Level.FINE, "Dash pattern syntax error: ", e);
            }
        }
        return dashPattern;
    }

    /**
     * Gets the stroking alpha constant specified by the external graphics state.
     *
     * @return the stroking alpha constant value.  If the stroking alpha constant
     *         was not specified in the dictionary null is returned.
     */
    Number getStrokingAlphConstant() {
        return getNumber("CA");
    }

    /**
     * Gets the non-stroking alpha constant specified by the external graphics state.
     *
     * @return the vstroking alpha constant value.  If the non-stroking alpha constant
     *         was not specified in the dictionary null is returned.
     */
    Number getNonStrokingAlphConstant() {
        return getNumber("ca");
    }

    /**
     * An optional flag specifying whether to apply overprint. In PDF 1.2
     * and earlier, there is a single overprint parameter that applies to
     * all painting operations. Beginning with PDF 1.3, there are two
     * separate overprint parameters: one for stroking and one for all other
     * painting operations. Specifying an OP entry sets both parameters
     * unless there is also an op entry in the same graphics state parameter
     * dictionary, in which case the OP entry sets only the overprint
     * parameter for stroking.
     *
     * @return true if OP is enabled.
     */
    Boolean getOverprint() {
        Object o = getObject("OP");
        if (o instanceof String)
            return Boolean.valueOf((String) o);
        else if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return null;
    }

    /**
     * An optional flag specifying whether to apply overprint for
     * painting operations other than stroking. If this entry is absent,
     * the OP entry, if any, sets this parameter.
     *
     * @return true if enabled, false otherwise.
     */
    Boolean getOverprintFill() {
        Object o = getObject("op");
        if (o instanceof String)
            return Boolean.valueOf((String) o);
        else if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return null;
    }

    /**
     * The overprint mode
     *
     * @return
     */
    Number getOverprintMode() {
        return getNumber("OPM");
    }

}
