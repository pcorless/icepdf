/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.AnnotationState;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.CircleAnnotation;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractDocumentViewModel;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ResourceBundle;

/**
 * CircleAnnotationPanel is a configuration panel for changing the properties
 * of a CircleAnnotationComponent and the underlying annotation component.
 *
 * @since 5.0
 */
public class CircleAnnotationPanel extends AnnotationPanelAdapter implements ItemListener,
        ActionListener {

    // default list values.
    private static final int DEFAULT_LINE_THICKNESS = 0;
    private static final int DEFAULT_LINE_STYLE = 0;
    private static final int DEFAULT_STROKE_TYPE = 0;
    private static final Color DEFAULT_BORDER_COLOR = Color.RED;
    private static final int DEFAULT_FILL_TYPE = 1;
    private static final Color DEFAULT_INTERIOR_COLOR = new Color(1, 1, 1);

    // Fill styles types.
    private final ValueLabelItem[] PAINT_TYPE_LIST = new ValueLabelItem[]{
            new ValueLabelItem(Boolean.TRUE, "Visible"),
            new ValueLabelItem(Boolean.FALSE, "Invisible")};

    // line thicknesses.
    private final ValueLabelItem[] LINE_THICKNESS_LIST = new ValueLabelItem[]{
            new ValueLabelItem(1f, "1"),
            new ValueLabelItem(2f, "2"),
            new ValueLabelItem(3f, "3"),
            new ValueLabelItem(4f, "4"),
            new ValueLabelItem(5f, "5"),
            new ValueLabelItem(10f, "10"),
            new ValueLabelItem(15f, "15")};

    // line styles.
    private final ValueLabelItem[] LINE_STYLE_LIST = new ValueLabelItem[]{
            new ValueLabelItem(BorderStyle.BORDER_STYLE_SOLID, "Solid"),
            new ValueLabelItem(BorderStyle.BORDER_STYLE_DASHED, "Dashed")};

    private SwingController controller;
    private ResourceBundle messageBundle;

    // action instance that is being edited
    private AnnotationComponent currentAnnotationComponent;

    // link action appearance properties.
    private JComboBox lineThicknessBox;
    private JComboBox lineStyleBox;
    private JComboBox fillTypeBox;
    private JButton colorFillButton;
    private JButton colorBorderButton;

    private CircleAnnotation annotation;

    // appearance properties to take care of.
//    private float lineThickness;
//    private Name lineStyle;
//    private Color borderColor;
//    private boolean isVisibleFillColor;
//    private Color fillColor;

    public CircleAnnotationPanel(SwingController controller) {
        super(new GridLayout(5, 2, 5, 2), true);

        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        // Setup the basics of the panel
        setFocusable(true);

        // Add the tabbed pane to the overall panel
        createGUI();

        // Start the panel disabled until an action is clicked
        setEnabled(false);

        revalidate();
    }


    /**
     * Method that should be called when a new AnnotationComponent is selected by the user
     * The associated object will be stored locally as currentAnnotation
     * Then all of it's properties will be applied to the UI pane
     * For example if the border was red, the color of the background button will
     * be changed to red
     *
     * @param newAnnotation to set and apply to this UI
     */
    public void setAnnotationComponent(AnnotationComponent newAnnotation) {

        if (newAnnotation == null || newAnnotation.getAnnotation() == null) {
            setEnabled(false);
            return;
        }
        // assign the new action instance.
        this.currentAnnotationComponent = newAnnotation;

        // For convenience grab the Annotation object wrapped by the component
        annotation = (CircleAnnotation)
                currentAnnotationComponent.getAnnotation();

        applySelectedValue(lineThicknessBox, annotation.getLineThickness());
        applySelectedValue(lineStyleBox, annotation.getLineStyle());
        applySelectedValue(fillTypeBox, annotation.isFillColor());
        colorBorderButton.setBackground(annotation.getColor());
        colorFillButton.setBackground(annotation.getFillColor());

        // disable appearance input if we have a invisible rectangle
        safeEnable(lineThicknessBox, true);
        safeEnable(lineStyleBox, true);
        safeEnable(colorFillButton, true);
        safeEnable(fillTypeBox, true);
        safeEnable(colorBorderButton, true);

        setStrokeFillColorButtons();
    }

    private void setStrokeFillColorButtons() {
        CircleAnnotation circleAnnotation = (CircleAnnotation)
                currentAnnotationComponent.getAnnotation();
        if (annotation.isFillColor()) {
            colorFillButton.setBackground(circleAnnotation.getFillColor());
            safeEnable(colorFillButton, true);
        } else {
            safeEnable(colorFillButton, false);
        }
    }

    public void itemStateChanged(ItemEvent e) {
        ValueLabelItem item = (ValueLabelItem) e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
            if (e.getSource() == lineThicknessBox) {
                annotation.getBorderStyle().setStrokeWidth((Float) item.getValue());
            } else if (e.getSource() == lineStyleBox) {
                annotation.getBorderStyle().setBorderStyle((Name) item.getValue());
            } else if (e.getSource() == fillTypeBox) {
                annotation.setFillColor((Boolean) item.getValue());
                setStrokeFillColorButtons();
            }
            // save the action state back to the document structure.
            updateAnnotationState();
            currentAnnotationComponent.resetAppearanceShapes();
            currentAnnotationComponent.repaint();
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == colorBorderButton) {
            Color chosenColor =
                    JColorChooser.showDialog(colorBorderButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.circle.colorBorderChooserTitle"),
                            colorBorderButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                colorBorderButton.setBackground(chosenColor);
                annotation.setColor(chosenColor);

                // save the action state back to the document structure.
                updateAnnotationState();
                currentAnnotationComponent.resetAppearanceShapes();
                currentAnnotationComponent.repaint();
            }
        } else if (e.getSource() == colorFillButton) {
            Color chosenColor =
                    JColorChooser.showDialog(colorFillButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.circle.colorInteriorChooserTitle"),
                            colorFillButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                colorFillButton.setBackground(chosenColor);
                annotation.setFillColor(chosenColor);

                // save the action state back to the document structure.
                updateAnnotationState();
                currentAnnotationComponent.resetAppearanceShapes();
                currentAnnotationComponent.repaint();
            }
        }
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {

        // Create and setup an Appearance panel
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.annotation.circle.appearance.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        // Line thickness
        lineThicknessBox = new JComboBox(LINE_THICKNESS_LIST);
        lineThicknessBox.setSelectedIndex(DEFAULT_LINE_THICKNESS);
        lineThicknessBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.circle.lineThickness")));
        add(lineThicknessBox);
        // Line style
        lineStyleBox = new JComboBox(LINE_STYLE_LIST);
        lineStyleBox.setSelectedIndex(DEFAULT_LINE_STYLE);
        lineStyleBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.circle.lineStyle")));
        add(lineStyleBox);
        // border colour
        colorBorderButton = new JButton();
        colorBorderButton.addActionListener(this);
        colorBorderButton.setOpaque(true);
        colorBorderButton.setBackground(DEFAULT_BORDER_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.circle.colorBorderLabel")));
        add(colorBorderButton);
        // fill type options
        fillTypeBox = new JComboBox(PAINT_TYPE_LIST);
        fillTypeBox.setSelectedIndex(DEFAULT_FILL_TYPE);
        fillTypeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.circle.fillTypeLabel")));
        add(fillTypeBox);
        // interior colour
        colorFillButton = new JButton();
        colorFillButton.addActionListener(this);
        colorFillButton.setOpaque(true);
        colorFillButton.setBackground(DEFAULT_INTERIOR_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.circle.colorInteriorLabel")));
        add(colorFillButton);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        safeEnable(lineThicknessBox, enabled);
        safeEnable(lineStyleBox, enabled);
        safeEnable(fillTypeBox, enabled);
        safeEnable(colorBorderButton, enabled);
        safeEnable(colorFillButton, enabled);
    }

    private void updateAnnotationState() {
        // store old state
        AnnotationState oldState = new AnnotationState(currentAnnotationComponent);
        // store new state from panel
        AnnotationState newState = new AnnotationState(currentAnnotationComponent);
        // todo: update how state is stored as we have a lot of annotations...
//        AnnotationState changes = new AnnotationState(
//                linkType, null, 0, textMarkupType, color);
        // apply new properties to the action and the component
//        newState.apply(changes);
        // temporary apply new state info
        CircleAnnotation circleAnnotation = (CircleAnnotation)
                currentAnnotationComponent.getAnnotation();

        // Add our states to the undo caretaker
        ((AbstractDocumentViewModel) controller.getDocumentViewController().
                getDocumentViewModel()).getAnnotationCareTaker()
                .addState(oldState, newState);

        // Check with the controller whether we can enable the undo/redo menu items
        controller.reflectUndoCommands();
    }


    /**
     * Convenience method to ensure a component is safe to toggle the enabled state on
     *
     * @param comp    to toggle
     * @param enabled the status to use
     * @return true on success
     */
    protected boolean safeEnable(JComponent comp, boolean enabled) {
        if (comp != null) {
            comp.setEnabled(enabled);
            return true;
        }
        return false;
    }

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

}