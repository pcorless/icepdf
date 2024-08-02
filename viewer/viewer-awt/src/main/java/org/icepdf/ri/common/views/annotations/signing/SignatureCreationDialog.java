package org.icepdf.ri.common.views.annotations.signing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureDictionaries;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.utility.annotation.properties.FontWidgetUtilities;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.annotations.acroform.SignatureComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * The SignatureCreationDialog allows users to select an available signing certificate and customize various setting
 * associated with signing a document.
 */
public class SignatureCreationDialog extends EscapeJDialog implements ActionListener, ListSelectionListener,
        ItemListener, FocusListener {

    private static final Logger logger =
            Logger.getLogger(SignatureCreationDialog.class.toString());

    private static final Locale[] supportedLocales = {
            new Locale("da"),
            new Locale("de"),
            new Locale("en"),
            new Locale("es"),
            new Locale("fi"),
            new Locale("fr"),
            new Locale("it"),
            new Locale("nl"),
            new Locale("no"),
            new Locale("pt"),
            new Locale("sv"),
    };

    private GridBagConstraints constraints;

    private JTable certificateTable;
    private JRadioButton signerRadioButton;
    private JRadioButton certifyRadioButton;
    private JCheckBox signerVisibilityCheckBox;
    private JTextField locationTextField;
    private JTextField dateTextField;
    private JTextField nameTextField;
    private JTextField contactTextField;
    private JTextArea reasonTextArea;

    private JComboBox<ValueLabelItem> fontNameBox;
    private JComboBox<ValueLabelItem> fontSizeBox;
    private JCheckBox showTextCheckBox;
    private JCheckBox showSignatureCheckBox;
    private JTextField imagePathTextField;

    private JComboBox<Locale> languagesComboBox;
    private JButton signButton;

    private final Preferences preferences;

    private SignerHandler signerHandler;

    private final SignatureValidator signatureValidator;
    private SignatureAppearanceModel signatureAppearanceModel;

    protected static ResourceBundle messageBundle;
    protected final SignatureComponent signatureWidgetComponent;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;

    public SignatureCreationDialog(Frame parent, ResourceBundle messageBundle,
                                   SignatureComponent signatureComponent) throws KeyStoreException {
        super(parent, true);
        this.messageBundle = messageBundle;
        this.preferences = ViewerPropertiesManager.getInstance().getPreferences();
        this.signatureWidgetComponent = signatureComponent;
        this.signatureWidgetAnnotation = signatureComponent.getAnnotation();
        this.signatureValidator = this.signatureWidgetAnnotation.getSignatureValidator();

        signatureAppearanceModel = new SignatureAppearanceModel();

        buildUI();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {

        Object source = actionEvent.getSource();
        if (source == null) return;

        if (source == signButton) {
            SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
            Library library = signatureWidgetAnnotation.getLibrary();
            SignatureDictionaries signatureDictionaries = library.getSignatureDictionaries();

            // set up signer dictionary as the primary certification signer.
            signatureDictionary = SignatureDictionary.getInstance(signatureWidgetAnnotation,
                    signerRadioButton.isSelected() ? SignatureType.SIGNER : SignatureType.CERTIFIER);
            signatureDictionaries.addCertifierSignature(signatureDictionary);
            signatureDictionary.setSignerHandler(signerHandler);

            // assign original values from cert
            signatureDictionary.setName(nameTextField.getText());
            signatureDictionary.setContactInfo(contactTextField.getText());
            signatureDictionary.setLocation(locationTextField.getText());
            signatureDictionary.setReason(signerRadioButton.isSelected() ?
                    SignatureType.SIGNER.toString().toLowerCase() :
                    SignatureType.CERTIFIER.toString().toLowerCase());

            buildAppearanceStream();

//            setVisible(false);
//            dispose();
        } else if (source == imagePathTextField) {
            setSignatureImage();
        } else if (source == signerRadioButton) {
            signatureAppearanceModel.setSignatureType(SignatureType.SIGNER);
        } else if (source == certifyRadioButton) {
            signatureAppearanceModel.setSignatureType(SignatureType.CERTIFIER);
        } else if (source == signerVisibilityCheckBox) {
            signatureAppearanceModel.setSignatureVisible(signerVisibilityCheckBox.isSelected());
        } else if (source == languagesComboBox) {
            signatureAppearanceModel.setLocale((Locale) languagesComboBox.getSelectedItem());
        }
        System.out.println("actionPerformed: " + actionEvent.getActionCommand());
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) {
            return;
        }
        if (e.getSource() == fontSizeBox) {
            ValueLabelItem item = (ValueLabelItem) fontSizeBox.getSelectedItem();
            if (item != null) {
                // todo update font size
                System.out.println("font size: " + item.getValue());
            }
        } else if (e.getSource() == fontNameBox) {
            ValueLabelItem item = (ValueLabelItem) fontNameBox.getSelectedItem();
            if (item != null) {
                // todo update font name
                System.out.println("font name: " + item.getValue());
            }
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        int row = certificateTable.convertRowIndexToModel(certificateTable.getSelectedRow());
        CertificateTableModel model = (CertificateTableModel) certificateTable.getModel();
        signerHandler.setCertAlias(model.getAliasAt(row));
        setSelectedCertificate(model.getCertificateAt(row));
    }

    @Override
    public void focusGained(FocusEvent focusEvent) {

    }

    @Override
    public void focusLost(FocusEvent focusEvent) {
        Object source = focusEvent.getSource();
        boolean changed = false;
        if (source == locationTextField) {
            signatureAppearanceModel.setLocation(locationTextField.getText());
            changed = true;
        } else if (source == contactTextField) {
            signatureAppearanceModel.setContact(contactTextField.getText());
            changed = true;
        } else if (source == nameTextField) {
            signatureAppearanceModel.setName(nameTextField.getText());
            changed = true;
        } else if (source == imagePathTextField) {
            setSignatureImage();
            changed = true;
        }
        if (changed) {
            buildAppearanceStream();
        }
    }

    private void updateModelAppearanceState() {
        signatureAppearanceModel.setLocation(locationTextField.getText());
        signatureAppearanceModel.setContact(contactTextField.getText());
        signatureAppearanceModel.setName(nameTextField.getText());
        signatureAppearanceModel.setSignatureType(signerRadioButton.isSelected() ?
                SignatureType.SIGNER : SignatureType.CERTIFIER);
        signatureAppearanceModel.setFontName(fontNameBox.getSelectedItem().toString());
        signatureAppearanceModel.setFontSize((int) ((ValueLabelItem) fontSizeBox.getSelectedItem()).getValue());
        signatureAppearanceModel.setSignatureImage(SignatureUtilities.loadSignatureImage(imagePathTextField.getText()));
        setSignatureImage();
    }


    private void setSignatureImage() {
        preferences.put(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_PATH,
                imagePathTextField.getText());
        signatureAppearanceModel.setSignatureImage(SignatureUtilities.loadSignatureImage(imagePathTextField.getText()));
    }

    private void buildAppearanceStream() {
        // todo should be set via the SwingController so it can be swapped out.
        BasicSignatureAppearanceCallback signatureAppearance =
                new BasicSignatureAppearanceCallback(signatureAppearanceModel);
        signatureWidgetAnnotation.setResetAppearanceCallback(signatureAppearance);
        signatureWidgetAnnotation.resetAppearanceStream(new AffineTransform());
        signatureWidgetComponent.repaint();
    }

    private void setSelectedCertificate(X509Certificate certificate) {
        if (certificate == null) {
            // clear metadata
            nameTextField.setText("");
            contactTextField.setText("");
            locationTextField.setText("");
            enableInputComponents(false);
            signerHandler.setCertAlias(null);
        } else {
            // pull out metadata from cert.
            X500Principal principal = certificate.getSubjectX500Principal();
            X500Name x500name = new X500Name(principal.getName());

            enableInputComponents(true);

            if (x500name.getRDNs() != null) {
                nameTextField.setText(SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.CN));
                contactTextField.setText(SignatureUtilities.parseRelativeDistinguishedName(x500name,
                        BCStyle.EmailAddress));
                String address = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.POSTAL_ADDRESS);
                if (address != null) {
                    locationTextField.setText(SignatureUtilities.parseRelativeDistinguishedName(x500name,
                            BCStyle.POSTAL_ADDRESS));
                } else {
                    String state = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.ST);
                    if (state != null) {
                        locationTextField.setText(SignatureUtilities.parseRelativeDistinguishedName(x500name,
                                BCStyle.ST));
                    }
                }

                ArrayList<String> location = new ArrayList<>(2);
                String state = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.ST);
                if (state != null) {
                    location.add(state);
                }
                String country = SignatureUtilities.parseRelativeDistinguishedName(x500name, BCStyle.C);
                if (country != null) {
                    location.add(country);
                }
                if (!location.isEmpty()) {
                    locationTextField.setText(String.join(", ", location));
                }
            }
        }
        updateModelAppearanceState();
        buildAppearanceStream();
    }

    private void buildUI() throws KeyStoreException {

        // need to build keystore right up front, so we can build out the JTable to show certs in the keychain
        PasswordDialogCallbackHandler passwordDialogCallbackHandler =
                new PasswordDialogCallbackHandler(this, messageBundle);
        signerHandler = PkcsSignerFactory.getInstance(passwordDialogCallbackHandler);

        this.setTitle(messageBundle.getString("viewer.annotation.signature.creation.dialog.title"));

        JPanel certificateSelectionPanel = buildCertificateSelectionPanel();
        JPanel signatureBuilderPanel = buildSignatureBuilderPanel();
        JTabbedPane signatureTabbedPane = new JTabbedPane();
        signatureTabbedPane.addTab(
                messageBundle.getString("viewer.annotation.signature.creation.dialog.certificate.tab.title"),
                certificateSelectionPanel);
        signatureTabbedPane.addTab(
                messageBundle.getString("viewer.annotation.signature.creation.dialog.signature.tab.title"),
                signatureBuilderPanel);

        enableInputComponents(false);

        // pack it up and go.
        getContentPane().add(signatureTabbedPane);
        pack();
        setLocationRelativeTo(getOwner());
        setResizable(true);
    }

    private JPanel buildSignatureBuilderPanel() {
        JPanel appearancePanel = new JPanel(new GridBagLayout());
        appearancePanel.setAlignmentY(JPanel.TOP_ALIGNMENT);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(2, 10, 2, 10);

        JPanel visibilityPanel = new JPanel(new GridBagLayout());
        visibilityPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        visibilityPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.annotation.signature.creation.dialog.signature.appearance.title")));
        fontNameBox = new JComboBox<>(FontWidgetUtilities.generateFontNameList(messageBundle));
        fontNameBox.addItemListener(this);
        fontSizeBox = new JComboBox<>(FontWidgetUtilities.generateFontSizeNameList(messageBundle));
        fontSizeBox.addItemListener(this);
        showTextCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.appearance.showText.label"));
        showSignatureCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.appearance.showSignature.label"));
        JLabel imagePathLabel = new JLabel(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.imagePath.label"));
        imagePathTextField = new JTextField();
        String imagePath = preferences.get(ViewerPropertiesManager.PROPERTY_SIGNATURE_IMAGE_PATH, "");

        imagePathTextField.setText(imagePath);
        if (!imagePath.isEmpty()) {
            signatureAppearanceModel.setSignatureImage(SignatureUtilities.loadSignatureImage(imagePath));
        }
        imagePathTextField.addFocusListener(this);
        // TODO add browse button

        // font name and size
        addGB(visibilityPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.signature.appearance.font.label")),
                0, 0, 1, 1);
        addGB(visibilityPanel, fontNameBox, 1, 0, 1, 1);
        addGB(visibilityPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.signature.appearance.fontSize.label")),
                2, 0, 1, 1);
        addGB(visibilityPanel, fontSizeBox, 3, 0, 1, 1);
        addGB(visibilityPanel, showTextCheckBox, 0, 1, 1, 2);
        addGB(visibilityPanel, showSignatureCheckBox, 2, 1, 1, 2);

        JPanel signaturePanel = new JPanel(new GridBagLayout());
        signaturePanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        signaturePanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.annotation.signature.creation.dialog.signature.canvas.title")));
        // image path input
        addGB(signaturePanel, imagePathLabel, 0, 0, 1, 1);
        addGB(signaturePanel, imagePathTextField, 1, 0, 1, 1);


        constraints.insets = new Insets(2, 10, 2, 10);
        addGB(appearancePanel, visibilityPanel, 0, 0, 1, 1);
        addGB(appearancePanel, signaturePanel, 0, 1, 1, 1);
        constraints.weighty = 1.0;
        addGB(appearancePanel, new Label(" "), 0, 9, 1, 1);

        return appearancePanel;
    }

    private JPanel buildCertificateSelectionPanel() throws KeyStoreException {
        JPanel certificateSelectionPanel = new JPanel(new GridBagLayout());
        certificateSelectionPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        this.setLayout(new BorderLayout());
        add(certificateSelectionPanel, BorderLayout.NORTH);

        // keystore certificate table
        Enumeration<String> aliases = signerHandler.buildKeyStore().aliases();
        CertificateTableModel certificateTableModel = new CertificateTableModel(signerHandler, aliases, messageBundle);
        certificateTable = new JTable(certificateTableModel);
        certificateTable.getSelectionModel().addListSelectionListener(this);
        certificateTable.setPreferredScrollableViewportSize(new Dimension(600, 100));
        certificateTable.setFillsViewportHeight(true);

        // certificate type selection
        // todo need to add a way to select the type of signature, signer or certifier as we can only have one certifier
        // either disable the radio box or simply override the certifier if the user selects a signer.
        signerRadioButton = new JRadioButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.type.signer.label"));
        signerRadioButton.setSelected(true);
        signerRadioButton.addActionListener(this);
        signerRadioButton.setActionCommand(SignatureType.SIGNER.toString());
        certifyRadioButton = new JRadioButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.type.certify.label"));
        certifyRadioButton.addActionListener(this);
        signerRadioButton.setActionCommand(SignatureType.CERTIFIER.toString());
        ButtonGroup certificateTypeButtonGroup = new ButtonGroup();
        certificateTypeButtonGroup.add(signerRadioButton);
        certificateTypeButtonGroup.add(certifyRadioButton);

        // signature visibility
        signerVisibilityCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.visibility.certify.label"));
        signerVisibilityCheckBox.setSelected(true);
        signerVisibilityCheckBox.addActionListener(this);

        // location and date
        locationTextField = new JTextField();
        locationTextField.addFocusListener(this);
        dateTextField = new JTextField();
        dateTextField.setEnabled(false);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date());
        dateTextField.setText(today);
        // name
        nameTextField = new JTextField();
        nameTextField.addFocusListener(this);
        // contact
        contactTextField = new JTextField();
        contactTextField.addFocusListener(this);
        // reason
        // todo remove, no longer needed as is.
        reasonTextArea = new JTextArea(4, 20);
        reasonTextArea.setLineWrap(true);
        reasonTextArea.setWrapStyleWord(true);

        // todo very much needed -> Timestamp service

        // language
        languagesComboBox = new JComboBox<>(supportedLocales);
        // be nice to do this and take into account country too.
        Locale defaultLocal = new Locale(Locale.getDefault().getLanguage());
        languagesComboBox.setSelectedItem(new Locale(Locale.getDefault().getLanguage()));
        languagesComboBox.addActionListener(this);
        signatureAppearanceModel.setLocale(defaultLocal);

        // close buttons.
        final JButton closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.close.button.label"));
        closeButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        closeButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        signButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.sign.button.label"));
        signButton.addActionListener(this);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(2, 10, 2, 10);

        // cert selection label
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.selection.label")),
                0, 0, 1, 3);

        // cert table
        addGB(certificateSelectionPanel, new JScrollPane(certificateTable), 0, 1, 1, 4);

        // type of signature.
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.type.description.label")),
                0, 2, 1, 1);
        addGB(certificateSelectionPanel, signerRadioButton, 1, 2, 1, 1);
        addGB(certificateSelectionPanel, certifyRadioButton, 2, 2, 1, 1);
        // signature visibility
        addGB(certificateSelectionPanel, signerVisibilityCheckBox, 3, 2, 1, 1);

        // location and Date
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.location.date.label")),
                0, 3, 1, 1);
        addGB(certificateSelectionPanel, locationTextField, 1, 3, 1, 1);
        addGB(certificateSelectionPanel, dateTextField, 2, 3, 1, 1);

        // name
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.name.label")),
                0, 5, 1, 1);
        addGB(certificateSelectionPanel, nameTextField, 1, 5, 1, 3);

        // contact
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.contact.label")),
                0, 6, 1, 1);
        addGB(certificateSelectionPanel, contactTextField, 1, 6, 1, 3);

        // Reason
//        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
//        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
//                        "viewer.annotation.signature.creation.dialog.certificate.reason.label")),
//                0, 7, 1, 1);
//        addGB(certificateSelectionPanel, new JScrollPane(reasonTextArea), 1, 7, 1, 3);

        // language selection
        constraints.anchor = GridBagConstraints.LINE_START;
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.i18n.label")),
                0, 8, 1, 1);
        addGB(certificateSelectionPanel, languagesComboBox, 1, 8, 1, 1);

        constraints.weighty = 1.0;
        addGB(certificateSelectionPanel, new Label(" "), 0, 9, 1, 1);

        // close and sign input
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        addGB(certificateSelectionPanel, closeButton, 0, 10, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        addGB(certificateSelectionPanel, signButton, 3, 10, 1, 1);
        return certificateSelectionPanel;
    }

    private void enableInputComponents(boolean enable) {
        nameTextField.setEnabled(enable);
        contactTextField.setEnabled(enable);
        locationTextField.setEnabled(enable);
        signerRadioButton.setEnabled(enable);
        certifyRadioButton.setEnabled(enable);
        signerVisibilityCheckBox.setEnabled(enable);
        languagesComboBox.setEnabled(enable);

        fontNameBox.setEnabled(enable);
        fontSizeBox.setEnabled(enable);
        showTextCheckBox.setEnabled(enable);
        showSignatureCheckBox.setEnabled(enable);
        imagePathTextField.setEnabled(enable);
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = colSpan;
        constraints.gridheight = rowSpan;
        layout.add(component, constraints);
    }
}
