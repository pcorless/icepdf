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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.BlendComposite;
import org.icepdf.core.pobjects.graphics.OptionalContentState;
import org.icepdf.core.pobjects.graphics.PaintTimer;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Applies BlendingComposite draw operations.
 *
 * @since 6.0.2
 */
public class BlendCompositeDrawCmd extends AbstractDrawCmd {

    private Composite blendComposite;
    private boolean alphaCompositeFallback;

    public BlendCompositeDrawCmd(Name blendComposite, float alpha) {
        // check for -1, value not set and default should be used.
        if (alpha == -1) {
            alpha = 1;
        }
        this.blendComposite = BlendComposite.getInstance(blendComposite, alpha);
    }

    /**
     * Enables fallback paint when a paint error was thrown trying to paint a BlendComposite. It should be noted
     * this problem only happens on X11 window systems (linux/unix/osx?).  And is limited to the Viewer application when
     * trying to paint Annotations on a swing/awt canvas.  This problem doesn't affect full page rending as all that
     * painting is written to a raster before painting to the swing/awt graphics. And similarly headless pages captures
     * are fine, fallback code shouldn't kick in.
     * <p>
     * Fallback paint simply adds a transparency effect which is usually close enough for most annotation types that
     * enable blending like text highlight annotations.
     * <p>
     * It's a JDK issue which currently can only be addressed by enabling the system property
     * -Dsun.java2d.opengl=true on a system that supports OpenGL.
     */
    public void enableAlphaCompositePaint() {
        alphaCompositeFallback = true;
    }

    @Override
    public Shape paintOperand(Graphics2D g, Page parentPage, Shape currentShape,
                              Shape clip, AffineTransform base, OptionalContentState optionalContentState,
                              boolean paintAlpha, PaintTimer paintTimer) {

        if (paintAlpha && blendComposite != null) {
            if (!alphaCompositeFallback) {
                g.setComposite(blendComposite);
            } else {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .30f));
            }
        }

        return currentShape;
    }

    /**
     * Gets the alpha value that is applied to the graphics context.
     *
     * @return alpha context which will be applied by this command.
     */
    public Composite getBlendComposite() {
        return blendComposite;
    }

}