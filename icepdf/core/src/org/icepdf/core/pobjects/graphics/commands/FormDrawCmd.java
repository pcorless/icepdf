/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
            xFormBuffer = createBufferXObject(parentPage, xForm, graphicsState, renderingHints);
            if (!disableXObjectSMask &&
                    graphicsState != null && graphicsState.getSoftMask() != null) {
                SoftMask softMask = graphicsState.getSoftMask();
                Form sMaskForm = softMask.getG();

                // check for a shading instance and if so that means
                // we need to alter the shapes stack.
                BufferedImage sMaskBuffer =
                        createBufferXObject(parentPage, sMaskForm, graphicsState, renderingHints);

                if (sMaskBuffer.getWidth() > xFormBuffer.getWidth()) {
                    x = (int) sMaskForm.getBBox().getX();
                    y = (int) sMaskForm.getBBox().getY();
                }
                // apply the mask and paint.
                if (!sMaskForm.getResources().isShading()) {
//                    ImageUtility.displayImage(xFormBuffer, "xform");
//                    ImageUtility.displayImage(sMaskBuffer, "sMask");
                    // check for a backdrop color.
//                    java.util.List<Number> list = softMask.getBC();
//                    if(list != null){
//                        System.out.println();
//                    }
                    xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
                    sMaskBuffer.flush();

                }
            }
            // apply transparency
//            AlphaComposite alphaComposite =
//                    AlphaComposite.getInstance(graphicsState.getAlphaRule(),
//                            graphicsState.getFillAlpha());
//            g.setComposite(alphaComposite);
        }
//        ImageUtility.displayImage(xFormBuffer, "final");
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
                                              RenderingHints renderingHints) {
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
        BufferedImage bi = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = bi.createGraphics();
        if (graphicsState.getFillAlpha() < 1.0f && !xForm.getResources().isShading()) {
            AlphaComposite alphaComposite =
                    AlphaComposite.getInstance(graphicsState.getAlphaRule(),
                            graphicsState.getFillAlpha());
            canvas.setComposite(alphaComposite);
        }
        // copy over the rendering hints
        canvas.setRenderingHints(renderingHints);
        // get shapes and paint them.
        Shapes xFormShapes = xForm.getShapes();
        if (xFormShapes != null) {
            xFormShapes.setPageParent(parentPage);
            // translate the coordinate system as we'll paint the g
            // graphic at the correctly location later.
//            if (!xForm.getResources().isShading()) {
                canvas.translate(-(int) bBox.getX(), -(int) bBox.getY());
                canvas.setClip(bBox);
                xFormShapes.paint(canvas);
                xFormShapes.setPageParent(null);
//            }
            // gradient define smask, this still needs some work to get the
            // coord system correct, but basically smask defines pattern but
            // doesn't actually paint/fill a shape, it's assumed that its done
            // by the pattern.
//            else{
//                canvas.setPaint(xForm.getResources().getShading(new Name("Sh0")).getPaint());
//                canvas.fill(bBox.getBounds2D());
//            }
        }
        canvas.dispose();
        return bi;
    }
}
