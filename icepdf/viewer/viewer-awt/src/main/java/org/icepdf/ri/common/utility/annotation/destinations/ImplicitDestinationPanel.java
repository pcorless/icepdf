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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.FloatTextFieldInputVerifier;
import org.icepdf.ri.common.FloatTextFieldKeyListener;
import org.icepdf.ri.common.PageNumberTextFieldInputVerifier;
import org.icepdf.ri.common.PageNumberTextFieldKeyListener;
import org.icepdf.ri.common.utility.annotation.properties.AnnotationProperties;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.ResourceBundle;

/**
 * ImplicitDestinationPanel allows for the manipulation of a Destination object.
 *
 * @since 6.3
 */
public class ImplicitDestinationPanel extends JPanel implements ItemListener, AnnotationProperties {

    private Controller controller;
    private ResourceBundle messageBundle;
    private AnnotationComponent currentAnnotation;

    private JComboBox implicitDestTypeComboBox;
    private JTextField pageNumberTextField;
    private JTextField topTextField;
    private JTextField bottomTextField;
    private JTextField leftTextField;
    private JTextField rightTextField;
    private JTextField zoomTextField;

    private GridBagConstraints constraints;

    public ImplicitDestinationPanel(Controller controller) {
        super();
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        setGui();
    }

    private void setGui() {

        setAlignmentY(JPanel.TOP_ALIGNMENT);
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        this.setLayout(new GridLayout(4, 4, 10, 5));

        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.type.label")));
        implicitDestTypeComboBox = buildImplicitDestTypes();
        implicitDestTypeComboBox.addItemListener(this);
        add(implicitDestTypeComboBox);
        // page assignment
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.page.label")));
        pageNumberTextField = buildDocumentPageNumbers();
        add(pageNumberTextField);
        // top position
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.top.label")));
        topTextField = buildFloatTextField();
        add(topTextField);
        // bottom position
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.bottom.label")));
        bottomTextField = buildFloatTextField();
        add(bottomTextField);
        // left position
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.left.label")));
        leftTextField = buildFloatTextField();
        add(leftTextField);
        // right position
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.right.label")));
        rightTextField = buildFloatTextField();
        add(rightTextField);
        // zoom level
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.zoom.label")));
        zoomTextField = buildFloatTextField();
        add(zoomTextField);
        // filler
        add(new JLabel());
        add(new JLabel());
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == implicitDestTypeComboBox) {
            ValueLabelItem valueItem = (ValueLabelItem) e.getItem();
            Name fitType = (Name) valueItem.getValue();
            enableFitTypeFields(fitType);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        implicitDestinationFieldsEnabled(enabled);
    }

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

        if (dest != null) {
            // first clear all previous values.
            clearImplicitDestinations();
            // implicit assignment
            if (dest.getNamedDestination() == null) {
                implicitDestinationFieldsEnabled(true);
                Name type = dest.getType();
                applySelectedValue(implicitDestTypeComboBox, type);
                // set field visibility for type
                enableFitTypeFields(type);
                // type assignment.
                applyTypeValues(dest, type);
                // finally assign the page number
                pageNumberTextField.setText(String.valueOf(controller.getDocument()
                        .getPageTree().getPageNumber(dest.getPageReference()) + 1));
            }
        }
    }

    public void setDefaultState() {
        if (implicitDestTypeComboBox.getSelectedItem() == null) {
            applySelectedValue(implicitDestTypeComboBox,
                    Destination.TYPE_FIT);
            enableFitTypeFields(Destination.TYPE_FIT);
        } else {
            Name fitType = (Name) ((ValueLabelItem) implicitDestTypeComboBox
                    .getSelectedItem()).getValue();
            enableFitTypeFields(fitType);
        }
    }

    public void setDestination(Destination destination) {
        applyTypeValues(destination, null);
        enableFitTypeFields(destination.getType());
        // apply page
        Reference ref = destination.getPageReference();
        if (ref != null) {
            int pageNumber = controller.getDocument().getPageTree().getPageNumber(ref) + 1;
            pageNumberTextField.setText(String.valueOf(pageNumber));
        }
    }

    public Destination getDestination(Library library) {
        Name fitType = (Name) ((ValueLabelItem) implicitDestTypeComboBox
                .getSelectedItem()).getValue();
        int pageNumber = Integer.parseInt(pageNumberTextField.getText());
        Reference pageReference = controller.getDocument().getPageTree()
                .getPageReference(pageNumber - 1);
        List destArray = null;
        if (fitType.equals(Destination.TYPE_FIT) ||
                fitType.equals(Destination.TYPE_FITB)) {
            destArray = Destination.destinationSyntax(pageReference, fitType);
        }
        // just top enabled
        else if (fitType.equals(Destination.TYPE_FITH) ||
                fitType.equals(Destination.TYPE_FITBH) ||
                fitType.equals(Destination.TYPE_FITV) ||
                fitType.equals(Destination.TYPE_FITBV)) {
            Object top = parseDestCoordinate(topTextField.getText());
            destArray = Destination.destinationSyntax(
                    pageReference, fitType, top);
        }
        // special xyz case
        else if (fitType.equals(Destination.TYPE_XYZ)) {
            Object left = parseDestCoordinate(leftTextField.getText());
            Object top = parseDestCoordinate(topTextField.getText());
            Object zoom = parseDestCoordinate(zoomTextField.getText());
            destArray = Destination.destinationSyntax(
                    pageReference, fitType, left, top, zoom);
        }
        // special FitR
        else if (fitType.equals(Destination.TYPE_FITR)) {
            Object left = parseDestCoordinate(leftTextField.getText());
            Object bottom = parseDestCoordinate(leftTextField.getText());
            Object right = parseDestCoordinate(leftTextField.getText());
            Object top = parseDestCoordinate(leftTextField.getText());
            destArray = Destination.destinationSyntax(
                    pageReference, fitType, left, bottom, right, top);
        }
        return new Destination(library, destArray);
    }

    private void applyTypeValues(Destination dest, Name type) {
        if (type == null) type = dest.getType();
        if (Destination.TYPE_XYZ.equals(type)) {
            leftTextField.setText(getDestCoordinate(dest.getLeft()));
            topTextField.setText(getDestCoordinate(dest.getTop()));
            zoomTextField.setText(getDestCoordinate(dest.getZoom()));
        } else if (Destination.TYPE_FIT.equals(type)) {
            // nothing to do
        } else if (Destination.TYPE_FITH.equals(type)) {
            // get top value
            topTextField.setText(getDestCoordinate(dest.getTop()));
        } else if (Destination.TYPE_FITV.equals(type)) {
            // get left value
            leftTextField.setText(getDestCoordinate(dest.getLeft()));
        } else if (Destination.TYPE_FITR.equals(type)) {
            // left, bottom right and top.
            leftTextField.setText(getDestCoordinate(dest.getLeft()));
            rightTextField.setText(getDestCoordinate(dest.getRight()));
            topTextField.setText(getDestCoordinate(dest.getTop()));
            bottomTextField.setText(getDestCoordinate(dest.getBottom()));
        } else if (Destination.TYPE_FITB.equals(type)) {
            // nothing to do.
        } else if (Destination.TYPE_FITH.equals(type)) {
            // get the top
            topTextField.setText(getDestCoordinate(dest.getTop()));
        } else if (Destination.TYPE_FITBV.equals(type)) {
            // get the left
            leftTextField.setText(getDestCoordinate(dest.getLeft()));
        }
    }

    /**
     * Assigns the fit type and applies the field enabled state logic for the
     * respective view type.
     *
     * @param fitType destination fit type to apply
     */
    private void enableFitTypeFields(Name fitType) {
        if (fitType.equals(Destination.TYPE_FIT) ||
                fitType.equals(Destination.TYPE_FITB)) {
            // disable all fields
            setFitTypesEnabled(false, false, false, false, false);
        }
        // just top enabled
        else if (fitType.equals(Destination.TYPE_FITH) ||
                fitType.equals(Destination.TYPE_FITBH)) {
            setFitTypesEnabled(true, false, false, false, false);
        }
        // Just left enabled
        else if (fitType.equals(Destination.TYPE_FITV) ||
                fitType.equals(Destination.TYPE_FITBV)) {
            setFitTypesEnabled(false, false, true, false, false);
        }
        // special xyz case
        else if (fitType.equals(Destination.TYPE_XYZ)) {
            setFitTypesEnabled(true, false, true, false, true);
        }
        // special FitR
        else if (fitType.equals(Destination.TYPE_FITR)) {
            setFitTypesEnabled(true, true, true, true, false);
        }
    }

    /**
     * Sets the enabled state of the input fields associated with implicit
     * destination fit types.
     *
     * @param top    top coordinat input field.
     * @param bottom bottom coordinat input field.
     * @param left   left coordinat input field.
     * @param right  right coordinat input field.
     * @param zoom   view port zoom value field.
     */
    private void setFitTypesEnabled(boolean top, boolean bottom,
                                    boolean left, boolean right, boolean zoom) {
        topTextField.setEnabled(top);
        bottomTextField.setEnabled(bottom);
        leftTextField.setEnabled(left);
        rightTextField.setEnabled(right);
        zoomTextField.setEnabled(zoom);
    }

    /**
     * Builds destination types combo box.
     *
     * @return combo box of possible implicit destination types.
     */
    private JComboBox<ValueLabelItem> buildImplicitDestTypes() {
        ValueLabelItem[] destTypes = new ValueLabelItem[]{
                new ValueLabelItem(Destination.TYPE_XYZ,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.xyz.label")),
                new ValueLabelItem(Destination.TYPE_FITH,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fith.label")),
                new ValueLabelItem(Destination.TYPE_FITR,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fitr.label")),
                new ValueLabelItem(Destination.TYPE_FIT,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fit.label")),
                new ValueLabelItem(Destination.TYPE_FITB,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fitb.label")),
                new ValueLabelItem(Destination.TYPE_FITBH,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fitbh.label")),
                new ValueLabelItem(Destination.TYPE_FITBV,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fitbv.label")),
                new ValueLabelItem(Destination.TYPE_FITBV,
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.goto.type.fitbv.label")),
        };
        return new JComboBox<>(destTypes);
    }

    /**
     * Utility for building input field that handles page number limits for the
     * current document.
     *
     * @return pageNumber text field with listeners for validation.
     */
    private JTextField buildFloatTextField() {
        final JTextField textField = new JTextField();
        textField.setInputVerifier(new FloatTextFieldInputVerifier());
        textField.addKeyListener(new FloatTextFieldKeyListener());
        textField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                Object src = e.getSource();
                if (src == null)
                    return;
                if (src == textField) {
                    String fieldValue = textField.getText();
                    // empty string, no problem we can allow that.
                    if ("".equals(fieldValue)) {
                        return;
                    }
                    float currentValue = Float.parseFloat(fieldValue);
                    textField.setText(String.valueOf(currentValue));
                }
            }
        });

        return textField;
    }

    /**
     * Utility for building input field that handles page number limits for the
     * current document.
     *
     * @return pageNumber text field with listeners for validation.
     */
    private JTextField buildDocumentPageNumbers() {
        final JTextField textField = new JTextField();
        textField.setInputVerifier(new PageNumberTextFieldInputVerifier());
        textField.addKeyListener(new PageNumberTextFieldKeyListener());
        textField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                Object src = e.getSource();
                if (src == null)
                    return;
                if (src == textField) {
                    String fieldValue = textField.getText();
                    int currentValue = Integer.parseInt(fieldValue);
                    int maxValue = controller.getDocument().getNumberOfPages();
                    if (currentValue > maxValue)
                        textField.setText(String.valueOf(maxValue));
                }
            }
        });
        // start off with page 1.
        textField.setText("1");
        return textField;
    }

    /**
     * Enables fields for destinations
     *
     * @param isImplictDestSelected true enables all implicit destination fields,
     *                              false enables all named destinations
     */
    private void implicitDestinationFieldsEnabled(boolean isImplictDestSelected) {

        // implicit dest fields
        pageNumberTextField.setEnabled(isImplictDestSelected);
        implicitDestTypeComboBox.setEnabled(isImplictDestSelected);
        leftTextField.setEnabled(isImplictDestSelected);
        topTextField.setEnabled(isImplictDestSelected);
        zoomTextField.setEnabled(isImplictDestSelected);

    }

    /**
     * Utility for parsing input text coordinates into valide numbers used
     * for destinations.  If an empty string or Na, we return a null value
     * which is valid in post script.
     *
     * @param fieldValue value to convert to either a number or null.
     * @return Float if valid fieldValue, Null otherwise.
     */
    private Object parseDestCoordinate(String fieldValue) {
        try {
            return Float.parseFloat(fieldValue);
        } catch (NumberFormatException e) {
            // empty on purpose
        }
        return null;
    }

    /**
     * Utility to return the
     *
     * @param coord float value to convert to UI usuable string
     * @return string value of coord or an empty string if coord is null
     */
    private String getDestCoordinate(Float coord) {
        if (coord != null) {
            return String.valueOf(coord);
        } else {
            return "";
        }
    }

    /**
     * Apply selected values to combo box. If a match can not be found
     * no values is applied.
     *
     * @param comboBox combo box to update
     * @param value    value to assing.
     */
    private void applySelectedValue(JComboBox comboBox, Object value) {
        comboBox.removeItemListener(this);
        ValueLabelItem currentItem;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            currentItem = (ValueLabelItem) comboBox.getItemAt(i);
            if (currentItem.getValue().equals(value)) {
                comboBox.setSelectedIndex(i);
                break;
            }
        }
        comboBox.addItemListener(this);
    }

    /**
     * Clears fields for destinations
     */
    public void clearImplicitDestinations() {
        // implicit
        pageNumberTextField.setText("");
        implicitDestTypeComboBox.setSelectedIndex(-1);
        leftTextField.setText("");
        topTextField.setText("");
        zoomTextField.setText("");
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
