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
package org.icepdf.ri.common.widgets.annotations;

import org.icepdf.ri.common.widgets.AbstractColorButton;
import org.icepdf.ri.common.widgets.ColorButton;
import org.icepdf.ri.common.widgets.PaintButtonInterface;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * AnnotationColorButton is a base construct for drop down button that uses a JButton as the main control.
 *
 * @since 6.3
 */
public abstract class AnnotationColorButton extends AbstractColorButton {

    private static final Logger logger = Logger.getLogger(AnnotationColorToggleButton.class.toString());

    protected AnnotationColorButton(Controller controller,
                                 ResourceBundle messageBundle,
                                 String title, String toolTip, String imageName,
                                 final String imageSize, Font font) {
        super(controller, messageBundle);

        colorButton = new ColorButton();
        colorButton.setFont(font);
        colorButton.setToolTipText(toolTip);
        if (imageSize.equals(Images.SIZE_LARGE)) {
            colorButton.setPreferredSize(new Dimension(32, 32));
        } else if (imageSize.equals(Images.SIZE_SMALL)) {
            colorButton.setPreferredSize(new Dimension(24, 24));
        }
        colorButton.setRolloverEnabled(true);

        try {
            colorButton.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            colorButton.setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
            colorButton.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            colorButton.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toolbar toggle drop down button images: " + imageName + "_i" + imageSize + ".png");
        }
        colorButton.setBorder(BorderFactory.createEmptyBorder());
        colorButton.setContentAreaFilled(false);
        colorButton.setFocusPainted(true);

    }

    public void setColor(Color newColor, boolean fireChangeEvent) {
        ((PaintButtonInterface) colorButton).setColor(newColor);
        colorButton.repaint();
        if (popup != null) popup.setVisible(false);
    }
}
