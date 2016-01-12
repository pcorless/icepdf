/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.*;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.logging.Logger;

/**
 * Square annotations (PDF 1.3) shall display, respectively, a
 * rectangle or an ellipse on the page. When opened, they shall display a
 * pop-up window containing the text of the associated note. The rectangle or
 * ellipse shall be inscribed within the annotation rectangle defined by the
 * annotation dictionary’s Rect entry (see Table 168).
 * <p/>
 * Figure 63 shows two annotations, each with a border width of 18 points. Despite
 * the names square and circle, the width and height of the annotation rectangle
 * need not be equal. Table 177 shows the annotation dictionary entries specific
 * to these types of annotations.
 *
 * @since 5.0
 */
public class SquareAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(SquareAnnotation.class.toString());

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

    private Color fillColor;
    private boolean isFillColor;
    private Rectangle rectangle;

    public SquareAnnotation(Library l, HashMap h) {
        super(l, h);
    }

    public void init() {
        super.init();
        // parse out interior colour, specific to link annotations.
        fillColor = Color.WHITE; // we default to black but probably should be null
        java.util.List C = (java.util.List) getObject(IC_KEY);
        // parse thought rgb colour.
        if (C != null && C.size() >= 3) {
            float red = ((Number) C.get(0)).floatValue();
            float green = ((Number) C.get(1)).floatValue();
            float blue = ((Number) C.get(2)).floatValue();
            red = Math.max(0.0f, Math.min(1.0f, red));
            green = Math.max(0.0f, Math.min(1.0f, green));
            blue = Math.max(0.0f, Math.min(1.0f, blue));
            fillColor = new Color(red, green, blue);
            isFillColor = true;
        }

        // try and generate an appearance stream.
        resetNullAppearanceStream();
    }

    /**
     * Gets an instance of a SquareAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new SquareAnnotation Instance.
     */
    public static SquareAnnotation getInstance(Library library,
                                               Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_SQUARE);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        SquareAnnotation squareAnnotation = new SquareAnnotation(library, entries);
        squareAnnotation.init();
        squareAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        squareAnnotation.setNew(true);

        // set default flags.
        squareAnnotation.setFlag(Annotation.FLAG_READ_ONLY, false);
        squareAnnotation.setFlag(Annotation.FLAG_NO_ROTATE, false);
        squareAnnotation.setFlag(Annotation.FLAG_NO_ZOOM, false);
        squareAnnotation.setFlag(Annotation.FLAG_PRINT, true);

        return squareAnnotation;
    }

    /**
     * Resets the annotations appearance stream.
     */
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageTransform) {

        Appearance appearance = appearances.get(currentAppearance);
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();

        appearanceState.setMatrix(new AffineTransform());
        appearanceState.setShapes(new Shapes());

        Rectangle2D bbox = appearanceState.getBbox();
        AffineTransform matrix = appearanceState.getMatrix();
        Shapes shapes = appearanceState.getShapes();

        // setup the AP stream.
        setModifiedDate(PDate.formatDateTime(new Date()));
        // refresh rectangle
        rectangle = getUserSpaceRectangle().getBounds();

        entries.put(Annotation.RECTANGLE_KEY,
                PRectangle.getPRectangleVector(rectangle));
        userSpaceRectangle = new Rectangle2D.Float(
                (float) rectangle.getX(), (float) rectangle.getY(),
                (float) rectangle.getWidth(), (float) rectangle.getHeight());

        int strokeWidth = (int) borderStyle.getStrokeWidth();
        Rectangle rectangleToDraw = new Rectangle(
                (int) rectangle.getX() + strokeWidth,
                (int) rectangle.getY() + strokeWidth,
                (int) rectangle.getWidth() - strokeWidth * 2,
                (int) rectangle.getHeight() - strokeWidth * 2);

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.scale(1, -1);
        af.translate(-bbox.getMinX(), -bbox.getMaxY());

        BasicStroke stroke;
        if (borderStyle.isStyleDashed()) {
            stroke = new BasicStroke(
                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    borderStyle.getStrokeWidth() * 2.0f, borderStyle.getDashArray(), 0.0f);
        } else {
            stroke = new BasicStroke(borderStyle.getStrokeWidth());
        }

        shapes.add(new TransformDrawCmd(af));
        shapes.add(new StrokeDrawCmd(stroke));
        shapes.add(new ShapeDrawCmd(rectangleToDraw));
        if (isFillColor) {
            shapes.add(new ColorDrawCmd(fillColor));
            shapes.add(new FillDrawCmd());
        }
        if (borderStyle.getStrokeWidth() > 0) {
            shapes.add(new ColorDrawCmd(color));
            shapes.add(new DrawDrawCmd());
        }

        // update the appearance stream
        // create/update the appearance stream of the xObject.
        StateManager stateManager = library.getStateManager();
        Form form;
        if (hasAppearanceStream()) {
            form = (Form) getAppearanceStream();
            // else a stream, we won't support this for annotations.
        } else {
            // create a new xobject/form object
            HashMap<Object, Object> formEntries = new HashMap<Object, Object>();
            formEntries.put(Form.TYPE_KEY, Form.TYPE_VALUE);
            formEntries.put(Form.SUBTYPE_KEY, Form.SUB_TYPE_VALUE);
            form = new Form(library, formEntries, null);
            form.setPObjectReference(stateManager.getNewReferencNumber());
            library.addObject(form, form.getPObjectReference());
        }

        if (form != null) {
            Rectangle2D formBbox = new Rectangle2D.Float(0, 0,
                    (float) bbox.getWidth(), (float) bbox.getHeight());
            form.setAppearance(shapes, matrix, formBbox);
            stateManager.addChange(new PObject(form, form.getPObjectReference()));
            // update the AP's stream bytes so contents can be written out
            form.setRawBytes(
                    PostScriptEncoder.generatePostScript(shapes.getShapes()));
            HashMap<Object, Object> appearanceRefs = new HashMap<Object, Object>();
            appearanceRefs.put(APPEARANCE_STREAM_NORMAL_KEY, form.getPObjectReference());
            entries.put(APPEARANCE_STREAM_KEY, appearanceRefs);

            // compress the form object stream.
            if (compressAppearanceStream) {
                form.getEntries().put(Stream.FILTER_KEY, new Name("FlateDecode"));
            } else {
                form.getEntries().remove(Stream.FILTER_KEY);
            }
        }
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        float[] compArray = new float[3];
        this.fillColor.getColorComponents(compArray);
        java.util.List<Float> colorValues = new ArrayList<Float>(compArray.length);
        for (float comp : compArray) {
            colorValues.add(comp);
        }
        entries.put(IC_KEY, colorValues);
    }

    public Rectangle getRectangle() {
        return rectangle;
    }

    public void setRectangle(Rectangle rectangle) {
        this.rectangle = rectangle;
    }

    public boolean isFillColor() {
        return isFillColor;
    }

    public void setFillColor(boolean fillColor) {
        isFillColor = fillColor;
        if (!isFillColor) {
            entries.remove(IC_KEY);
        }
    }
}
