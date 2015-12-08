/*
 * Copyright 2006-2015 ICEsoft Technologies Inc.
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
package org.icepdf.core.pobjects.graphics.commands;

import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.ImageUtility;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.util.Defs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;


/**
 * The FormDrawCmd when executed will draw an xForm's shapes to a raster and
 * then paint the raster.  This procedure is only executed if the xForm
 * is part of transparency group that has a alpha value < 1.0f.
 *
 * @since 5.0
 */
public class FormDrawCmd extends AbstractDrawCmd {

    private Form xForm;

    private BufferedImage xFormBuffer;
    private int x, y;

    private static boolean disableXObjectSMask;

    static {
        // decide if large images will be scaled
        disableXObjectSMask =
                Defs.sysPropertyBoolean("org.icepdf.core.disableXObjectSMask",
                        false);
    }

    public FormDrawCmd(Form xForm) {
        this.xForm = xForm;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        if (optionalContentState.isVisible() && xFormBuffer == null) {
            RenderingHints renderingHints = g.getRenderingHints();
            Rectangle2D bBox = xForm.getBBox();
            x = (int) bBox.getX();
            y = (int) bBox.getY();
            // check if we have  sMask and apply it.
            GraphicsState graphicsState = xForm.getGraphicsState();
            // create the buffer of the xobject content.
            xFormBuffer = createBufferXObject(parentPage, xForm, graphicsState, renderingHints, false);
            if (!disableXObjectSMask &&
                    graphicsState != null && graphicsState.getSoftMask() != null) {
                SoftMask softMask = graphicsState.getSoftMask();
                Form sMaskForm = softMask.getG();

                // check for a shading instance and if so that means
                // we need to alter the shapes stack.
                BufferedImage sMaskBuffer =
                        createBufferXObject(parentPage, sMaskForm, graphicsState, renderingHints, true);

                if (sMaskBuffer.getWidth() > xFormBuffer.getWidth()) {
                    x = (int) sMaskForm.getBBox().getX();
                    y = (int) sMaskForm.getBBox().getY();
                }
                // apply the mask and paint.
                if (!sMaskForm.getResources().isShading()) {
//                    ImageUtility.displayImage(xFormBuffer, "xform" + xForm.getGroup() + " " + xForm.getPObjectReference());
//                    ImageUtility.displayImage(sMaskBuffer, "sMask" + xForm.getGroup() + " " + xForm.getPObjectReference());
                    if (softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_ALPHA)) {
                        logger.warning("Smask alpha example, currently not supported.");
                    } else if (softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_LUMINOSITY)) {
                        // check for a backdrop color.
                        java.util.List<Number> list = softMask.getBC();
                        if (list != null && xForm.getGroup().get(new Name("CS")) == null) {
                            // great a new image with thee specified colour,  apply the mask to it,
                            // generally this will create an inversion
                            if (graphicsState.getExtGState() != null) {
                                xFormBuffer = ImageUtility.applyExplicitLuminosity(xFormBuffer, sMaskBuffer);
                            } else {
                                xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
                            }
                        } else {
                            xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
                        }
                        // test for TR function
                        if (softMask.getTR() != null) {
                            logger.warning("Smask Transfer Function example, currently not supported.");
                        }
                        // need to look at matte too which is on the xobject.
                    }
                    sMaskBuffer.flush();
                } else {
                    // still not property aligning the form or mask space to correctly apply a shading pattern.
                    // experimental as it fixes some, breaks others, but regardless we don't support it well.
                    logger.warning("Smask pattern paint example, currently not supported.");
                    xFormBuffer.flush();
                    xFormBuffer = sMaskBuffer;
                    return currentShape;
                }
            } else {
                BufferedImage shape = createBufferXObject(parentPage, xForm, graphicsState, renderingHints, true);
                xFormBuffer = ImageUtility.applyExplicitOutline(xFormBuffer, shape);
            }
        }
//        ImageUtility.displayImage(xFormBuffer, "final" + xForm.getGroup() + " " + xForm.getPObjectReference());
        g.drawImage(xFormBuffer, null, x, y);
        return currentShape;
    }

    /**
     * Paint the form content to a BufferedImage so that the forms content can be
     * used to apply the smask data.  Further work is needed to fully support this
     * section of transparency groups.
     *
     * @param parentPage     parent page object
     * @param xForm          form being drawn to buffer.
     * @param renderingHints graphic state rendering hinds of parent.
     * @return buffered image of xObject content.
     */
    private BufferedImage createBufferXObject(Page parentPage, Form xForm,
                                              GraphicsState graphicsState,
                                              RenderingHints renderingHints, boolean isMask) {
        Rectangle2D bBox = xForm.getBBox();
        int width = (int) bBox.getWidth();
        int height = (int) bBox.getHeight();
        // corner cases where some bBoxes don't have a dimension.
        if (width == 0) {
            width = 1;
        } else if (width >= Short.MAX_VALUE) {
            width = xFormBuffer.getWidth();
        }
        if (height == 0) {
            height = 1;
        } else if (height >= Short.MAX_VALUE) {
            height = xFormBuffer.getHeight();
        }
        // create the new image to write too.
        BufferedImage bi = ImageUtility.createTranslucentCompatibleImage(width, height);
        Graphics2D canvas = bi.createGraphics();
        if (!isMask && graphicsState.getSoftMask() == null ) {// && graphicsState.getSoftMask() != null && graphicsState.getSoftMask().getBC() != null
//            java.util.List<Number> compRaw = graphicsState.getSoftMask().getBC();
//            float[] comps = new float[compRaw.size()];
//            for (int i= 0, max = comps.length; i < max; i++){
//                comps[i] = compRaw.get(i).floatValue();
//            }
//            canvas.setColor(graphicsState.getFillColorSpace().getColor(comps));
            canvas.setColor(Color.WHITE);
            canvas.fillRect(0, 0, width, height);
        }
        // copy over the rendering hints
        canvas.setRenderingHints(renderingHints);
        // get shapes and paint them.
        Shapes xFormShapes = xForm.getShapes();
        if (xFormShapes != null) {
            xFormShapes.setPageParent(parentPage);
            // translate the coordinate system as we'll paint the g
            // graphic at the correctly location later.
            if (!xForm.getResources().isShading()) {
                canvas.translate(-(int) bBox.getX(), -(int) bBox.getY());
                canvas.setClip(bBox);
                xFormShapes.paint(canvas);
                xFormShapes.setPageParent(null);
            }
            // gradient define smask, this still needs some work to get the
            // coord system correct, but basically smask defines pattern but
            // doesn't actually paint/fill a shape, it's assumed that its done
            // by the pattern.
            else{
                if (xFormShapes != null){
//                    for (DrawCmd cmd: xFormShapes.getShapes()){
//                        if (cmd instanceof ShapeDrawCmd && ((ShapeDrawCmd)cmd).getShape() == null ){
//                            Rectangle2D bounds = bBox.getBounds2D();
//                            ((ShapeDrawCmd)cmd).setShape(bounds);
//                        }
//                    }
                    canvas.translate(-(int) bBox.getX(), -(int) bBox.getY());
                    canvas.setClip(bBox);
                    xFormShapes.paint(canvas);
                    xFormShapes.setPageParent(null);
                }
            }
        }
        canvas.dispose();
        return bi;
    }
}
