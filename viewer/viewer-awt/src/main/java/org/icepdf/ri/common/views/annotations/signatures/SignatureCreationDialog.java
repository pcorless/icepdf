package org.icepdf.ri.common.views.annotations.signatures;

import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.EscapeJDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The SignatureCreationDialog allows users to create the appears of their signature annotations
 * and select a certificate to sign the document with
 */
public class SignatureCreationDialog extends EscapeJDialog {

    private static final Logger logger =
            Logger.getLogger(SignatureCreationDialog.class.toString());

    private GridBagConstraints constraints;

    private final SignatureValidator signatureValidator;
    protected static ResourceBundle messageBundle;
    protected final SignatureWidgetAnnotation signatureWidgetAnnotation;


    public SignatureCreationDialog(Frame parent, ResourceBundle messageBundle,
                                   SignatureWidgetAnnotation signatureWidgetAnnotation) {
        super(parent, true);
        this.messageBundle = messageBundle;
        this.signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
        this.signatureWidgetAnnotation = signatureWidgetAnnotation;
        buildUI();
    }

    private void buildUI() {


        JPanel annotationPanel = new JPanel(new GridBagLayout());
        add(annotationPanel, BorderLayout.NORTH);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 10, 10, 10);

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
            System.out.println("signing.....");

            try {
                String relativeCacertsPath = "/lib/security/cacerts".replace("/", File.separator);
                String filename = System.getProperty("java.home") + relativeCacertsPath;
                FileInputStream is = new FileInputStream(filename);

                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                String password = "changeit";
                keystore.load(is, password.toCharArray());
                Enumeration<String> aliases = keystore.aliases();
                System.out.println(aliases);

                // load PKCS12 keystore
                KeyStore myKeystore = KeyStore.getInstance("PKCS12");
                myKeystore.load(new FileInputStream("/home/pcorless/dev/cert-test/keypair/sender_keystore.pfx"),
                        password.toCharArray());
                PrivateKey privateKey = (PrivateKey) myKeystore.getKey("senderKeyPair", password.toCharArray());
                System.out.println(privateKey.getFormat());

            } catch (KeyStoreException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } catch (CertificateException ex) {
                throw new RuntimeException(ex);
            } catch (UnrecoverableKeyException ex) {
                throw new RuntimeException(ex);
            }

            setVisible(false);
            dispose();
        });

        // SignatureDictionary Information - todo, should probably persist or manage?
        // /location
        // /M
        // /ContactInfo
        // /Reason
//        JPanel signatureDictionaryPanel =
//                new SignatureValidationPanel(signatureValidationStatus, messageBundle, signatureWidgetAnnotation,
//                        signatureValidator, false, true);
//        addGB(annotationPanel, signatureDictionaryPanel, 0, 1, 2, 1);

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
