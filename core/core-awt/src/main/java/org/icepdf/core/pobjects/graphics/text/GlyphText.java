/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.pobjects.Name;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.logging.Logger;

/**
 * Glyph Sprite contains glyph bound and textual information for drawing
 * and text extraction.  The Object is used as a child of TextSprite
 * for painting and as a child of TextWord for text extraction and selection.
 *
 * @since 4.0
 */
public class GlyphText extends AbstractText {

    private static final Logger logger =
            Logger.getLogger(GlyphText.class.getName());

    // x and y coordinates used for painting glyph
    private final float x;
    private final float y;

    private final float advanceX;
    private final float advanceY;

    // character code used to represent glyph, maybe ascii or CID value
    private final char cid;
    private final Name fontName;

    // Unicode/ASCII value that is represented by glyph, a cid can be
    // represented by one or more characters.
    private final String unicode;

    private boolean flagged;
    private int fontSubTypeFormat;

    public GlyphText(float x, float y, float advanceX, float advanceY, Rectangle2D.Double bounds,
                     float pageRotation, char cid, String unicode, Name FontName) {
        this.x = x;
        this.y = y;
        this.advanceX = advanceX;
        this.advanceY = advanceY;
        this.pageRotation = pageRotation;
        this.bounds = bounds;
        this.textSelectionBounds = new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
        this.cid = cid;
        this.unicode = unicode;
        this.fontName = FontName;
    }

    /**
     * Maps the glyph bounds to user space
     *
     * @param af transform from glyph space to user space
     * @param af1 transform from glyph space, if shear is detected the
     *            extracted bounds are rotated back to the portrait layout.
     */
    public void normalizeToUserSpace(AffineTransform af, AffineTransform af1) {
        // Map the glyph bounds from glyph space to user space.  af may include rotation or shear, so the result is
        // the bounding box of the four transformed corners.  Computing that directly and updating the bounds in
        // place avoids allocating a Path2D (and its backing arrays) for every glyph - the previous approach was a
        // dominant source of GC during text extraction (GH-495).
        double x0 = bounds.x, y0 = bounds.y;
        double x1 = x0 + bounds.width, y1 = y0 + bounds.height;
        double[] pts = {x0, y0, x1, y0, x1, y1, x0, y1};
        af.transform(pts, 0, pts, 0, 4);
        double minX = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        double maxX = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        double minY = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        double maxY = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        bounds.setRect(minX, minY, maxX - minX, maxY - minY);
        textSelectionBounds = bounds;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public int getFontSubTypeFormat() {
        return fontSubTypeFormat;
    }

    public void setFontSubTypeFormat(int fontSubTypeFormat) {
        this.fontSubTypeFormat = fontSubTypeFormat;
    }

    public void flagged() {
        this.flagged = true;
    }

    public char getCid() {
        return cid;
    }

    public String getUnicode() {
        return unicode;
    }

    public float getX() {
        return x;
    }

    public float getAdvanceX() {
        return advanceX;
    }

    public float getY() {
        return y;
    }

    public Rectangle2D.Double getBounds() {
        return bounds;
    }

    public Name getFontName() {
        return fontName;
    }
}
