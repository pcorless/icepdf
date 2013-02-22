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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Text markup annotations shall appear as highlights, underlines, strikeouts
 * (all PDF 1.3), or jagged (“squiggly”) underlines (PDF 1.4) in the text of a
 * document. When opened, they shall display a pop-up window containing the text
 * of the associated note. Table 179 shows the annotation dictionary entries
 * specific to these types of annotations.
 *
 * @since 5.0
 */
public class TextMarkupAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(TextMarkupAnnotation.class.toString());

    public static final Name SUBTYPE_HIGHLIGHT = new Name("Highlight");
    public static final Name SUBTYPE_UNDERLINE = new Name("Underline");
    public static final Name SUBTYPE_SQUIGGLY = new Name("Squiggly");
    public static final Name SUBTYPE_STRIKE_OUT = new Name("StrikeOut");

    private static Color highlightColor;
    private static Color strikeOutColor;
    private static Color underlineColor;

    static {

        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.textmarkup.highlight.color", "#ffff00");
            int colorValue = ColorUtil.convertColor(color);
            highlightColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ffff00", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading Text Markup Annotation highlight colour");
            }
        }
        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.textmarkup.strikeOut.color", "#ff0000");
            int colorValue = ColorUtil.convertColor(color);
            strikeOutColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("ff0000", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading Text Markup Annotation strike out colour");
            }
        }
        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.textmarkup.underline.color", "#00ff00");
            int colorValue = ColorUtil.convertColor(color);
            underlineColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("00ff00", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading Text Markup Annotation underline colour");
            }
        }
    }

    /**
     * (Required) An array of 8 × n numbers specifying the coordinates of
     * n quadrilaterals in default user space. Each quadrilateral shall encompasses
     * a word or group of contiguous words in the text underlying the annotation.
     * The coordinates for each quadrilateral shall be given in the order
     * x1 y1 x2 y2 x3 y3 x4 y4
     * specifying the quadrilateral’s four vertices in counterclockwise order
     * (see Figure 64). The text shall be oriented with respect to the edge
     * connecting points (x1, y1) and (x2, y2).
     * <p/>
     * The annotation dictionary’s AP entry, if present, shall take precedence
     * over QuadPoints; see Table 168 and 12.5.5, “Appearance Streams.”
     */
    public static final Name KEY_QUAD_POINTS = new Name("QuadPoints");

    /**
     * Converted Quad points.
     */
    private Shape[] quadrilaterals;

    private Color textMarkupColor;

    private GeneralPath markupPath;
    private ArrayList<Shape> markupBounds;

    /**
     * Creates a new instance of an TextMarkupAnnotation.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public TextMarkupAnnotation(Library l, HashMap h) {
        super(l, h);
        // collect the quad points.
        List quadPoints = library.getArray(entries, KEY_QUAD_POINTS);
        if (quadPoints != null) {
            int size = quadPoints.size() / 8;
            quadrilaterals = new Shape[size];
            GeneralPath shape;
            for (int i = 0, count = 0; i < size; i++, count += 8) {
                shape = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                shape.moveTo((Float) quadPoints.get(count), (Float) quadPoints.get(count + 1));
                shape.lineTo((Float) quadPoints.get(count + 2), (Float) quadPoints.get(count + 3));
                shape.lineTo((Float) quadPoints.get(count + 4), (Float) quadPoints.get(count + 5));
                shape.lineTo((Float) quadPoints.get(count + 6), (Float) quadPoints.get(count + 7));
                shape.closePath();
                quadrilaterals[i] = shape;
            }
        }
        if (SUBTYPE_HIGHLIGHT.equals(subtype)) {
            textMarkupColor = highlightColor;
        } else if (SUBTYPE_STRIKE_OUT.equals(subtype)) {
            textMarkupColor = strikeOutColor;
        } else if (SUBTYPE_UNDERLINE.equals(subtype)) {
            textMarkupColor = underlineColor;
        } else if (SUBTYPE_SQUIGGLY.equals(subtype)) {
            // not implemented
        }

        // for editing purposes grab anny shapes from the AP Stream and
        // store them as markupBounds and markupPath. This works ok but
        // perhaps a better way would be to reapply the bound box
        if (shapes != null) {
            markupBounds = new ArrayList<Shape>();
            markupPath = new GeneralPath();

            ShapeDrawCmd shapeDrawCmd;
            for (DrawCmd cmd : shapes.getShapes()) {
                if (cmd instanceof ShapeDrawCmd) {
                    shapeDrawCmd = (ShapeDrawCmd) cmd;
                    markupBounds.add(shapeDrawCmd.getShape());
                    markupPath.append(shapeDrawCmd.getShape(), false);
                }
            }

        }

    }

    /**
     * Gets an instance of a TextMarkupAnnotation that has valid Object Reference.
     *
     * @param library         document library
     * @param rect            bounding rectangle in user space
     * @param annotationState annotation state object of undo
     * @return new TextMarkupAnnotation Instance.
     */
    public static TextMarkupAnnotation getInstance(Library library,
                                                   Rectangle rect,
                                                   final Name subType,
                                                   AnnotationState annotationState) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, subType);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        TextMarkupAnnotation textMarkupAnnotation =
                new TextMarkupAnnotation(library, entries);
        textMarkupAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        textMarkupAnnotation.setNew(true);

        // apply state
        if (annotationState != null) {
            annotationState.restore(textMarkupAnnotation);
        }
        // some defaults just for display purposes.
        else {
            annotationState = new AnnotationState(
                    Annotation.INVISIBLE_RECTANGLE,
                    LinkAnnotation.HIGHLIGHT_NONE, 1f,
                    BorderStyle.BORDER_STYLE_SOLID, Color.BLACK);
            annotationState.restore(textMarkupAnnotation);
        }
        return textMarkupAnnotation;
    }


    public static boolean isTextMarkupAnnotation(Name subType) {
        return SUBTYPE_HIGHLIGHT.equals(subType) ||
                SUBTYPE_UNDERLINE.equals(subType) ||
                SUBTYPE_SQUIGGLY.equals(subType) ||
                SUBTYPE_STRIKE_OUT.equals(subType);
    }

    public void setKeyQuadPoints(Rectangle2D bbox, GeneralPath path) {
        // todo if time permits for backwards compatibility.
    }

    /**
     * Resets the annotations appearance stream.
     */
    public void resetAppearanceStream() {
        setAppearanceStream(bbox,
                markupBounds,
                markupPath);
    }

    /**
     * Sets the shapes that make up the appearance stream that match the
     * current state of the annotation.
     *
     * @param bbox bounding box bounds.
     */
    public void setAppearanceStream(Rectangle2D bbox,
                                    ArrayList<Shape> bounds,
                                    GeneralPath path) {
        matrix = new AffineTransform();
        this.bbox = bbox;
        shapes = new Shapes();
        markupPath = path;
        markupBounds = bounds;

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
//        af.scale(1,-1);
        af.translate(-this.bbox.getMinX(), -this.bbox.getMinY());
        shapes = new Shapes();

        if (SUBTYPE_HIGHLIGHT.equals(subtype)) {
            shapes.add(new TransformDrawCmd(af));
            shapes.add(new ShapeDrawCmd(markupPath));
            shapes.add(new ColorDrawCmd(textMarkupColor));
            shapes.add(new FillDrawCmd());
        } else if (SUBTYPE_STRIKE_OUT.equals(subtype)) {
            shapes.add(new TransformDrawCmd(af));
            for (Shape shape : bounds) {
                // calculate the line that will stroke the bounds
                GeneralPath stroke = new GeneralPath();
                Rectangle2D bound = shape.getBounds2D();
                double y = bound.getMinY() + (bound.getHeight() / 2);
                stroke.moveTo(bound.getMinX(), y);
                stroke.lineTo(bound.getMaxX(), y);
                stroke.closePath();
                shapes.add(new ShapeDrawCmd(stroke));
                shapes.add(new StrokeDrawCmd(new BasicStroke(1f)));
                shapes.add(new ColorDrawCmd(textMarkupColor));
                shapes.add(new DrawDrawCmd());
            }
        } else if (SUBTYPE_UNDERLINE.equals(subtype)) {
            shapes.add(new TransformDrawCmd(af));
            for (Shape shape : bounds) {
                // calculate the line that will stroke the bounds
                GeneralPath stroke = new GeneralPath();
                Rectangle2D bound = shape.getBounds2D();
                stroke.moveTo(bound.getMinX(), bound.getMinY());
                stroke.lineTo(bound.getMaxX(), bound.getMinY());
                stroke.closePath();
                shapes.add(new ShapeDrawCmd(stroke));
                shapes.add(new ColorDrawCmd(textMarkupColor));
                shapes.add(new DrawDrawCmd());
            }
        } else if (SUBTYPE_SQUIGGLY.equals(subtype)) {
            // not implemented,  need to create a custom stroke or
            // build out a costome line move.
        }
    }


    @Override
    protected void renderAppearanceStream(Graphics2D g) {
        // check to see if we are painting highlight annotations.
        // if so we add some transparency to the context.
        if (subtype != null && SUBTYPE_HIGHLIGHT.equals(subtype)) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .30f));
            // remove other alpha defs from painting
            if (shapes != null) {
                shapes.setPaintAlpha(false);
            }
        }

        // Appearance stream takes precedence over the quad points.
        if (shapes != null) {
            super.renderAppearanceStream(g);
        }
        // draw the quad points.
        else if (quadrilaterals != null) {

            Object tmp = getObject(RECTANGLE_KEY);
            Rectangle2D.Float rectangle = null;
            if (tmp instanceof List) {
                rectangle = library.getRectangle(entries, RECTANGLE_KEY);
            }

            // get the current position of the userspaceRectangle
            Rectangle2D.Float origRect = getUserSpaceRectangle();
            // build the transform to go back to users space
            AffineTransform af = g.getTransform();
            double x = rectangle.getX() - origRect.getX();
            double y = rectangle.getY() - origRect.getY();
            af.translate(-origRect.getX(), -origRect.getY());
            g.setTransform(af);
            g.setColor(highlightColor);
            AffineTransform af2 = new AffineTransform();
            af2.translate(-x, -y);
            for (Shape shape : quadrilaterals) {
                g.fill(af2.createTransformedShape(shape));
            }
        }

        // revert the alpha value.
        if (subtype != null && SUBTYPE_HIGHLIGHT.equals(subtype)) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            // remove other alpha defs from painting
            if (shapes != null) {
                shapes.setPaintAlpha(true);
            }
        }

    }

    public Color getTextMarkupColor() {
        return textMarkupColor;
    }

    public void setTextMarkupColor(Color textMarkupColor) {
        this.textMarkupColor = textMarkupColor;
    }

    public void setSubtype(Name subtype) {
        this.subtype = subtype;
    }


}
