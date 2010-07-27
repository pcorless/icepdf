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

import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

/**
 * <p>This class represents text which will be rendered to the a graphics context.
 * This class was created to act as a wrapper for painting text using the Phelphs
 * font library as well as painting using java.awt.Font.</p>
 * <p/>
 * <p>Objects of this type are created by the content parser when "TJ" or "Tj"
 * operands are encountered in a page's content stream.  Each TextSprite object
 * is comprised of a list of CharSprites which can be painted by the Shapes
 * class at render time.</p>
 *
 * @since 2.0
 */
public class TextSprite {

    // ability to turn off optimized drawing for text.
    private final static boolean OPTIMIZED_DRAWING_ENABLED =
            Defs.booleanProperty("org.icepdf.core.text.optimized", true);

    // child GlyphText objects
    private ArrayList<GlyphText> glyphTexts;

    // text bounds, including all child Glyph sprites, in glyph space
    // this bound is used during painting to respect painting clip.
    Rectangle2D.Float bounds;

    // space reference for where glyph
    private AffineTransform graphicStateTransform;

    // stroke color
    private Color strokeColor;
    // the write
    private int rmode;
    // Font used to paint text
    private FontFile font;

    /**
     * <p>Creates a new TextSprit object.</p>
     *
     * @param font font used when painting glyphs.
     * @param size size of the font in user space
     */
    public TextSprite(FontFile font, int size, AffineTransform graphicStateTransform) {
        glyphTexts = new ArrayList<GlyphText>(size);
        // all glyphs in text share this ctm
        this.graphicStateTransform = graphicStateTransform;
        this.font = font;
        bounds = new Rectangle2D.Float();
    }

    /**
     * <p>Adds a new text char to the TextSprite which will pe painted at x, y under
     * the current CTM</p>
     *
     * @param character character to paint.
     * @param x         x-coordinate to paint.
     * @param y         y-coordinate to paint.
     * @param width     width of character from font.
     */
    // todo add tranform
    public GlyphText addText(char character, char unicode, float x, float y, float width) {

        // keep track of the text total bound, important for shapes painting.
        // IMPORTANT: where working in Java Coordinates with any of the Font bounds

        float w = width;//(float)stringBounds.getWidth();

        float h = (float) (font.getAscent() + font.getDescent());

        if (h <= 0.0f) {
            h = (float) (font.getMaxCharBounds().getHeight());
        }
        if (w <= 0.0f) {
            w = (float) font.getMaxCharBounds().getWidth();
        }
        // zero height will not intersect with clip rectangle
        // todo: test if this can occur, might be legacy code from old bug...
        if (h <= 0.0f) {
            h = 1.0f;
        }
        if (w <= 0.0f) {
            w = 1.0f;
        }
        Rectangle2D.Float glyphBounds =
                new Rectangle2D.Float(x, y - (float) font.getAscent(), w, h);

        // add bounds to total text bounds.
        bounds.add(glyphBounds);

        // create glyph and normalize bounds.
        GlyphText glyphText =
                new GlyphText(x, y, glyphBounds, character, unicode);
        glyphText.normalizeToUserSpace(graphicStateTransform);
        glyphTexts.add(glyphText);
        return glyphText;
    }

    /**
     * Gets the character bounds of each glyph found in the TextSprite.
     * @return  bounds in PDF coordinates of character bounds
     */
    public ArrayList<GlyphText> getGlyphSprites() {
        return glyphTexts;
    }

    public AffineTransform getGraphicStateTransform() {
        return graphicStateTransform;
    }

    /**
     * Set the graphic state transorm on all child sprites, This is used for
     * xForm object parsing and text selection.  There is no need to do this
     * outside of the context parser. 
     * @param graphicStateTransform
     */
    public void setGraphicStateTransform(AffineTransform graphicStateTransform) {
        this.graphicStateTransform = graphicStateTransform;
        for (GlyphText sprite : glyphTexts){
            sprite.normalizeToUserSpace(this.graphicStateTransform);
        }
    }

    /**
     * <p>Set the rmode for all the characters being in this object. Rmode can
     * have the following values:</p>
     * <ul>
     * <li>0 - Fill text.</li>
     * <li>1 - Stroke text. </li>
     * <li>2 - fill, then stroke text.  </li>
     * <li>3 - Neither fill nor stroke text (invisible).  </li>
     * <li>4 - Fill text and add to path for clipping.  </li>
     * <li>5 - Stroke text and add to path for clipping.   </li>
     * <li>6 - Fill, then stroke text and add to path for clipping. </li>
     * <li>7 - Add text to path for clipping.</li>
     * </ul>
     *
     * @param rmode valid rmode from 0-7
     */
    public void setRMode(int rmode) {
        if (rmode >= 0) {
            this.rmode = rmode;
        }
    }

    public String toString() {
        StringBuilder text = new StringBuilder(glyphTexts.size());
        for (GlyphText glyphText : glyphTexts){
            text.append(glyphText.getUnicode());
        }
        return text.toString();
    }

    public void setStrokeColor(Color color) {
        strokeColor = color;
    }

    /**
     * Getst the bounds of the text that makes up this sprite.  The bounds
     * are defined PDF space and are relative to the current CTM.
     * @return
     */
    public Rectangle2D.Float getBounds(){
        return bounds;
    }

    /**
     * <p>Paints all the character elements in this TextSprite to the graphics
     * context</p>
     *
     * @param g graphics context to which the characters will be painted to.
     */
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        // draw bounding box.
//        drawBoundBox(g2d);

        for (GlyphText glyphText : glyphTexts){

            // paint glyph
            font.drawEstring(g2d,
                    String.valueOf((char) glyphText.getCid()),
                    glyphText.getX(),
                    glyphText.getY(),
                    FontFile.LAYOUT_NONE, rmode, strokeColor);

            // debug glyph box
            // draw glyph box
//            drawGyphBox(g2d, glyphText);
        }
    }
    /*
    private void drawBoundBox(Graphics2D gg) {

        // draw the characters
        GeneralPath charOutline;

        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        double scale = gg.getTransform().getScaleX();

        scale = 1.0f / scale;
        if (scale <= 0) {
            scale = 1;
        }
        gg.setStroke(new BasicStroke((float) (scale)));
        gg.setColor(Color.blue);

        charOutline = new GeneralPath(bounds);
        gg.draw(charOutline);

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);
    }
    */
    /*
    private void drawGyphBox(Graphics2D gg, GlyphText glyphSprite) {

        // draw the characters
        GeneralPath charOutline;

        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        double scale = gg.getTransform().getScaleX();

        scale = 1.0f / scale;
        if (scale <= 0) {
            scale = 1;
        }
        gg.setStroke(new BasicStroke((float) (scale)));
        gg.setColor(Color.red);

        charOutline = new GeneralPath(glyphSprite.getGeneralPath());
        gg.draw(charOutline);

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);

    }
    */

    /**
     * Tests if the interior of the <code>TextSprite</code> bounds intersects the
     * interior of a specified <code>shape</code>.
     *
     * @param shape shape to calculate intersection against
     * @return true, if <code>TextSprite</code> bounds intersects <code>shape</code>;
     *         otherwise; false.
     */
    public boolean intersects(Shape shape) {
//        return shape.intersects(bounds.toJava2dCoordinates());
        return !OPTIMIZED_DRAWING_ENABLED || shape.intersects(bounds);
    }

    /**
     * Dispose this TextSprite Object.
     */
    public void dispose() {
        glyphTexts.clear();
        glyphTexts.trimToSize();
        strokeColor = null;
        font = null;
    }
}
