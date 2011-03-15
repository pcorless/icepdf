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
package org.icepdf.core.pobjects;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

/**
 * <p>A rectangle in a PDF document is slightly different than the <code>Rectangle</code> class
 * in Java.  A PDF rectangle is written as an array of four numbers giving the
 * coordinates of a pair of diagonally opposite corners.  Typically, the
 * array takes the form:</p>
 * <p/>
 * <ul>
 * [II<sub>x</sub> II<sub>y</sub> UR<sub>x</sub> UR<sub>y</sub>]
 * </ul>
 * <p/>
 * <p>This specifies the lower-left x, lower-left y, upper-right x, and upper-right y
 * coordinates of the rectangle, in that order.  However, this format is not
 * guaranteed and this class normalizes such rectangles.</p>
 * <p/>
 * <p>Another very important difference between PRectangles Rectangles is that
 * PRectangles use the Cartesian Coordinate system, where Rectangles use the
 * Java2D coordinates system.  As a result, the user of this class must know the
 * context in which the PRectangle is being used.  For example there is a significant
 * difference between the inherited method createIntersection and PRectangles
 * createCartesianIntersection.</p>
 *
 * @since 2.0
 */
public class PRectangle extends Rectangle2D.Float {

    private float p1x;
    private float p1y;
    private float p2x;
    private float p2y;

    /**
     * <p>Creates a new PRectangle object.  The points are automatically normalized
     * by the constructor. These two coordinates represent the diagonal
     * corners of the rectangle.
     *
     * @param p1 first point of diagonal, in the Cartesian coordinate space
     * @param p2 second point of diagonal, in the Cartesian coordinate space
     */
    public PRectangle(Point2D.Float p1, Point2D.Float p2) {
        super();
        normalizeCoordinates(p1.x, p1.y, p2.x, p2.y);
        // assign original data
        p1x = p1.x;
        p1y = p1.y;
        p2x = p2.x;
        p2y = p2.y;
    }

    /**
     * <p>Creates a new PRectangle object assumed to be in the Cartesian coordinate
     * space.</p>
     *
     * @param x      the specified x coordinate
     * @param y      the specified y coordinate
     * @param width  the width of the Rectangle
     * @param height the height of the Rectangle
     */
    private PRectangle(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a new PRectangle object.  The points are automatically normalized
     * by the constructor.
     *
     * @param coordinates a vector containing four elements where the first and
     *                    second elements represent the x and y coordinates of one point and the
     *                    third and fourth elements represent the x and y cooordinates of the second
     *                    point.  These two coordinates represent the diagonal corners of the
     *                    rectangle.
     * @throws IllegalArgumentException thrown if coordinates is null or does not
     *                                  have four elements
     */
    public PRectangle(Vector coordinates) throws IllegalArgumentException {
        if (coordinates == null || coordinates.size() < 4)
            throw new IllegalArgumentException();
        float x1 = ((Number) coordinates.elementAt(0)).floatValue();
        float y1 = ((Number) coordinates.elementAt(1)).floatValue();

        float x2 = ((Number) coordinates.elementAt(2)).floatValue();
        float y2 = ((Number) coordinates.elementAt(3)).floatValue();

        // assign original data
        p1x = x1;
        p1y = y1;
        p2x = x2;
        p2y = y2;

        //System.out.println(x1 + " : " + y1 + " : " + x2 + " : " + y2 );
        normalizeCoordinates(x1, y1, x2, y2);
    }

    /**
     * Returns a new PRectangle object representing the intersection of this
     * PRectangle with the specified PRectangle using the Cartesian coordinate
     * system. If a Java2D coordinate system is used, then the rectangle should
     * be first converted to that space {@see #toJava2dCoordinates() }.
     *
     * @param src2 the Rectangle2D to be intersected with this Rectangle2D.
     * @return object representing the intersection of the two PRectangles.
     */
    public PRectangle createCartesianIntersection(PRectangle src2) {
        PRectangle rec = new PRectangle(src2.x, src2.y, src2.width, src2.height);
        float xLeft = (x > rec.x) ? x : rec.x;
        float xRight = ((x + width) > (rec.x + rec.width)) ?
                (rec.x + rec.width) : (x + width);

        float yBottom = ((y - height) < (rec.y - rec.height) ?
                (rec.y - rec.height) : (y - height));
        float yTop = (y > rec.y) ? rec.y : y;

        rec.x = xLeft;
        rec.y = yTop;
        rec.width = xRight - xLeft;
        rec.height = yTop - yBottom;

        // If rectangles don't intersect, return zeroed intersection.
        if (rec.width < 0 || rec.height < 0) {
            rec.x = rec.y = rec.width = rec.height = 0;
        }
        return rec;
    }

    /**
     * <p>Gets the orgional two points that created the PRectangle object.  The
     * first point is represented by the the rectangle positions x, y and the
     * second poitn is represented by the width and height of the rectangle.
     *
     * @return rectangle representing the PRectangle object initial two point.
     */
    public Rectangle2D.Float getOriginalPoints() {
        return new Rectangle2D.Float(p1x, p1y, p2x, p2y);
    }

    /**
     * <p>Converts the Cartesian representation of the Rectangle into Rectangle in
     * the Java2D coordinate space.</p>
     *
     * @return rectangle in the Java2D coordinate space.
     */
    public Rectangle2D.Float toJava2dCoordinates() {
        return new Rectangle2D.Float(x, y - height, width, height);
    }

    /**
     * Converts a rectangle defined in user page in Java2D coordinates back
     * to PDF space and in the vector for of the rectangle.
     * @param rect user space rectangle in Java2D coordinates space.
     * @return vector notation of rectangle in PDF space.
     *
     */
    public static Vector getPRectangleVector(Rectangle2D rect){

        // convert the rectangle back to PDF space.
        rect = new Rectangle2D.Double(rect.getX(),
                rect.getY(),
                rect.getWidth(), rect.getHeight());
        // get two adjacent points
        Number p1x = rect.getMinX();
        Number ply = rect.getMinY();
        Number p2x = rect.getMaxX();
        Number p2y = rect.getMaxY();
        // create and return the new vector
        Vector<Number> rectVector = new Vector<Number>(4);
        rectVector.add(p1x);
        rectVector.add(ply);
        rectVector.add(p2x);
        rectVector.add(p2y);
        return rectVector;
    }

    /**
     * Normalizes the given coordinates so that the rectangle is created with the
     * proper dimensions.
     *
     * @param x1 x value of coordinate 1
     * @param y1 y value of coordinate 1
     * @param x2 x value of coordinate 2
     * @param y2 y value of coordinate 2
     */
    private void normalizeCoordinates(float x1, float y1, float x2, float y2) {
        float x = x1, y = y1,
                w = Math.abs(x2 - x1),
                h = Math.abs(y2 - y1);
        // get smallest x
        if (x1 > x2) {
            x = x2;
        }
        // get largest y
        if (y1 < y2) {
            y = y2;
        }
        setRect(x, y, w, h);
    }
}
