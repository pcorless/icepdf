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

import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * QuickPaintAnnotationButton allows users to quickly change the color of a markup annotation that is displayed in the
 * the annotation utility panel.
 *
 * @since 6.3
 */
public class QuickPaintAnnotationButton extends AnnotationColorButton {

    private static final Logger logger = Logger.getLogger(QuickPaintAnnotationButton.class.toString());

    // define the bounded shape used to colourise the icon with the current colour
    private static GeneralPath textIconPathSmall;
    private static GeneralPath textIconPathLarge;

    static {
        // small
        textIconPathSmall = new GeneralPath();
        textIconPathSmall.moveTo(10, 7);
        textIconPathSmall.lineTo(16, 12);
        textIconPathSmall.lineTo(9, 19);
        textIconPathSmall.lineTo(4, 13);
        textIconPathSmall.closePath();

        // large
        textIconPathLarge = new GeneralPath();
        textIconPathLarge.moveTo(14, 10);
        textIconPathLarge.lineTo(21, 16);
        textIconPathLarge.lineTo(12, 26);
        textIconPathLarge.lineTo(5, 18);
        textIconPathLarge.closePath();
    }

    public QuickPaintAnnotationButton(SwingController swingController, ResourceBundle messageBundle, String title,
                                      String toolTip, String imageName, String imageSize, Font font) {
        super(swingController, messageBundle, title, toolTip, imageName, imageSize, font);

        PaintButtonInterface paintButton = (PaintButtonInterface) colorButton;
        paintButton.setColorBound(imageSize.equals(Images.SIZE_LARGE) ? textIconPathLarge : textIconPathSmall);

        colorButton.addActionListener(this);

        // apply the settings colour
        Color color = null;
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_QUICK_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_QUICK_COLOR, 0);
            color = new Color(rgb);
        }
        // apply the settings or system property base colour for the given subtype.
        if (color != null) {
            paintButton.setColor(color);
        }

        addPropertyChangeListener(PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE, swingController);

        setupLayout();
    }

    public void setColor(Color newColor, boolean fireChangeEvent) {
        super.setColor(newColor, fireChangeEvent);
        // set the colour back to the respective preference
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_QUICK_COLOR, newColor.getRGB());

        if (fireChangeEvent) {
            firePropertyChange(
                    PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE, null, newColor);
        }
    }

}
