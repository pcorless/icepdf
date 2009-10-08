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

import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.Rectangle2D;

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

    // private Vector textObjects;
    private float[][] textObjects;
    private int count;
    // stroke color
    private Color strokeColor;
    // the write
    private int rmode;
    // Font used to paint text
    private FontFile font;

    // string bounds
    Rectangle2D.Float bounds;

    /**
     * <p>Creates a new TextSprit object.</p>
     *
     * @param font font used when painting glyphs.
     * @param size size of the font in user space
     */
    public TextSprite(FontFile font, int size) {
        textObjects = new float[size][3];
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
    public void addText(char character, float x, float y, float width) {
        textObjects[count][0] = x;
        textObjects[count][1] = y;
        textObjects[count][2] = (int) character;

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
        // todo: for reliable text selection we have to calculate bounds based
        // on individual bounds data.
        if (h <= 0.0f) {
            h = 1.0f;
        }
        if (w <= 0.0f) {
            w = 1.0f;
        }
        bounds.add(new Rectangle2D.Float(x, y - (float) font.getAscent(), w, h));

        count++;
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
        StringBuffer text = new StringBuffer(textObjects.length);
        for (int i = 0, max = textObjects.length; i < max; i++) {
            text.append(textObjects[i][2]);
        }
        return text.toString();
    }

    public void setStrokeColor(Color color) {
        strokeColor = color;
    }

    /**
     * <p>Gets the list of textObjects.</p>
     *
     * @return list of all CharSprites contained in this TextSprite.
     */
    public float[][] getTextObjects() {
        return textObjects;
    }

    /**
     * <p>Gets the size value in user space associated with this TextSprite.</p>
     *
     * @return size of font in user space.
     */
    public int getSize() {
        return textObjects.length;
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

        for (int i = textObjects.length - 1; i >= 0; i--) {

            // draw glyph box
//            drawGyphBox(g2d, (char)textObjects[i][2],
//                        textObjects[i][0], textObjects[i][1],
//                        oldGraphics);

            // draw glyph
            font.drawEstring(g2d,
                    String.valueOf((char) textObjects[i][2]),
                    textObjects[i][0],
                    textObjects[i][1],
                    FontFile.LAYOUT_NONE, rmode, strokeColor);
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

    private void drawGyphBox(Graphics2D gg, char glyph, float x, float y,
                             AffineTransform transform) {
        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();

        // draw the characters
        Rectangle2D stringBounds;
        GeneralPath charOutline;

        // set graphics context
        AffineTransform oldGraphics = null;

        double scale = gg.getTransform().getScaleX();

        scale = 1.0f / scale;
        if (scale <= 0) {
            scale = 1;
        }
        gg.setStroke(new BasicStroke((float) (scale)));
        gg.setColor(Color.red);


//        stringBounds = font.getCharBounds(glyph);
        stringBounds = font.getMaxCharBounds();

        float h = (float) stringBounds.getHeight();
        float w = (float) stringBounds.getWidth();

        charOutline = new GeneralPath();
        charOutline.moveTo(x, y);
        charOutline.lineTo(x + w, y);
        charOutline.lineTo(x + w, y + h);
        charOutline.lineTo(x, y + h);
        charOutline.lineTo(x, y);

        gg.draw(charOutline);

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);

    }  */

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
        textObjects = null;
        strokeColor = null;
        font = null;
    }
}
