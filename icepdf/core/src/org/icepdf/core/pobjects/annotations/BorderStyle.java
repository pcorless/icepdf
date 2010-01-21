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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Hashtable;
import java.util.Vector;

/**
 * BorderStyle state of a PDF annotation.  Some values of this class are
 * mutable and do not change the value of the underlying PDF in any way.  They
 * are only keep in memory for the duration that the PDF document is open.
 * <p/>
 * <h2>Refer to: 8.4.3 Border Styles</h2>
 * <p/>
 * <table border=1>
 * <tr>
 * <td>Key</td>
 * <td>Type</td>
 * <td>Value</td>
 * </tr>
 * <tr>
 * <td>Type</td>
 * <td>name</td>
 * <td><i>(Optional)</i> The type of PDF object that this dictionary describes; if present, must be
 * <b>Border</b> for a border style dictionary.</td>
 * </tr>
 * <tr>
 * <td>W</td>
 * <td>number</td>
 * <td><i>(Optional)</i> The border width in points. If this value is 0, no border is drawn. Default
 * value: 1.</td>
 * </tr>
 * <tr>
 * <td>S</td>
 * <td>name</td>
 * <td><i>(Optional)</i> The border style:
 * <table>
 * <tr>
 * <td>S</td>
 * <td>(Solid) A solid rectangle surrounding the annotation.</td>
 * </tr>
 * <tr>
 * <td>D</td>
 * <td>(Dashed) A dashed rectangle surrounding the annotation. The dash pattern
 * is specified by the <b>D</b> entry (see below).</td>
 * </tr>
 * <tr>
 * <td>B</td>
 * <td>(Beveled) A simulated embossed rectangle that appears to be raised above the
 * surface of the page.</td>
 * </tr>
 * <tr>
 * <td>I</td>
 * <td>(Inset) A simulated engraved rectangle that appears to be recessed below the
 * surface of the page.</td>
 * </tr>
 * <tr>
 * <td>U</td>
 * <td>(Underline) A single line along the bottom of the annotation rectangle.</td>
 * </tr>
 * </table>Default value: S.</td>
 * </tr>
 * <tr>
 * <td>D</td>
 * <td>array</td>
 * <td><i>(Optional)</i> A <i>dash array</i> defining a pattern of dashes and gaps to be used in drawing a
 * dashed border (border style D above). The dash array is specified in the same format
 * as in the line dash pattern parameter of the graphics state (see "Line Dash Pattern" on
 * page 187). The dash phase is not specified and is assumed to be 0. For example, a <b>D</b>
 * entry of [3 2] specifies a border drawn with 3-point dashes alternating with 2-point
 * gaps. Default value: [3].</td>
 * </tr>
 * </table>
 *
 * @author Mark Collette
 * @since 2.5
 */
public class BorderStyle extends Dictionary {

    //todo fill out with valid numbers...
    private static final float[] DEFAULT_DASH_ARRAY = new float[]{3.0f};

    public static final Name BORDER_STYLE_KEY = new Name("S");
    public static final Name BORDER_WIDTH_KEY = new Name("W");

    public static final Color DARKEST = Color.black;
    public static final Color DARK = new Color(0xFF606060);
    public static final Color LIGHT = new Color(0xFF909090);
    public static final Color LIGHTEST = new Color(0xFFE5E5E5);

    /**
     * Solid rectangle border style surrounding the annotation
     */
    public static final String BORDER_STYLE_SOLID = "S";

    /**
     * Dashed rectangle border style surrounding the annotation
     */
    public static final String BORDER_STYLE_DASHED = "D";

    /**
     * Beveled rectangle border style surrounding the annotation
     */
    public static final String BORDER_STYLE_BEVELED = "B";

    /**
     * Inset rectangle border style surrounding the annotation
     */
    public static final String BORDER_STYLE_INSET = "I";

    /**
     * Underline rectangle border style surrounding the annotation
     */
    public static final String BORDER_STYLE_UNDERLINE = "U";

    // stroke width
    private float strokeWidth = 1.0f;

    // border style, default is solid
    private String borderStyle = null;

    // dash array
    private float[] dashArray = DEFAULT_DASH_ARRAY;

    /**
     * Creates a new instance of a BorderStyle.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public BorderStyle(Library l, Hashtable h) {
        super(l, h);
        // parse out stroke width
        Number value = (Number) getObject(BORDER_WIDTH_KEY);
        if (value != null) {
            strokeWidth = value.floatValue();
        }
        // parse the default style.
        Object style = getObject(BORDER_STYLE_KEY);
        if (style != null) {
            borderStyle = style.toString();
        }
        // parse dash array.
        Vector dashVector = (Vector) getObject(BORDER_STYLE_DASHED);
        if (dashVector != null) {
            int sz = dashVector.size();
            float[] dashArray = new float[sz];
            for (int i = 0; i < sz; i++) {
                Number num = (Number) dashVector.get(i);
                dashArray[i] = num.floatValue();
            }
            this.dashArray = dashArray;
        }
    }

    public BorderStyle() {
        super(null, null);
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public String getBorderStyle() {
        return borderStyle;
    }

    /**
     * Sets the stroke width of the border style.  Default value 1.0.
     *
     * @param strokeWidth float value representing width.
     */
    public void setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
        entries.put(BORDER_WIDTH_KEY, this.strokeWidth);
    }

    /**
     * Sets the borderStyle type for this instance.
     *
     * @param lineStyle border style type as defined by, BORDER_STYLE_SOLID,
     *                    BORDER_STYLE_DASHED, BORDER_STYLE_BEVELED, BORDER_STYLE_INSET,
     *                    BORDER_STYLE_UNDERLINE
     */
    public void setBorderStyle(final String lineStyle) {
        this.borderStyle = lineStyle;
        entries.put(BORDER_STYLE_KEY, new Name(this.borderStyle));
    }

    public boolean isStyleSolid() {
        return BORDER_STYLE_SOLID.equals(borderStyle);
    }

    public boolean isStyleDashed() {
        return BORDER_STYLE_DASHED.equals(borderStyle);
    }

    public boolean isStyleBeveled() {
        return BORDER_STYLE_BEVELED.equals(borderStyle);
    }

    public boolean isStyleInset() {
        return BORDER_STYLE_INSET.equals(borderStyle);
    }

    public boolean isStyleUnderline() {
        return BORDER_STYLE_UNDERLINE.equals(borderStyle);
    }

    public void setDashArray(float[] dashArray) {
        if (dashArray != null) {
            this.dashArray = dashArray;
            int sz = dashArray.length;
            Vector<Number> dashVector = new Vector<Number>(sz);
            for (int i = 0; i < sz; i++) {
                dashVector.add(dashArray[i]);
            }
            this.dashArray = dashArray;
            entries.put(BORDER_STYLE_DASHED, dashVector);
        }
    }

    public float[] getDashArray() {
        return dashArray;
    }

}
