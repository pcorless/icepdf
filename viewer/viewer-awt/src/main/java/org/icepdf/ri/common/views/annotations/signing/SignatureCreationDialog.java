package org.icepdf.ri.common.views.annotations.signing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureAppearanceCallback;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.acroform.signature.utils.SignatureUtilities;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.SignatureDictionaries;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.utility.annotation.properties.FontWidgetUtilities;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.acroform.SignatureComponent;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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

/**
 * The SignatureCreationDialog allows users to select an available signing certificate and customize various setting
 * associated with signing a document.
 */
public class SignatureCreationDialog extends EscapeJDialog implements ActionListener, ListSelectionListener,
        ItemListener, FocusListener, ChangeListener {

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
    private JTextField nameTextField;
    private JTextField contactTextField;

    private JComboBox<ValueLabelItem> fontNameBox;
    private JComboBox<ValueLabelItem> fontSizeBox;
    private JCheckBox showTextCheckBox;
    private JCheckBox showSignatureCheckBox;
    private JTextField imagePathTextField;
    private JButton imagePathBrowseButton;
    private JSlider imageScaleSlider;

    private JComboBox<Locale> languagesComboBox;
    private JButton signButton;
    private JButton closeButton;

    private SignerHandler signerHandler;

    private final SignatureAppearanceCallback signatureAppearanceCallback;
    private final SignatureAppearanceModelImpl signatureAppearanceModel;

    protected static ResourceBundle messageBundle;
    protected final SignatureComponent signatureWidgetComponent;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;

    public SignatureCreationDialog(Controller controller, ResourceBundle messageBundle,
                                   SignatureComponent signatureComponent) throws KeyStoreException {
        super(controller.getViewerFrame(), true);
        SignatureCreationDialog.messageBundle = messageBundle;
        this.signatureWidgetComponent = signatureComponent;
        this.signatureWidgetAnnotation = signatureComponent.getAnnotation();

        signatureAppearanceCallback = controller.getDocumentViewController().getSignatureAppearanceCallback();
        signatureAppearanceModel = new SignatureAppearanceModelImpl(signatureComponent.getAnnotation().getLibrary());
        signatureAppearanceCallback.setSignatureAppearanceModel(signatureAppearanceModel);
        signatureWidgetAnnotation.setAppearanceCallback(signatureAppearanceCallback);

        buildUI();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {

        Object source = actionEvent.getSource();
        if (source == null) return;

        if (source == signButton) {
            Library library = signatureWidgetAnnotation.getLibrary();
            SignatureDictionaries signatureDictionaries = library.getSignatureDictionaries();

            // set up signer dictionary as the primary certification signer.
            SignatureDictionary signatureDictionary;
            if (signerRadioButton.isSelected()) {
                signatureDictionary = SignatureDictionary.getInstance(signatureWidgetAnnotation, SignatureType.SIGNER);
                signatureDictionaries.addSignerSignature(signatureDictionary);
            } else {
                if (signatureDictionaries.hasExistingCertifier()) {
                    JOptionPane.showMessageDialog(this,
                            messageBundle.getString("viewer.annotation.signature.creation.dialog.certify.error.msg"),
                            messageBundle.getString("viewer.annotation.signature.creation.dialog.certify.error.title"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                signatureDictionary = SignatureDictionary.getInstance(signatureWidgetAnnotation,
                        SignatureType.CERTIFIER);
                signatureDictionaries.addCertifierSignature(signatureDictionary);
            }
            signatureDictionary.setSignerHandler(signerHandler);

            // assign original values from cert
            signatureDictionary.setName(nameTextField.getText());
            signatureDictionary.setContactInfo(contactTextField.getText());
            signatureDictionary.setLocation(locationTextField.getText());
            signatureDictionary.setReason(signerRadioButton.isSelected() ?
                    SignatureType.SIGNER.toString().toLowerCase() :
                    SignatureType.CERTIFIER.toString().toLowerCase());
            buildAppearanceStream();

            setVisible(false);
            dispose();
        } else if (source == closeButton) {
            // clean anything we set up and just leave the signature with an empty dictionary
            signatureAppearanceCallback.removeAppearanceStream(signatureWidgetAnnotation, new AffineTransform(), true);
            signatureWidgetAnnotation.setAppearanceCallback(null);
            setVisible(false);
            dispose();
        } else if (source == imagePathTextField) {
            setSignatureImage();
            buildAppearanceStream();
        } else if (source == signerRadioButton) {
            signatureAppearanceModel.setSignatureType(SignatureType.SIGNER);
        } else if (source == certifyRadioButton) {
            signatureAppearanceModel.setSignatureType(SignatureType.CERTIFIER);
        } else if (source == signerVisibilityCheckBox) {
            signatureAppearanceModel.setSignatureVisible(signerVisibilityCheckBox.isSelected());
            buildAppearanceStream();
        } else if (source == languagesComboBox) {
            signatureAppearanceModel.setLocale((Locale) languagesComboBox.getSelectedItem());
            buildAppearanceStream();
        } else if (source == showTextCheckBox) {
            signatureAppearanceModel.setSignatureTextVisible(showTextCheckBox.isSelected());
            buildAppearanceStream();
        } else if (source == showSignatureCheckBox) {
            signatureAppearanceModel.setSignatureImageVisible(showSignatureCheckBox.isSelected());
            buildAppearanceStream();
        } else if (source == imagePathBrowseButton) {
            String imagePath = signatureAppearanceModel.getSignatureImagePath();
            JFileChooser fileChooser = new JFileChooser(imagePath);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setMultiSelectionEnabled(false);
            fileChooser.setDialogTitle(messageBundle.getString(
                    "viewer.annotation.signature.creation.dialog.signature.selection.title"));
            final int responseValue = fileChooser.showDialog(this, messageBundle.getString(
                    "viewer.annotation.signature.creation.dialog.signature.selection.accept.label"));
            if (responseValue == JFileChooser.APPROVE_OPTION) {
                imagePathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                setSignatureImage();
                buildAppearanceStream();
            }
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) {
            return;
        }
        if (e.getSource() == fontSizeBox) {
            ValueLabelItem item = (ValueLabelItem) fontSizeBox.getSelectedItem();
            if (item != null) {
                int fontSize = (int) item.getValue();
                signatureAppearanceModel.setFontSize(fontSize);
                buildAppearanceStream();
            }
        } else if (e.getSource() == fontNameBox) {
            ValueLabelItem item = (ValueLabelItem) fontNameBox.getSelectedItem();
            if (item != null) {
                String fontName = item.getValue().toString();
                signatureAppearanceModel.setFontName(fontName);
                buildAppearanceStream();
            }
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();
        if (!source.getValueIsAdjusting()) {
            int scale = source.getValue();
            signatureAppearanceModel.setImageScale(scale);
            buildAppearanceStream();
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
        buildAppearanceStream();
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
        signatureAppearanceModel.setFontName(Objects.requireNonNull(fontNameBox.getSelectedItem()).toString());
        signatureAppearanceModel.setFontSize((int) ((ValueLabelItem) Objects.requireNonNull(fontSizeBox.getSelectedItem())).getValue());
        signatureAppearanceModel.setSignatureImagePath(imagePathTextField.getText());
        signatureAppearanceModel.setImageScale(imageScaleSlider.getValue());
        setSignatureImage();
    }


    private void setSignatureImage() {
        signatureAppearanceModel.setSignatureImagePath(imagePathTextField.getText());
    }

    private void buildAppearanceStream() {

        signatureWidgetAnnotation.resetAppearanceStream(new AffineTransform());
        signatureWidgetComponent.repaint();
    }

    private void setSelectedCertificate(X509Certificate certificate) {
        signatureAppearanceModel.setSelectedCertificate(certificate != null);
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
        // font name setup
        String fontName = signatureAppearanceModel.getFontName();
        ValueLabelItem[] fontNameItems = FontWidgetUtilities.generateFontNameList(messageBundle);
        fontNameBox = new JComboBox<>(fontNameItems);
        fontNameBox.setSelectedItem(Arrays.stream(fontNameItems).filter(t -> t.getValue() == fontName).findAny().orElse(fontNameItems[0]));
        fontNameBox.addItemListener(this);

        // font size setup
        int fontSize = signatureAppearanceModel.getFontSize();
        ValueLabelItem[] fontSizeItems = FontWidgetUtilities.generateFontSizeNameList(messageBundle);
        fontSizeBox = new JComboBox<>(fontSizeItems);
        fontSizeBox.setSelectedItem(Arrays.stream(fontSizeItems).filter(t -> (int) t.getValue() == fontSize).findAny().orElse(fontSizeItems[0]));
        fontSizeBox.addItemListener(this);

        // show text on signature appearance stream
        boolean showText = signatureAppearanceModel.isSignatureTextVisible();
        showTextCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.appearance.showText.label"), showText);
        showTextCheckBox.addActionListener(this);

        // show image on signature appearance stream
        boolean showImage = signatureAppearanceModel.isSignatureImageVisible();
        showSignatureCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.appearance.showSignature.label"), showImage);
        showSignatureCheckBox.addActionListener(this);

        // image path
        JLabel imagePathLabel = new JLabel(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.imagePath.label"));
        imagePathTextField = new JTextField();
        String imagePath = signatureAppearanceModel.getSignatureImagePath();

        imagePathTextField.setText(imagePath);
        if (!imagePath.isEmpty()) {
            signatureAppearanceModel.setSignatureImagePath(imagePath);
        }
        imagePathTextField.addFocusListener(this);
        imagePathBrowseButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.selection.browse.label"));
        imagePathBrowseButton.addActionListener(this);

        // image scale
        int imageScale = signatureAppearanceModel.getImageScale();
        JLabel imageScaleLabel = new JLabel(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.signature.imageScale.label"));
        imageScaleSlider = new JSlider(JSlider.HORIZONTAL, 0, 300, imageScale);
        imageScaleSlider.setMajorTickSpacing(50);
        imageScaleSlider.setPaintLabels(true);

        imageScaleSlider.setPaintTicks(true);
        imageScaleSlider.addChangeListener(this);

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
        addGB(signaturePanel, imagePathBrowseButton, 2, 0, 1, 1);
        addGB(signaturePanel, imageScaleLabel, 0, 1, 1, 1);
        addGB(signaturePanel, imageScaleSlider, 1, 1, 1, 2);


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
        JTextField dateTextField = new JTextField();
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

        // todo very much needed -> Timestamp service

        // language
        languagesComboBox = new JComboBox<>(supportedLocales);
        // be nice to do this and take into account country too.
        Locale defaultLocal = new Locale(Locale.getDefault().getLanguage());
        languagesComboBox.setSelectedItem(new Locale(Locale.getDefault().getLanguage()));
        languagesComboBox.addActionListener(this);
        signatureAppearanceModel.setLocale(defaultLocal);

        // close buttons.
        closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.close.button.label"));
        closeButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        closeButton.addActionListener(this);
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
