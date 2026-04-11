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
package org.icepdf.ri.common.preferences;

import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Contains imaging setting for the viewer reference implementation.
 *
 * @since 6.3
 */
public class ImagingPreferencesPanel extends JPanel {

    // layouts constraint
    private final GridBagConstraints constraints;

    private final Preferences preferences;

    public ImagingPreferencesPanel(SwingController controller, ViewerPropertiesManager propertiesManager,
                                   ResourceBundle messageBundle) {

        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        preferences = propertiesManager.getPreferences();

        // Build combo items dynamically from the registry so custom types appear automatically
        List<ImageReferenceItem> itemList = new ArrayList<>();
        for (Map.Entry<String, ImageReferenceFactory.RegistryEntry> e :
                ImageReferenceFactory.getRegisteredTypes().entrySet()) {
            String key = e.getKey();
            String label = localizedLabel(messageBundle, key, e.getValue().getDisplayName());
            itemList.add(new ImageReferenceItem(label, key));
        }
        ImageReferenceItem[] imageReferenceItems = itemList.toArray(new ImageReferenceItem[0]);

        JComboBox<ImageReferenceItem> imageReferenceComboBox = new JComboBox<>(imageReferenceItems);
        imageReferenceComboBox.setSelectedItem(new ImageReferenceItem("", ImageReferenceFactory.imageReferenceType));
        imageReferenceComboBox.addActionListener(e -> {
            JComboBox cb = (JComboBox) e.getSource();
            ImageReferenceItem selectedItem = (ImageReferenceItem) cb.getSelectedItem();
            ImageReferenceFactory.imageReferenceType = selectedItem.getValue();
            preferences.put(ViewerPropertiesManager.PROPERTY_IMAGING_REFERENCE_TYPE, selectedItem.getValue());
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

    /**
     * Returns a localized label for a built-in type key, falling back to the registry display name
     * for any custom type that has no bundle entry.
     */
    private static String localizedLabel(ResourceBundle bundle, String key, String fallback) {
        String bundleKey;
        switch (key) {
            case ImageReferenceFactory.TYPE_DEFAULT:
                bundleKey = "viewer.dialog.viewerPreferences.section.imaging.imageReference.default.label";
                break;
            case ImageReferenceFactory.TYPE_SCALED:
                bundleKey = "viewer.dialog.viewerPreferences.section.imaging.imageReference.scaled.label";
                break;
            case ImageReferenceFactory.TYPE_MIP_MAP:
                bundleKey = "viewer.dialog.viewerPreferences.section.imaging.imageReference.mipMap.label";
                break;
            case ImageReferenceFactory.TYPE_SMOOTH_SCALED:
                bundleKey = "viewer.dialog.viewerPreferences.section.imaging.imageReference.smothScaled.label";
                break;
            case ImageReferenceFactory.TYPE_BLURRED:
                bundleKey = "viewer.dialog.viewerPreferences.section.imaging.imageReference.blurred.label";
                break;
            default:
                return fallback;
        }
        try {
            return bundle.getString(bundleKey);
        } catch (java.util.MissingResourceException e) {
            return fallback;
        }
    }

    static class ImageReferenceItem {
        final String label;
        final String value;

        public ImageReferenceItem(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
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
            else if (imageReferenceItem instanceof String) {
                return value.equals(imageReferenceItem);
            }
            return false;
        }

    }

}
