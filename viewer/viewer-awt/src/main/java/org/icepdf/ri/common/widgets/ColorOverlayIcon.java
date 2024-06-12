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
package org.icepdf.ri.common.widgets;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Decorator class for painting in front of an Icon
 *
 * @since 6.3
 */
public class ColorOverlayIcon implements Icon {

    protected final PaintButtonBase paintButtonBase;
    protected final Icon baseIcon;

    public ColorOverlayIcon(URL location) {
        baseIcon = new ImageIcon(location);
        paintButtonBase = new PaintButtonBase(null);
    }

    public ColorOverlayIcon(Icon icon) {
        baseIcon = icon;
        paintButtonBase = new PaintButtonBase(null);
    }

    public void setColor(Color color, float alpha) {
        paintButtonBase.color = color;
        paintButtonBase.alpha = alpha;
    }

    public void setColor(Color color, float alpha, boolean fill, boolean back) {
        paintButtonBase.color = color;
        paintButtonBase.alpha = alpha;
        paintButtonBase.fill = fill;
        paintButtonBase.back = back;
    }

    public void setColor(Color color) {
        paintButtonBase.color = color;
    }

    public void setFill(boolean fill) {
        paintButtonBase.fill = fill;
    }

    public void setBack(boolean back) {
        paintButtonBase.back = back;
    }

    public void setColorBound(Shape colorBound) {
        paintButtonBase.colorBound = colorBound;
    }

    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        if (paintButtonBase.back) paintButtonBase.paintComponent(g);
        baseIcon.paintIcon(c, g, x, y);
        if (!paintButtonBase.back) paintButtonBase.paintComponent(g);
    }

    @Override
    public int getIconWidth() {
        return baseIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return baseIcon.getIconHeight();
    }

}
