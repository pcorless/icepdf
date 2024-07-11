package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.handlers.PasswordCallbackHandler;

import javax.security.auth.callback.*;
import javax.swing.*;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PasswordDialogCallbackHandler extends PasswordCallbackHandler {

    private static final Logger logger = Logger.getLogger(PasswordDialogCallbackHandler.class.getName());

    private JDialog parentComponent;
    private ResourceBundle messageBundle;
    private String dialogType;

    public PasswordDialogCallbackHandler(JDialog parentDialog, ResourceBundle messageBundle) {
        super(null);
        this.parentComponent = parentDialog;
        this.messageBundle = messageBundle;
    }

    // todo really need a enum to handle the repeat of this constant
    public void setType(String dialogType) {
        this.dialogType = dialogType;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback) callback;
                pc.setPassword("changeit".toCharArray());
//                JPanel panel = new JPanel();
//                // todo setup i18n, and need to look at the dialog type, pin vs. password verbiage.
//                JLabel label = new JLabel("Enter keystore password:");
//                JPasswordField pass = new JPasswordField(15);
//                panel.add(label);
//                panel.add(pass);
//                String[] options = new String[]{"OK", "Cancel"};
//                int option = JOptionPane.showOptionDialog(parentComponent, panel, "Keystore Password",
//                        JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
//                        null, options, options[0]);
//                if (option == OK_OPTION) {
//                    char[] password = pass.getPassword();
//                    System.out.println("Your password is: " + new String(password));
//                    pc.setPassword(password);
//                } else if (option == CLOSED_OPTION) {
//                    System.out.println("closed");
//                }

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
