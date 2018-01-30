/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
 * ColorButton with a base color picker model.
 *
 * @since 6.3
 */
public class ColorButton extends JButton implements PaintButtonInterface {

    protected PaintButtonBase paintButtonBase;

    public ColorButton() {
        paintButtonBase = new PaintButtonBase(this);
    }

    public ColorButton(Color color, Shape colorBound, float alpha) {
        paintButtonBase = new PaintButtonBase(this, color, colorBound, alpha);
    }

    @Override
    public void setColor(Color color) {
        paintButtonBase.setColor(color);
    }

    @Override
    public void setColorBound(Shape colorBound) {
        paintButtonBase.setColorBound(colorBound);
    }

    @Override
    public void setAlpha(float alpha) {
        paintButtonBase.setAlpha(alpha);
    }

    @Override
    public Color getColor() {
        return paintButtonBase.getColor();
    }

    @Override
    public Shape getColorBound() {
        return paintButtonBase.getColorBound();
    }

    @Override
    public float getAlpha() {
        return paintButtonBase.getAlpha();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintButtonBase.paintComponent(g);
    }
}