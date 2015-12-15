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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.SoftMask;
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
            boolean hasMask = ((xForm.getGraphicsState().getExtGState() != null &&
                    xForm.getGraphicsState().getExtGState().getSMask() != null) ||
                    (xForm.getExtGState() != null && xForm.getExtGState().getSMask() != null));
            boolean isExtendGraphicState = xForm.getGraphicsState().getExtGState() != null &&
                    xForm.getExtGState() != null;

            // create the form and we'll paint it at the very least
            xFormBuffer = createBufferXObject(parentPage, xForm, null, renderingHints, !isExtendGraphicState);

            if (!disableXObjectSMask && hasMask) {
                SoftMask formSoftMask = null;
                SoftMask softMask = null;
                boolean isShading = false;
//                System.out.println("from AIS " + xForm.getExtGState().isAlphaAShape());
//                System.out.println("shape AIS " + xForm.getGraphicsState().getExtGState().isAlphaAShape());
                if (xForm.getGraphicsState().getExtGState().getSMask() != null) {
                    softMask = xForm.getGraphicsState().getExtGState().getSMask();
                    isShading = softMask.getG().getResources().isShading();
                    x = (int) softMask.getG().getBBox().getX();
                    y = (int) softMask.getG().getBBox().getY();
                }
                if (xForm.getExtGState().getSMask() != null) {
                    formSoftMask = xForm.getExtGState().getSMask();
                    x = (int) formSoftMask.getG().getBBox().getX();
                    y = (int) formSoftMask.getG().getBBox().getY();
                }
                // check if we have the same xobject.
                if (softMask != null && formSoftMask != null) {
                    if (softMask.getPObjectReference() != null && formSoftMask.getPObjectReference() != null &&
                            softMask.getPObjectReference().equals(formSoftMask.getPObjectReference())) {
                        softMask = null;
                    }
                }

                // apply the mask and paint.
                if (!isShading) {
                    if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_ALPHA)) {
                        logger.warning("Smask alpha example, currently not supported.");
                    } else if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_LUMINOSITY)) {
                        xFormBuffer = applyMask(parentPage, xFormBuffer, softMask, formSoftMask != null, g.getRenderingHints(),
                                false);
                    }
                } else if (softMask != null) {
                    // still not property aligning the form or mask space to correctly apply a shading pattern.
                    // experimental as it fixes some, breaks others, but regardless we don't support it well.
                    logger.warning("Smask pattern paint example, currently not supported.");
                    xFormBuffer.flush();
                    xFormBuffer = createBufferXObject(parentPage, softMask.getG(), null, renderingHints, true);
                    return currentShape;
                }
                // apply the form mask to current form content that has been rasterized to xFormBuffer
                if (formSoftMask != null) {
                    BufferedImage formSMaskBuffer = applyMask(parentPage, xFormBuffer, formSoftMask, softMask != null,//softMask != null,
                            g.getRenderingHints(), false);
                    // compost all the images.
                    if (softMask != null) {
                        BufferedImage formBuffer = ImageUtility.createTranslucentCompatibleImage(
                                xFormBuffer.getWidth(), xFormBuffer.getHeight());
                        Graphics2D g2d = (Graphics2D) formBuffer.getGraphics();
//                        java.util.List<Number> compRaw = formSoftMask.getBC();
//                        if (compRaw != null) {
//                            g2d.setColor(Color.BLACK);
//                            g2d.fillRect(0, 0, xFormBuffer.getWidth(), xFormBuffer.getHeight());
//                        }
                        g2d.drawImage(formSMaskBuffer, 0, 0, null);
//                        g2d.drawImage(xFormBuffer, 0, 0, null);
                        xFormBuffer.flush();
                        xFormBuffer = formBuffer;
                    } else {
                        xFormBuffer = formSMaskBuffer;
                    }
                }
            } else if (isExtendGraphicState) {
                BufferedImage shape = createBufferXObject(parentPage, xForm, null, renderingHints, true);
                xFormBuffer = ImageUtility.applyExplicitOutline(xFormBuffer, shape);
            }
//            ImageUtility.displayImage(xFormBuffer, "final" + xForm.getGroup() + " " + xForm.getPObjectReference());

        }
        g.drawImage(xFormBuffer, null, x, y);
        return currentShape;
    }

    private BufferedImage applyMask(Page parentPage, BufferedImage xFormBuffer, SoftMask softMask, boolean useLuminosity,
                                    RenderingHints renderingHints, boolean isAIS) {

        if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_ALPHA)) {
            logger.warning("Smask alpha example, currently not supported.");
        } else if (softMask != null && softMask.getS().equals(SoftMask.SOFT_MASK_TYPE_LUMINOSITY)) {

            BufferedImage sMaskBuffer = createBufferXObject(parentPage, softMask.getG(), softMask, renderingHints, true);
//            ImageUtility.displayImage(xFormBuffer,"base " + xForm.getPObjectReference() + " " +xFormBuffer.getHeight() + " x " + xFormBuffer.getHeight());
//            ImageUtility.displayImage(sMaskBuffer,"smask " + softMask.getG().getPObjectReference() + " " + isAIS + " " + useLuminosity);
            if (!useLuminosity) {
                xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
            } else {
                // todo try and figure out how to apply an AIS=false alpha to an xoject.
//                xFormBuffer = ImageUtility.applyExplicitLuminosity(xFormBuffer, sMaskBuffer);
                xFormBuffer = ImageUtility.applyExplicitOutline(xFormBuffer, sMaskBuffer);
            }
            // test for TR function
            if (softMask.getTR() != null) {
                logger.warning("Smask Transfer Function example, currently not supported.");
            }
            // todo need to look at matte too which is on the xobject.
        }
//        ImageUtility.displayImage(xFormBuffer, "final  " + softMask.getG().getPObjectReference());
        return xFormBuffer;
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
    private BufferedImage createBufferXObject(Page parentPage, Form xForm, SoftMask softMask,
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
        if (!isMask) {
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
            else {
                if (xFormShapes != null) {
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
