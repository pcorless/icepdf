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

import java.awt.*;

/**
 * <p>This class represents a dimension similar to java.awt.geom.Dimension2D.Dimension
 * but ensures that width and height are stored using floating point values.</p>
 *
 * @since 2.0
 */
public class PDimension {
    private float width;
    private float height;

    /**
     * Creates a new instance of a PDimension.
     *
     * @param w width of new dimension.
     * @param h height of new dimension.
     */
    public PDimension(float w, float h) {
        set(w, h);
    }

    /**
     * Creates a new instance of a PDimension.
     *
     * @param w width of new dimension.
     * @param h height of new dimension.
     */
    public PDimension(int w, int h) {
        set(w, h);
    }

    /**
     * Sets the width and height of the dimension.
     *
     * @param w new width value.
     * @param h new height value.
     */
    public void set(float w, float h) {
        width = w;
        height = h;
    }

    /**
     * Sets the width and height of the dimension.
     *
     * @param w new width value.
     * @param h new height value.
     */
    public void set(int w, int h) {
        width = w;
        height = h;
    }

    /**
     * Gets the width of the dimension object.
     *
     * @return width
     */
    public float getWidth() {
        return width;
    }

    /**
     * Gets the height of the dimension object.
     *
     * @return height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Converts this object to a java.awt.geom.Dimension2D.Dimension.  The
     * floating point accuracy of the width and height are lost when converted
     * to int.
     *
     * @return a new java.awt.geom.Dimension2D.Dimension
     */
    public Dimension toDimension() {
        return new Dimension((int) width, (int) height);
    }

    /**
     * String representation of this object.
     *
     * @return string summary of width and height of this object.
     */
    public String toString() {
        return "PDimension { width=" + width + ", height=" + height + " }";
    }
}
