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

import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.pobjects.graphics.batik.ext.awt.LinearGradientPaint;
import org.icepdf.core.pobjects.graphics.batik.ext.awt.MultipleGradientPaint;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * <p>Type 2 (axial) shadings define colour blend that varies along a linear
 * axis between two endpoints and extends indefinitely perpendicular to the
 * that axis.</p>
 *
 * @author ICEsoft Technologies Inc.
 * @since 2.7
 */
public class ShadingType2Pattern extends ShadingPattern {

    private static final Logger logger =
            Logger.getLogger(ShadingType2Pattern.class.toString());

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
    protected Vector<Float> domain;

    // An array of four numbers [x0, y0, x1, y1] specifying the starting and
    // ending coordinates of the axis, expressed in the shading's target
    // coordinate space.
    protected Vector coords;

    // An array of two Boolean values specifying whether to extend the shading
    // beyond the starting and ending points of the axis, Default [false, false].
    protected Vector<Boolean> extend;

    // linear gradient paint describing the gradient.
    private LinearGradientPaint linearGradientPaint;

    public ShadingType2Pattern(Library library, Hashtable entries) {
        super(library, entries);
    }

    public void init() {

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
            domain = (Vector<Float>) library.getObject(shading, "Domain");
        } else {
            domain = new Vector<Float>(2);
            domain.add(new Float(0.0));
            domain.add(new Float(1.0));
        }

        if (library.getObject(shading, "Coords") instanceof Vector) {
            coords = (Vector) library.getObject(shading, "Coords");
        }
        if (library.getObject(shading, "Extend") instanceof Vector) {
            extend = (Vector<Boolean>) library.getObject(shading, "Extend");
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

        // calculate the t's
        float t0 = ((Number) domain.get(0)).floatValue();
        float t1 = ((Number) domain.get(1)).floatValue();

        // first off, create the two needed start and end points of the line
        Point2D.Float point1 = new Point2D.Float(
                ((Number) coords.get(0)).floatValue(),
                ((Number) coords.get(1)).floatValue());

        Point2D.Float point2 = new Point2D.Float(
                ((Number) coords.get(2)).floatValue(),
                ((Number) coords.get(3)).floatValue());

        // calculate mid point so we have another colour other then the end points
        // to calculate a colour for.
        // todo: replace with equation of line to caluclate any point along line...
        Point2D.Float point3 = new Point2D.Float(
                (point2.x + point1.x) / 2.0f, (point2.y + point1.y) / 2.0f);

        Point2D.Float point4 = new Point2D.Float(
                (point3.x + point1.x) / 2.0f, (point3.y + point1.y) / 2.0f);

        Point2D.Float point5 = new Point2D.Float(
                (point2.x + point3.x) / 2.0f, (point2.y + point3.y) / 2.0f);

        // get the number off components in the colour
        Color color1 = calculateColour(colorSpace, point1, point1, point2, t0, t1);
        Color color2 = calculateColour(colorSpace, point2, point1, point2, t0, t1);
        Color color3 = calculateColour(colorSpace, point3, point1, point2, t0, t1);
//        Color color4 = calculateColour(colorSpace, point4, point1, point2, t0, t1);
//        Color color5 = calculateColour(colorSpace, point5, point1, point2, t0, t1);

//        System.out.println("color 1: " + color1);
//        System.out.println("color 2: " + color4);
//        System.out.println("color 3: " + color3);
//        System.out.println("color 4: " + color5);
//        System.out.println("color 5: " + color2);

        if (color1 == null || color2 == null || color3 == null) {
            return;
        }
        // Construct a LinearGradientPaint object to be use by java2D
        float[] dist = {t0, t1 / 2.0f, t1};
        Color[] colors = {color1, color3, color2};
        linearGradientPaint = new LinearGradientPaint(
                point1, point2, dist, colors,
                MultipleGradientPaint.NO_CYCLE,
                MultipleGradientPaint.LINEAR_RGB,
                matrix);
        inited = true;
    }

    private Color calculateColour(PColorSpace colorSpace, Point2D.Float xy,
                                  Point2D.Float point1, Point2D.Float point2,
                                  float t0, float t1) {

        // find colour at point 1
        float xPrime = linearMapping(xy, point1, point2);
        float t = parametrixValue(xPrime, t0, t1, extend);
        // find colour at point 2
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
            logger.fine("Error processing Shading Type 2 Pattern.");
            return null;
        }

    }

    /**
     * Colour blend function to be applied to a point on the line with endpoints
     * poin1 and point1 for a given point x,y.
     *
     * @param xy     point to linearize.
     * @param point1 end point of line
     * @param point2 end poitn of line.
     * @return linearized x' value.
     */
    private float linearMapping(Point2D.Float xy, Point2D.Float point1, Point2D.Float point2) {
        float x = xy.x;
        float y = xy.y;
        float x0 = point1.x;
        float y0 = point1.y;
        float x1 = point2.x;
        float y1 = point2.y;
        float top = (((x1 - x0) * (x - x0)) + ((y1 - y0) * (y - y0)));
        float bottom = (((x1 - x0) * (x1 - x0)) + ((y1 - y0) * (y1 - y0)));

        return top / bottom;
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

        if (linearMapping < 0 && ((Boolean) extended.get(0))) {
            return t0;
        } else if (linearMapping > 1 && ((Boolean) extended.get(1))) {
            return t1;
        } else {
            return t0 + ((t1 - t0) * linearMapping);
        }
    }

    public Paint getPaint() {

        init();

        return linearGradientPaint;
    }

    public String toString() {
        return super.toString() +
                "\n                    domain: " + domain +
                "\n                    coords: " + coords +
                "\n                    extend: " + extend +
                "\n                 function: " + function;
    }
}