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
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.logging.Logger;

/**
 * Glyph Sprite contains glyph bound and textual information for drawing
 * and text extraction.  The Object is used as a child of TextSprite
 * for painting and as a child of TextWord for text extaction and selection.
 *
 * @since 4.0
 */
public class GlyphText extends AbstractText {

    private static final Logger logger =
            Logger.getLogger(GlyphText.class.toString());

    // x and y coordinates used for painting glyph
    private float x, y;

    // character code used to represent glyph, maybe ascii or CID value
    private int cid;

    // Unicode/ASCII value that is represented by glyph
    private int unicode;

    public GlyphText(float x, float y, Rectangle2D.Float bounds,
                     int cid, int unicode) {
        this.x = x;
        this.y = y;
        this.bounds = bounds;
        this.cid = cid;
        this.unicode = unicode;
    }

    /**
     * Maps the glyph bounds to user space
     *
     * @param af tranform from glyph space to user space
     */
    public void normalizeToUserSpace(AffineTransform af) {
        // map the coordinates from glyph space to user space.
        GeneralPath generalPath = new GeneralPath(bounds);
        generalPath.transform(af);
        bounds = (Rectangle2D.Float) generalPath.getBounds2D();
    }

    public int getCid() {
        return cid;
    }

    public int getUnicode() {
        return unicode;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public Rectangle2D.Float getBounds() {
        return bounds;
    }
}
