package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.handlers.SignerHandler;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.EscapeJDialog;

import javax.swing.*;
import java.awt.*;
import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The SignatureCreationDialog allows users to select an available signing certificate and customize various setting
 * associated with signing a document.
 */
public class SignatureCreationDialog extends EscapeJDialog {

    private static final Logger logger =
            Logger.getLogger(SignatureCreationDialog.class.toString());

    private GridBagConstraints constraints;

    private final SignatureValidator signatureValidator;
    protected static ResourceBundle messageBundle;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;


    public SignatureCreationDialog(Frame parent, ResourceBundle messageBundle,
                                   SignatureWidgetAnnotation signatureWidgetAnnotation) throws KeyStoreException {
        super(parent, true);
        this.messageBundle = messageBundle;
        this.signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    private void buildUI() throws KeyStoreException {

        // todo need to build keystore right up front so we can build out the JTable to show certs in the keychain
        final JDialog parent = this;
        PasswordDialogCallbackHandler passwordDialogCallbackHandler =
                new PasswordDialogCallbackHandler(parent, messageBundle);
        SignerHandler signerHandler = PkcsSignerFactory.getInstance(passwordDialogCallbackHandler);

        JPanel annotationPanel = new JPanel(new GridBagLayout());
        add(annotationPanel, BorderLayout.NORTH);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 10, 10, 10);

        // keystore certificate table
        Enumeration<String> aliases = signerHandler.buildKeyStore().aliases();
        CertificateTableModel certificateTableModel = new CertificateTableModel(signerHandler, aliases, messageBundle);
        JTable certTable = new JTable(certificateTableModel);
        certTable.setPreferredScrollableViewportSize(new Dimension(500, 70));
        certTable.setFillsViewportHeight(true);

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

        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        addGB(annotationPanel, new JScrollPane(certTable), 0, 0, 1, 3);


        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        addGB(annotationPanel, signerButton, 0, 3, 1, 1);

        constraints.anchor = GridBagConstraints.EAST;
        constraints.weightx = 1.0;
        addGB(annotationPanel, closeButton, 1, 3, 1, 1);

        // pack it up and go.
        getContentPane().add(annotationPanel);
        pack();
        setLocationRelativeTo(getOwner());
        setResizable(false);
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
