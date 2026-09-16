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
package org.icepdf.ri.common.views.annotations.signing;

import org.icepdf.core.pobjects.acroform.signature.handlers.PasswordCallbackHandler;

import javax.security.auth.callback.*;
import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
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

    /**
     * Shows the password prompt with the caret already in the password field, so that the password
     * can simply be typed.
     * <p>
     * Built rather than gone through {@link JOptionPane#showOptionDialog} because the focus is the
     * point: an option pane puts the focus on its default button as it opens, through
     * {@link JOptionPane#selectInitialValue()}, and it does so after the dialog is shown - so asking
     * for the focus beforehand, or queueing the request as the field is added, is simply overridden
     * a moment later.  Overriding that method is the hook the option pane provides for it.
     *
     * @param panel   contents of the prompt, which includes {@code password}
     * @param field   the field to put the caret in
     * @param title   dialog title
     * @param options button labels, the first of which is the default
     * @return the index of the button pressed, or {@link JOptionPane#CLOSED_OPTION} if the prompt
     * was dismissed - the same values {@code showOptionDialog} returns
     */
    private int showPasswordDialog(JPanel panel, JComponent field, String title, String[] options) {
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.YES_NO_OPTION, null, options, options[0]) {
            @Override
            public void selectInitialValue() {
                field.requestFocusInWindow();
            }
        };
        JDialog dialog = optionPane.createDialog(parentComponent, title);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }
        Object selected = optionPane.getValue();
        int option = selected == null ? CLOSED_OPTION : Arrays.asList(options).indexOf(selected);
        return option < 0 ? CLOSED_OPTION : option;
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
                String dialogTitle;
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
                int option = showPasswordDialog(panel, pass, dialogTitle, options);
                if (option == OK_OPTION) {
                    char[] password = pass.getPassword();
                    this.password = password;
                    pc.setPassword(password);
                }

            } else if (callback instanceof TextOutputCallback) {
                TextOutputCallback tc = (TextOutputCallback) callback;
                logger.log(Level.WARNING,
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
