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
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.ContentParser;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public static final Name PATTERNTYPE_KEY = new Name("PatternType");
    public static final Name PAINTTYPE_KEY = new Name("PaintType");
    public static final Name TILINGTYPE_KEY = new Name("TilingType");
    public static final Name BBOX_KEY = new Name("BBox");
    public static final Name XSTEP_KEY = new Name("XStep");
    public static final Name YSTEP_KEY = new Name("YStep");
    public static final Name MATRIX_KEY = new Name("Matrix");
    public static final Name RESOURCES_KEY = new Name("Resources");

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
    private Name type;

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

    // cached pattern paint
    private TexturePaint patternPaint;

    public TilingPattern(Stream stream) {
        super(stream.getLibrary(), stream.getEntries(), stream.getRawBytes());
        initiParams();
    }

    /**
     * @param l
     * @param h
     * @param streamInputWrapper
     */
    public TilingPattern(Library l, HashMap h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h, streamInputWrapper);
        initiParams();
    }

    private void initiParams() {
        type = library.getName(entries, TYPE_KEY);

        patternType = library.getInt(entries, PATTERNTYPE_KEY);

        paintType = library.getInt(entries, PAINTTYPE_KEY);

        tilingType = library.getInt(entries, TILINGTYPE_KEY);

        bBox = library.getRectangle(entries, BBOX_KEY);

        xStep = library.getFloat(entries, XSTEP_KEY);

        yStep = library.getFloat(entries, YSTEP_KEY);

        List v = (List) library.getObject(entries, MATRIX_KEY);
        if (v != null) {
            matrix = getAffineTransform(v);
        } else {
            // default is identity matrix
            matrix = new AffineTransform();
        }
    }

    public Name getType() {
        return type;
    }

    /**
     * Utility method for parsing a vector of affine transform values to an
     * affine transform.
     *
     * @param v vector containing affine transform values.
     * @return affine tansform based on v
     */
    private static AffineTransform getAffineTransform(List v) {
        float f[] = new float[6];
        for (int i = 0; i < 6; i++) {
            f[i] = ((Number) v.get(i)).floatValue();
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
    public synchronized void init() {

        if (inited) {
            return;
        }
        inited = true;

        // try and find the form's resources dictionary.
        Resources leafResources = library.getResources(entries, RESOURCES_KEY);
        // apply resource for tiling if any, otherwise we use the default dictionary.
        if (leafResources != null) {
            resources = leafResources;
        }

        // Build a new content parser for the content streams and apply the
        // content stream of the calling content stream.
        ContentParser cp = new ContentParser(library, leafResources);
        cp.setGraphicsState(parentGraphicState);
        try {
            shapes = cp.parse(new byte[][]{getDecodedStreamBytes()}).getShapes();
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error processing tiling pattern.", e);
        }
    }

    /**
     * Applies the pattern paint specified by this TilingPattern instance.
     * Handles both uncoloured and coloured pattern types.
     *
     * @param g          graphics context to apply textured paint too.
     * @param parentPage parent page used to lookup any resources.
     */
    public void paintPattern(Graphics2D g, Page parentPage) {
        if (patternPaint == null) {
//            final AffineTransform matrixInv = getInvMatrix();
            // adjust the bBox so that xStep and yStep can be applied
            // for tile spacing.
            Rectangle2D bBoxMod = new Rectangle2D.Double(
                    bBox.getX(), bBox.getY(),
                    bBox.getWidth() == xStep ? bBox.getWidth() : xStep,
                    bBox.getHeight() == yStep ? bBox.getHeight() : yStep);
            bBoxMod = matrix.createTransformedShape(bBoxMod).getBounds2D();

            int width = (int) bBoxMod.getWidth();
            int height = (int) bBoxMod.getHeight();

            // corner cases where some bBoxes don't have a dimension.
            if (width == 0) {
                width = 1;
            }
            if (height == 0) {
                height = 1;
            }

            // create the new image to write too.
            final BufferedImage bi = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvas = bi.createGraphics();

            // create the pattern paint before  we paint encase there
            // is some recursive calls during the paint PDF-436
            patternPaint = new TexturePaint(bi, bBoxMod);
            g.setPaint(patternPaint);

            // apply current hints
            canvas.setRenderingHints(g.getRenderingHints());
            // copy over the rendering hints
            // get shapes and paint them.
            final Shapes tilingShapes = getShapes();

            // add clip for bBoxMod, needed for some shapes painting.
            canvas.setClip(0, 0, (int) bBoxMod.getWidth(), (int) bBoxMod.getHeight());

            // paint the pattern
            paintPattern(canvas, tilingShapes);

            // show it in a frame
//            final JFrame f = new JFrame("Test");
//            f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//            f.getContentPane().add(new JComponent() {
//                @Override
//                public void paint(Graphics g_) {
//                    super.paint(g_);
//                    paintPattern((Graphics2D)g_, tilingShapes);
//                }
//            });
//            f.setSize(new Dimension(800, 800));
//            f.setVisible(true);
            // post paint cleanup
            canvas.dispose();
            bi.flush();
        } else {
            g.setPaint(patternPaint);
        }
    }

    private void paintPattern(Graphics2D g2d, Shapes tilingShapes) {

        // store previous state so we can draw bounds
        AffineTransform preAf = g2d.getTransform();

        // apply scale an shear but not translation
        AffineTransform af = g2d.getTransform();
        // todo there is a still a bug here, the call to setToShear
        // wipes out the scale, further investigation is needed.
        if (matrix.getScaleX() != 0.0 && matrix.getScaleY() != 0.0) {
            af.scale(matrix.getScaleX(), Math.abs(matrix.getScaleY()));
        }
        af.shear(matrix.getShearX(), matrix.getShearY());
        g2d.transform(af);
        // pain the key pattern
        tilingShapes.paint(g2d);

        // if we have a shear then we need to pad out the pattern
        // so we can fill a pattern paint buffer.
        if (matrix.getShearX() > 0 ||
                matrix.getShearY() > 0) {
            g2d.translate(bBox.getWidth(), 0);
            tilingShapes.paint(g2d);
            g2d.translate(0, -bBox.getHeight());
            tilingShapes.paint(g2d);
            g2d.translate(-bBox.getWidth(), 0);
            tilingShapes.paint(g2d);
            g2d.translate(-bBox.getWidth(), 0);
            tilingShapes.paint(g2d);
            g2d.translate(0, bBox.getHeight());
            tilingShapes.paint(g2d);
            g2d.translate(0, bBox.getHeight());
            tilingShapes.paint(g2d);
            g2d.translate(bBox.getWidth(), 0);
            tilingShapes.paint(g2d);
            g2d.translate(bBox.getWidth(), 0);
            tilingShapes.paint(g2d);
            // highlight key square.
//            g2d.translate(-bBox.getWidth(), -bBox.getHeight());
//            g2d.setColor(Color.red);
//            g2d.draw(bBox);
        }
        g2d.setTransform(preAf);
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
