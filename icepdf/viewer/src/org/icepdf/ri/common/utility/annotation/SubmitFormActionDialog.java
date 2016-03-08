/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.ActionFactory;
import org.icepdf.core.pobjects.actions.FileSpecification;
import org.icepdf.core.pobjects.actions.SubmitFormAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * SubmitFormActionDialog builds a valid Submit-Form Action which at the very least contains a URL but may also
 * include a list of form elements to submit.
 */
public class SubmitFormActionDialog extends AbstractFormActionDialog {

    // submit fields
    private JTextField urlTextField;

    // submit action flag properties
    private JCheckBox includeNoValueCheckbox;
    private JCheckBox useGetCheckbox;
    private JCheckBox submitCoordinateCheckbox;


    public SubmitFormActionDialog(SwingController controller,
                                  ActionsPanel actionsPanel) {
        super(controller, actionsPanel);
        setTitle(messageBundle.getString("viewer.utilityPane.action.dialog.submitForm.title"));
    }

    protected void saveActionState() {

        Annotation annotation = currentAnnotation.getAnnotation();
        SubmitFormAction action = (SubmitFormAction) annotation.getAction();

        // if no previous action then we have a 'new' action.
        if (action == null) {
            action = (SubmitFormAction)
                    ActionFactory.buildAction(annotation.getLibrary(),
                            ActionFactory.SUBMIT_ACTION);
            annotation.addAction(action);
            // update the parent view with the new action.
            actionsPanel.clearActionList();
            actionsPanel.addActionToList(action);
        } else {
            annotation.updateAction(action);
        }

        // set field specification
        FileSpecification fileSpecification = action.getFileSpecification();
        if (fileSpecification == null) {
            // keep it simple and add it as a dictionary entry
            HashMap<Name, Object> entries = new HashMap<Name, Object>(2);
            fileSpecification = new FileSpecification(annotation.getLibrary(), entries);
        }
        fileSpecification.setFileSystemName(new Name("URL"));
        fileSpecification.setFileSpecification(urlTextField.getText());
        action.setFileSpecification(fileSpecification);
        // update flag values.
        action.setFlag(SubmitFormAction.INCLUDE_NO_VALUE_FIELDS_BIT, includeNoValueCheckbox.isSelected());
        action.setFlag(SubmitFormAction.GET_METHOD_BIT, useGetCheckbox.isSelected());
        action.setFlag(SubmitFormAction.SUBMIT_COORDINATES_BIT, submitCoordinateCheckbox.isSelected());

        // build the include/exclude list.
        action.setFlag(SubmitFormAction.INCLUDE_EXCLUDE_BIT, excludeFieldsCheckbox.isSelected());
        if (excludeIncludedListModel.getSize() > 0) {
            // build out a list of the references.
            int max = excludeIncludedListModel.getSize();
            ArrayList<Object> referenceList = new ArrayList<Object>(max);
            for (int i = 0; i < max; i++) {
                referenceList.add(excludeIncludedListModel.get(i).getReference());
            }
            action.setFieldsValue(referenceList);
        }

    }


    public void setAnnotationComponent(AnnotationComponent annotation) {
        currentAnnotation = annotation;
        AbstractWidgetAnnotation widgetAnnotation = (AbstractWidgetAnnotation) annotation.getAnnotation();
        widgetAnnotation.getFieldDictionary();
        // load the field list data
        ButtonWidgetAnnotation buttonWidgetAnnotation = (ButtonWidgetAnnotation) widgetAnnotation;
        // load/sort field data
        loadFieldListData(buttonWidgetAnnotation);
        // update the flags.
        org.icepdf.core.pobjects.actions.Action action = buttonWidgetAnnotation.getAction();
        if (action != null) {
            SubmitFormAction submitFormAction = (SubmitFormAction) action;
            FileSpecification fileSpecification = submitFormAction.getFileSpecification();
            // setup the URL text.
            if (fileSpecification != null) {
                urlTextField.setText(fileSpecification.getFileSpecification());
            }
            // apply text box values.
            useGetCheckbox.setSelected(submitFormAction.isGetMethod());
            submitCoordinateCheckbox.setSelected(submitFormAction.isSubmitCoordinates());
            includeNoValueCheckbox.setSelected(submitFormAction.isIncludeNoValueFields());
        }
    }


    /**
     * Method to create and customize the actions section of the panel
     */
    protected void setGui() {

        /**
         * Place GUI elements on dialog
         */

        JPanel submitActionPanel = new JPanel();

        submitActionPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        GridBagLayout layout = new GridBagLayout();
        submitActionPanel.setLayout(layout);

        GridBagConstraints mainLayoutConstraints;
        mainLayoutConstraints = new GridBagConstraints();
        mainLayoutConstraints.fill = GridBagConstraints.NONE;
        mainLayoutConstraints.weightx = 1.0;
        mainLayoutConstraints.anchor = GridBagConstraints.NORTH;
        mainLayoutConstraints.anchor = GridBagConstraints.EAST;
        mainLayoutConstraints.insets = new Insets(5, 5, 5, 5);

        /**
         *  Create explicit layout
         */

        // url panel
        JPanel urlPanel = new JPanel(new FlowLayout());
        urlPanel.add(new JLabel(messageBundle.getString("viewer.utilityPane.action.dialog.submitForm.url.label")));
        urlTextField = new JTextField(50);
        urlTextField.addActionListener(this);
        urlPanel.add(urlTextField);
        // submit options
        excludeFieldsCheckbox = new JCheckBox(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.exclude.label"), false);
        includeNoValueCheckbox = new JCheckBox(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.includeNoValue.label"), false);
        useGetCheckbox = new JCheckBox(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.useGet.label"), false);
        submitCoordinateCheckbox = new JCheckBox(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.submitCoordinates.label"), false);
        JPanel submitOptionPanel = new JPanel(new FlowLayout());
        submitOptionPanel.add(excludeFieldsCheckbox);
        submitOptionPanel.add(includeNoValueCheckbox);
        submitOptionPanel.add(useGetCheckbox);
        submitOptionPanel.add(submitCoordinateCheckbox);

        // Field selection panel
        JPanel fieldSelectionPanel = buildFieldSelectionPanel();

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

        mainLayoutConstraints.insets = new Insets(15, 5, 5, 5);
        mainLayoutConstraints.anchor = GridBagConstraints.NORTHWEST;
        addGB(submitActionPanel, mainLayoutConstraints, urlPanel, 0, 1, 1, 1);
        addGB(submitActionPanel, mainLayoutConstraints, submitOptionPanel, 0, 2, 1, 1);
        mainLayoutConstraints.anchor = GridBagConstraints.CENTER;
        addGB(submitActionPanel, mainLayoutConstraints, fieldSelectionPanel, 0, 3, 1, 1);
        addGB(submitActionPanel, mainLayoutConstraints, okCancelPanel, 0, 4, 1, 1);

        this.getContentPane().add(submitActionPanel);

        this.pack();
        setLocationRelativeTo(controller.getViewerFrame());
    }


}
