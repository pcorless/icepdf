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
import org.icepdf.core.pobjects.PObject;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Document properties defined by the documents /info dictionary, encryption permissions and font properties all shown
 * in a tabbed interface
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
public class PropertiesDialog extends EscapeJDialog {

    // layouts constraint
    private final GridBagConstraints constraints;

    private final JButton okButton;

    public PropertiesDialog(final JFrame frame, final SwingController controller,
                            final ResourceBundle messageBundle) {
        super(frame, true);

        final Document document = controller.getDocument();

        setTitle(messageBundle.getString("viewer.dialog.documentProperties.tab.title"));

        // Create GUI elements

        final JTabbedPane propertiesTabbedPane = new JTabbedPane();
        propertiesTabbedPane.setAlignmentY(JPanel.TOP_ALIGNMENT);

        // build the description
        final InformationPanel infoPanel = new InformationPanel(document, messageBundle);
        propertiesTabbedPane.addTab(
                messageBundle.getString("viewer.dialog.documentProperties.tab.description"),
                infoPanel);

        // build out the security tab
        propertiesTabbedPane.addTab(
                messageBundle.getString("viewer.dialog.documentProperties.tab.security"),
                new PermissionsPanel(document, messageBundle));

        // build out the fonts tab.
        final FontPanel fontPanel = new FontPanel(controller);
        addWindowListener(fontPanel);
        propertiesTabbedPane.addTab(
                messageBundle.getString("viewer.dialog.documentProperties.tab.fonts"),
                fontPanel);

        // build out custom properties panel
        final CustomPropertiesPanel customPanel = new CustomPropertiesPanel(document, messageBundle, this);
        propertiesTabbedPane.addTab(messageBundle.getString("viewer.dialog.documentProperties.tab.custom"), customPanel);
        final JPanel layoutPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.NORTH;
        addGB(layoutPanel, propertiesTabbedPane, 0, 0, 10, 1);
        //dummies
        for (int i = 0; i < 8; ++i) {
            addGB(layoutPanel, new JLabel(), i, 1, 1, 1);
        }

        final JButton cancelButton = new JButton(messageBundle.getString("viewer.button.cancel.label"));
        cancelButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        cancelButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(e -> {
            if (e.getSource() == okButton) {
                final Map<String, String> allProperties = infoPanel.getProperties();
                allProperties.putAll(customPanel.getProperties());
                if (document.getInfo().update(allProperties)) {
                    document.getStateManager().addChange(new PObject(document.getInfo().getEntries(),
                            document.getInfo().getPObjectReference()));
                }
                setVisible(false);
                dispose();
            }
        });
        addGB(layoutPanel, okButton, 8, 1, 1, 1);
        addGB(layoutPanel, cancelButton, 9, 1, 1, 1);

        this.setLayout(new BorderLayout(15, 15));
        add(layoutPanel, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(frame);
    }

    void setOkEnabled(final boolean enabled) {
        okButton.setEnabled(enabled);
    }

    private void addGB(final JPanel layout, final Component component,
                       final int x, final int y,
                       final int rowSpan, final int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
