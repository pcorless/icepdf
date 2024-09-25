package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.handlers.PasswordCallbackHandler;

import javax.security.auth.callback.*;
import javax.swing.*;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.swing.JOptionPane.CLOSED_OPTION;
import static javax.swing.JOptionPane.OK_OPTION;
import static org.icepdf.ri.common.preferences.SigningPreferencesPanel.PKCS_11_TYPE;

/**
 * PasswordDialogCallbackHandler handles requesting passwords or pins when accessing a users keystore.   The password
 * is used to open the keystore as well as retrieve the private key used when signing a document.
 *
 * @since 7.3
 */
public class PasswordDialogCallbackHandler extends PasswordCallbackHandler {

    private static final Logger logger = Logger.getLogger(PasswordDialogCallbackHandler.class.getName());

    private JDialog parentComponent;
    private ResourceBundle messageBundle;
    private String dialogType;

    public PasswordDialogCallbackHandler(JDialog parentDialog, ResourceBundle messageBundle) {
        super("");
        this.parentComponent = parentDialog;
        this.messageBundle = messageBundle;
    }

    public void setType(String dialogType) {
        this.dialogType = dialogType;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback) callback;
//                pc.setPassword("changeit".toCharArray());
//                password = "changeit";
                JPanel panel = new JPanel();
                String[] options = new String[]{
                        messageBundle.getString("viewer.button.ok.label"),
                        messageBundle.getString("viewer.button.cancel.label")};
                String dialogTitle = null;
                // slightly different verbiage for pkcs11 or pks12.
                if (dialogType.equals(PKCS_11_TYPE)) {
                    dialogTitle = messageBundle.getString(
                            "viewer.annotation.signature.creation.keystore.pkcs11.dialog.title");
                    JLabel label = new JLabel(messageBundle.getString(
                            "viewer.annotation.signature.creation.keystore.pkcs11.dialog.label"));
                    panel.add(label);
                } else {
                    dialogTitle = messageBundle.getString(
                            "viewer.annotation.signature.creation.keystore.pkcs12.dialog.title");
                    JLabel label = new JLabel(messageBundle.getString(
                            "viewer.annotation.signature.creation.keystore.pkcs12.dialog.label"));
                    panel.add(label);
                }
                JPasswordField pass = new JPasswordField(15);
                panel.add(pass);
                int option = JOptionPane.showOptionDialog(parentComponent, panel,
                        dialogTitle,
                        JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                        null, options, options[0]);
                if (option == OK_OPTION) {
                    char[] password = pass.getPassword();
                    this.password = password;
                    pc.setPassword(password);
                } else if (option == CLOSED_OPTION) {
                    System.out.println("closed");
                }

            } else if (callback instanceof TextOutputCallback) {
                TextOutputCallback tc = (TextOutputCallback) callback;
                logger.log(Level.INFO,
                        "TextOutputCallback type {0} message: {1}",
                        new Object[]{tc.getMessageType(), tc.getMessage()});
                throw new UnsupportedCallbackException(callback);
            } else if (callback instanceof NameCallback) {
                throw new UnsupportedCallbackException(callback);
            } else {
                logger.log(Level.WARNING,
                        "Unknown callback type {0}",
                        callback.getClass().getName());
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}
