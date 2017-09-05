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
 * ColorChooserButton allows for the quick creation of a rgb color chooser button which reflects the currently
 * selected color as the background colour.
 *
 * @since 6.3
 */
public class ColorChooserButton extends JButton {

    public ColorChooserButton(Color color, int width, int height) {
        ColorChooserButton.setButtonBackgroundColor(color, this);
        setPreferredSize(new Dimension(width, height));
        addActionListener(e -> {
            Color newColor = RgbColorChooser.showDialog(this, "new color", null);
            ColorChooserButton.setButtonBackgroundColor(newColor, this);
        });
    }

    public ColorChooserButton(Color color) {
        ColorChooserButton.setButtonBackgroundColor(color, this);
        setPreferredSize(new Dimension(25, 22));
        addActionListener(e -> {
            Color newColor = RgbColorChooser.showDialog(this, "new color", null);
            ColorChooserButton.setButtonBackgroundColor(newColor, this);
        });
    }

    public static void setButtonBackgroundColor(Color color, JButton button) {
        if (color != null) {
            if (color.getAlpha() < 255) {
                color = new Color(color.getRGB());
            }
            button.setBackground(color);
            button.setBorder(BorderFactory.createLineBorder(Color.lightGray));
            button.setContentAreaFilled(false);
            button.setOpaque(true);
        }
    }
}