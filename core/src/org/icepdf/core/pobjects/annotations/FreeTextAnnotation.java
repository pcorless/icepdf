/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.ofont.OFont;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.TextState;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;

import javax.swing.text.DefaultStyledDocument;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * A free text annotation (PDF 1.3) displays text directly on the page. Unlike
 * an ordinary text annotation (see 12.5.6.4, “Text Annotations”), a free text
 * annotation has no open or closed state; instead of being displayed in a pop-up
 * window, the text shall be always visible. Table 174 shows the annotation
 * dictionary entries specific to this type of annotation. 12.7.3.3,
 * “Variable Text” describes the process of using these entries to generate the
 * appearance of the text in these annotations.
 *
 * @since 5.0
 */
public class FreeTextAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(FreeTextAnnotation.class.toString());

    /**
     * (Required) The default appearance string that shall be used in formatting
     * the text (see 12.7.3.3, “Variable Text”).
     * <p/>
     * The annotation dictionary’s AP entry, if present, shall take precedence
     * over the DA entry; see Table 168 and 12.5.5, “Appearance Streams.”
     */
    public static final Name DA_KEY = new Name("DA");

    /**
     * (Optional; PDF 1.4) A code specifying the form of quadding
     * (justification) that shall be used in displaying the annotation’s text:
     * 0 - Left-justified
     * 1 - Centered
     * 2 - Right-justified
     * Default value: 0 (left-justified).
     */
    public static final Name Q_KEY = new Name("Q");


    /**
     * (Optional; PDF 1.5) A default style string, as described in 12.7.3.4,
     * “Rich Text Strings.”
     */
    public static final Name DS_KEY = new Name("DS");

    /**
     * (Optional; meaningful only if IT is FreeTextCallout; PDF 1.6) An array of
     * four or six numbers specifying a callout line attached to the free text
     * annotation. Six numbers [ x1 y1 x2 y2 x3 y3 ] represent the starting,
     * knee point, and ending coordinates of the line in default user space, as
     * shown in Figure 8.4. Four numbers [ x1 y1 x2 y2 ] represent the starting
     * and ending coordinates of the line.
     */
    public static final Name CL_KEY = new Name("CL");

    /**
     * (Optional; PDF 1.6) A name describing the intent of the free text
     * annotation (see also the IT entry in Table 170). The following values
     * shall be valid:
     * <p/>
     * FreeTextThe annotation is intended to function as a plain free-text
     * annotation. A plain free-text annotation is also known as a text box comment.
     * FreeTextCallout The annotation is intended to function as a callout. The
     * callout is associated with an area on the page through the callout line
     * specified in CL.
     * <p/>
     * FreeTextTypeWriterThe annotation is intended to function as a click-to-type
     * or typewriter object and no callout line is drawn.
     * Default value: FreeText
     */
//    public static final Name IT_KEY = new Name("IT");

    /**
     * (Optional; PDF 1.6) A border effect dictionary (see Table 167) used in
     * conjunction with the border style dictionary specified by the BS entry.
     */
    public static final Name BE_KEY = new Name("BE");

    /**
     * (Optional; PDF 1.6) A set of four numbers describing the numerical
     * differences between two rectangles: the Rect entry of the annotation and
     * a rectangle contained within that rectangle. The inner rectangle is where
     * the annotation’s text should be displayed. Any border styles and/or border
     * effects specified by BS and BE entries, respectively, shall be applied to
     * the border of the inner rectangle.
     * <p/>
     * The four numbers correspond to the differences in default user space
     * between the left, top, right, and bottom coordinates of Rect and those
     * of the inner rectangle, respectively. Each value shall be greater than
     * or equal to 0. The sum of the top and bottom differences shall be less
     * than the height of Rect, and the sum of the left and right differences
     * shall be less than the width of Rect.
     */
    public static final Name RD_KEY = new Name("RD");

    /**
     * (Optional; PDF 1.6) A border style dictionary (see Table 166) specifying
     * the line width and dash pattern that shall be used in drawing the
     * annotation’s border.
     * <p/>
     * The annotation dictionary’s AP entry, if present, takes precedence over
     * the BS entry; see Table 164 and 12.5.5, “Appearance Streams”.
     */
    public static final Name BS_KEY = new Name("BS");

    /**
     * (Optional; meaningful only if CL is present; PDF 1.6) A name specifying
     * the line ending style that shall be used in drawing the callout line
     * specified in CL. The name shall specify the line ending style for the
     * endpoint defined by the pairs of coordinates (x1, y1). Table 176 shows
     * the possible line ending styles.
     * <p/>
     * Default value: None.
     */
    public static final Name LE_KEY = new Name("LE");

    /**
     * Left-justified quadding
     */
    public static final int QUADDING_LEFT_JUSTIFIED = 0;

    /**
     * Right-justified quadding
     */
    public static final int QUADDING_CENTER_JUSTIFIED = 1;

    /**
     * Center-justified quadding
     */
    public static final int QUADDING_RIGHT_JUSTIFIED = 2;

    protected String defaultAppearance;

    protected int quadding = QUADDING_LEFT_JUSTIFIED;

    protected String defaultStylingString;
    protected boolean hideRenderedOutput;

    protected String richText;

    // appearance properties not to be confused with annotation properties,
    // this properties are updated by the UI components and used to regenerate
    // the annotations appearance stream and other needed properties on edits.
    private String fontName = "Dialog";
    private int fontStyle = Font.PLAIN;
    private int fontSize = 24;
    private Color fontColor = Color.DARK_GRAY;
    // fill
    private boolean fillType = false;
    private Color fillColor = Color.WHITE;
    // stroke
    private boolean strokeType = false;

    // editing placeholder
    protected DefaultStyledDocument document;

    public FreeTextAnnotation(Library l, HashMap h) {
        super(l, h);

        if (matrix == null) {
            matrix = new AffineTransform();
        }

        defaultAppearance = library.getString(entries, DA_KEY);

        if (library.getObject(entries, Q_KEY) != null) {
            quadding = library.getInt(entries, Q_KEY);
        }
        // find rich text string
        Object tmp = library.getObject(entries, RC_KEY);
        if (tmp != null && tmp instanceof StringObject) {
            StringObject tmpRichText = (StringObject) tmp;
            richText = tmpRichText.getLiteralString();
        }
        // border style
        HashMap BS = (HashMap) getObject(BORDER_STYLE_KEY);
        if (BS != null) {
            borderStyle = new BorderStyle(library, BS);
        } else {
            borderStyle = new BorderStyle(library, new HashMap());
        }

        // default style string
        if (library.getObject(entries, DS_KEY) != null) {
            defaultStylingString = library.getString(entries, DS_KEY);
        }
    }

    /**
     * Gets an instance of a FreeTextAnnotation that has valid Object Reference.
     *
     * @param library         document library
     * @param rect            bounding rectangle in user space
     * @param annotationState annotation state object of undo
     * @return new FreeTextAnnotation Instance.
     */
    public static FreeTextAnnotation getInstance(Library library,
                                                 Rectangle rect,
                                                 AnnotationState annotationState) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_FREE_TEXT);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        FreeTextAnnotation freeTextAnnotation = new FreeTextAnnotation(library, entries);
        freeTextAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        freeTextAnnotation.setNew(true);

        // apply state
        if (annotationState != null) {
            annotationState.restore(freeTextAnnotation);
        }
        // some defaults just for display purposes.
        else {
            annotationState = new AnnotationState(
                    Annotation.INVISIBLE_RECTANGLE,
                    LinkAnnotation.HIGHLIGHT_INVERT, 1f,
                    BorderStyle.BORDER_STYLE_SOLID, Color.RED);
            annotationState.restore(freeTextAnnotation);
        }
        return freeTextAnnotation;
    }

    @Override
    public void render(Graphics2D origG, int renderHintType, float totalRotation, float userZoom, boolean tabSelected) {
        // suspend the rendering when the UI tools are present.
        if (!hideRenderedOutput) {
            super.render(origG, renderHintType, totalRotation, userZoom, tabSelected);
        }
    }

    /**
     * Sets the shapes that make up the appearance stream that match the
     * current state of the annotation.
     *
     * @param bbox bounding box bounds.
     */
    public void setAppearanceStream(Rectangle bbox) {

        matrix = new AffineTransform();
        this.bbox = new Rectangle();
        this.bbox.setRect(bbox);
        if (shapes == null) {
            shapes = new Shapes();
        }

        // remove any previous text
        this.shapes.getShapes().clear();

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.scale(1, -1);
        af.translate(-this.bbox.getMinX(),
                -this.bbox.getMaxY());

        shapes.add(new TransformDrawCmd(af));

        // create the new font to draw with
        Font awtFont = new Font(fontName, fontStyle, fontSize);
        OFont font = new OFont(awtFont);
        // init font's metrics
        font.echarAdvance(' ');
        TextSprite textSprites =
                new TextSprite(font,
                        content.length(),
                        new AffineTransform(new AffineTransform()));
        textSprites.setRMode(TextState.MODE_FILL);
        textSprites.setStrokeColor(fontColor);

        // iterate over each line of text painting the strings.
        String[] lines = content.split("[\\r\\n]+");

        float padding = 10; // border padding of the component
        float lineHeight = (float) font.getDescent() +
                (float) (font.getAscent());

        float advanceX = (float) bbox.getMinX() + padding;
        float advanceY = (float) bbox.getMinY() + padding;

        float currentX;
        float currentY = advanceY + lineHeight;

        float lastx = 0;

        for (String line : lines) {
            char currentChar;
            // glyph placement params
            float newAdvanceX;
            GlyphText glyphText = null;
            for (int i = 0; i < line.length(); i++) {
                currentChar = line.charAt(i);

                newAdvanceX = (float) font.echarAdvance(currentChar).getX();
                currentX = advanceX + lastx;
                lastx += newAdvanceX;

                // get normalized from from text sprite
                glyphText = textSprites.addText(
                        String.valueOf(currentChar), // cid
                        String.valueOf(currentChar), // unicode value
                        currentX, currentY, newAdvanceX);
            }

            if (glyphText != null) {
                currentY += lineHeight;
                advanceX = (float) bbox.getMinX() + padding;
                lastx = 0;
            }
        }
        BasicStroke stroke;
        if (borderStyle.isStyleDashed()) {
            stroke = new BasicStroke(
                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, borderStyle.getDashArray(), 0.0f);
        } else {
            stroke = new BasicStroke(borderStyle.getStrokeWidth());
        }

        // background colour
        shapes.add(new ShapeDrawCmd(bbox));
        if (fillType) {
            shapes.add(new ColorDrawCmd(fillColor));
            shapes.add(new FillDrawCmd());
        }
        // border
        if (strokeType) {
            shapes.add(new StrokeDrawCmd(stroke));
            shapes.add(new ColorDrawCmd(color));
            shapes.add(new DrawDrawCmd());
        }
        // actual font.
        shapes.add(new ColorDrawCmd(fontColor));
        shapes.add(new TextSpriteDrawCmd(textSprites));
    }

    public String getDefaultStylingString() {
        return defaultStylingString;
    }

    public void clearShapes() {
        shapes = null;
    }

    public void setDocument(DefaultStyledDocument document) {
        this.document = document;
    }

    public boolean isHideRenderedOutput() {
        return hideRenderedOutput;
    }

    // print. Consider making it static.
    public void setHideRenderedOutput(boolean hideRenderedOutput) {
        this.hideRenderedOutput = hideRenderedOutput;
    }

    public String getDefaultAppearance() {
        return defaultAppearance;
    }

    public void setDefaultAppearance(String defaultAppearance) {
        this.defaultAppearance = defaultAppearance;
    }

    public int getQuadding() {
        return quadding;
    }

    public void setQuadding(int quadding) {
        this.quadding = quadding;
    }

    public String getRichText() {
        return richText;
    }

    public void setRichText(String richText) {
        this.richText = richText;
    }


    public Color getFontColor() {
        return fontColor;
    }

    public void setFontColor(Color fontColor) {
        this.fontColor = fontColor;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public int getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(int fontStyle) {
        this.fontStyle = fontStyle;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isFillType() {
        return fillType;
    }

    public void setFillType(boolean fillType) {
        this.fillType = fillType;
    }

    public boolean isStrokeType() {
        return strokeType;
    }

    public void setStrokeType(boolean strokeType) {
        this.strokeType = strokeType;
    }
}
