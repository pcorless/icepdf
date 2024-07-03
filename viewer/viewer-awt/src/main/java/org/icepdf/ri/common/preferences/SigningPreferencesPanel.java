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

    private final Preferences preferences;

    public SigningPreferencesPanel(SwingController controller, ViewerPropertiesManager propertiesManager,
                                   ResourceBundle messageBundle) {

        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        preferences = propertiesManager.getPreferences();

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
        // todo should really have file chooser to set this up.
        pkcsPathTextField = new JTextField();
        updatePkcsPaths(messageBundle);

        pkcsPathTextField.addActionListener(e -> savePkcsPaths(keystoreTypeComboBox));

        keystoreTypeComboBox.addActionListener(e -> {
            JComboBox cb = (JComboBox) e.getSource();
            KeystoreTypeItem selectedItem = (KeystoreTypeItem) cb.getSelectedItem();
            preferences.put(ViewerPropertiesManager.PROPERTY_PKCS_KEYSTORE_TYPE, selectedItem.getValue());
            updatePkcsPaths(messageBundle);
        });

        JPanel imagingPreferencesPanel = new JPanel(new GridBagLayout());
        imagingPreferencesPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        imagingPreferencesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.signatures.pkcs.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        addGB(imagingPreferencesPanel, new JLabel(messageBundle.getString(
                        "viewer.dialog.viewerPreferences.section.signatures.pkcs.label")),
                0, 0, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        addGB(imagingPreferencesPanel, keystoreTypeComboBox, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(imagingPreferencesPanel, pkcsPathLabel, 0, 1, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(imagingPreferencesPanel, pkcsPathTextField, 1, 1, 1, 1);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, imagingPreferencesPanel, 0, 0, 1, 1);
        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 1, 1, 1);
    }

    private void updatePkcsPaths(ResourceBundle messageBundle) {
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