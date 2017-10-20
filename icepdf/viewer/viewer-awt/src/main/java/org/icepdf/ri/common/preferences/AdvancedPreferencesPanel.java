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

import org.icepdf.core.pobjects.graphics.ImageReference;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.PageNumberTextFieldInputVerifier;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Contains advanced setting for the viewer reference implementation.
 *
 * @since 6.3
 */
public class AdvancedPreferencesPanel extends JPanel {

    // layouts constraint
    private GridBagConstraints constraints;

    private Preferences preferences;

    private JComboBox<BooleanItem> enableImageProxyComboBox;
    private JTextField imageProxyThreadCountField;

    private JTextField commonThreadCountField;

    public AdvancedPreferencesPanel(Controller controller, PropertiesManager propertiesManager,
                                    ResourceBundle messageBundle) {
        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        this.preferences = propertiesManager.getPreferences();

        boolean imageProxyEnabled = preferences.getBoolean(PropertiesManager.PROPERTY_IMAGE_PROXY_ENABLED, ImageReference.useProxy);
        int imageThreadCount = preferences.getInt(PropertiesManager.PROPERTY_IMAGE_PROXY_THREAD_COUNT, Library.imagePoolThreads);
        int commonThreadCount = preferences.getInt(PropertiesManager.PROPERTY_COMMON_THREAD_COUNT, Library.commonPoolThreads);

        // enable/disable the image proxy.
        BooleanItem[] fontHintingOptions = new BooleanItem[]{
                new BooleanItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.advanced.imageProxyEnabled.label"),
                        true),
                new BooleanItem(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.advanced.imageProxyDisabled.label"),
                        false)};
        enableImageProxyComboBox = new JComboBox<>(fontHintingOptions);
        enableImageProxyComboBox.setSelectedItem(new BooleanItem("", imageProxyEnabled));
        enableImageProxyComboBox.addActionListener(e -> {
            JComboBox cb = (JComboBox) e.getSource();
            BooleanItem selectedItem = (BooleanItem) cb.getSelectedItem();
            if (selectedItem != null) {
                preferences.putBoolean(PropertiesManager.PROPERTY_IMAGE_PROXY_ENABLED, selectedItem.getValue());
                imageProxyThreadCountField.setEnabled(selectedItem.getValue());
            }
        });

        imageProxyThreadCountField = new JTextField(2);
        imageProxyThreadCountField.setEnabled(imageProxyEnabled);
        imageProxyThreadCountField.setText(String.valueOf(imageThreadCount));
        imageProxyThreadCountField.setInputVerifier(new PageNumberTextFieldInputVerifier());
        imageProxyThreadCountField.addActionListener(e -> preferences.putInt(
                PropertiesManager.PROPERTY_IMAGE_PROXY_THREAD_COUNT, Integer.parseInt(imageProxyThreadCountField.getText())));

        commonThreadCountField = new JTextField(2);
        commonThreadCountField.setText(String.valueOf(commonThreadCount));
        commonThreadCountField.setInputVerifier(new PageNumberTextFieldInputVerifier());
        commonThreadCountField.addActionListener(e -> preferences.putInt(
                PropertiesManager.PROPERTY_COMMON_THREAD_COUNT, Integer.parseInt(commonThreadCountField.getText())));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // build out panel for common thread pool
        JPanel commonThreadPoolPanel = new JPanel(new GridBagLayout());
        commonThreadPoolPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        commonThreadPoolPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.advanced.commonThreadPool.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(commonThreadPoolPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.advanced.commonThreadPool.label")), 0, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(commonThreadPoolPanel, commonThreadCountField, 1, 0, 1, 1);

        // build out panel for common thread pool
        constraints.anchor = GridBagConstraints.WEST;
        JPanel imageThreadPoolPanel = new JPanel(new GridBagLayout());
        imageThreadPoolPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        imageThreadPoolPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.advanced.imageProxy.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(imageThreadPoolPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.advanced.imageProxy.label")), 0, 0, 1, 1);
        addGB(imageThreadPoolPanel, enableImageProxyComboBox, 1, 0, 1, 1);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(imageThreadPoolPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.advanced.imageProxyPoolSize.label")), 0, 1, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(imageThreadPoolPanel, imageProxyThreadCountField, 1, 1, 1, 1);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, commonThreadPoolPanel, 0, 0, 1, 1);
        addGB(this, imageThreadPoolPanel, 0, 1, 1, 1);
        addGB(this, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.advanced.restartRequired.label")), 0, 2, 1, 1);
        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 3, 1, 1);
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
