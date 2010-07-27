/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.pobjects.security.SecurityManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * This class is a reference implementation for displaying a PDF document's
 * associated security permissions.
 *
 * @since 1.1
 */
public class PermissionsDialog extends JDialog {

    // layouts constraint
    private GridBagConstraints constraints;


    /**
     * Creates the permissions dialog.
     */
    public PermissionsDialog(JFrame frame, Document document,
                             ResourceBundle messageBundle) {
        super(frame, true);
        setTitle(messageBundle.getString("viewer.dialog.documentPermissions.title"));

        // get common values for permissions values
        String none = messageBundle.getString("viewer.dialog.documentPermissions.none");
        String no = messageBundle.getString("viewer.dialog.documentPermissions.no");
        String yes = messageBundle.getString("viewer.dialog.documentPermissions.yes");
        String fullyAllowed = messageBundle.getString("viewer.dialog.documentPermissions.fullyAllowed");
        String notAllowed = messageBundle.getString("viewer.dialog.documentPermissions.notAllowed");
        String allowed = messageBundle.getString("viewer.dialog.documentPermissions.allowed");
        String standardSecurity = messageBundle.getString("viewer.dialog.documentPermissions.standardSecurity");
        String lowQuality = messageBundle.getString("viewer.dialog.documentPermissions.partial");
        String securityLevel = messageBundle.getString("viewer.dialog.documentPermissions.securityLevel");


        // Do some work on Permissions to get display values
        String securityMethod = none;
        String userPassword = no;
        String ownerPassword = no;
        String printing = fullyAllowed;
        String changing = allowed;
        String extraction = allowed;
        String authoring = allowed;
        String forms = allowed;
        String accessibility = allowed;
        String assembly = allowed;
        String level = none;

        // get permission values if available

        SecurityManager securityManager = document.getSecurityManager();
        if (securityManager != null) {
            Permissions permissions = securityManager.getPermissions();
            if (permissions != null) {
                // currenly only adobe standard security supported
                securityMethod = standardSecurity;

                // Get user and owner passwords
                if (!securityManager.getSecurityHandler().isUserAuthorized("")) {
                    userPassword = yes;
                }
                if (!securityManager.getSecurityHandler().isOwnerAuthorized("")) {
                    ownerPassword = yes;
                }
                if (!permissions.getPermissions(Permissions.PRINT_DOCUMENT)) {
                    if (!permissions.getPermissions(Permissions.PRINT_DOCUMENT_QUALITY)) {
                        printing = lowQuality;
                    } else {
                        printing = notAllowed;
                    }
                }
                if (!permissions.getPermissions(Permissions.MODIFY_DOCUMENT)) {
                    changing = notAllowed;
                }
                if (!permissions.getPermissions(Permissions.CONTENT_EXTRACTION)) {
                    extraction = notAllowed;
                }
                if (!permissions.getPermissions(Permissions.AUTHORING_FORM_FIELDS)) {
                    authoring = notAllowed;
                }
                if (!permissions.getPermissions(Permissions.FORM_FIELD_FILL_SIGNING)) {
                    forms = notAllowed;
                }
                if (!permissions.getPermissions(Permissions.CONTENT_ACCESSABILITY)) {
                    accessibility = notAllowed;
                }
                if (!permissions.getPermissions(Permissions.DOCUMENT_ASSEMBLY)) {
                    assembly = notAllowed;
                }

                // Figure level of encryption
                int length = securityManager.getEncryptionDictionary().getKeyLength();
                Object[] messageArguments = new Object[]{
                        String.valueOf(length),
                        String.valueOf(securityManager.getEncryptionDictionary().getVersion()),
                        String.valueOf(securityManager.getEncryptionDictionary().getRevisionNumber())
                };
                MessageFormat formatter = new MessageFormat(securityLevel);
                level = formatter.format(messageArguments);

            }
        }

        // Create GUI elements
        final JButton okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == okButton) {
                    setVisible(false);
                    dispose();
                }

            }
        });

        /**
         * Place GUI elements on dialog
         */

        JPanel permissionsPanel = new JPanel();

        permissionsPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        GridBagLayout layout = new GridBagLayout();
        permissionsPanel.setLayout(layout);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // add labels
        addGB(permissionsPanel, new JLabel("Security Method:"), 0, 0, 1, 1);
        addGB(permissionsPanel, new JLabel("User Password:"), 0, 1, 1, 1);
        addGB(permissionsPanel, new JLabel("Owner Password:"), 0, 2, 1, 1);
        addGB(permissionsPanel, new JLabel("Printing:"), 0, 3, 1, 1);
        addGB(permissionsPanel, new JLabel("Changing the Document:"), 0, 4, 1, 1);
        addGB(permissionsPanel, new JLabel("Content Copying or Extraction:"), 0, 5, 1, 1);
        addGB(permissionsPanel, new JLabel("Authoring Comments and Form Fields:"), 0, 6, 1, 1);
        addGB(permissionsPanel, new JLabel("Form Field Fill-in or Signing:"), 0, 7, 1, 1);
        addGB(permissionsPanel, new JLabel("Content Accessibility Enabled:"), 0, 8, 1, 1);
        addGB(permissionsPanel, new JLabel("Document Assembly:"), 0, 9, 1, 1);
        addGB(permissionsPanel, new JLabel("Encryption Level:"), 0, 10, 1, 1);
        constraints.insets = new Insets(15, 5, 5, 5);
        constraints.anchor = GridBagConstraints.CENTER;
        addGB(permissionsPanel, okButton, 0, 11, 2, 1);

        // add values
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(permissionsPanel, new JLabel(securityMethod), 1, 0, 1, 1);
        addGB(permissionsPanel, new JLabel(userPassword), 1, 1, 1, 1);
        addGB(permissionsPanel, new JLabel(ownerPassword), 1, 2, 1, 1);
        addGB(permissionsPanel, new JLabel(printing), 1, 3, 1, 1);
        addGB(permissionsPanel, new JLabel(changing), 1, 4, 1, 1);
        addGB(permissionsPanel, new JLabel(extraction), 1, 5, 1, 1);
        addGB(permissionsPanel, new JLabel(authoring), 1, 6, 1, 1);
        addGB(permissionsPanel, new JLabel(forms), 1, 7, 1, 1);
        addGB(permissionsPanel, new JLabel(accessibility), 1, 8, 1, 1);
        addGB(permissionsPanel, new JLabel(assembly), 1, 9, 1, 1);
        addGB(permissionsPanel, new JLabel(level), 1, 10, 1, 1);

        this.getContentPane().add(permissionsPanel);

        pack();
        setLocationRelativeTo(frame);
    }

    /**
     * Override createRootPane so that "escape" key can be used to
     * close this window.
     */
    protected JRootPane createRootPane() {
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                setVisible(false);
                dispose();
            }
        };
        JRootPane rootPane = new JRootPane();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        rootPane.registerKeyboardAction(actionListener, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
        return rootPane;
    }

    /**
     * Gridbag constructor helper
     *
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan
     * @param colSpan
     */
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
