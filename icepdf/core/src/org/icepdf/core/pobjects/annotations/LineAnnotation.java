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
import org.icepdf.core.util.Library;
import org.icepdf.core.views.common.LineArrowAnnotationHandler;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * The purpose of a line annotation (PDF 1.3) is to display a single straight
 * line on the page. When opened, it shall display a pop-up window containing
 * the text of the associated note. Table 175 shows the annotation dictionary
 * entries specific to this type of annotation.
 *
 * @since 5.0
 */
public class LineAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(LineAnnotation.class.toString());

    /**
     * (Required) An array of four numbers, [x1 y1 x2 y2], specifying the starting
     * and ending coordinates of the line in default user space.
     * <p/>
     * If the LL entry is present, this value shall represent the endpoints of
     * the leader lines rather than the endpoints of the line itself; see Figure 60.
     */
    public static final Name L_KEY = new Name("L");

    /**
     * (Optional; PDF 1.4) An array of two names specifying the line ending styles
     * that shall be used in drawing the line. The first and second elements of
     * the array shall specify the line ending styles for the endpoints defined,
     * respectively, by the first and second pairs of coordinates, (x1, y1) and
     * (x2, y2), in the L array. Table 176 shows the possible values.
     * <p/>
     * Default value: [/None /None].
     */
    public static final Name LE_KEY = new Name("LE");

    /**
     * (Required if LLE is present, otherwise optional; PDF 1.6) The length of
     * leader lines in default user space that extend from each endpoint of the
     * line perpendicular to the line itself, as shown in Figure 60. A positive
     * value shall mean that the leader lines appear in the direction that is
     * clockwise when traversing the line from its starting point to its ending
     * point (as specified by L); a negative value shall indicate the opposite
     * direction.
     * <p/>
     * Default value: 0 (no leader lines).
     */
    public static final Name LL_KEY = new Name("LL");

    /**
     * (Optional; PDF 1.6) A non-negative number that shall represents the
     * length of leader line extensions that extend from the line proper 180
     * degrees from the leader lines, as shown in Figure 60.
     * <p/>
     * Default value: 0 (no leader line extensions).
     */
    public static final Name LLE_KEY = new Name("LLE");

    /**
     * (Optional; PDF 1.4) An array of numbers in the range 0.0 to 1.0 specifying
     * the interior color that shall be used to fill the annotation’s line endings
     * (see Table 176). The number of array elements shall determine the colour
     * space in which the colour is defined:
     * 0 - No colour; transparent
     * 1 - DeviceGray
     * 3 - DeviceRGB
     * 4 - DeviceCMYK
     */
    public static final Name IC_KEY = new Name("IC");

    /**
     * (Optional; PDF 1.6) If true, the text specified by the Contents or RC
     * entries shall be replicated as a caption in the appearance of the line,
     * as shown in Figure 61 and Figure 62. The text shall be rendered in a
     * manner appropriate to the content, taking into account factors such as
     * writing direction.
     * <p/>
     * Default value: false.
     */
    public static final Name CAP_KEY = new Name("Cap");

    /**
     * (Optional; PDF 1.6) A name describing the intent of the line annotation
     * (see also Table 170). Valid values shall be LineArrow, which means that
     * the annotation is intended to function as an arrow, and LineDimension,
     * which means that the annotation is intended to function as a dimension line.
     */
//    public static final Name IT_KEY = new Name("IT");

    /**
     * (Optional; PDF 1.7) A non-negative number that shall represent the length
     * of the leader line offset, which is the amount of empty space between the
     * endpoints of the annotation and the beginning of the leader lines.
     */
    public static final Name LLO_KEY = new Name("LLO");


    /**
     * (Optional; meaningful only if Cap is true; PDF 1.7) A name describing the
     * annotation’s caption positioning. Valid values are Inline, meaning the
     * caption shall be centered inside the line, and Top, meaning the caption
     * shall be on top of the line.
     * <p/>
     * Default value: Inline
     */
    public static final Name CP_KEY = new Name("CP");

    /**
     * (Optional; PDF 1.7) A measure dictionary (see Table 261) that shall
     * specify the scale and units that apply to the line annotation.
     */
    public static final Name MEASURE_KEY = new Name("Measure");

    /**
     * (Optional; meaningful only if Cap is true; PDF 1.7) An array of two numbers
     * that shall specify the offset of the caption text from its normal position.
     * The first value shall be the horizontal offset along the annotation line
     * from its midpoint, with a positive value indicating offset to the right
     * and a negative value indicating offset to the left. The second value shall
     * be the vertical offset perpendicular to the annotation line, with a
     * positive value indicating a shift up and a negative value indicating a
     * shift down.
     * <p/>
     * Default value: [0, 0] (no offset from normal positioning)
     */
    public static final Name CO_KEY = new Name("CO");

    /**
     * A square filled with the annotation’s interior color, if any
     */
    public static final Name LINE_END_NONE = new Name("None");

    /**
     * A circle filled with the annotation’s interior color, if any
     */
    public static final Name LINE_END_SQUARE = new Name("Square");

    /**
     * A diamond shape filled with the annotation’s interior color, if any
     */
    public static final Name LINE_END_CIRCLE = new Name("Circle");

    /**
     * Two short lines meeting in an acute angle to form an open arrowhead
     */
    public static final Name LINE_END_DIAMOND = new Name("Diamond");

    /**
     * Two short lines meeting in an acute angle as in the OpenArrow style and
     * connected by a third line to form a triangular closed arrowhead filled
     * with the annotation’s interior color, if any
     */
    public static final Name LINE_END_OPEN_ARROW = new Name("OpenArrow");

    /**
     * No line ending
     */
    public static final Name LINE_END_CLOSED_ARROW = new Name("ClosedArrow");

    protected Point2D startOfLine;
    protected Point2D endOfLine;
    protected Color interiorColor;

    // default line caps.
    protected Name startArrow = LINE_END_NONE;
    protected Name endArrow = LINE_END_NONE;

    public LineAnnotation(Library l, HashMap h) {
        super(l, h);

        // line points
        List value = library.getArray(entries, L_KEY);
        if (value != null) {
            startOfLine = new Point2D.Float((Float) value.get(0), (Float) value.get(1));
            endOfLine = new Point2D.Float((Float) value.get(2), (Float) value.get(3));
        }

        // line border style
        HashMap BS = (HashMap) getObject(BORDER_STYLE_KEY);
        if (BS != null) {
            borderStyle = new BorderStyle(library, BS);
        } else {
            borderStyle = new BorderStyle(library, new HashMap());
        }

        // line ends.
        value = library.getArray(entries, LE_KEY);
        if (value != null) {
            startArrow = (Name) value.get(0);
            endArrow = (Name) value.get(1);
        }

        // parse out interior colour, specific to link annotations.
        interiorColor = Color.black; // we default to black but probably should be null
        List C = (List) getObject(IC_KEY);
        // parse thought rgb colour.
        if (C != null && C.size() >= 3) {
            float red = ((Number) C.get(0)).floatValue();
            float green = ((Number) C.get(1)).floatValue();
            float blue = ((Number) C.get(2)).floatValue();
            red = Math.max(0.0f, Math.min(1.0f, red));
            green = Math.max(0.0f, Math.min(1.0f, green));
            blue = Math.max(0.0f, Math.min(1.0f, blue));
            interiorColor = new Color(red, green, blue);
        }
    }

    /**
     * Gets an instance of a LineAnnotation that has valid Object Reference.
     *
     * @param library         document library
     * @param rect            bounding rectangle in user space
     * @param annotationState annotation state object of undo
     * @return new LineAnnotation Instance.
     */
    public static LineAnnotation getInstance(Library library,
                                             Rectangle rect,
                                             AnnotationState annotationState) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_LINE);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        LineAnnotation lineAnnotation = new LineAnnotation(library, entries);
        lineAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        lineAnnotation.setNew(true);

        // apply state
        if (annotationState != null) {
            annotationState.restore(lineAnnotation);
        }
        // some defaults just for display purposes.
        else {
            annotationState = new AnnotationState(
                    Annotation.INVISIBLE_RECTANGLE,
                    LinkAnnotation.HIGHLIGHT_INVERT, 1f,
                    BorderStyle.BORDER_STYLE_SOLID, Color.RED);
            annotationState.restore(lineAnnotation);
        }
        return lineAnnotation;
    }

    /**
     * Resets the annotations appearance stream.
     */
    public void resetAppearanceStream() {
        setAppearanceStream(bbox.getBounds());
    }

    /**
     * Sets the shapes that make up the appearance stream that match the
     * current state of the annotation.
     *
     * @param bbox bounding box bounds.
     */
    public void setAppearanceStream(Rectangle bbox) {

        BasicStroke stroke;
        if (borderStyle.isStyleDashed()) {
            stroke = new BasicStroke(
                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, borderStyle.getDashArray(), 0.0f);
        } else {
            stroke = new BasicStroke(borderStyle.getStrokeWidth());
        }

        matrix = new AffineTransform();
        this.bbox = bbox;
        shapes = new Shapes();

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.translate(-this.bbox.getMinX(), -this.bbox.getMinY());

        // draw the basic line.
        GeneralPath line = new GeneralPath();
        line.moveTo((float) startOfLine.getX(), (float) startOfLine.getY());
        line.lineTo((float) endOfLine.getX(), (float) endOfLine.getY());
        line.closePath();
        shapes.add(new TransformDrawCmd(af));
        shapes.add(new ShapeDrawCmd(line));
        shapes.add(new StrokeDrawCmd(stroke));
        shapes.add(new ColorDrawCmd(color));
        shapes.add(new DrawDrawCmd());

        // check for a ending end cap.
        if (startArrow.equals(LineAnnotation.LINE_END_OPEN_ARROW)) {
            LineArrowAnnotationHandler.openArrowStartDrawOps(
                    shapes, af, startOfLine, endOfLine, color, interiorColor);
        } else if (startArrow.equals(LineAnnotation.LINE_END_CLOSED_ARROW)) {
            LineArrowAnnotationHandler.closedArrowStartDrawOps(
                    shapes, af, startOfLine, endOfLine, color, interiorColor);
        } else if (startArrow.equals(LineAnnotation.LINE_END_CIRCLE)) {
            LineArrowAnnotationHandler.circleDrawOps(
                    shapes, af, startOfLine, startOfLine, endOfLine, color, interiorColor);
        } else if (startArrow.equals(LineAnnotation.LINE_END_DIAMOND)) {
            LineArrowAnnotationHandler.diamondDrawOps(
                    shapes, af, startOfLine, startOfLine, endOfLine, color, interiorColor);
        } else if (startArrow.equals(LineAnnotation.LINE_END_SQUARE)) {
            LineArrowAnnotationHandler.squareDrawOps(
                    shapes, af, startOfLine, startOfLine, endOfLine, color, interiorColor);
        }
        // check for a starting end cap.
        if (endArrow.equals(LineAnnotation.LINE_END_OPEN_ARROW)) {
            LineArrowAnnotationHandler.openArrowEndDrawOps(
                    shapes, af, startOfLine, endOfLine, color, interiorColor);
        } else if (endArrow.equals(LineAnnotation.LINE_END_CLOSED_ARROW)) {
            LineArrowAnnotationHandler.closedArrowEndDrawOps(
                    shapes, af, startOfLine, endOfLine, color, interiorColor);
        } else if (endArrow.equals(LineAnnotation.LINE_END_CIRCLE)) {
            LineArrowAnnotationHandler.circleDrawOps(
                    shapes, af, endOfLine, startOfLine, endOfLine, color, interiorColor);
        } else if (endArrow.equals(LineAnnotation.LINE_END_DIAMOND)) {
            LineArrowAnnotationHandler.diamondDrawOps(
                    shapes, af, endOfLine, startOfLine, endOfLine, color, interiorColor);
        } else if (endArrow.equals(LineAnnotation.LINE_END_SQUARE)) {
            LineArrowAnnotationHandler.squareDrawOps(
                    shapes, af, endOfLine, startOfLine, endOfLine, color, interiorColor);
        }
    }

    public static Logger getLogger() {
        return logger;
    }

    public Point2D getStartOfLine() {
        return startOfLine;
    }

    public Point2D getEndOfLine() {
        return endOfLine;
    }

    public Color getInteriorColor() {
        return interiorColor;
    }

    public Name getStartArrow() {
        return startArrow;
    }

    public Name getEndArrow() {
        return endArrow;
    }

    public void setStartOfLine(Point2D startOfLine) {
        this.startOfLine = startOfLine;
    }

    public void setEndArrow(Name endArrow) {
        this.endArrow = endArrow;
    }

    public void setStartArrow(Name startArrow) {
        this.startArrow = startArrow;
    }

    public void setInteriorColor(Color interiorColor) {
        this.interiorColor = interiorColor;
    }

    public void setEndOfLine(Point2D endOfLine) {
        this.endOfLine = endOfLine;
    }
}
