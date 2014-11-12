/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
    private String cid;

    // Unicode/ASCII value that is represented by glyph, a cid can be
    // represented by one or more characters.
    private String unicode;

    public GlyphText(float x, float y, Rectangle2D.Float bounds,
                     String cid, String unicode) {
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

    public String getCid() {
        return cid;
    }

    public String getUnicode() {
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
