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

import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * The PreferencesDialog exposes numbers settings that would normally only be configurable with system properties.
 * Default values can still be added to the ICEpdfDefault.properties.  As a general rule system properties should
 * be used when using the rendering core only and the PreferenceDialog should be used when configuring the Viewer RI.
 *
 * @since 6.3
 */
public class PreferencesDialog extends EscapeJDialog {

    // layouts constraint
    private GridBagConstraints constraints;

    public PreferencesDialog(JFrame frame, SwingController swingController,
                             ResourceBundle messageBundle) {
        super(frame, true);

        Document document = swingController.getDocument();

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

//        JTabbedPane propertiesTabbedPane = new JTabbedPane();
//        propertiesTabbedPane.setAlignmentY(JPanel.TOP_ALIGNMENT);
//
//        // build the description
//        propertiesTabbedPane.addTab(
//                messageBundle.getString("viewer.dialog.documentProperties.tab.description"),
//                new InformationPanel(document, messageBundle));
//
//        // build out the security tab
//        propertiesTabbedPane.addTab(
//                messageBundle.getString("viewer.dialog.documentProperties.tab.security"),
//                new PermissionsPanel(document, messageBundle));
//
//        // build out the fonts tab.
//        propertiesTabbedPane.addTab(
//                messageBundle.getString("viewer.dialog.documentProperties.tab.fonts"),
//                new FontPanel(document, swingController, messageBundle));

        JPanel layoutPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.NORTH;
//        addGB(layoutPanel, propertiesTabbedPane, 0, 0, 1, 1);

        constraints.fill = GridBagConstraints.NONE;
        addGB(layoutPanel, okButton, 0, 1, 1, 1);

        this.setLayout(new BorderLayout(15, 15));
        this.add(layoutPanel, BorderLayout.NORTH);

        setSize(540, 440);
        setLocationRelativeTo(frame);
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