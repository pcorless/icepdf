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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * An ink annotation (PDF 1.3) represents a freehand “scribble” composed of one
 * or more disjoint paths. When opened, it shall display a pop-up window
 * containing the text of the associated note. Table 182 shows the annotation
 * dictionary entries specific to this type of annotation.
 *
 * @since 5.0
 */
public class InkAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(InkAnnotation.class.toString());

    /**
     * (Required) An array of n arrays, each representing a stroked path. Each
     * array shall be a series of alternating horizontal and vertical coordinates
     * in default user space, specifying points along the path. When drawn, the
     * points shall be connected by straight lines or curves in an
     * implementation-dependent way
     */
    public static final Name INK_LIST_KEY = new Name("InkList");

    protected Shape inkPath;

    public InkAnnotation(Library l, HashMap h) {
        super(l, h);

        // line border style
        HashMap BS = (HashMap) getObject(BORDER_STYLE_KEY);
        if (BS != null) {
            borderStyle = new BorderStyle(library, BS);
        } else {
            borderStyle = new BorderStyle(library, new HashMap());
        }

        // look for an ink list
        List<List<Float>> inkLists = library.getArray(entries, INK_LIST_KEY);
        GeneralPath inkPaths = new GeneralPath();
        if (inkLists != null) {
            inkPath = new GeneralPath();
            for (List<Float> inkList : inkLists) {
                GeneralPath inkPath = null;
                for (int i = 0, max = inkList.size() - 1; i < max; i += 2) {
                    if (inkPath == null) {
                        inkPath = new GeneralPath();
                        inkPath.moveTo(inkList.get(i), inkList.get(i + 1));
                    } else {
                        inkPath.lineTo(inkList.get(i), inkList.get(i + 1));
                    }
                }
                inkPaths.append(inkPath, false);
            }
        }
        this.inkPath = inkPaths;
    }

    /**
     * Gets an instance of a InkAnnotation that has valid Object Reference.
     *
     * @param library document library
     * @param rect    bounding rectangle in user space
     * @return new InkAnnotation Instance.
     */
    public static InkAnnotation getInstance(Library library,
                                            Rectangle rect) {
        // state manager
        StateManager stateManager = library.getStateManager();

        // create a new entries to hold the annotation properties
        HashMap<Name, Object> entries = new HashMap<Name, Object>();
        // set default link annotation values.
        entries.put(Dictionary.TYPE_KEY, Annotation.TYPE_VALUE);
        entries.put(Dictionary.SUBTYPE_KEY, Annotation.SUBTYPE_INK);
        // coordinates
        if (rect != null) {
            entries.put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(rect));
        } else {
            entries.put(Annotation.RECTANGLE_KEY, new Rectangle(10, 10, 50, 100));
        }

        // create the new instance
        InkAnnotation inkAnnotation = new InkAnnotation(library, entries);
        inkAnnotation.setPObjectReference(stateManager.getNewReferencNumber());
        inkAnnotation.setNew(true);

        return inkAnnotation;
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
        matrix = new AffineTransform();
        this.bbox = bbox;
        shapes = new Shapes();

        BasicStroke stroke;
        if (borderStyle.isStyleDashed()) {
            stroke = new BasicStroke(
                    borderStyle.getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    borderStyle.getStrokeWidth() * 2.0f, borderStyle.getDashArray(), 0.0f);
        } else {
            stroke = new BasicStroke(borderStyle.getStrokeWidth());
        }

        // setup the space for the AP content stream.
        AffineTransform af = new AffineTransform();
        af.translate(-this.bbox.getMinX(), -this.bbox.getMinY());

        shapes.add(new TransformDrawCmd(af));
        shapes.add(new StrokeDrawCmd(stroke));
        shapes.add(new ColorDrawCmd(color));
        shapes.add(new ShapeDrawCmd(inkPath));
        shapes.add(new DrawDrawCmd());
    }

    public Shape getInkPath() {
        return inkPath;
    }

    public void setInkPath(Shape inkPath) {
        this.inkPath = inkPath;
    }
}
