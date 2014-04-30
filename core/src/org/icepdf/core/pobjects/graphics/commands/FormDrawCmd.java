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
    private int x, y, width, height;

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
            width = (int) bBox.getWidth();
            height = (int) bBox.getHeight();
            // create the buffer of the xobject content.
            xFormBuffer = createBufferXObject(parentPage, xForm, renderingHints);
            // check if we have  sMask and apply it.
            GraphicsState graphicsState = xForm.getGraphicsState();
            if (graphicsState != null && graphicsState.getSoftMask() != null) {
                SoftMask softMask = graphicsState.getSoftMask();
                Form sMaskForm = softMask.getG();
                BufferedImage sMaskBuffer =
                        createBufferXObject(parentPage, sMaskForm, renderingHints);
                // apply the mask and paint.
                if (sMaskBuffer != null) {
                    xFormBuffer = ImageUtility.applyExplicitSMask(xFormBuffer, sMaskBuffer);
                    sMaskBuffer.flush();
                }
            }
        }
        g.drawImage(xFormBuffer, x, y, width, height, null);
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
        // copy over the rendering hints
        canvas.setRenderingHints(renderingHints);
        // get shapes and paint them.
        Shapes xFormShapes = xForm.getShapes();
        if (xFormShapes != null) {
            xFormShapes.setPageParent(parentPage);
            // translate the coordinate system as we'll paint the g
            // graphic at the correctly location later.
            canvas.translate(-(int) bBox.getX(), -(int) bBox.getY());
            canvas.setClip(bBox);
            xFormShapes.paint(canvas);
            xFormShapes.setPageParent(null);
        }
        canvas.dispose();
        return bi;
    }
}
