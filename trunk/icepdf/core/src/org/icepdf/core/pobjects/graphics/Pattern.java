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


import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;


/**
 * <p>Patterns come in two varieties:</p>
 * <ul>
 * <li><p><i>Tiling patterns</i> consist of a small graphical figure (called a
 * pattern cell) that is replicated at fixed horizontal and vertical
 * intervals to fill the area to be painted. The graphics objects to
 * use for tiling are described by a content stream. (PDF 1.2)</li>
 * <p/>
 * <li><p><i>Shading patterns</i> define a gradient fill that produces a smooth
 * transition between colors across the area. The color to use is
 * specified as a function of position using any of a variety of
 * methods. (PDF 1.3)</li>
 * </ul>
 * <p>Note Tiling pattern and shading patterns are not currently supported</p>
 *
 * @since 1.0
 */
public interface Pattern {

    /**
     * The pattern type is a tiling pattern
     */
    public static final int PATTERN_TYPE_TILING = 1;

    /**
     * The pattern type is a shading pattern
     */
    public static final int PATTERN_TYPE_SHADING = 2;

    public String getType();

    public int getPatternType();

    public AffineTransform getMatrix();

    public void setMatrix(AffineTransform matrix);

    public Rectangle2D getBBox();

    void init();

    public Paint getPaint();

    public void setParentGraphicState(GraphicsState graphicsState);

}
