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
import javax.swing.colorchooser.AbstractColorChooserPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;

/**
 * RgbColorChooser is a custom color chooser that only allows users to select colors in the RGB color space.
 *
 * @since 6.3
 */
public class RgbColorChooser {

    /**
     * Shows a modal color-chooser dialog and blocks until the
     * dialog is hidden.  If the user presses the "OK" button, then
     * this method hides/disposes the dialog and returns the selected color.
     * If the user presses the "Cancel" button or closes the dialog without
     * pressing "OK", then this method hides/disposes the dialog and returns
     * <code>null</code>.
     *
     * @param component    the parent <code>Component</code> for the dialog
     * @param title        the String containing the dialog's title
     * @param initialColor the initial Color set when the color-chooser is shown
     * @return the selected color or <code>null</code> if the user opted out
     * @throws HeadlessException if GraphicsEnvironment.isHeadless()
     *                           returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static Color showDialog(Component component, String title, Color initialColor)
            throws HeadlessException {

        try {
            String defaultLF = UIManager.getSystemLookAndFeelClassName();
            if (defaultLF.contains("GTK")) {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        JColorChooser colorChooser = new JColorChooser(initialColor != null ?
                initialColor : Color.white);
        AbstractColorChooserPanel[] panels = colorChooser.getChooserPanels();
        for (AbstractColorChooserPanel p : panels) {
            String displayName = p.getDisplayName();
            // removed the none rgb colour space reference panels
            switch (displayName) {
                case "HSV":
                    colorChooser.removeChooserPanel(p);
                    break;
                case "HSL":
                    colorChooser.removeChooserPanel(p);
                    break;
                case "CMYK":
                    colorChooser.removeChooserPanel(p);
                    break;
            }
        }
        ColorTracker colorTracker = new ColorTracker(colorChooser);

        ActionListener cancelActionListener = e -> colorTracker.setColor(null);

        JDialog colorChooserDialog = JColorChooser.createDialog(component,
                title,
                true, colorChooser, colorTracker, cancelActionListener);

        colorChooserDialog.setVisible(true);

        try {
            String defaultLF = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(defaultLF);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return colorTracker.getColor();
    }

}

class ColorTracker implements ActionListener, Serializable {
    private JColorChooser chooser;
    private Color color;

    ColorTracker(JColorChooser c) {
        chooser = c;
    }

    public void actionPerformed(ActionEvent e) {
        color = chooser.getColor();
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}