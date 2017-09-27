/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common;

import javax.swing.*;
import java.awt.*;

/**
 * Decorator class for painting behind a button's main graphic image.
 *
 * @since 6.1
 */
public class PaintButtonBase {

    protected Color color = Color.YELLOW;
    protected Shape colorBound;
    protected float alpha = 0.35f;
    protected float alphaDisabled = 0.10f;
    protected AbstractButton button;

    public PaintButtonBase(AbstractButton button) {
        this.button = button;
    }

    public PaintButtonBase(AbstractButton button, Color color, Shape colorBound, float alpha) {
        this.button = button;
        this.color = color;
        this.colorBound = colorBound;
        this.alpha = alpha;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void setColorBound(Shape colorBound) {
        this.colorBound = colorBound;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public Color getColor() {
        return color;
    }

    public Shape getColorBound() {
        return colorBound;
    }

    public float getAlpha() {
        return alpha;
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (button.isEnabled()) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        } else {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaDisabled));
        }
        if (color != null) g2d.setColor(color);
        if (colorBound != null) g2d.fill(colorBound);
    }
}
