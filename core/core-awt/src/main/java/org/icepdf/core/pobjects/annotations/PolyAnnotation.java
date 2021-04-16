package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.logging.Logger;

/**
 * Polygon annotations (PDF 1.5) display closed polygons on the page. Such polygons may have any number of
 * vertices connected by straight lines. Polyline annotations (PDF 1.5) are similar to polygons, except that the first
 * and last vertex are not implicitly connected
 *
 * @since 6.4
 */
public class PolyAnnotation extends MarkupAnnotation {

    private static final Logger logger =
            Logger.getLogger(PolyAnnotation.class.toString());

    public static final Name SUBTYPE_POLYLINE = new Name("PolyLine");
    public static final Name SUBTYPE_POLYGON = new Name("Polygon");

    public static final Set<Name> ALL_SUBTYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(SUBTYPE_POLYLINE, SUBTYPE_POLYGON)));

    /**
     * (Required) An array of numbers (see Table 174) specifying the width and
     * dash pattern that shall represent the alternating horizontal and vertical
     * coordinates, respectively, of each vertex, in default user space.
     */
    public static final Name VERTICES_KEY = new Name("Vertices");

    /**
     * (Optional; meaningful only for polyline annotations) An array of two
     * names that shall specify the line ending styles. The first and second
     * elements of the array shall specify the line ending styles for the endpoints
     * defined, respectively, by the first and last pairs of coordinates in the
     * Vertices array. Table 176 shows the possible values (Butt, ROpenArrow, slash etc). Default value: [ /
     * None /None ]
     */
    public static final Name LE_KEY = new Name("LE");

    /**
     * (Optional; meaningful only for polygon annotations) A border effect
     * dictionary that shall describe an effect applied to the border described by
     * the BS entry (see Table 167).
     */
    public static final Name BE_KEY = new Name("BE");

    /**
     * (Optional; PDF 1.4) An array of numbers in the range 0.0 to 1.0 specifying
     * the interior color that shall be used to fill the annotation’s line endings
     * (see Table 176). The number of array elements shall determine the colour
     * space in which the colour is defined:
     * 0 - No colour; transparent
     * 1 - DeviceGray
     * 3 - DeviceRGB
     * 4 - DeviceCMYK
     * TODO consolidate duplication across the line type shapes to new base class.
     */
    public static final Name IC_KEY = new Name("IC");

    /**
     * (Optional; PDF 1.6) A name that shall describe the intent of the polygon
     * or polyline annotation (see also Table 170). The following values shall be
     * valid:
     * PolygonCloud - The annotation is intended to function as a cloud object.
     * PolyLineDimension - (PDF 1.7) The polyline annotation is intended to function as a dimension.
     * PolygonDimension - (PDF 1.7) The polygon annotation is intended to function as a dimension.
     */
    public static final Name IT_KEY = new Name("IT");

    /**
     * (Optional; PDF 1.7) A measure dictionary (see Table 261) that shall
     * specify the scale and units that apply to the annotation.
     */
    public static final Name MEASURE_KEY = new Name("Measure");


    public PolyAnnotation(Library l, HashMap h) {
        super(l, h);
    }

    public static boolean isPolyAnnotation(Name subType) {
        return ALL_SUBTYPES.contains(subType);
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace, boolean isNew) {

    }
}
