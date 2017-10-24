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

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Custom text annotation colour picker button.  Allows for the seamless setting of text annotation colours
 * and the automatic storing of the colour in the backing store.
 *
 * @since 6.3
 */
public class TextAnnotationToggleButton extends AnnotationColorToggleButton {

    // define the bounded shape used to colourize the icon with the current colour
    private static GeneralPath textIconPathLarge;
    private static Shape textIconPathSmall;

    static {
        textIconPathLarge = new GeneralPath();
        textIconPathLarge.moveTo(0, 0);
        textIconPathLarge.curveTo(1.407, -0.402, 2.422, -0.484, 4.03, -0.484);
        textIconPathLarge.curveTo(10.786, -0.484, 16.262, 3.419, 16.262, 8.232);
        textIconPathLarge.curveTo(16.262, 13.046, 10.786, 16.948, 4.03, 16.948);
        textIconPathLarge.curveTo(-2.727, 16.948, -8.204, 13.046, -8.204, 8.2329);
        textIconPathLarge.curveTo(-8.204, 5.809, -6.815, 3.616, -4.576, 2.037);
        textIconPathLarge.curveTo((float) textIconPathLarge.getCurrentPoint().getX(), (float) textIconPathLarge.getCurrentPoint().getY(),
                -4.472, 0.82, -4.91, -0.484);
        textIconPathLarge.curveTo(-5.482, -2.184, -5.994, -2.679, -5.994, -2.679);
        textIconPathLarge.curveTo((float) textIconPathLarge.getCurrentPoint().getX(), (float) textIconPathLarge.getCurrentPoint().getY(),
                -4.555, -2.679, -2.525, -1.866);
        textIconPathLarge.curveTo(-0.685, -1.127, 0, 0, 0, 0);
        textIconPathLarge.transform(new AffineTransform(1, 0, 0, -1, 11.8502, 23.1348));
        textIconPathLarge.closePath();

        textIconPathSmall =
                textIconPathLarge.createTransformedShape(new AffineTransform(0.75, 0, 0, 0.75, 0, 0));
    }

    public TextAnnotationToggleButton(Controller controller, ResourceBundle messageBundle, String title,
                                      String toolTip, String imageName, String imageSize, Font font) {
        super(controller, messageBundle, title, toolTip, imageName, imageSize, font);

        PaintButtonInterface paintButton = (PaintButtonInterface) colorButton;
        if (imageSize.equals(Images.SIZE_LARGE)) {
            paintButton.setColorBound(textIconPathLarge);
        } else if (imageSize.equals(Images.SIZE_SMALL)) {
            paintButton.setColorBound(textIconPathSmall);
        }

        // apply the settings colour
        Color color = null;
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_BUTTON_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_BUTTON_COLOR, 0);
            color = new Color(rgb);
        }
        // apply the settings or system property base colour for the given subtype.
        if (color != null) {
            paintButton.setColor(color);
        }

        setupLayout();
    }

    public void setColor(Color newColor, boolean fireChangeEvent) {
        super.setColor(newColor, fireChangeEvent);
        // set the colour back to the respective preference
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_TEXT_BUTTON_COLOR, newColor.getRGB());
    }
}