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
package org.icepdf.ri.common.properties;

import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.EscapeJDialog;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * This class is a reference implementation for displaying a PDF document's
 * associated security permissions.
 *
 * @since 1.1
 */
@SuppressWarnings("serial")
public class PermissionsDialog extends EscapeJDialog {

    // layouts constraint
    private GridBagConstraints constraints;

    /**
     * Creates the permissions dialog.
     *
     * @param frame         parent frame.
     * @param document      document
     * @param messageBundle i18n message bundle
     */
    public PermissionsDialog(Frame frame, Document document,
                             ResourceBundle messageBundle) {
        super(frame, true);
        setTitle(messageBundle.getString("viewer.dialog.documentPermissions.title"));

        // Create GUI elements
        final JButton okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(e -> {
            if (e.getSource() == okButton) {
                setVisible(false);
                dispose();
            }
        });

        // Place GUI elements on dialog
        JPanel permissionsPanel = new PermissionsPanel(document, messageBundle);
        permissionsPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);

        JPanel layoutPanel = new JPanel();
        GridBagLayout layout = new GridBagLayout();
        layoutPanel.setLayout(layout);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.CENTER;

        addGB(layoutPanel, permissionsPanel, 0, 0, 1, 1);

        constraints.fill = GridBagConstraints.NONE;
        addGB(layoutPanel, okButton, 0, 1, 1, 1);

        this.getContentPane().add(layoutPanel);
        pack();
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
