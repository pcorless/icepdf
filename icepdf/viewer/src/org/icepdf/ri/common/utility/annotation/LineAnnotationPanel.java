/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.annotations.LineAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * LineAnnotationPanel is a configuration panel for changing the properties
 * of a LineAnnotationComponent and the underlying annotation component.
 *
 * @since 5.0
 */
public class LineAnnotationPanel extends AnnotationPanelAdapter implements ItemListener,
        ActionListener {

    // default list values.
    private static final int DEFAULT_START_END_TYPE = 0;
    private static final int DEFAULT_END_END_TYPE = 0;
    private static final int DEFAULT_LINE_THICKNESS = 0;
    private static final int DEFAULT_LINE_STYLE = 0;
    private static final Color DEFAULT_BORDER_COLOR = Color.DARK_GRAY;
    private static final Color DEFAULT_FILL_COLOR = Color.DARK_GRAY;

    // line end types.
    private static ValueLabelItem[] END_TYPE_LIST;

    // link action appearance properties.
    private JComboBox startEndTypeBox;
    private JComboBox endEndTypeBox;
    private JComboBox lineThicknessBox;
    private JComboBox lineStyleBox;
    private JButton colorButton;
    private JButton internalColorButton;

    private LineAnnotation annotation;

    public LineAnnotationPanel(SwingController controller) {
        super(controller);
        setLayout(new GridLayout(6, 2, 5, 2));

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
        annotation = (LineAnnotation)
                currentAnnotationComponent.getAnnotation();

        applySelectedValue(startEndTypeBox, annotation.getStartArrow());
        applySelectedValue(endEndTypeBox, annotation.getEndArrow());
        applySelectedValue(lineThicknessBox, annotation.getLineThickness());
        applySelectedValue(lineStyleBox, annotation.getLineStyle());
        colorButton.setBackground(annotation.getColor());
        internalColorButton.setBackground(annotation.getInteriorColor());

        // disable appearance input if we have a invisible rectangle
        safeEnable(startEndTypeBox, true);
        safeEnable(endEndTypeBox, true);
        safeEnable(lineThicknessBox, true);
        safeEnable(lineStyleBox, true);
        safeEnable(colorButton, true);
        safeEnable(internalColorButton, true);
    }

    public void itemStateChanged(ItemEvent e) {
        ValueLabelItem item = (ValueLabelItem) e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
            if (e.getSource() == startEndTypeBox) {
                annotation.setStartArrow((Name) item.getValue());
            } else if (e.getSource() == endEndTypeBox) {
                annotation.setEndArrow((Name) item.getValue());
            } else if (e.getSource() == lineThicknessBox) {
                annotation.getBorderStyle().setStrokeWidth((Float) item.getValue());
            } else if (e.getSource() == lineStyleBox) {
                annotation.getBorderStyle().setBorderStyle((Name) item.getValue());
            }
            // save the action state back to the document structure.
            updateCurrentAnnotation();
            currentAnnotationComponent.resetAppearanceShapes();
            currentAnnotationComponent.repaint();
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == colorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(colorButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.line.colorChooserTitle"),
                            colorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                colorButton.setBackground(chosenColor);
                annotation.setColor(chosenColor);

                // save the action state back to the document structure.
                updateCurrentAnnotation();
                currentAnnotationComponent.resetAppearanceShapes();
                currentAnnotationComponent.repaint();
            }
        } else if (e.getSource() == internalColorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(internalColorButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.line.colorInternalChooserTitle"),
                            internalColorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                internalColorButton.setBackground(chosenColor);
                annotation.setInteriorColor(chosenColor);

                // save the action state back to the document structure.
                updateCurrentAnnotation();
                currentAnnotationComponent.resetAppearanceShapes();
                currentAnnotationComponent.repaint();
            }
        }

    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {
        // line end types
        if (END_TYPE_LIST == null) {
            END_TYPE_LIST = new ValueLabelItem[]{
                    new ValueLabelItem(LineAnnotation.LINE_END_NONE,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.none")),
                    new ValueLabelItem(LineAnnotation.LINE_END_OPEN_ARROW,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.openArrow")),
                    new ValueLabelItem(LineAnnotation.LINE_END_CLOSED_ARROW,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.closedArrow")),
                    new ValueLabelItem(LineAnnotation.LINE_END_DIAMOND,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.diamond")),
                    new ValueLabelItem(LineAnnotation.LINE_END_SQUARE,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.square")),
                    new ValueLabelItem(LineAnnotation.LINE_END_CIRCLE,
                            messageBundle.getString("viewer.utilityPane.annotation.line.end.circle"))};
        }

        // Create and setup an Appearance panel
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.annotation.line.appearance.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        // Line start type
        startEndTypeBox = new JComboBox(END_TYPE_LIST);
        startEndTypeBox.setSelectedIndex(DEFAULT_START_END_TYPE);
        startEndTypeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.line.startStyle")));
        add(startEndTypeBox);
        // Line end type
        endEndTypeBox = new JComboBox(END_TYPE_LIST);
        endEndTypeBox.setSelectedIndex(DEFAULT_END_END_TYPE);
        endEndTypeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.line.endStyle")));
        add(endEndTypeBox);
        // Line thickness
        lineThicknessBox = new JComboBox(LINE_THICKNESS_LIST);
        lineThicknessBox.setSelectedIndex(DEFAULT_LINE_THICKNESS);
        lineThicknessBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.line.lineThickness")));
        add(lineThicknessBox);
        // Line style
        lineStyleBox = new JComboBox(LINE_STYLE_LIST);
        lineStyleBox.setSelectedIndex(DEFAULT_LINE_STYLE);
        lineStyleBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.line.lineStyle")));
        add(lineStyleBox);
        // border colour
        colorButton = new JButton();
        colorButton.addActionListener(this);
        colorButton.setOpaque(true);
        colorButton.setBackground(DEFAULT_BORDER_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.line.colorLabel")));
        add(colorButton);
        // line colour
        internalColorButton = new JButton();
        internalColorButton.addActionListener(this);
        internalColorButton.setOpaque(true);
        internalColorButton.setBackground(DEFAULT_FILL_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.line.colorInternalLabel")));
        add(internalColorButton);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        safeEnable(startEndTypeBox, enabled);
        safeEnable(endEndTypeBox, enabled);
        safeEnable(lineThicknessBox, enabled);
        safeEnable(lineStyleBox, enabled);
        safeEnable(colorButton, enabled);
        safeEnable(internalColorButton, enabled);
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