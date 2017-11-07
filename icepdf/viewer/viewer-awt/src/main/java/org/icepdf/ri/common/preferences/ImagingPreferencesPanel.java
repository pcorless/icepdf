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
package org.icepdf.ri.common.preferences;

import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Contains imaging setting for the viewer reference implementation.
 *
 * @since 6.3
 */
public class ImagingPreferencesPanel extends JPanel {

    // layouts constraint
    private GridBagConstraints constraints;

    private Preferences preferences;
    private JComboBox<ImageReferenceItem> imageReferenceComboBox;

    public ImagingPreferencesPanel(SwingController controller, PropertiesManager propertiesManager,
                                   ResourceBundle messageBundle) {

        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        preferences = propertiesManager.getPreferences();

        ImageReferenceItem[] imageReferenceItems = new ImageReferenceItem[]{
                new ImageReferenceItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.imaging.imageReference.default.label"),
                        ImageReferenceFactory.ImageReference.DEFAULT),
                new ImageReferenceItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.imaging.imageReference.scaled.label"),
                        ImageReferenceFactory.ImageReference.SCALED),
                new ImageReferenceItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.imaging.imageReference.mipMap.label"),
                        ImageReferenceFactory.ImageReference.MIP_MAP),
                new ImageReferenceItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.imaging.imageReference.smothScaled.label"),
                        ImageReferenceFactory.ImageReference.SMOOTH_SCALED),
                new ImageReferenceItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.imaging.imageReference.blurred.label"),
                        ImageReferenceFactory.ImageReference.BLURRED)
        };

        imageReferenceComboBox = new JComboBox<>(imageReferenceItems);
        imageReferenceComboBox.setSelectedItem(new ImageReferenceItem("", ImageReferenceFactory.imageReferenceType));
        imageReferenceComboBox.addActionListener(e -> {
            JComboBox cb = (JComboBox) e.getSource();
            ImageReferenceItem selectedItem = (ImageReferenceItem) cb.getSelectedItem();
            ImageReferenceFactory.imageReferenceType = selectedItem.getValue();
            preferences.put(PropertiesManager.PROPERTY_IMAGING_REFERENCE_TYPE, selectedItem.getValue().toString());
        });

        JPanel imagingPreferencesPanel = new JPanel(new GridBagLayout());
        imagingPreferencesPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        imagingPreferencesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.imaging.imageReference.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        addGB(imagingPreferencesPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.imaging.imageReference.label")),
                0, 0, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        addGB(imagingPreferencesPanel, imageReferenceComboBox, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, imagingPreferencesPanel, 0, 0, 1, 1);
        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 1, 1, 1);
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

    class ImageReferenceItem {
        String label;
        ImageReferenceFactory.ImageReference value;

        public ImageReferenceItem(String label, ImageReferenceFactory.ImageReference value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public ImageReferenceFactory.ImageReference getValue() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object imageReferenceItem) {
            if (imageReferenceItem instanceof ImageReferenceItem)
                return value.equals(((ImageReferenceItem) imageReferenceItem).getValue());
            else if (imageReferenceItem instanceof ImageReferenceFactory.ImageReference) {
                return value.equals(imageReferenceItem);
            }
            return false;
        }

    }

}
