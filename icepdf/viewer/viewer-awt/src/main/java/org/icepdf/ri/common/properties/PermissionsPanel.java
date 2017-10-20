/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.properties;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.pobjects.security.SecurityManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Panel display of document permission properties.   The panel can be used as a fragment in any user interface.
 *
 * @since 6.3
 */
public class PermissionsPanel extends JPanel {

    // layouts constraint
    private GridBagConstraints constraints;

    public PermissionsPanel(Document document, ResourceBundle messageBundle) {

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
                // currently only adobe standard security supported
                securityMethod = standardSecurity;

                // Get user and owner passwords, owner first, just encase it has a password.
                if (!securityManager.getSecurityHandler().isOwnerAuthorized("")) {
                    ownerPassword = yes;
                }
                if (!securityManager.getSecurityHandler().isUserAuthorized("")) {
                    userPassword = yes;
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

        setAlignmentY(JPanel.TOP_ALIGNMENT);
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // layout the document security section
        JPanel documentSecurityPanel = new JPanel(new GridBagLayout());
        documentSecurityPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.documentPermissions.security.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(documentSecurityPanel,
                new JLabel(messageBundle.getString("viewer.dialog.documentPermissions.securityMethod.label")),
                0, 0, 1, 1);
        addGB(documentSecurityPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.userPassword.label")),
                0, 1, 1, 1);
        addGB(documentSecurityPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.ownerPassword.label")),
                0, 2, 1, 1);
        addGB(documentSecurityPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.encryptionLevel.label")),
                0, 3, 1, 1);

        // layout the document restrictions section
        JPanel documentRestrictionsPanel = new JPanel(new GridBagLayout());
        documentRestrictionsPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.documentPermissions.restrictions.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.printing.label")),
                0, 0, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.changing.label")),
                0, 1, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.copyExtraction.label")),
                0, 2, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.comments.label")),
                0, 3, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.formFillingIn.label")),
                0, 4, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.accessibility.label")),
                0, 5, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentPermissions.assembly.label")),
                0, 6, 1, 1);

        // add the root security panels
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, documentSecurityPanel, 0, 0, 1, 1);
        addGB(this, documentRestrictionsPanel, 0, 1, 1, 1);

        // setup value constrains values
        constraints.anchor = GridBagConstraints.WEST;
        // security
        addGB(documentSecurityPanel, new JLabel(securityMethod), 1, 0, 1, 1);
        addGB(documentSecurityPanel, new JLabel(userPassword), 1, 1, 1, 1);
        addGB(documentSecurityPanel, new JLabel(ownerPassword), 1, 2, 1, 1);
        addGB(documentSecurityPanel, new JLabel(level), 1, 3, 1, 1);
        // restrictions
        addGB(documentRestrictionsPanel, new JLabel(printing), 1, 0, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(changing), 1, 1, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(extraction), 1, 2, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(authoring), 1, 3, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(forms), 1, 4, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(accessibility), 1, 5, 1, 1);
        addGB(documentRestrictionsPanel, new JLabel(assembly), 1, 6, 1, 1);



    }

    protected void addGB(JPanel layout, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
