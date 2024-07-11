package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.appearance.SignatureType;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.EscapeJDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The SignatureCreationDialog allows users to select an available signing certificate and customize various setting
 * associated with signing a document.
 */
public class SignatureCreationDialog extends EscapeJDialog implements ActionListener {

    private static final Logger logger =
            Logger.getLogger(SignatureCreationDialog.class.toString());

    private GridBagConstraints constraints;

    private final SignatureValidator signatureValidator;
    protected static ResourceBundle messageBundle;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;

    private String SIGNATURE_TYPE_SIGNER = "signer";
    private String SIGNATURE_TYPE_CERTIFY = "certify";


    public SignatureCreationDialog(Frame parent, ResourceBundle messageBundle,
                                   SignatureWidgetAnnotation signatureWidgetAnnotation) throws KeyStoreException {
        super(parent, true);
        this.messageBundle = messageBundle;
        this.signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {

    }

    private void buildUI() throws KeyStoreException {

        // need to build keystore right up front, so we can build out the JTable to show certs in the keychain
        final JDialog parent = this;
        PasswordDialogCallbackHandler passwordDialogCallbackHandler =
                new PasswordDialogCallbackHandler(parent, messageBundle);
        SignerHandler signerHandler = PkcsSignerFactory.getInstance(passwordDialogCallbackHandler);

        this.setTitle(messageBundle.getString("viewer.annotation.signature.creation.dialog.title"));

        JPanel certificateSelectionPanel = new JPanel(new GridBagLayout());
        certificateSelectionPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        this.setLayout(new BorderLayout());
        add(certificateSelectionPanel, BorderLayout.NORTH);

        // keystore certificate table
        Enumeration<String> aliases = signerHandler.buildKeyStore().aliases();
        CertificateTableModel certificateTableModel = new CertificateTableModel(signerHandler, aliases, messageBundle);
        JTable certTable = new JTable(certificateTableModel);
        certTable.setPreferredScrollableViewportSize(new Dimension(600, 100));
        certTable.setFillsViewportHeight(true);

        // certificate type selection
        JRadioButton signerRadioButton = new JRadioButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.type.signer.label"));
        signerRadioButton.addActionListener(this);
        signerRadioButton.setActionCommand(SignatureType.SIGNER.toString());
        JRadioButton certifyRadioButton = new JRadioButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.type.certify.label"));
        certifyRadioButton.addActionListener(this);
        signerRadioButton.setActionCommand(SignatureType.CERTIFIER.toString());
        ButtonGroup certificateTypeButtonGroup = new ButtonGroup();
        certificateTypeButtonGroup.add(signerRadioButton);
        certificateTypeButtonGroup.add(certifyRadioButton);

        // signature visibility
        JCheckBox signerVisibilityCheckBox = new JCheckBox(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.certificate.visibility.certify.label"));

        // location and date
        JTextField locationTextField = new JTextField();
        JTextField dateTextField = new JTextField();
        // contact
        JTextField contactTextField = new JTextField();
        // reason
        JTextArea responseTextField = new JTextArea(4, 20);
        responseTextField.setLineWrap(true);
        responseTextField.setWrapStyleWord(true);

        // todo Timestamp

        // language
        Locale[] supportedLocales = {
                new Locale("en", "CA"),
                new Locale("fr", "FR")
        };
        JComboBox<Locale> languagesComboBox = new JComboBox<>(supportedLocales);

        // close buttons.
        final JButton closeButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.close.button.label"));
        closeButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        closeButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        final JButton signerButton = new JButton(messageBundle.getString(
                "viewer.annotation.signature.creation.dialog.sign.button.label"));

        signerButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);

        // cert selection label
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.selection.label")),
                0, 0, 1, 3);

        // cert table
        addGB(certificateSelectionPanel, new JScrollPane(certTable), 0, 1, 1, 4);

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

        // contact
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.contact.label")),
                0, 4, 1, 1);
        addGB(certificateSelectionPanel, contactTextField, 1, 4, 1, 3);

        // Reason
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.reason.label")),
                0, 5, 1, 1);
        addGB(certificateSelectionPanel, responseTextField, 1, 5, 1, 3);

        // language selection
        constraints.anchor = GridBagConstraints.LINE_START;
        addGB(certificateSelectionPanel, new JLabel(messageBundle.getString(
                        "viewer.annotation.signature.creation.dialog.certificate.i18n.label")),
                0, 6, 1, 1);
        addGB(certificateSelectionPanel, languagesComboBox, 1, 6, 1, 1);

        constraints.weighty = 1.0;
        addGB(certificateSelectionPanel, new Label(" "), 0, 7, 1, 1);

        // close and sign input
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        addGB(certificateSelectionPanel, closeButton, 0, 8, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        addGB(certificateSelectionPanel, signerButton, 3, 8, 1, 1);



        // pack it up and go.
        getContentPane().add(certificateSelectionPanel);
        pack();
        setLocationRelativeTo(getOwner());
        setResizable(true);
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
