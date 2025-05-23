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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * <p>This class represents text which will be rendered to the a graphics context.
 * This class was created to act as a wrapper for painting text using the Phelphs
 * font library as well as painting using java.awt.Font.</p>
 * <br>
 * <p>Objects of this type are created by the content parser when "TJ" or "Tj"
 * operands are encountered in a page's content stream.  Each TextSprite object
 * is comprised of a list of CharSprites which can be painted by the Shapes
 * class at render time.</p>
 *
 * @since 2.0
 */
public class TextSprite {

    // ability to turn off optimized drawing for text.
    private static boolean optimizedDrawingEnabled =
            Defs.booleanProperty("org.icepdf.core.text.optimized", true);

    private final static boolean OPTIMIZED_DRAWING_TYPE_3_ENABLED =
            Defs.booleanProperty("org.icepdf.core.text.optimized.type3", true);

    // child GlyphText objects
    private final ArrayList<GlyphText> glyphTexts;

    private final byte subTypeFormat;

    // text bounds, including all child Glyph sprites, in glyph space
    // this bound is used during painting to respect painting clip.
    final Rectangle2D.Float bounds;

    // space reference for where glyph
    private AffineTransform graphicStateTransform;
    private final AffineTransform tmTransform;

    // stroke color
    private Color strokeColor;
    // the write
    private int rmode;
    // Font used to paint text
    private FontFile font;
    // font's resource name and size, used by PS writer.
    private String fontName;
    private int fontSize;

    private static final String TYPE_3 = "Type3";

    /**
     * <p>Creates a new TextSprite object.</p>
     *
     * @param font                  font used when painting glyphs.
     * @param subTypeFormat         font type format
     * @param contentLength         length of text content.
     * @param graphicStateTransform ctm transform.
     * @param tmTransform           text transform form postScript.
     */
    public TextSprite(FontFile font, byte subTypeFormat, int contentLength, AffineTransform graphicStateTransform,
                      AffineTransform tmTransform) {
        glyphTexts = new ArrayList<>(contentLength);
        this.subTypeFormat = subTypeFormat;
        // all glyphs in text share this ctm
        this.graphicStateTransform = graphicStateTransform;
        this.tmTransform = tmTransform;
        this.font = font;
        if (optimizedDrawingEnabled && !OPTIMIZED_DRAWING_TYPE_3_ENABLED) {
            optimizedDrawingEnabled = !(font.getFormat() != null && font.getFormat().equals(TYPE_3));
        }
        bounds = new Rectangle2D.Float();
    }

    /**
     * <p>Adds a new text char to the TextSprite which will pe painted at x, y under
     * the current CTM</p>
     *
     * @param cid     cid to paint.
     * @param fontName name of associated font.
     * @param unicode unicode representation of cid.
     * @param x       x-coordinate to paint.
     * @param y       y-coordinate to paint.
     * @param width   width of cid from font.
     * @param height  height of cid from font.
     * @return new GlyphText object containing the text data.
     */
    public GlyphText addText(char cid, Name fontName, String unicode, float x, float y, float width, float height,
                             float pageRotation) {
        // x,y must not change as it will affect painting of the glyph,
        // we can change the bounds of glyphBounds as this is what needs to be normalized
        // to page space
        // IMPORTANT: where working in Java Coordinates with any of the Font bounds
        float ascent = (float) font.getAscent();
        float descent = (float) font.getDescent();
        float w = width;
        float h = ascent - descent;
        // width/height are kept unscaled for coords, w/h are scaled to get correct bounds w/h
        h = Math.abs(h);

        // zero height will not intersect with clip rectangle and maybe have visibility issues.
        // we generally get here if the font.getAscent is zero and as a result must compensate.
        if (h == 0.0f) {
            Rectangle2D bounds = font.getBounds(cid, 0, 1);
            if (bounds != null && bounds.getHeight() > 0) {
                h = (float) bounds.getHeight();
            } else {
                // match the width, as it will make text selection work a bit better.
                h = font.getSize();
            }
        }
        // can't have Rectangle2D with negative w or h, api will zero the bounds.
        w = Math.abs(w);
        // this is still terrible, should be applying the fontTransform but this little hack is fast until I can
        // figure out the geometry for the corner cases.
        Rectangle2D.Double glyphBounds;
        // negative layout
        if (width < 0.0f || font.getSize() < 0) {
            glyphBounds = new Rectangle2D.Double(x + width, y - descent, w, h);
        }
        // standard layout.
        else {
            glyphBounds = new Rectangle2D.Double(x, y - ascent, w, h);
        }

        // add bounds to total text bounds.
        bounds.add(glyphBounds);

        // create glyph and normalize bounds.
        GlyphText glyphText =
                new GlyphText(x, y, width, height, glyphBounds, pageRotation, cid, unicode, fontName);
        glyphText.normalizeToUserSpace(graphicStateTransform, tmTransform);
        glyphText.setFontSubTypeFormat(subTypeFormat);
        glyphTexts.add(glyphText);
        return glyphText;
    }

    /**
     * Gets the character bounds of each glyph found in the TextSprite.
     *
     * @return bounds in PDF coordinates of character bounds
     */
    public ArrayList<GlyphText> getGlyphSprites() {
        return glyphTexts;
    }

    public AffineTransform getGraphicStateTransform() {
        return graphicStateTransform;
    }

    /**
     * Set the graphic state transform on all child sprites, This is used for
     * xForm object parsing and text selection.  There is no need to do this
     * outside of the context parser.
     *
     * @param graphicStateTransform graphics state transform for the xForm.
     */
    public void setGraphicStateTransform(AffineTransform graphicStateTransform) {
        this.graphicStateTransform = graphicStateTransform;
        for (GlyphText sprite : glyphTexts) {
            sprite.normalizeToUserSpace(this.graphicStateTransform, tmTransform);
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
        for (GlyphText glyphText : glyphTexts) {
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
     *
     * @return text sprites bounds.
     */
    public Rectangle2D.Float getBounds() {
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

        for (GlyphText glyphText : glyphTexts) {

            // paint glyph
            font.paint(g2d,
                    glyphText.getCid(),
                    glyphText.getX(), glyphText.getY(),
                    FontFile.LAYOUT_NONE, rmode, strokeColor);

            // debug glyph box
            // draw glyph box
//            drawGyphBox(g2d, glyphText);
        }
    }

    /**
     * Gets the glyph outline as an Area.  This method is primarily used
     * for processing text rendering modes 4 - 7.
     *
     * @return area representing the glyph outline.
     */
    public Area getGlyphOutline() {
        Area glyphOutline = null;
        for (GlyphText glyphText : glyphTexts) {
            if (glyphOutline != null) {
                glyphOutline.add(new Area(font.getOutline(
                        glyphText.getCid(),
                        glyphText.getX(), glyphText.getY())));
            } else {
                glyphOutline = new Area(font.getOutline(
                        glyphText.getCid(),
                        glyphText.getX(), glyphText.getY()));
            }
        }
        return glyphOutline;
    }

    public FontFile getFont() {
        return font;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public byte getSubTypeFormat() {
        return subTypeFormat;
    }

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

        float[] dashPattern = {(float) (10 * scale), (float) (5 * scale)}; // 10 pixels on, 10 pixels off
        gg.setStroke(new BasicStroke((float) (scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern,
                0));
        gg.setColor(Color.LIGHT_GRAY);

        charOutline = new GeneralPath(bounds);
        gg.draw(charOutline);

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);
    }

    public void setFont(FontFile font) {
        this.font = font;
    }

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

        charOutline = new GeneralPath(glyphSprite.getBounds());
        gg.draw(charOutline);

        gg.setColor(oldColor);
        gg.setStroke(oldStroke);

    }

    /**
     * Tests if the interior of the <code>TextSprite</code> bounds intersects the
     * interior of a specified <code>shape</code>.
     *
     * @param shape shape to calculate intersection against
     * @return true, if <code>TextSprite</code> bounds intersects <code>shape</code>;
     * otherwise; false.
     */
    public boolean intersects(Shape shape) {
//        return shape.intersects(bounds.toJava2dCoordinates());
        return !(optimizedDrawingEnabled) ||
                (shape != null && shape.intersects(bounds));
    }
}
