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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>Shading Pattern is a Base class for a all shading Types.  It contains
 * all common dictionary entries and acts a factory examing the patternType
 * entry and returning a know Pattern Type implementation.  Currently the
 * factory only support Shading Type2 and Type3 patterns, as thses are the only
 * types we have concrete examples of. </p>
 *
 * @author ICEsoft Technologies Inc.
 * @since 3.0
 */
public abstract class ShadingPattern extends Dictionary implements Pattern {

    private static final Logger logger =
            Logger.getLogger(ShadingPattern.class.toString());

    // pattern types by number.
    public static final int SHADING_PATTERN_TYPE_1 = 1;
    public static final int SHADING_PATTERN_TYPE_2 = 2;
    public static final int SHADING_PATTERN_TYPE_3 = 3;
    public static final int SHADING_PATTERN_TYPE_4 = 4;
    public static final int SHADING_PATTERN_TYPE_5 = 5;
    public static final int SHADING_PATTERN_TYPE_6 = 6;

    // type of PObject, should always be "Pattern"
    protected String type;

    // A code identifying the type of pattern that this dictionary describes
    protected int patternType;

    // shading dictionary, entries vary depending on shading type.
    protected Hashtable shading;

    // shading type 1-7,  most common, 2,3,6..
    protected int shadingType;

    // start of common shading dictionary entries.

    // An array of four numbers in the pattern coordinate system giving the
    // coordinates of the left, bottom, right, and top edges, respectively, of
    // the pattern cell’s bounding box. These boundaries are used to clip the
    // pattern cell.
    protected Rectangle2D bBox;

    // any device, cie-based or special color except Pattern, required.
    protected PColorSpace colorSpace;

    // background colors (optional), not applicable on 'sh'
    protected Vector background;

    // turn on/off antiAliasing.  (optional)
    protected boolean antiAlias;

    // end of common shading dictionary entries.

    // An array of six numbers specifying the pattern matrix. The default value
    // is the identity matrix [1 0 0 1 0 0].
    protected AffineTransform matrix;

    // graphics state for shading pattern
    protected ExtGState extGState;

    //  initiated flag
    protected boolean inited;

    public ShadingPattern(Library library, Hashtable entries) {
        super(library, entries);

        type = library.getName(entries, "Type");

        patternType = library.getInt(entries, "PatternType");

        Object attribute = library.getObject(entries, "ExtGState");
        if (attribute instanceof Hashtable) {
            extGState = new ExtGState(library, (Hashtable) attribute);
        } else if (attribute instanceof Reference) {
            extGState = new ExtGState(library,
                    (Hashtable) library.getObject(
                            (Reference) attribute));
        }

        Vector v = (Vector) library.getObject(entries, "Matrix");
        if (v != null) {
            matrix = getAffineTransform(v);
        } else {
            // default is identity matrix
            matrix = new AffineTransform();
        }
    }

    /**
     * Factory method to resolve the shading dictionaries ShaddingType.  Currently
     * only types 2 and 3 are supported. In test suite these are the most
     * common of the pattern shading types
     *
     * @param library   library for document
     * @param attribute dictionary for potential shading object.
     * @return returns a ShadingPatern object based ont he shadingType criteria.
     *         if the proper constructor cannot be found then null is returned.
     */
    public static ShadingPattern getShadingPattern(Library library, Hashtable attribute) {
        // factory type approach, find shading entries and get type
        Hashtable shading =
                library.getDictionary(attribute, "Shading");
        if (shading != null) {
            return shadingFactory(library, attribute, shading);
        }
        return null;
    }

    /**
     * Factory call create a support pattern type.  Currently only types 2 and
     * 3 are supported.
     *
     * @param library document library
     * @param entries entries in the the currently dictionary.
     * @param shading shading dictionary.
     * @return shading pattern
     */
    public static ShadingPattern getShadingPattern(Library library,
                                                   Hashtable entries,
                                                   Hashtable shading) {
        // resolve shading pattern
        if (entries != null) {
            ShadingPattern shadingPattern = shadingFactory(library, shading, shading);
            // assign shading dictionary for sh instances that only define
            // the shading dictionary and not the full pattern dictionary.
            shadingPattern.setShading(shading);
            return shadingPattern;
        }

        return null;
    }

    // create a new shading pattern.
    private static ShadingPattern shadingFactory(Library library,
                                                 Hashtable attribute,
                                                 Hashtable patternDictionary) {
        int shadingType = library.getInt(patternDictionary, "ShadingType");
        if (shadingType == ShadingPattern.SHADING_PATTERN_TYPE_2) {
            return new ShadingType2Pattern(library, attribute);
        } else if (shadingType == ShadingPattern.SHADING_PATTERN_TYPE_3) {
            return new ShadingType3Pattern(library, attribute);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Shading pattern of Type " + shadingType +
                        " are not currently supported");
            }
        }
        return null;
    }

    /**
     * Utility method for parsing a vector of affinetranform values to an
     * affine transform.
     *
     * @param v vectory containing affine transform values.
     * @return affine tansform based on v
     */
    private static AffineTransform getAffineTransform(Vector v) {
        float f[] = new float[6];
        for (int i = 0; i < 6; i++) {
            f[i] = ((Number) v.elementAt(i)).floatValue();
        }
        return new AffineTransform(f);
    }

    /**
     * Gets the Paint object need to fill a shape etc.  Each individual
     * implementation will return a particular paint type.
     *
     * @return Paint type for fill.
     */
    public abstract Paint getPaint();

    /**
     * Initialized shading dictionary attributes. Discrepancies between sh and
     * scn tokens cause us to handle initialization at a later time.
     */
    public abstract void init();

    public void setParentGraphicState(GraphicsState graphicsState) {
        // nothing to be done for shading. 
    }

    public void setMatrix(AffineTransform matrix) {
        this.matrix = matrix;
    }

    public int getPatternType() {
        return patternType;
    }

    public Rectangle2D getBBox() {
        return bBox;
    }

    public AffineTransform getMatrix() {
        return matrix;
    }

    public int getShadingType() {
        return shadingType;
    }

    public void setShading(Hashtable shading) {
        this.shading = shading;
    }

    public String getType() {
        return type;
    }

    public PColorSpace getColorSpace() {
        return colorSpace;
    }

    public Vector getBackground() {
        return background;
    }

    public boolean isAntiAlias() {
        return antiAlias;
    }

    public ExtGState getExtGState() {
        return extGState;
    }

    public boolean isInited() {
        return inited;
    }

    public String toString() {
        return "Shading Pattern: \n" +
                "           type: pattern " +
                "\n    patternType: shading" +
                "\n         matrix: " + matrix +
                "\n      extGState: " + extGState +
                "\n        shading dictionary: " + shading +
                "\n               shadingType: " + shadingType +
                "\n               colourSpace: " + colorSpace +
                "\n                background: " + background +
                "\n                      bbox: " + bBox +
                "\n                 antiAlias: " + antiAlias;
    }
}
