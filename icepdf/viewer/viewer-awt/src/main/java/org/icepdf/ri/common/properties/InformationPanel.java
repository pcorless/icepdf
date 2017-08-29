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
import org.icepdf.core.pobjects.PInfo;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * Panel display of document information properties.   The panel can be used as a fragment in any user interface.
 *
 * @since 6.3
 */
public class InformationPanel extends JPanel {

    // layouts constraint
    private GridBagConstraints constraints;

    public InformationPanel(Document document,
                            ResourceBundle messageBundle) {
        // Do some work on information to get display values
        String title = "";
        String author = "";
        String subject = "";
        String keyWords = "";
        String creator = "";
        String producer = "";
        String creationDate = "";
        String modDate = "";

        // get duplicate names from message bundle
        String notAvailable =
                messageBundle.getString("viewer.dialog.documentInformation.notAvailable");

        // get information values if available
        PInfo documentInfo = document.getInfo();
        if (documentInfo != null) {
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
        }

        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);


        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTHEAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        JPanel layoutPanel = new JPanel(new GridBagLayout());

        layoutPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.documentInformation.description.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        // add labels
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.title.label")),
                0, 0, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.subject.label")),
                0, 1, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.author.label")),
                0, 2, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.keywords.label")),
                0, 3, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.creator.label")),
                0, 4, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.producer.label")),
                0, 5, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.created.label")),
                0, 6, 1, 1);
        addGB(layoutPanel, new JLabel(
                        messageBundle.getString("viewer.dialog.documentInformation.modified.label")),
                0, 7, 1, 1);

        // add values
        constraints.anchor = GridBagConstraints.NORTHWEST;
        addGB(layoutPanel, new JLabel(title), 1, 0, 1, 1);
        addGB(layoutPanel, new JLabel(subject), 1, 1, 1, 1);
        addGB(layoutPanel, new JLabel(author), 1, 2, 1, 1);
        addGB(layoutPanel, new JLabel(keyWords), 1, 3, 1, 1);
        addGB(layoutPanel, new JLabel(creator), 1, 4, 1, 1);
        addGB(layoutPanel, new JLabel(producer), 1, 5, 1, 1);
        addGB(layoutPanel, new JLabel(creationDate), 1, 6, 1, 1);
        addGB(layoutPanel, new JLabel(modDate), 1, 7, 1, 1);
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, layoutPanel, 0, 0, 1, 1);
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
