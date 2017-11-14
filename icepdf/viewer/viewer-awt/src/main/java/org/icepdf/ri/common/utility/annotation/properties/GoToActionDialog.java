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
package org.icepdf.ri.common.utility.annotation.properties;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.actions.ActionFactory;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.utility.annotation.destinations.ImplicitDestinationPanel;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ResourceBundle;

/**
 * GoTo Action panel used for setting an GoTo Action type properties.  GoTo
 * actions store a PDF Destination data structure which can either be a named
 * destination or a vector of properties that specifies a page location.
 *
 * @since 4.0
 */
@SuppressWarnings("serial")
public class GoToActionDialog extends AnnotationDialogAdapter
        implements ActionListener, ItemListener {

    public static final String EMPTY_DESTINATION = "      ";

    private org.icepdf.ri.common.views.Controller controller;
    private ResourceBundle messageBundle;
    private AnnotationComponent currentAnnotation;
    private ActionsPanel actionsPanel;

    // state full ui elements.
    private GridBagConstraints constraints;
    private JButton okButton;
    private JButton cancelButton;
    private JRadioButton implicitDestination;
    private JRadioButton namedDestination;

    // named destination fields.
    private JLabel destinationName;
    private JButton viewNamedDesButton;
    private NameTreeDialog nameTreeDialog;

    // implicit destinations panel
    private ImplicitDestinationPanel implicitDestinationPanel;

    public GoToActionDialog(Controller controller,
                            ActionsPanel actionsPanel) {
        super(controller.getViewerFrame(), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        this.actionsPanel = actionsPanel;

        setTitle(messageBundle.getString("viewer.utilityPane.action.dialog.goto.title"));
        // setup gui components.
        setGui();
    }

    /**
     * Copies state information from the annotation so it can pre represented
     * in the UI.  This method does not modify the annotation object in any way.
     * State saving should handled with save state call.
     *
     * @param annotation annotation to be updated by dialog.
     */
    public void setAnnotationComponent(AnnotationComponent annotation) {

        // get a reference so we can setup a save on dialog close
        currentAnnotation = annotation;

        org.icepdf.core.pobjects.actions.Action action = currentAnnotation.getAnnotation().getAction();

        // get the destination object, doesn't matter where it comes from.
        Destination dest = null;
        if (action != null && action instanceof GoToAction) {
            dest = ((GoToAction) action).getDestination();
        }
        // alternatively we can have a dest field on Link annotations
        else if (action == null && currentAnnotation.getAnnotation() instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotation = (LinkAnnotation) currentAnnotation.getAnnotation();
            dest = linkAnnotation.getDestination();
        }
        // check to see of we have a name tree in the document, if not we
        // disable the controls for named destinations
        if (controller.getDocument().getCatalog().getNames() == null ||
                controller.getDocument().getCatalog().getNames().getDestsNameTree() == null) {
            implicitDestinationFieldsEnabled(true);
            clearImplicitDestinations(true);
            namedDestination.setEnabled(false);
        } else {
            namedDestination.setEnabled(true);
        }

        // start gui value assignments.
        if (dest != null) {
            // first clear all previous values.
            clearImplicitDestinations(false);
            clearImplicitDestinations(true);
            // implicit assignment
            if (dest.getNamedDestination() == null) {
                implicitDestinationPanel.setAnnotationComponent(annotation);
            }
            // named assignment
            else {
                // enable GUI elements.
                implicitDestinationFieldsEnabled(false);
                // assign name to name label
                destinationName.setText(dest.getNamedDestination().toString());
            }
        } else {
            // apply default fit type for new annotations.
//            applySelectedValue(implicitDestTypeComboBox, Destination.TYPE_FIT);
//            enableFitTypeFields(Destination.TYPE_FIT);
        }
    }

    /**
     * Utility or saving the complicated state of a GoTo action.
     */
    private void saveActionState() {

        Annotation annotation = currentAnnotation.getAnnotation();
        Destination destination;

        // create a new implicit destination
        if (implicitDestination.isSelected()) {
            destination = implicitDestinationPanel.getDestination(annotation.getLibrary());
        }
        // otherwise a simple named destination
        else {
            destination = new Destination(annotation.getLibrary(), new LiteralStringObject(destinationName.getText()));
        }
        GoToAction action = (GoToAction) annotation.getAction();
        // if no previous action then we have a 'new' or old 'dest' that
        // that is getting updated.  VERY IMPORTANT, dest are replaced with
        // similar GoToActions under the current implementation.
        if (action == null) {
            action = (GoToAction) ActionFactory.buildAction(annotation.getLibrary(), ActionFactory.GOTO_ACTION);
            if (action != null) {
                action.setDestination(destination);
                annotation.addAction(action);
                actionsPanel.clearActionList();
                actionsPanel.addActionToList(action);
            }
        } else {
            // set new destination value and merge the change back into the
            // annotation.
            action.setDestination(destination);
            annotation.updateAction(action);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            // if all is
            saveActionState();
            dispose();
        } else if (e.getSource() == cancelButton) {
            // disposes this dialog
            dispose();
        } else if (e.getSource() == viewNamedDesButton) {
            // select the named destination radio
            namedDestination.setSelected(true);
            // test implementation of a NameJTree for destinations.
            NameTree nameTree = controller.getDocument().getCatalog().getNames().getDestsNameTree();
            if (nameTree != null) {
                // create new dialog instance.
                nameTreeDialog = new NameTreeDialog(
                        controller,
                        true, nameTree);
                nameTreeDialog.setDestinationName(destinationName.getText());
                // add the nameTree instance.
                nameTreeDialog.setVisible(true);
                nameTreeDialog.dispose();
            }
        }
    }

    @Override
    public void dispose() {
        setVisible(false);
        super.dispose();
        // dispose the name tree if someone opened
        if (nameTreeDialog != null) {
            nameTreeDialog.dispose();
        }
    }

    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED ||
                e.getStateChange() == ItemEvent.DESELECTED) {
            // enable/disable field sets for the two destinations types.
            if (e.getSource() == implicitDestination) {
                implicitDestinationFieldsEnabled(e.getStateChange() == ItemEvent.SELECTED);
                // check for an empty type and if so assign fit
                if (implicitDestination.isSelected()) {
                    implicitDestinationPanel.setDefaultState();
                }
            }
        }
    }

    /**
     * Method to create and customize the actions section of the panel
     */
    private void setGui() {
        JPanel goToActionPanel = new JPanel();
        goToActionPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        GridBagLayout layout = new GridBagLayout();
        goToActionPanel.setLayout(layout);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // main panel for implicit fields, more work need for other fit types.
        implicitDestinationPanel = new ImplicitDestinationPanel(controller);
        // put the explicit destinations fields into one container.
        JPanel pageNumberPane = new JPanel(new BorderLayout(5, 5));
        implicitDestination = new JRadioButton(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.explicitDestination.title"), true);
        implicitDestination.addItemListener(this);
        pageNumberPane.add(implicitDestination, BorderLayout.NORTH);
        pageNumberPane.add(implicitDestinationPanel, BorderLayout.CENTER);

        // Setup Named destinations
        JPanel namedDestSubpane = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        namedDestSubpane.setBorder(new EmptyBorder(0, 40, 0, 0));
        // name of named dest..
        namedDestSubpane.add(new JLabel(messageBundle.getString("viewer.utilityPane.action.dialog.goto.name.label")));
        destinationName = new JLabel(EMPTY_DESTINATION);
        namedDestSubpane.add(destinationName);
        // browse button to show named destination tree.
        viewNamedDesButton = new JButton(messageBundle.getString("viewer.utilityPane.action.dialog.goto.browse"));
        viewNamedDesButton.addActionListener(this);
        namedDestSubpane.add(viewNamedDesButton);
        // put the named destination into one container.
        JPanel namedDestPane = new JPanel(new BorderLayout(5, 5));
        namedDestination =
                new JRadioButton(messageBundle.getString(
                        "viewer.utilityPane.action.dialog.goto.nameDestination.title"), false);
        namedDestPane.add(namedDestination, BorderLayout.NORTH);
        namedDestPane.add(namedDestSubpane, BorderLayout.CENTER);

        // Button group to link the two panels toggled functionality.
        ButtonGroup actionButtonGroup = new ButtonGroup();
        actionButtonGroup.add(implicitDestination);
        actionButtonGroup.add(namedDestination);

        // ok button to save changes and close the dialog.
        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(this);
        cancelButton = new JButton(messageBundle.getString("viewer.button.cancel.label"));
        cancelButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        cancelButton.addActionListener(this);
        // panel for OK and cancel
        JPanel okCancelPanel = new JPanel(new FlowLayout());
        okCancelPanel.add(okButton);
        okCancelPanel.add(cancelButton);

        // add values
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;

        addGB(goToActionPanel, pageNumberPane, 0, 0, 1, 1);
        addGB(goToActionPanel, namedDestPane, 0, 1, 1, 1);

        constraints.insets = new Insets(15, 5, 5, 5);
        constraints.anchor = GridBagConstraints.CENTER;
        addGB(goToActionPanel, okCancelPanel, 0, 2, 1, 1);

        this.getContentPane().add(goToActionPanel);

        setSize(new Dimension(500, 325));
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

    /**
     * Enables fields for destinations
     *
     * @param isImplictDestSelected true enables all implicit destination fields,
     *                              false enables all named destinations
     */
    private void implicitDestinationFieldsEnabled(boolean isImplictDestSelected) {

        // radio selection
        implicitDestination.setSelected(isImplictDestSelected);
        namedDestination.setSelected(!isImplictDestSelected);

        // implicit dest fields
        implicitDestinationPanel.setEnabled(isImplictDestSelected);
        // named fields
        destinationName.setEnabled(!isImplictDestSelected);
        viewNamedDesButton.setEnabled(!isImplictDestSelected);
    }

    /**
     * Clears fields for destinations
     *
     * @param isImplictDestSelected true clears all implicit destination fields,
     *                              false clears all named destinations
     */
    private void clearImplicitDestinations(boolean isImplictDestSelected) {
        // implicit
        if (!isImplictDestSelected) {
            implicitDestinationPanel.clearImplicitDestinations();
        }
        // named
        else {
            destinationName.setText(EMPTY_DESTINATION);
        }
    }

}
