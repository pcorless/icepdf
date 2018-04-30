/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * The PreferencesDialog exposes numbers settings that would normally only be configurable with system properties.
 * Default values can still be added to the ICEpdfDefault.properties.  As a general rule system properties should
 * be used when using the rendering core only and the PreferenceDialog should be used when configuring the Viewer RI.
 *
 * Panel visibility can be controlled with the followign preference values.
 * <ul>
 *     <li>PropertiesManager.PROPERTY_SHOW_PREFERENCES_GENERAL</li>
 *     <li>PropertiesManager.PROPERTY_SHOW_PREFERENCES_ANNOTATIONS</li>
 *     <li>PropertiesManager.PROPERTY_SHOW_PREFERENCES_IMAGING</li>
 *     <li>PropertiesManager.PROPERTY_SHOW_PREFERENCES_FONTS</li>
 *     <li>PropertiesManager.PROPERTY_SHOW_PREFERENCES_ADVANCED</li>
 * </ul>
 *
 * @since 6.3
 */
public class PreferencesDialog extends EscapeJDialog {

    // layouts constraint
    private GridBagConstraints constraints;

    private JTabbedPane propertiesTabbedPane;

    public PreferencesDialog(JFrame frame, SwingController controller,
                             ResourceBundle messageBundle) {
        super(frame, true);
        setTitle(messageBundle.getString("viewer.dialog.viewerPreferences.title"));

        // Create GUI elements
        final JButton okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(e -> {
            if (e.getSource() == okButton) {
                setVisible(false);
                dispose();
            }
        });

        propertiesTabbedPane = new JTabbedPane();
        propertiesTabbedPane.setAlignmentY(JPanel.TOP_ALIGNMENT);

        PropertiesManager propertiesManager = PropertiesManager.getInstance();

        // build the general preferences tab
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_PREFERENCES_GENERAL)) {
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.viewerPreferences.section.general.title"),
                    new GeneralPreferencesPanel(controller, propertiesManager, messageBundle));
        }
        // build the annotation preferences tab
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_PREFERENCES_ANNOTATIONS)) {
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.title"),
                    new AnnotationPreferencesPanel(controller, propertiesManager, messageBundle));
        }
        // build the imaging preferences tab
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_PREFERENCES_IMAGING)) {
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.viewerPreferences.section.imaging.title"),
                    new ImagingPreferencesPanel(controller, propertiesManager, messageBundle));
        }
        // build the fonts preferences tab
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_PREFERENCES_FONTS)) {
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.viewerPreferences.section.fonts.title"),
                    new FontsPreferencesPanel(controller, propertiesManager, messageBundle));
        }
        // build the advanced preferences tab
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_PREFERENCES_ADVANCED)) {
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.viewerPreferences.section.advanced.title"),
                    new AdvancedPreferencesPanel(controller, propertiesManager, messageBundle));
        }

        JPanel layoutPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.NORTH;
        addGB(layoutPanel, propertiesTabbedPane, 0, 0, 1, 1);

        constraints.fill = GridBagConstraints.NONE;
        addGB(layoutPanel, okButton, 0, 1, 1, 1);

        this.setLayout(new BorderLayout(5, 5));
        this.add(layoutPanel, BorderLayout.NORTH);
//        this.pack();
        setSize(575, 475);
        validate();
        setLocationRelativeTo(frame);
    }

    /**
     * Allows for the selection of a specific preference panel on first view.
     *
     * @param selectedPreference preference tab to show by default.
     */
    public void setSelectedPreference(final String selectedPreference) {
        if (PropertiesManager.PROPERTY_SHOW_PREFERENCES_GENERAL.equals(selectedPreference)) {
            propertiesTabbedPane.setSelectedIndex(0);
        } else if (PropertiesManager.PROPERTY_SHOW_PREFERENCES_ANNOTATIONS.equals(selectedPreference)) {
            propertiesTabbedPane.setSelectedIndex(1);
        } else if (PropertiesManager.PROPERTY_SHOW_PREFERENCES_IMAGING.equals(selectedPreference)) {
            propertiesTabbedPane.setSelectedIndex(2);
        } else if (PropertiesManager.PROPERTY_SHOW_PREFERENCES_FONTS.equals(selectedPreference)) {
            propertiesTabbedPane.setSelectedIndex(3);
        } else if (PropertiesManager.PROPERTY_SHOW_PREFERENCES_ADVANCED.equals(selectedPreference)) {
            propertiesTabbedPane.setSelectedIndex(4);
        } else {
            propertiesTabbedPane.setSelectedIndex(0);
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
}