/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.common.preferences;

import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Contains singing setting for the viewer reference implementation.  Allows users to pick the default signing handler.
 * <ul>
 *     <li><b>Pkcs11SigningHandler</b>Enables smart card Pkcs11 keystore capabilities</li>
 *     <li><b>Pkcs12SigningHandler</b>Enables standard Pkcs12 keystore capabilities</li>
 * </ul>
 *
 * @since 7.3
 */
public class SigningPreferencesPanel extends JPanel {

    public static final String PKCS_11_TYPE = "PKCS#11";
    public static final String PKCS_12_TYPE = "PKCS#12";

    private static final short PKCS11 = 0;
    private static final short PKCS12 = 1;

    // layouts constraint
    private final GridBagConstraints constraints;
    private final JComboBox<KeystoreTypeItem> keystoreTypeComboBox;
    private final JLabel pkcsPathLabel;
    private final JTextField pkcsPathTextField;
    private final JTextField tsaUrlTextField;

    private final ResourceBundle messageBundle;
    private final Preferences preferences;

    public SigningPreferencesPanel(SwingController controller, ViewerPropertiesManager propertiesManager,
                                   ResourceBundle messageBundle) {

        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        preferences = propertiesManager.getPreferences();
        this.messageBundle = messageBundle;

        KeystoreTypeItem[] pkcsTypeItems =
                new KeystoreTypeItem[]{
                        new KeystoreTypeItem(messageBundle.getString(
                                "viewer.dialog.viewerPreferences.section.signatures.pkcs.11.label"),
                                PKCS_11_TYPE),
                        new KeystoreTypeItem(messageBundle.getString(
                                "viewer.dialog.viewerPreferences.section.signatures.pkcs.12.label"),
                                PKCS_12_TYPE),
                };

        keystoreTypeComboBox = new JComboBox<>(pkcsTypeItems);
        keystoreTypeComboBox.setSelectedItem(new KeystoreTypeItem("",
                preferences.get(ViewerPropertiesManager.PROPERTY_PKCS_KEYSTORE_TYPE, pkcsTypeItems[PKCS12].value)));

        // setup default state
        pkcsPathLabel = new JLabel();
        pkcsPathTextField = new JTextField();
        tsaUrlTextField = new JTextField(preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_TSA_URL, ""));
        updatePkcsPaths();
        JButton pkcsPathBrowseButton = new JButton(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.signatures.pkcs.keystore.path.browse.label"));
        pkcsPathBrowseButton.addActionListener(e -> showBrowseDialog());

        pkcsPathTextField.addActionListener(e -> savePkcsPaths(keystoreTypeComboBox));
        tsaUrlTextField.addActionListener(e -> {
            preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_TSA_URL, tsaUrlTextField.getText());
        });
        tsaUrlTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_TSA_URL, tsaUrlTextField.getText());
            }
        });

        keystoreTypeComboBox.addActionListener(e -> {
            updatePkcsPaths();
        });

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // signature preferences panel
        JPanel signaturePreferencesPanel = new JPanel(new GridBagLayout());
        signaturePreferencesPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        signaturePreferencesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.signatures.pkcs.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        addGB(signaturePreferencesPanel, new JLabel(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.signatures.pkcs.label")),
                0, 0, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        addGB(signaturePreferencesPanel, keystoreTypeComboBox, 1, 0, 2, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(signaturePreferencesPanel, pkcsPathLabel, 0, 1, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(signaturePreferencesPanel, pkcsPathTextField, 1, 1, 1, 1);
        addGB(signaturePreferencesPanel, pkcsPathBrowseButton, 2, 1, 1, 1);

        // TSA URL
        JPanel tsaPreferencesPanel = new JPanel(new GridBagLayout());
        tsaPreferencesPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        tsaPreferencesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.signatures.tsa.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.weightx = 0;
        addGB(tsaPreferencesPanel, new JLabel(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.signatures.tsa.label.label")),
                0, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(tsaPreferencesPanel, tsaUrlTextField, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, signaturePreferencesPanel, 0, 0, 1, 1);
        addGB(this, tsaPreferencesPanel, 0, 1, 1, 1);

        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 2, 1, 1);
    }

    private void updatePkcsPaths() {
        // update which config/keystore input to show.
        if (keystoreTypeComboBox.getSelectedIndex() == PKCS11) {
            pkcsPathLabel.setText(messageBundle.getString(
                    "viewer.dialog.viewerPreferences.section.signatures.pkcs.11.config.path.label"));
            pkcsPathTextField.setText(preferences.get(ViewerPropertiesManager.PROPERTY_PKCS11_PROVIDER_CONFIG_PATH,
                    ""));
        } else if (keystoreTypeComboBox.getSelectedIndex() == PKCS12) {
            pkcsPathLabel.setText(messageBundle.getString(
                    "viewer.dialog.viewerPreferences.section.signatures.pkcs.12.keystore.path.label"));
            pkcsPathTextField.setText(preferences.get(ViewerPropertiesManager.PROPERTY_PKCS12_PROVIDER_KEYSTORE_PATH,
                    ""));
        }
        KeystoreTypeItem selectedItem = (KeystoreTypeItem) keystoreTypeComboBox.getSelectedItem();
        if (selectedItem != null) {
            preferences.put(ViewerPropertiesManager.PROPERTY_PKCS_KEYSTORE_TYPE, selectedItem.getValue());
        }
    }

    private void savePkcsPaths(JComboBox cb) {
        // update which config/keystore input to show.
        if (cb.getSelectedIndex() == PKCS11) {
            preferences.put(ViewerPropertiesManager.PROPERTY_PKCS11_PROVIDER_CONFIG_PATH, pkcsPathTextField.getText());
        } else if (cb.getSelectedIndex() == PKCS12) {
            preferences.put(ViewerPropertiesManager.PROPERTY_PKCS12_PROVIDER_KEYSTORE_PATH,
                    pkcsPathTextField.getText());
        }
    }

    private void showBrowseDialog() {
        String pkcsPath = pkcsPathTextField.getText();
        JFileChooser fileChooser = new JFileChooser(pkcsPath);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setDialogTitle(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.signatures.pkcs.keystore.path.selection.title"));
        final int responseValue = fileChooser.showDialog(this, messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.signatures.pkcs.keystore.path.accept.label"));
        if (responseValue == JFileChooser.APPROVE_OPTION) {
            pkcsPathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            savePkcsPaths(keystoreTypeComboBox);
            updatePkcsPaths();
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

    static class KeystoreTypeItem {
        final String label;
        final String value;

        public KeystoreTypeItem(String label, String value) {
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
        public boolean equals(Object keystoreTypeItem) {
            if (keystoreTypeItem instanceof KeystoreTypeItem) {
                return value.equals(((KeystoreTypeItem) keystoreTypeItem).getValue());
            } else {
                return value.equals(keystoreTypeItem);
            }
        }
    }

}