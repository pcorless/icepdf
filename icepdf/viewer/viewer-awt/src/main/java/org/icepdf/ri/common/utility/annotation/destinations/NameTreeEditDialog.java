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
package org.icepdf.ri.common.utility.annotation.destinations;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * Simple dialog used to create and edit name tree destination values.
 *
 * @since 6.3
 */
public class NameTreeEditDialog extends EscapeJDialog implements ActionListener {

    private org.icepdf.ri.common.views.Controller controller;
    private ResourceBundle messageBundle;

    private NameTreeNode nameTreeNode;
    private String name;
    private Destination destination;

    private JButton okButton;
    private JButton cancelButton;
    private JTextField nameTextField;
    private JLabel errorLabel;
    private ImplicitDestinationPanel implicitDestinationPanel;

    private GridBagConstraints constraints;

    public NameTreeEditDialog(Controller controller, Page page, int x, int y) {
        super(controller.getViewerFrame(), true);
        this.controller = controller;
        messageBundle = controller.getMessageBundle();
        destination = new Destination(page, x, y);
        setGui();
    }

    public NameTreeEditDialog(org.icepdf.ri.common.views.Controller controller, NameTreeNode treeNode) {
        super(controller.getViewerFrame(), true);
        this.controller = controller;
        nameTreeNode = treeNode;
        messageBundle = controller.getMessageBundle();
        this.name = treeNode.getName().toString();
        // figure out what the destination package is.
        this.destination = new Destination(controller.getDocument().getCatalog().getLibrary(), treeNode.getReference());
        this.destination.setNamedDestination(treeNode.getName().toString());
        setGui();
    }

    public NameTreeEditDialog(org.icepdf.ri.common.views.Controller controller, Destination destination) {
        super(controller.getViewerFrame(), true);
        this.controller = controller;
        nameTreeNode = new NameTreeNode(new LiteralStringObject(destination.getNamedDestination()),
                destination.getPageReference());
        messageBundle = controller.getMessageBundle();
        this.name = destination.getNamedDestination();
        // figure out what the destination package is.
        this.destination = destination;
        setGui();
    }

    public NameTreeNode getNameTreeNode() {
        return nameTreeNode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == okButton) {
            Catalog catalog = controller.getDocument().getCatalog();
            String oldName = nameTreeNode == null ? name : nameTreeNode.getName().toString();
            name = nameTextField.getText();
            Destination oldDestination = destination;

            destination = implicitDestinationPanel.getDestination(catalog.getLibrary());
            if (name == null) {
                errorLabel.setText(messageBundle.getString(
                        "viewer.utilityPane.destinations.dialog.error.emptyName.label"));
                return;
            } else {
                destination.setNamedDestination(name);
                errorLabel.setText("");
            }

            boolean updated;
            if (nameTreeNode == null) {
                updated = catalog.addNamedDestination(name, destination);
            } else {
                updated = catalog.updateNamedDestination(oldName, name, destination);
            }
            if (!updated) {
                errorLabel.setText(messageBundle.getString(
                        "viewer.utilityPane.destinations.dialog.error.existingName.label"));
            } else {
                // fire property change event to rebuild name tree.
                if (nameTreeNode != null) {
                    controller.getDocumentViewController().firePropertyChange(PropertyConstants.DESTINATION_UPDATED,
                            oldDestination, destination);
                } else {
                    controller.getDocumentViewController().firePropertyChange(PropertyConstants.DESTINATION_ADDED,
                            null, destination);
                }
                setVisible(false);
                dispose();
            }
        } else if (e.getSource() == cancelButton) {
            setVisible(false);
            dispose();
        }
    }

    private void setGui() {
        JPanel destinationPanel = new JPanel(new GridBagLayout());
        destinationPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        setTitle(messageBundle.getString("viewer.utilityPane.destinations.dialog.title"));
        constraints = new GridBagConstraints();
        destinationPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // ok button to save changes and close the dialog.
        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(this);
        cancelButton = new JButton(messageBundle.getString("viewer.button.cancel.label"));
        cancelButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        cancelButton.addActionListener(this);

        errorLabel = new JLabel();
        errorLabel.setForeground(Color.red);
        nameTextField = new JTextField();
        if (name != null) nameTextField.setText(name);
        implicitDestinationPanel = new ImplicitDestinationPanel(controller);
        if (destination != null) {
            implicitDestinationPanel.setDestination(destination);
        }

        // add values
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.1;
        constraints.weighty = 0.0;
        addGB(destinationPanel, new JLabel(messageBundle.getString(
                "viewer.utilityPane.destinations.dialog.name.label")), 0, 0, 1, 1);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        addGB(destinationPanel, nameTextField, 1, 0, 5, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(destinationPanel, errorLabel, 1, 1, 1, 1);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(destinationPanel, implicitDestinationPanel, 0, 2, 6, 1);
        constraints.fill = GridBagConstraints.BOTH;

        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(destinationPanel, new JLabel(""), 0, 3, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.NONE;
        addGB(destinationPanel, okButton, 1, 4, 1, 1);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(destinationPanel, cancelButton, 2, 4, 1, 1);

        this.getContentPane().add(destinationPanel);
        setSize(new Dimension(450, 250));
        setLocationRelativeTo(controller.getViewerFrame());
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
