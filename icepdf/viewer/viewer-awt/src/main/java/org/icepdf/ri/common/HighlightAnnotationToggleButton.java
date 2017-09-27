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

import org.icepdf.ri.util.PropertiesManager;

import java.awt.*;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Custom highlight annotation colour picker button.  Allows for the seamless setting of highlight annotation colours
 * and the automatic storing of the colour in the backing store.
 *
 * @since 6.3
 */
public class HighlightAnnotationToggleButton extends AnnotationColorToggleButton {

    public HighlightAnnotationToggleButton(SwingController swingController, ResourceBundle messageBundle, String title,
                                           String toolTip, String imageName, String imageSize, Font font) {
        super(swingController, messageBundle, title, toolTip, imageName, imageSize, font);

        // define the bounded shape used to colourize the icon with the current colour
        PaintButtonInterface paintButton = (PaintButtonInterface) colorButton;
        paintButton.setColorBound(new Rectangle(5, 9, 12, 13));

        // apply the settings colour
        Color color = null;
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        if (preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR, 0);
            color = new Color(rgb);
        }
        // apply the settings or system property base colour for the given subtype.
        if (color != null) {
            paintButton.setColor(color);
        }

        setupLayout();
    }

    public void setColor(Color newColor) {
        super.setColor(newColor);
        // set the colour back to the respective preference
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_BUTTON_COLOR, newColor.getRGB());
    }
}
