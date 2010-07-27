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

import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.pobjects.graphics.batik.ext.awt.MultipleGradientPaint;
import org.icepdf.core.pobjects.graphics.batik.ext.awt.RadialGradientPaint;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * <p>Type 3 (radial) shading define a colour blend that varies between two
 * circles.  Shading of this type are commonly used to depict three-dimensional
 * spheres and cones.</p>
 *
 * @author ICEsoft Technologies Inc.
 * @since 3.0
 */
public class ShadingType3Pattern extends ShadingPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType3Pattern.class.toString());

    // A 1-in, n-out function or an array of n 1-in, 1-out functions (where n
    // is the number of colour components in the shading dictionary's colour
    // space).  The function(s) are called with values of the parametric variables
    // t in the domain defined by the domain entry.  Each function's domain must
    // be a superset of that of the shading dictionary.  If the return value
    // is out of range it is adjusted to the nearest value.
    protected Function function;

    // An array of two numbers [t0, t1] specifying the limiting values of a
    // parametric variable t. The variable is considered to vary linearly between
    // these two values as the colour gradient varies between the starting and
    // ending points of the axis.  The variable t becomes the argument to the
    // colour function(s).  Default [0,1].
    protected Vector<Number> domain;

    // An array of six numbers [x0, y0, r0, x1, y1, r1] specifying the centers
    // and radii of the starting and ending circles.  Expressed in the shading
    // target coordinate space.  The radii r0 and r1 must both be greater than
    // or equal to 0. If both are zero nothing is painted.
    protected Vector coords;

    // An array of two Boolean values specifying whether to extend the shading
    // beyond the starting and ending points of the axis, Default [false, false].
    protected Vector<Boolean> extend;

    // radial gradient paint that is used by java for paint. 
    protected RadialGradientPaint radialGradientPaint;


    public ShadingType3Pattern(Library library, Hashtable entries) {
        super(library, entries);
    }

    public synchronized void init() {

        if (inited) {
            return;
        }

        // shading dictionary
        if (shading == null) {
            shading = library.getDictionary(entries, "Shading");
        }

        shadingType = library.getInt(shading, "ShadingType");
        bBox = library.getRectangle(shading, "BBox");
        colorSpace = PColorSpace.getColorSpace(library,
                library.getObject(shading, "ColorSpace"));
        if (library.getObject(shading, "Background") != null &&
                library.getObject(shading, "Background") instanceof Vector) {
            background = (Vector) library.getObject(shading, "Background");
        }
        antiAlias = library.getBoolean(shading, "AntiAlias");

        // get type 2 specific data.
        if (library.getObject(shading, "Domain") instanceof Vector) {
            domain = (Vector<Number>) library.getObject(shading, "Domain");
        } else {
            domain = new Vector<Number>(2);
            domain.add(new Float(0.0));
            domain.add(new Float(1.0));
        }

        if (library.getObject(shading, "Coords") instanceof Vector) {
            coords = (Vector) library.getObject(shading, "Coords");
        }
        if (library.getObject(shading, "Extend") instanceof Vector) {
            extend = (Vector) library.getObject(shading, "Extend");
        } else {
            extend = new Vector<Boolean>(2);
            extend.add(false);
            extend.add(false);
        }
        Object tmp = library.getObject(shading, "Function");
        if (tmp != null) {
            function = Function.getFunction(library,
                    tmp);
        }

        float t0 = domain.get(0).floatValue();
        float t1 = domain.get(1).floatValue();
        float s[] = new float[]{0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        Point2D.Float center = new Point2D.Float(
                ((Number) coords.get(0)).floatValue(),
                ((Number) coords.get(1)).floatValue());

        Point2D.Float focus = new Point2D.Float(
                ((Number) coords.get(3)).floatValue(),
                ((Number) coords.get(4)).floatValue());

        float radius = ((Number) coords.get(2)).floatValue();
        float radius2 = ((Number) coords.get(5)).floatValue();

        // approximation, as we don't full support radial point via the paint
        // class. 
        if (radius2 > radius) {
            radius = radius2;
        }

        // get the number off components in the colour
        Color color1 = calculateColour(colorSpace, s[0], t0, t1);
        Color color2 = calculateColour(colorSpace, s[1], t0, t1);
        Color color3 = calculateColour(colorSpace, s[2], t0, t1);
        Color color4 = calculateColour(colorSpace, s[3], t0, t1);
        Color color5 = calculateColour(colorSpace, s[4], t0, t1);

        if (color1 == null || color2 == null) {
            return;
        }
        // Construct a LinearGradientPaint object to be use by java2D
        Color[] colors = {color1, color2, color3, color4, color5};

        radialGradientPaint = new RadialGradientPaint(
                center, radius,
                focus,
                s,
                colors,
                MultipleGradientPaint.NO_CYCLE,
                MultipleGradientPaint.LINEAR_RGB,
                matrix);

        // get type 3 specific data.
        inited = true;
    }

    private Color calculateColour(PColorSpace colorSpace, float s,
                                  float t0, float t1) {

        // find colour at point 1
        float t = parametrixValue(s, t0, t1, extend);
        // find colour at point 
        float[] input = new float[1];
        input[0] = t;

        if (function != null) {
            float[] output = function.calculate(input);
            if (output != null) {
                if (!(colorSpace instanceof DeviceN)) {
                    output = PColorSpace.reverse(output);
                }
                return colorSpace.getColor(output);
            } else {
                return null;
            }

        } else {
            logger.fine("Error processing Shading Type 3 Pattern.");
            return null;
        }

    }

    /**
     * Parametric variable t calculation as defined in Section 4.6, Type 2
     * (axial) shadings.
     *
     * @param linearMapping linear mapping of some point x'
     * @param t0            domain of axial shading, limit 1
     * @param t1            domain of axial shading, limit 2
     * @param extended      2 element vector, indicating line extension along domain
     * @return parametric value.
     */
    private float parametrixValue(float linearMapping, float t0, float t1,
                                  Vector extended) {
        return t0 + ((t1 - t0) * linearMapping);
    }

    public Paint getPaint() {
        init();
        return radialGradientPaint;
    }

    public String toSting() {
        return super.toString() +
                "\n                    domain: " + domain +
                "\n                    coords: " + coords +
                "\n                    extend: " + extend +
                "\n                 function: " + function;
    }
}