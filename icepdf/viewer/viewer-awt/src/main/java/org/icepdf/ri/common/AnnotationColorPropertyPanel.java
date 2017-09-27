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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.PropertiesManager.PROPERTY_ANNOTATION_RECENT_COLORS;

/**
 * @since 6.3
 */
public class AnnotationColorPropertyPanel extends JPanel implements ActionListener {

    // layouts constraint
    private GridBagConstraints constraints;

    private SwingController swingController;
    private ResourceBundle messageBundle;

    // optional/lazy loaded panels.
    private JPanel recentColorsPanel;
    private JPanel labeledColorPanel;

    // main controls
    private JButton colourPickerButton;
    private JButton preferencesButton;

    // last selected color;
    private Color lastColor = Color.RED;

    protected AbstractColorButton annotationColorButton;

    public AnnotationColorPropertyPanel(SwingController swingController, ResourceBundle messageBundle) {
        super(new GridBagLayout());
        this.swingController = swingController;
        this.messageBundle = messageBundle;

        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setBorder(BorderFactory.createLineBorder(Color.lightGray));

        colourPickerButton =
                new JButton(messageBundle.getString("viewer.popup.annotation.color.morecolors.label"));
        colourPickerButton.addActionListener(this);
        preferencesButton =
                new JButton(messageBundle.getString("viewer.popup.annotation.color.preferences.label"));
        preferencesButton.addActionListener(this);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(5, 2, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;

        // labels section
        labeledColorPanel = new JPanel(new GridBagLayout());
        buildLabeledColour();
        addGB(this, labeledColorPanel, 0, 0, 10, 1);

        // addition of standard colours.
        addGB(this,
                new JLabel(messageBundle.getString("viewer.popup.annotation.color.standard.label")),
                0, 1, 10, 1);

        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        addGB(this, new ColorButton(192, 0, 0), 0, 2, 1, 1);
        addGB(this, new ColorButton(255, 0, 0), 1, 2, 1, 1);
        addGB(this, new ColorButton(255, 192, 0), 2, 2, 1, 1);
        addGB(this, new ColorButton(255, 255, 0), 3, 2, 1, 1);
        addGB(this, new ColorButton(146, 208, 80), 4, 2, 1, 1);
        addGB(this, new ColorButton(0, 176, 80), 5, 2, 1, 1);
        addGB(this, new ColorButton(0, 176, 240), 6, 2, 1, 1);
        addGB(this, new ColorButton(0, 112, 192), 7, 2, 1, 1);
        addGB(this, new ColorButton(32, 76, 112), 8, 2, 1, 1);
        addGB(this, new ColorButton(112, 48, 160), 9, 2, 1, 1);

        // recent colour panel
        recentColorsPanel = new JPanel(new GridBagLayout());
        buildRecentColours(null);
        constraints.insets = new Insets(0, 0, 0, 0);
        addGB(this, recentColorsPanel, 0, 3, 10, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;

        // colour picker button.
        constraints.insets = new Insets(5, 2, 2, 2);
        addGB(this, colourPickerButton, 0, 4, 10, 1);
        constraints.insets = new Insets(0, 2, 2, 2);
        // preferences link
        addGB(this, preferencesButton, 0, 5, 10, 1);
    }

    public void setCallback(AbstractColorButton annotationColorButton) {
        this.annotationColorButton = annotationColorButton;
    }

    /**
     * Forces the dynamic labeled colours and recent colour sub buttons to be refreshed, should be called
     * after a preference panel update.
     */
    public void refreshColorPanel() {
        buildLabeledColour();
        buildRecentColours(null);
    }

    public void buildRecentColours(Color newColor) {
        recentColorsPanel.removeAll();
        // add the section header
        addGB(recentColorsPanel,
                new JLabel(messageBundle.getString("viewer.popup.annotation.color.lastused.label")),
                0, 0, 10, 1);
        // check preferences for a recent colors entry.
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        String rawRecents = preferences.get(PROPERTY_ANNOTATION_RECENT_COLORS, null);
        ArrayList<Color> recentColors = new ArrayList<>(10);
        // if we have some colour parse out the colours
        if (rawRecents != null) {
            StringTokenizer toker = new StringTokenizer(rawRecents, PropertiesManager.PROPERTY_TOKEN_SEPARATOR);
            while (toker.hasMoreTokens()) {
                recentColors.add(0, new Color(Integer.parseInt(toker.nextToken())));
            }

        }
        // if we have one we one we want to check if the new Color is already in the list and remove it.
        if (newColor != null) {
            recentColors.remove(newColor);
        }

        // add the colour to the front of the list
        if (newColor != null) recentColors.add(0, newColor);

        // max out at ten colors
        while (recentColors.size() > 10) {
            recentColors.remove(recentColors.size() - 1);
        }
        recentColors.trimToSize();

        if (recentColors.size() == 0) {
            recentColorsPanel.setVisible(false);
        } else {
            // build out the colours.
            constraints.insets = new Insets(2, 2, 2, 2);
            constraints.weightx = 0;
            constraints.fill = GridBagConstraints.NONE;
            int x = 0;
            for (Color recentColor : recentColors) {
                addGB(recentColorsPanel, new ColorButton(recentColor.getRGB()), x++, 1, 1, 1);
            }
            recentColorsPanel.setVisible(true);
        }
    }

    private void buildLabeledColour() {
        labeledColorPanel.removeAll();
        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        if (colorLabels.size() == 0) {
            labeledColorPanel.setVisible(false);
            return;
        } else {
            labeledColorPanel.setVisible(true);
        }
        // add the section header
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(labeledColorPanel,
                new JLabel(messageBundle.getString("viewer.popup.annotation.color.labels.label")),
                0, 0, 2, 1);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.NONE;
        // add the current color labels.
        int y = 1;
        Color color;
        for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
            color = colorLabel.getColor();
            constraints.weightx = 0;
            addGB(labeledColorPanel, new ColorButton(color.getRed(), color.getGreen(), color.getBlue()),
                    0, y, 1, 1);
            constraints.weightx = 1.0;
            addGB(labeledColorPanel, new JLabel(colorLabel.getLabel()), 1, y, 1, 1);
            y++;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source.equals(preferencesButton)) {
            swingController.showViewerPreferences(PropertiesManager.PROPERTY_SHOW_PREFERENCES_ANNOTATIONS);
        } else if (source.equals(colourPickerButton)) {
            // add colour to recent colour list, only show rgb pallet and setup default colour
            Color newColor = RgbColorChooser.showDialog(
                    this,
                    messageBundle.getString("viewer.popup.annotation.color.morecolors.label"),
                    lastColor);
            // assign the new color
            if (newColor != null) {
                annotationColorButton.setColor(newColor);
                buildRecentColours(newColor);
                // store the new recent colour
                Preferences preferences = PropertiesManager.getInstance().getPreferences();
                String rawRecents = preferences.get(PROPERTY_ANNOTATION_RECENT_COLORS, null);
                if (rawRecents != null) {
                    rawRecents = newColor.getRGB() + PropertiesManager.PROPERTY_TOKEN_SEPARATOR + rawRecents;
                } else {
                    rawRecents = String.valueOf(newColor.getRGB());
                }
                preferences.put(PROPERTY_ANNOTATION_RECENT_COLORS, rawRecents);
            }
        }
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

    /**
     * Helper button class for displaying a coloured button that set the selected colour of the parent/associated
     * AnnotationColorButton
     */
    private class ColorButton extends JButton {

        /**
         * Preset colours
         *
         * @param r red value
         * @param g green value
         * @param b blue value
         */
        ColorButton(int r, int g, int b) {
            ColorChooserButton.setButtonBackgroundColor(new Color(r, g, b), this);
            setPreferredSize(new Dimension(15, 15));
            setSize(15, 15);
            addActionListener(e -> {
                annotationColorButton.setColor(getBackground());
            });
        }

        /**
         * Called when building the recent colours.
         *
         * @param rgb int colour value.
         */
        ColorButton(int rgb) {
            ColorChooserButton.setButtonBackgroundColor(new Color(rgb), this);
            setPreferredSize(new Dimension(15, 15));
            setSize(15, 15);
            addActionListener(e -> {
                buildRecentColours(getBackground());
                annotationColorButton.setColor(getBackground());
                lastColor = getBackground();
            });
        }

    }
}
