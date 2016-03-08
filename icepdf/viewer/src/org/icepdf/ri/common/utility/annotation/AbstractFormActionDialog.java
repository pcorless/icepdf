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

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.ButtonFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.actions.FormAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * Base class for the FormActionDialogs, submit and reset.  Capture commonality for field selection and exclude
 * and include flag.
 */
public abstract class AbstractFormActionDialog extends AnnotationDialogAdapter
        implements ActionListener, ListSelectionListener {

    protected SwingController controller;
    protected ResourceBundle messageBundle;
    protected AnnotationComponent currentAnnotation;
    protected ActionsPanel actionsPanel;

    // action properties
    protected JCheckBox excludeFieldsCheckbox;
    // field selection
    protected JList<FieldItem> fieldList;
    protected JList<FieldItem> includedList;
    protected DefaultListModel<FieldItem> fieldListModel;
    protected DefaultListModel<FieldItem> excludeIncludedListModel;
    protected JButton includeFieldButton;
    protected JButton excludeFieldButton;

    // state full ui elements.
    protected JButton okButton;
    protected JButton cancelButton;


    public AbstractFormActionDialog(SwingController controller,
                                    ActionsPanel actionsPanel) {
        super(controller.getViewerFrame(), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        this.actionsPanel = actionsPanel;

        // setup gui components.
        setGui();
    }

    protected abstract void saveActionState();

    protected abstract void setGui();

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            saveActionState();
            dispose();
        } else if (e.getSource() == cancelButton) {
            // disposes this dialog
            dispose();
        } else if (e.getSource() == excludeFieldButton) {
            java.util.List<FieldItem> selectedValues = includedList.getSelectedValuesList();
            for (FieldItem selected : selectedValues) {
                excludeIncludedListModel.removeElement(selected);
                fieldListModel.addElement(selected);
            }
        } else if (e.getSource() == includeFieldButton) {
            java.util.List<FieldItem> selectedValues = fieldList.getSelectedValuesList();
            for (FieldItem selected : selectedValues) {
                fieldListModel.removeElement(selected);
                excludeIncludedListModel.addElement(selected);
            }
        }
    }

    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {
            // enable/disable the move buttons
            if (fieldList.getSelectedIndex() == -1) {
                includeFieldButton.setEnabled(false);
            } else {
                includeFieldButton.setEnabled(true);
            }
            if (includedList.getSelectedIndex() == -1) {
                excludeFieldButton.setEnabled(false);
            } else {
                excludeFieldButton.setEnabled(true);
            }
        }
    }


    // load the field list data
    protected void loadFieldListData(ButtonWidgetAnnotation buttonWidgetAnnotation) {

        ButtonFieldDictionary buttonFieldDictionary = buttonWidgetAnnotation.getFieldDictionary();
        org.icepdf.core.pobjects.actions.Action action = buttonWidgetAnnotation.getAction();

        // load field names,  which is name and indirect reference as the value.
        Library library = buttonWidgetAnnotation.getLibrary();
        Catalog catalog = library.getCatalog();
        ArrayList<Object> fields = catalog.getInteractiveForm().getFields();
        for (Object field : fields) {
            descendFormTree(library, fieldListModel, field);
        }

        // process an existing action,  do nothing if we are creating a new one.
        if (action instanceof FormAction) {
            FormAction submitFormAction = (FormAction) action;
            // setup the URL text.
            excludeFieldsCheckbox.setSelected(submitFormAction.isIncludeExclude());

            // check for a fields entry and add the entries
            if (submitFormAction.getFields() != null) {
                java.util.List fieldsDictionary = submitFormAction.getFields();
                for (Object field : fieldsDictionary) {
                    if (field instanceof Reference) {
                        Reference reference = (Reference) field;
                        Object ref = library.getObject(reference);
                        if (ref instanceof AbstractWidgetAnnotation) {
                            AbstractWidgetAnnotation formField = (AbstractWidgetAnnotation) ref;
                            FieldItem foundFieldItem = new FieldItem(formField.getPObjectReference(),
                                    formField.getFieldDictionary().getPartialFieldName());
                            excludeIncludedListModel.addElement(foundFieldItem);
                            // double back and removed any fields values for the field list.
                            Enumeration<FieldItem> fieldItems = fieldListModel.elements();
                            while (fieldItems.hasMoreElements()) {
                                FieldItem tmp = fieldItems.nextElement();
                                if (tmp.equals(foundFieldItem)) {
                                    fieldListModel.removeElement(tmp);
                                }
                            }
                        }
                    } else if (fields instanceof StringObject) {
                        String fullQualifiedName = ((StringObject) fields).getLiteralString();
                        throw new IllegalStateException("Found Form widget Fields StringObject:");
                    }
                }
            }
        }
    }

    protected JPanel buildFieldSelectionPanel() {

        fieldListModel = new DefaultListModel();
        fieldList = new JList(fieldListModel);
        fieldList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fieldList.setLayoutOrientation(JList.VERTICAL);
        fieldList.addListSelectionListener(this);
        JScrollPane fieldListScroller = new JScrollPane(fieldList);
        fieldListScroller.setPreferredSize(new Dimension(150, 90));
        excludeIncludedListModel = new DefaultListModel();
        includedList = new JList(excludeIncludedListModel);
        includedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        includedList.setLayoutOrientation(JList.VERTICAL);
        includedList.addListSelectionListener(this);
        JScrollPane includeListScroller = new JScrollPane(includedList);
        includeListScroller.setPreferredSize(new Dimension(150, 90));
        includeFieldButton = new JButton(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.fieldInclude.label"));
        includeFieldButton.setEnabled(false);
        includeFieldButton.addActionListener(this);
        excludeFieldButton = new JButton(
                messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.fieldExclude.label"));
        excludeFieldButton.setEnabled(false);
        excludeFieldButton.addActionListener(this);
        GridBagLayout layout = new GridBagLayout();
        JPanel fieldSelectionPanel = new JPanel(layout);

        GridBagConstraints mainLayoutConstraints;
        mainLayoutConstraints = new GridBagConstraints();
        mainLayoutConstraints.fill = GridBagConstraints.NONE;
        mainLayoutConstraints.weightx = 1.0;
        mainLayoutConstraints.anchor = GridBagConstraints.NORTH;
        mainLayoutConstraints.anchor = GridBagConstraints.EAST;
        mainLayoutConstraints.insets = new Insets(5, 5, 5, 5);

        mainLayoutConstraints.anchor = GridBagConstraints.NORTHWEST;
        addGB(fieldSelectionPanel, mainLayoutConstraints,
                new JLabel(messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.Fields.label")),
                0, 0, 1, 1);
        addGB(fieldSelectionPanel, mainLayoutConstraints, fieldListScroller, 0, 1, 1, 2);
        addGB(fieldSelectionPanel, mainLayoutConstraints, includeFieldButton, 2, 1, 1, 1);
        addGB(fieldSelectionPanel, mainLayoutConstraints, excludeFieldButton, 2, 2, 1, 1);
        addGB(fieldSelectionPanel, mainLayoutConstraints,
                new JLabel(messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.excludeInclude.label")),
                3, 0, 1, 1);
        addGB(fieldSelectionPanel, mainLayoutConstraints, includeListScroller, 3, 1, 1, 2);

        return fieldSelectionPanel;
    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param formNode root form node.
     */
    private void descendFormTree(Library library, DefaultListModel<FieldItem> listModel, Object formNode) {

        if (formNode instanceof AbstractWidgetAnnotation) {
            AbstractWidgetAnnotation widgetAnnotation = (AbstractWidgetAnnotation) formNode;
            listModel.addElement(new FieldItem(widgetAnnotation.getPObjectReference(),
                    widgetAnnotation.getFieldDictionary().getPartialFieldName()));
        } else if (formNode instanceof FieldDictionary) {
            // iterate over the kid's array.
            FieldDictionary child = (FieldDictionary) formNode;
            formNode = child.getKids();
            if (formNode != null) {
                ArrayList kidsArray = (ArrayList) formNode;
                for (Object kid : kidsArray) {
                    if (kid instanceof Reference) {
                        kid = library.getObject((Reference) kid);
                    }
                    if (kid instanceof AbstractWidgetAnnotation) {
                        AbstractWidgetAnnotation widgetAnnotation = (AbstractWidgetAnnotation) kid;
                        listModel.addElement(new FieldItem(widgetAnnotation.getPObjectReference(),
                                widgetAnnotation.getFieldDictionary().getPartialFieldName()));
                    } else if (kid instanceof FieldDictionary) {
                        descendFormTree(library, listModel, kid);
                    }
                }
            }

        }
    }

    /**
     * Gridbag constructor helper
     *
     * @param layout    panel to invoke layout on
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowspan value
     * @param colSpan   colspan value
     */
    protected void addGB(JPanel layout, GridBagConstraints constraints, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

    protected class FieldItem {
        private Reference reference;
        private String label;

        public FieldItem(Reference reference, String label) {
            this.reference = reference;
            if (label != null && label.length() > 0) {
                this.label = label;
            } else {
                this.label = messageBundle.getString("viewer.utilityPane.action.dialog.formAction.fields.empty.label");
            }

        }

        public Reference getReference() {
            return reference;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FieldItem) {
                return ((FieldItem) obj).getReference().equals(reference);
            }
            return super.equals(obj);
        }
    }
}
