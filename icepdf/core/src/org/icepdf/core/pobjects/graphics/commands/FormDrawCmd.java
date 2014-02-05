/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.Shapes;

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

    public FormDrawCmd(Form xForm) {
        this.xForm = xForm;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        if (optionalContentState.isVisible()) {
            Rectangle2D bBox = xForm.getBBox();
            int width = (int) bBox.getWidth();
            int height = (int) bBox.getHeight();
            // corner cases where some bBoxes don't have a dimension.
            if (width == 0) {
                width = 1;
            }
            if (height == 0) {
                height = 1;
            }
            // create the new image to write too.
            BufferedImage bi = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvas = bi.createGraphics();
            // copy over the rendering hints
            canvas.setRenderingHints(g.getRenderingHints());
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
            // finally paint the graphic using the current gs.
            g.drawImage(bi, null, (int) bBox.getX(), (int) bBox.getY());
        }
        return currentShape;
    }
}
