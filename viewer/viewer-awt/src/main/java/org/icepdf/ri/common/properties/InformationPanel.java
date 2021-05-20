/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.security.Permissions;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Panel display of document information properties.   The panel can be used as a fragment in any user interface.
 *
 * @since 6.3
 */
public class InformationPanel extends JPanel {

    // layouts constraint
    private final GridBagConstraints constraints;
    private final Map<String, JTextField> rows;

    public InformationPanel(final Document document,
                            final ResourceBundle messageBundle) {
        // Do some work on information to get display values
        rows = new HashMap<>(8);
        final String title;
        final String author;
        final String subject;
        final String keyWords;
        final String creator;
        final String producer;
        final String creationDate;
        final String modDate;
        final String path;

        // get duplicate names from message bundle
        final String notAvailable =
                messageBundle.getString("viewer.dialog.documentInformation.notAvailable");

        // get information values if available
        final PInfo documentInfo = document.createOrGetInfo();
        title = documentInfo.getTitle();
        author = documentInfo.getAuthor();
        subject = documentInfo.getSubject();
        keyWords = documentInfo.getKeywords();
        creator = documentInfo.getCreator() != null ?
                documentInfo.getCreator() : notAvailable;
        producer = documentInfo.getProducer() != null ?
                documentInfo.getProducer() : notAvailable;
        creationDate = documentInfo.getCreationDate() != null ?
                documentInfo.getCreationDate().toString() : notAvailable;
        modDate = documentInfo.getModDate() != null ?
                documentInfo.getModDate().toString() : notAvailable;

        final String origin = document.getDocumentOrigin();
        if (origin == null || origin.isEmpty()) {
            final String location = document.getDocumentLocation();
            if (location == null || location.isEmpty()) {
                path = "";
            } else {
                path = location;
            }
        } else {
            path = origin;
        }
        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);


        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTHEAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        final JPanel layoutPanel = new JPanel(new GridBagLayout());

        layoutPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.documentInformation.description.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        // add labels
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.title.label")),
                0, 0, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.subject.label")),
                0, 1, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.author.label")),
                0, 2, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.keywords.label")),
                0, 3, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.creator.label")),
                0, 4, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.producer.label")),
                0, 5, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.created.label")),
                0, 6, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.modified.label")),
                0, 7, 1, 1);
        addGB(layoutPanel, new JLabel(messageBundle.getString("viewer.dialog.documentInformation.path.label")),
                0, 8, 1, 1);

        final boolean canModify = document.getSecurityManager() == null ||
                document.getSecurityManager().getPermissions().getPermissions(Permissions.MODIFY_DOCUMENT);
        // add values
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 5, 5, 5);
        addEditableRow(layoutPanel, PInfo.TITLE_KEY.getName(), createTextField(title, canModify), 1, 0, 1, 1);
        addEditableRow(layoutPanel, PInfo.SUBJECT_KEY.getName(), createTextField(subject, canModify), 1, 1, 1, 1);
        addEditableRow(layoutPanel, PInfo.AUTHOR_KEY.getName(), createTextField(author, canModify), 1, 2, 1, 1);
        addEditableRow(layoutPanel, PInfo.KEYWORDS_KEY.getName(), createTextField(keyWords, canModify), 1, 3, 1, 1);
        addEditableRow(layoutPanel, PInfo.CREATOR_KEY.getName(), createTextField(creator, canModify), 1, 4, 1, 1);
        addEditableRow(layoutPanel, PInfo.PRODUCER_KEY.getName(), createTextField(producer, canModify), 1, 5, 1, 1);
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(layoutPanel, new JLabel(creationDate), 1, 6, 1, 1);
        addGB(layoutPanel, new JLabel(modDate), 1, 7, 1, 1);
        addGB(layoutPanel, new JLabel(path), 1, 8, 1, 1);
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, layoutPanel, 0, 0, 1, 1);
    }

    /**
     * @return The map of key->value shown in the panel
     */
    Map<String, String> getProperties() {
        return rows.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getText()));
    }

    private static JTextField createTextField(final String content, final boolean canModify) {
        final JTextField textField = new JTextField(content);
        textField.setEnabled(canModify);
        return textField;
    }

    private void addEditableRow(final JPanel layout, final String key, final JTextField textField,
                                final int x, final int y,
                                final int rowSpan, final int colSpan) {
        addGB(layout, textField, x, y, rowSpan, colSpan);
        rows.put(key, textField);
    }

    private void addGB(final JPanel layout, final Component component,
                       final int x, final int y,
                       final int rowSpan, final int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
