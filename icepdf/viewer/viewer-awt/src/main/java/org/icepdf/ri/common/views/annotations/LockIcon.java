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
package org.icepdf.ri.common.views.annotations;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * Vector based lock icon.
 *
 * @since 6.3
 */
public class LockIcon implements Icon {

    protected static GeneralPath unlockedPath, lockedPath, unlockKeyPath, lockedKeyPath;


    protected static double pathSize = 20;
    protected static int width;
    protected static int height;

    private Color backgroundColor;
    private boolean isLock = true;

    public LockIcon(Color backgroundColor, Dimension dimension, boolean isLock) {
        this.backgroundColor = backgroundColor;
        this.isLock = isLock;
        width = dimension.width;
        height = dimension.height;
    }

    public void setBackgroundColor(Color color) {
        backgroundColor = backgroundColor;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create(0, 0, c.getWidth(), c.getHeight());

        g2d.setColor(backgroundColor);

        double scaledX = c.getWidth() / pathSize;
        double scaledY = c.getHeight() / pathSize;

        g2d.scale(scaledX, scaledY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        if (isLock) {
            g2d.draw(lockedPath);
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.draw(unlockKeyPath);
        } else {
            g2d.draw(unlockedPath);
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.draw(lockedKeyPath);
        }
        g2d.dispose();
    }

    public int getIconWidth() {
        return width;
    }

    public int getIconHeight() {
        return height;
    }

    static {

        unlockedPath = new GeneralPath();
        unlockedPath.moveTo(4, 9);
        unlockedPath.lineTo(15.5, 9);
        unlockedPath.lineTo(15.5, 17);
        unlockedPath.lineTo(4, 17);
        unlockedPath.closePath();

        unlockedPath.moveTo(5, 9);
        unlockedPath.curveTo(5, 9, 5, 3, 9.75, 3);
        unlockedPath.curveTo(9.75, 3, 14, 3, 14, 6);

        unlockKeyPath = new GeneralPath();
        unlockKeyPath.moveTo(8, 13);
        unlockKeyPath.lineTo(11, 13);

        lockedPath = new GeneralPath();
        lockedPath.moveTo(4, 9);
        lockedPath.lineTo(15.5, 9);
        lockedPath.lineTo(15.5, 17);
        lockedPath.lineTo(4, 17);
        lockedPath.closePath();

        lockedPath.moveTo(5, 9);
        lockedPath.curveTo(5, 9, 5, 3, 9.75, 3);
        lockedPath.curveTo(9.75, 3, 14, 3, 14, 9);

        lockedKeyPath = new GeneralPath();
        lockedKeyPath.moveTo(9.5, 11);
        lockedKeyPath.lineTo(9.5, 15);

    }

}
