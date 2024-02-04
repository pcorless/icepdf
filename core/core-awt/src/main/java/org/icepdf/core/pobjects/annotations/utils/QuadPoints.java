package org.icepdf.core.pobjects.annotations.utils;

/**
 * Common handling of QuadPoints parsing and dictionary value assignment for rendering.
 **/

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.icepdf.core.pobjects.annotations.MarkupAnnotation.KEY_QUAD_POINTS;

/**
 * Utility for common quad point manipulation in markup annotation, mainly redaction and text markup.
 */
public class QuadPoints {

    /**
     * Pulls the KEY_QUAD_POINTS key from the specified dictionary and an attempt is made to parse the data.
     * An array of 8 × n numbers specifying the coordinates of n quadrilaterals in default user space.
     * Each quadrilateral shall encompass a word or group of contiguous words in the text underlying the annotation.
     * The coordinates for each quadrilateral shall be given in the order
     * x1 y1 x2 y2 x3 y3 x4 y4
     * specifying the quadrilateral’s four vertices in counterclockwise order
     * (see Figure 64). The text shall be oriented with respect to the edge
     * connecting points (x1, y1) and (x2, y2).
     * <br>
     * The annotation dictionary’s AP entry, if present, shall take precedence
     * over QuadPoints; see Table 168 and 12.5.5, "Appearance Streams."
     */
    public static Shape[] parseQuadPoints(Library library, DictionaryEntries entries) {
        List<Number> quadPoints = library.getArray(entries, KEY_QUAD_POINTS);
        Shape[] quadrilaterals = null;
        if (quadPoints != null) {
            int size = quadPoints.size() / 8;
            quadrilaterals = new Shape[size];
            GeneralPath shape;
            for (int i = 0, count = 0; i < size; i++, count += 8) {
                shape = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                shape.moveTo(quadPoints.get(count + 6).floatValue(), quadPoints.get(count + 7).floatValue());
                shape.lineTo(quadPoints.get(count + 4).floatValue(), quadPoints.get(count + 5).floatValue());
                shape.lineTo(quadPoints.get(count).floatValue(), quadPoints.get(count + 1).floatValue());
                shape.lineTo(quadPoints.get(count + 2).floatValue(), quadPoints.get(count + 3).floatValue());
                shape.closePath();
                quadrilaterals[i] = shape;
            }
        }
        return quadrilaterals;
    }

    /**
     * Takes the given bounds and creates a valid KEY_QUAD_POINTS key value for insertion.
     *
     * @param markupBounds found to convert to quad points
     * @return valid quadPoint array.
     */
    public static ArrayList<Float> buildQuadPoints(ArrayList<Shape> markupBounds) {
        ArrayList<Float> quadPoints = new ArrayList<>();
        if (markupBounds != null) {
            Rectangle2D bounds;
            // build out the square in quadrant 1.
            for (Shape shape : markupBounds) {
                bounds = shape.getBounds2D();

                quadPoints.add((float) bounds.getX());
                quadPoints.add((float) (bounds.getY() + bounds.getHeight()));

                quadPoints.add((float) (bounds.getX() + bounds.getWidth()));
                quadPoints.add((float) (bounds.getY() + bounds.getHeight()));

                quadPoints.add((float) (bounds.getX()));
                quadPoints.add((float) (bounds.getY()));

                quadPoints.add((float) (bounds.getX() + bounds.getWidth()));
                quadPoints.add((float) (bounds.getY()));
            }
        }
        return quadPoints;
    }
}
