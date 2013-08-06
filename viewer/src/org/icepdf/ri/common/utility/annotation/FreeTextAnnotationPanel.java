/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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
import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.annotations.FreeTextAnnotationComponent;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * FreeTextAnnotationPanel is a configuration panel for changing the properties
 * of a FreeTextAnnotationComponent and the underlying annotation component.
 *
 * @since 5.0
 */
public class FreeTextAnnotationPanel extends AnnotationPanelAdapter implements ItemListener,
        ActionListener {

    // default list values.
    private static final int DEFAULT_FONT_SIZE = 5;
    private static final int DEFAULT_FONT_FAMILY = 0;
    private static final Color DEFAULT_FONT_COLOR = Color.DARK_GRAY;

    public static final int DEFAULT_STROKE_THICKNESS_STYLE = 0;
    public static final int DEFAULT_STROKE_STYLE = 0;
    public static final int DEFAULT_FILL_STYLE = 0;
    private static final Color DEFAULT_BORDER_COLOR = Color.LIGHT_GRAY;
    private static final Color DEFAULT_STROKE_COLOR = new Color(1, 1, 1);

    // font styles.
    private static ValueLabelItem[] FONT_NAMES_LIST;

    // Font size.
    private static ValueLabelItem[] FONT_SIZES_LIST;

    // action instance that is being edited
    private FreeTextAnnotation freeTextAnnotation;

    // font configuration
    private JComboBox fontNameBox;
    private JComboBox fontSizeBox;
    private JButton fontColorButton;

    // fill configuration
    private JComboBox fillTypeBox;
    private JButton fillColorButton;

    // border configuration
    private JComboBox strokeTypeBox;
    private JComboBox strokeThicknessBox;
    private JComboBox strokeStyleBox;
    private JButton strokeColorButton;

    public FreeTextAnnotationPanel(SwingController controller) {
        super(controller);
        setLayout(new GridLayout(9, 2, 5, 2));

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
        FreeTextAnnotationComponent freeTextAnnotationComponent = (FreeTextAnnotationComponent)
                currentAnnotationComponent;

        freeTextAnnotation = (FreeTextAnnotation) freeTextAnnotationComponent.getAnnotation();


        // font comps
        applySelectedValue(fontNameBox, freeTextAnnotation.getFontName());
        applySelectedValue(fontSizeBox, freeTextAnnotation.getFontSize());
        fontColorButton.setBackground(freeTextAnnotation.getFontColor());

        // border comps.
        applySelectedValue(strokeTypeBox, freeTextAnnotation.isStrokeType());
        applySelectedValue(strokeStyleBox, freeTextAnnotation.getBorderStyle().getBorderStyle());
        applySelectedValue(strokeThicknessBox, freeTextAnnotation.getBorderStyle().getStrokeWidth());
        strokeColorButton.setBackground(freeTextAnnotation.getColor());

        // fill comps.
        applySelectedValue(fillTypeBox, freeTextAnnotation.isFillType());
        fillColorButton.setBackground(freeTextAnnotation.getFillColor());

        safeEnable(fontNameBox, true);
        safeEnable(fontSizeBox, true);
        safeEnable(fontColorButton, true);

        safeEnable(strokeTypeBox, true);
        safeEnable(strokeThicknessBox, true);
        safeEnable(strokeStyleBox, true);
        safeEnable(strokeColorButton, true);

        safeEnable(fillTypeBox, true);
        safeEnable(fillColorButton, true);

        // set visibility based on fill and stroke type.
        disableInvisibleFields();
    }

    private void disableInvisibleFields() {

        boolean fillType = freeTextAnnotation.isFillType();
        boolean strokeType = freeTextAnnotation.isStrokeType();

        safeEnable(fillColorButton, fillType);
        safeEnable(strokeThicknessBox, strokeType);
        safeEnable(strokeStyleBox, strokeType);
        safeEnable(strokeColorButton, strokeType);

    }

    public void itemStateChanged(ItemEvent e) {
        ValueLabelItem item = (ValueLabelItem) e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
            if (e.getSource() == fontNameBox) {
                freeTextAnnotation.setFontName((String) item.getValue());
            } else if (e.getSource() == fontSizeBox) {
                freeTextAnnotation.setFontSize((Integer) item.getValue());
            } else if (e.getSource() == strokeTypeBox) {
                Boolean visible = (Boolean) item.getValue();
                freeTextAnnotation.setStrokeType(visible);
                if (visible) {
                    // set the line thickness
                    freeTextAnnotation.getBorderStyle().setStrokeWidth(
                            (Float) ((ValueLabelItem) strokeThicknessBox.getSelectedItem()).getValue());
                    // set teh default stroke.
                    freeTextAnnotation.getBorderStyle().setBorderStyle(
                            (Name) ((ValueLabelItem) strokeStyleBox.getSelectedItem()).getValue());
                } else {
                    freeTextAnnotation.getBorderStyle().setStrokeWidth(0);
                }
                disableInvisibleFields();
            } else if (e.getSource() == strokeStyleBox) {
                freeTextAnnotation.getBorderStyle().setBorderStyle((Name) item.getValue());
            } else if (e.getSource() == strokeThicknessBox) {
                freeTextAnnotation.getBorderStyle().setStrokeWidth((Float) item.getValue());
            } else if (e.getSource() == fillTypeBox) {
                freeTextAnnotation.setFillType((Boolean) item.getValue());
                if (freeTextAnnotation.isFillType()) {
                    freeTextAnnotation.setFillColor(fillColorButton.getBackground());
                }
                disableInvisibleFields();
            }
            // save the action state back to the document structure.
            updateCurrentAnnotation();
            currentAnnotationComponent.resetAppearanceShapes();
            currentAnnotationComponent.repaint();
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == strokeColorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(strokeColorButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.freeText.border.color.ChooserTitle"),
                            strokeColorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                strokeColorButton.setBackground(chosenColor);
                freeTextAnnotation.setColor(chosenColor);
            }
        } else if (e.getSource() == fillColorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(fillColorButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.freeText.fill.color.ChooserTitle"),
                            fillColorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                fillColorButton.setBackground(chosenColor);
                freeTextAnnotation.setFillColor(chosenColor);
            }
        } else if (e.getSource() == fontColorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(fillColorButton,
                            messageBundle.getString(
                                    "viewer.utilityPane.annotation.freeText.font.color.ChooserTitle"),
                            fontColorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                fontColorButton.setBackground(chosenColor);
                freeTextAnnotation.setFontColor(chosenColor);
            }
        }
        // save the action state back to the document structure.
        updateCurrentAnnotation();
        currentAnnotationComponent.resetAppearanceShapes();
        currentAnnotationComponent.repaint();
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {

        // font styles.
        if (FONT_NAMES_LIST == null) {
            FONT_NAMES_LIST = new ValueLabelItem[]{
                    new ValueLabelItem("Helvetica", "Helvetica"),
                    new ValueLabelItem("Helvetica-Oblique", "Helvetica-Oblique"),
                    new ValueLabelItem("Helvetica-Bold", "Helvetica-Bold"),
                    new ValueLabelItem("Helvetica-BoldOblique", "Helvetica-BoldOblique"),
                    new ValueLabelItem("Times-Italic", "Times-Italic"),
                    new ValueLabelItem("Times-Bold", "Times-Bold"),
                    new ValueLabelItem("Times-BoldItalic", "Times-BoldItalic"),
                    new ValueLabelItem("Times-Roman", "Times-Roman"), new ValueLabelItem("Courier", "Courier"),
                    new ValueLabelItem("Courier-Oblique", "Courier-Oblique"),
                    new ValueLabelItem("Courier-BoldOblique", "Courier-BoldOblique"),
                    new ValueLabelItem("Courier-Bold", "Courier-Bold"),
                    new ValueLabelItem("Courier-Bold", "Courier-Bold")};
        }

        // Font size.
        if (FONT_SIZES_LIST == null) {
            FONT_SIZES_LIST = new ValueLabelItem[]{
                    new ValueLabelItem(6, messageBundle.getString("viewer.common.number.six")),
                    new ValueLabelItem(8, messageBundle.getString("viewer.common.number.eight")),
                    new ValueLabelItem(9, messageBundle.getString("viewer.common.number.nine")),
                    new ValueLabelItem(10, messageBundle.getString("viewer.common.number.ten")),
                    new ValueLabelItem(12, messageBundle.getString("viewer.common.number.twelve")),
                    new ValueLabelItem(14, messageBundle.getString("viewer.common.number.fourteen")),
                    new ValueLabelItem(16, messageBundle.getString("viewer.common.number.sixteen")),
                    new ValueLabelItem(18, messageBundle.getString("viewer.common.number.eighteen")),
                    new ValueLabelItem(20, messageBundle.getString("viewer.common.number.twenty")),
                    new ValueLabelItem(24, messageBundle.getString("viewer.common.number.twentyFour")),
                    new ValueLabelItem(36, messageBundle.getString("viewer.common.number.thirtySix")),
                    new ValueLabelItem(48, messageBundle.getString("viewer.common.number.fortyEight"))};
        }

        // Create and setup an Appearance panel
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.annotation.freeText.appearance.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        // Font name
        fontNameBox = new JComboBox(FONT_NAMES_LIST);
        fontNameBox.setSelectedIndex(DEFAULT_FONT_FAMILY);
        fontNameBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name")));
        add(fontNameBox);
        // border style
        fontSizeBox = new JComboBox(FONT_SIZES_LIST);
        fontSizeBox.setSelectedIndex(DEFAULT_FONT_SIZE);
        fontSizeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.font.size")));
        add(fontSizeBox);
        // border colour
        fontColorButton = new JButton();
        fontColorButton.addActionListener(this);
        fontColorButton.setOpaque(true);
        fontColorButton.setBackground(DEFAULT_FONT_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.freeText.font.color")));
        add(fontColorButton);

        // stroke type
        strokeTypeBox = new JComboBox(VISIBLE_TYPE_LIST);
        strokeTypeBox.setSelectedIndex(DEFAULT_STROKE_STYLE);
        strokeTypeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.border.type")));
        add(strokeTypeBox);
        // border thickness
        strokeThicknessBox = new JComboBox(LINE_THICKNESS_LIST);
        strokeThicknessBox.setSelectedIndex(DEFAULT_STROKE_THICKNESS_STYLE);
        strokeThicknessBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.border.thickness")));
        add(strokeThicknessBox);
        // border style
        strokeStyleBox = new JComboBox(LINE_STYLE_LIST);
        strokeStyleBox.setSelectedIndex(DEFAULT_STROKE_STYLE);
        strokeStyleBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.border.style")));
        add(strokeStyleBox);
        // border colour
        strokeColorButton = new JButton();
        strokeColorButton.addActionListener(this);
        strokeColorButton.setOpaque(true);
        strokeColorButton.setBackground(DEFAULT_BORDER_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.freeText.border.color")));
        add(strokeColorButton);

        // fill type
        fillTypeBox = new JComboBox(VISIBLE_TYPE_LIST);
        fillTypeBox.setSelectedIndex(DEFAULT_FILL_STYLE);
        fillTypeBox.addItemListener(this);
        add(new JLabel(messageBundle.getString("viewer.utilityPane.annotation.freeText.fill.type")));
        add(fillTypeBox);
        // fill colour
        fillColorButton = new JButton();
        fillColorButton.addActionListener(this);
        fillColorButton.setOpaque(true);
        fillColorButton.setBackground(DEFAULT_STROKE_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.annotation.freeText.fill.color")));
        add(fillColorButton);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        safeEnable(fontNameBox, enabled);
        safeEnable(fontSizeBox, enabled);
        safeEnable(fontColorButton, enabled);

        safeEnable(strokeTypeBox, enabled);
        safeEnable(strokeThicknessBox, enabled);
        safeEnable(strokeStyleBox, enabled);
        safeEnable(strokeColorButton, enabled);

        safeEnable(fillTypeBox, enabled);
        safeEnable(fillColorButton, enabled);

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