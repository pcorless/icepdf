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

import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.ContentParser;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p><i>Tiling patterns</i> consist of a small graphical figure (called a
 * pattern cell) that is replicated at fixed horizontal and vertical
 * intervals to fill the area to be painted. The graphics objects to
 * use for tiling are described by a content stream. (PDF 1.2)
 *
 * @author ICEsoft Technologies Inc.
 * @since 3.0
 */
public class TilingPattern extends Stream implements Pattern {

    private static final Logger logger =
            Logger.getLogger(TilingPattern.class.toString());

    // A code identifying the type of pattern that this dictionary describes
    private int patternType;

    // A code that determines how the color of the pattern cell is to be specified
    private int paintType;

    // uncolored tiling pattern colour, if specified.
    private Color unColored;

    /**
     * Colored tiling pattern. The pattern's content stream itself specifies the
     * colors used to paint the pattern cell. When the content stream begins
     * execution, the current color is the one that was initially in effect in
     * the pattern's parent content stream.
     */
    public static final int PAINTING_TYPE_COLORED_TILING_PATTERN = 1;

    /**
     * Uncolored tiling pattern. The pattern's content stream does not specify
     * any color information. Instead, the entire pattern cell is painted with a
     * separately specified color each time the pattern is used. Essentially,
     * the content stream describes a stencil through which the current color is
     * to be poured. The content stream must not invoke operators that specify
     * colors or other color-related parameters in the graphics state;
     * otherwise, an error will occur
     */
    public static final int PAINTING_TYPE_UNCOLORED_TILING_PATTERN = 2;

    // A code that controls adjustments to the spacing of tiles relative to the
    // device pixel grid
    private int tilingType;

    // type of PObject, should always be "Pattern"
    private String type;

    /**
     * Spacing of tiles relative to the device grid: Pattern cells are spaced
     * consistently-that is, by a multiple of a device pixel. To achieve this,
     * the viewer application may need to distort the pattern cell slightly by
     * making small adjustments to XStep, YStep, and the transformation matrix.
     * The amount of distortion does not exceed 1 device pixel.
     */
    public static final int TILING_TYPE_CONSTANT_SPACING = 1;

    /**
     * The pattern cell is not
     * distorted, but the spacing between pattern cells may vary by as much as
     * 1 device pixel, both horizontally and vertically, when the pattern is
     * painted. This achieves the spacing requested by XStep and YStep on
     * average, but not necessarily for each individual pattern cell.
     */
    public static final int TILING_TYPE_NO_DISTORTION = 2;

    /**
     * Pattern cells are spaced consistently as in tiling type 1, but with
     * additional distortion permitted to enable a more efficient implementation.
     */
    public static final int TILING_TYPE_CONSTANT_SPACING_FASTER = 3;

    // An array of four numbers in the pattern coordinate system giving the
    // coordinates of the left, bottom, right, and top edges, respectively, of
    // the pattern cell's bounding box. These boundaries are used to clip the
    // pattern cell.
    private Rectangle2D bBox;

    // The desired horizontal spacing between pattern cells, measured in the
    // pattern coordinate system.
    private float xStep;

    // The desired vertical spacing between pattern cells, measured in the
    // pattern coordinate system. Note that XStep and YStep may differ from the
    // dimensions of the pattern cell implied by the BBox entry. This allows
    // tiling with irregularly shaped figures. XStep and YStep may be either
    // positive or negative, but not zero.
    private float yStep;

    // A resource dictionary containing all of the named resources required by
    // the pattern's content stream
    private Resources resources;

    // An array of six numbers specifying the pattern matrix. The default value
    // is the identity matrix [1 0 0 1 0 0].
    private AffineTransform matrix;

    // Parsed resource data is stored here.
    private Shapes shapes;

    // Fill colour
    public Color fillColour = null;

    //  initiated flag
    private boolean inited;

    // textured paint
    private TexturePaint texturePaint;

    private GraphicsState parentGraphicState;

    /**
     * @param l
     * @param h
     * @param streamInputWrapper
     */
    public TilingPattern(Library l, Hashtable h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h, streamInputWrapper);

        type = library.getName(entries, "Type");

        patternType = library.getInt(entries, "PatternType");

        paintType = library.getInt(entries, "PaintType");

        tilingType = library.getInt(entries, "TilingType");

        bBox = library.getRectangle(entries, "BBox");

        xStep = library.getFloat(entries, "XStep");

        yStep = library.getFloat(entries, "YStep");

        resources = library.getResources(entries, "Resources");

        Vector v = (Vector) library.getObject(entries, "Matrix");
        if (v != null) {
            matrix = getAffineTransform(v);
        } else {
            // default is identity matrix
            matrix = new AffineTransform();
        }

    }


    public String getType() {
        return type;
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

    /*
     * Since tiling patterns are still not fully supported we need  to make
     * a best guess at which colour to use for stroking or non stroking
     * operations
     */
    public Color getFirstColor() {
        // find and cache first colour found in stack
        if (shapes != null && unColored == null) {
            for (int i = 0, max = shapes.shapes.size(); i < max; i++) {
                if (shapes.shapes.get(i) instanceof Color) {
                    unColored = (Color) shapes.shapes.get(i);
                    return unColored;
                }
            }
        }
        // if now shapes then we go with black.  
        if (unColored == null) {
            unColored = Color.black;
            return unColored;
        } else {
            return unColored;
        }
    }

    /**
     *
     */
    public void init() {

        if (inited) {
            return;
        }

        // try and find the form's resources dictionary.
        Resources leafResources = library.getResources(entries, "Resources");
        // apply resource for tiling if any, otherwise we use the default dictionary.
        if (leafResources != null) {
            resources = leafResources;
        }

        // Build a new content parser for the content streams and apply the
        // content stream of the calling content stream.
        ContentParser cp = new ContentParser(library, leafResources);
        cp.setGraphicsState(parentGraphicState);
        InputStream in = getInputStreamForDecodedStreamBytes();
        if (in != null) {
            try {
                shapes = cp.parse(in);
            }
            catch (Throwable e) {
                logger.log(Level.FINE, "Error processing tiling pattern.", e);
            }
            finally {
                try {
                    in.close();
                }
                catch (IOException e) {
                }
            }
        }
        /*
        // the matrix maps the pattern's internal coordinate system to the default
        // coordinate system.  We need to convert the steps and bbox to the
        // default coordinate space.

//        Vector v = (Vector) library.getObject(entries, "BBox");
//        float x1 = ((Number) v.elementAt(0)).floatValue();
//        float y1 = ((Number) v.elementAt(1)).floatValue();
//
//        float x2 = ((Number) v.elementAt(2)).floatValue();
//        float y2 = ((Number) v.elementAt(3)).floatValue();
//        double[] steps = new double[]{x1, y1, x2, y2};
//        matrix.deltaTransform(steps, 0, steps,0, 2);
//        v.clear();
//        v.add(new Float(steps[0]));
//        v.add(new Float(steps[1]));
//        v.add(new Float(steps[2]));
//        v.add(new Float(steps[3]));

        double[] steps = new double[]{bBox.getX(), bBox.getY()};
        matrix.deltaTransform(steps, 0, steps,0, 1);

        double width = Math.abs(Math.ceil(xStep));
        double height = Math.abs(Math.ceil(yStep));

        BufferedImage bi = new BufferedImage((int)width, (int)height,
                BufferedImage.TRANSLUCENT);
        Graphics2D g2d = bi.createGraphics();

        g2d.setClip(0, 0, (int)width, (int)height);
        g2d.setTransform(matrix);

        // get the current CTM
        AffineTransform af = new AffineTransform(parentGraphicState.getCTM());

         // test tile boundary
        shapes.add(af);
//        shapes.add(bBox);
//        shapes.add(Color.red);
//        shapes.addDrawCommand();


        if (shapes != null){
            shapes.paint(g2d);
        }

        texturePaint = new TexturePaint(bi, bBox);

        */
    }

    public Paint getPaint() {
        return texturePaint;
    }

    public int getPatternType() {
        return patternType;
    }

    public void setPatternType(int patternType) {
        this.patternType = patternType;
    }

    public int getPaintType() {
        return paintType;
    }

    public void setPaintType(int paintType) {
        this.paintType = paintType;
    }

    public int getTilingType() {
        return tilingType;
    }

    public void setTilingType(int tilingType) {
        this.tilingType = tilingType;
    }

    public Rectangle2D getBBox() {
        return bBox;
    }

    public float getXStep() {
        return xStep;
    }

    public float getYStep() {
        return yStep;
    }

    public AffineTransform getMatrix() {
        return matrix;
    }

    public AffineTransform getInvMatrix() {
        try {
            return matrix.createInverse();
        } catch (NoninvertibleTransformException e) {
            
        }
        return null;
    }

    public void setMatrix(AffineTransform matrix) {
        this.matrix = matrix;
    }

    public Shapes getShapes() {
        return shapes;
    }

    public void setShapes(Shapes shapes) {
        this.shapes = shapes;
    }


    public void setParentGraphicState(GraphicsState graphicsState) {
        this.parentGraphicState = graphicsState;
    }

    public GraphicsState getParentGraphicState() {
        return parentGraphicState;
    }

    public Color getUnColored() {
        return unColored;
    }

    public void setUnColored(Color unColored) {
        this.unColored = unColored;
    }

    /**
     * @return
     */
    public String toString() {
        return "Tiling Pattern: \n" +
                "           type: pattern " +
                "\n    patternType: tilling" +
                "\n      paintType: " + (paintType == PAINTING_TYPE_COLORED_TILING_PATTERN ? "colored" : "uncoloured") +
                "\n    tilingType: " + tilingType +
                "\n          bbox: " + bBox +
                "\n         xStep: " + xStep +
                "\n         yStep: " + yStep +
                "\n      resource: " + resources +
                "\n        matrix: " + matrix;
    }
}
