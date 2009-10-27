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
        this.generalPath = new GeneralPath(bounds);
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

    public GeneralPath getGeneralPath() {
        return generalPath;
    }

    public Rectangle2D.Float getBounds() {
        return bounds;
    }
}
