/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;
import org.icepdf.core.pobjects.graphics.TextSprite;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * The DrawDrawCmd (no didn't stutter) will call draw and the Graphics2D context
 * for the currentShape.  The execute method will not draw the currentShape
 * if the shape does not interest the current graphics clip.
 *
 * @since 5.0
 */
public class DrawDrawCmd extends AbstractDrawCmd {

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base,
                              OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {
        Rectangle2D currentShapeBounds = currentShape.getBounds2D();
        // hitClip tests the rasterized clip region (and is true when there is no clip); g.getClip() would copy the
        // whole clip outline per stroke.  Hairline-thin shapes skip the clip test, as their bounds can miss the clip
        // while the stroke still reaches it, but not the optional content test: a hidden layer stays hidden.
        if (optionalContentState.isVisible() &&
                (TextSprite.hitClip(g, currentShapeBounds) ||
                        currentShapeBounds.getWidth() < 1.0 ||
                        currentShapeBounds.getHeight() < 1.0)) {
            g.draw(currentShape);
            // Send a PaintPage Event to listeners
            if (parentPage != null && paintTimer.shouldTriggerRepaint()) {
                parentPage.notifyPaintPageListeners();
            }
        }
        return currentShape;
    }
}
