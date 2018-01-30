/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.preferences;

import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.ColorChooserButton;
import org.icepdf.ri.common.DragDropColorList;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * AnnotationPreferencesPanel currently allows for the customization of the labeled colour section of the
 * AnnotationColorPropertyPanel.  A list of named colours is stored int the preferences backing store. If no named
 * colours are defined then no named colours show up in the annotation preferences panel.  The controls on this
 * preferences panel allow for the management of user specific named colours.
 * <p>
 * A property change listener is also setup so the named colour list can be appropriately updated for any
 * AnnotationColorButton instance.
 *
 * @since 6.3
 */
public class AnnotationPreferencesPanel extends JPanel implements ListSelectionListener, ActionListener {

    // layouts constraint
    private GridBagConstraints constraints;

    private DragDropColorList dragDropColorList;
    private JButton addNamedColorButton;
    private JButton removeNamedColorButton;
    private JButton updateNamedColorButton;
    private ColorChooserButton colorButton;
    private JTextField colorLabelTextField;

    private SwingController swingController;
    private Preferences preferences;

    public AnnotationPreferencesPanel(SwingController controller, PropertiesManager propertiesManager,
                                      ResourceBundle messageBundle) {

        this.swingController = controller;
        preferences = propertiesManager.getPreferences();

        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(5, 5, 5, 5);

        JPanel namedColorsPanel = new JPanel(new GridBagLayout());
        namedColorsPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.named.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        buildNamedColors(namedColorsPanel, messageBundle);

        addPropertyChangeListener(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, controller);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(5, 5, 5, 5);

        JPanel recentColorsPanel = new JPanel(new GridBagLayout());
        recentColorsPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.recent.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(recentColorsPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.annotations.recent.colors.label")),
                0, 0, 1, 1);
        JButton resetResentColorsButton = new JButton(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.annotations.recent.colors.button"));
        resetResentColorsButton.addActionListener(e -> {
            // clear the preferences for recent colours.
            preferences.put(PropertiesManager.PROPERTY_ANNOTATION_RECENT_COLORS, "");
            // rebuild the annotation drop down panels.
            firePropertyChange(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, null, true);
        });
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.weighty = 1;
        addGB(recentColorsPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.annotations.recent.colors.label")),
                0, 0, 1, 1);
        addGB(recentColorsPanel, resetResentColorsButton, 1, 0, 1, 1);

        // add the two panels.
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(5, 5, 5, 5);

        addGB(this, namedColorsPanel, 0, 0, 1, 1);
        addGB(this, recentColorsPanel, 0, 1, 1, 1);
    }

    private void buildNamedColors(JPanel panel, ResourceBundle messageBundle) {
        // build out named color model
        dragDropColorList = new DragDropColorList(swingController, preferences);

        // create current list of colours
        JScrollPane scrollPane = new JScrollPane(dragDropColorList);
        addGB(panel, scrollPane, 0, 0, 5, 1);

        dragDropColorList.addListSelectionListener(this);

        // build out the CRUD GUI.
        addNamedColorButton = new JButton(
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.named.add.label"));
        addNamedColorButton.addActionListener(this);
        addNamedColorButton.setEnabled(true);

        removeNamedColorButton = new JButton(
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.named.remove.label"));
        removeNamedColorButton.addActionListener(this);
        removeNamedColorButton.setEnabled(false);

        updateNamedColorButton = new JButton(
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.named.edit.label"));
        updateNamedColorButton.addActionListener(this);
        updateNamedColorButton.setEnabled(false);

        colorButton = new ColorChooserButton(Color.DARK_GRAY);
        colorButton.setEnabled(true);
        colorLabelTextField = new JTextField("");
        colorLabelTextField.setEnabled(true);

        // add, edit remove controls.
        constraints.insets = new Insets(5, 5, 5, 1);
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.01;
        constraints.weighty = 1.0;
        addGB(panel, colorButton, 0, 1, 1, 1);

        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(5, 1, 5, 1);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(panel, colorLabelTextField, 1, 1, 1, 1);

        constraints.weightx = 0.01;
        constraints.fill = GridBagConstraints.NONE;
        addGB(panel, addNamedColorButton, 2, 1, 1, 1);
        addGB(panel, updateNamedColorButton, 3, 1, 1, 1);

        constraints.insets = new Insets(5, 0, 5, 5);
        addGB(panel, removeNamedColorButton, 4, 1, 1, 1);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        DragDropColorList colorList = (DragDropColorList) e.getSource();
        if (colorList.isSelectionEmpty()) {
            // disable all but the add controls.
            updateNamedColorButton.setEnabled(false);
            removeNamedColorButton.setEnabled(false);
            addNamedColorButton.setEnabled(true);
            // clear the color and label.
            colorButton.setEnabled(true);
            ColorChooserButton.setButtonBackgroundColor(Color.DARK_GRAY, colorButton);
            colorLabelTextField.setEnabled(true);
            colorLabelTextField.setText("");
        } else {
            // enable controls and set the color and label appropriately
            addNamedColorButton.setEnabled(false);
            updateNamedColorButton.setEnabled(true);
            removeNamedColorButton.setEnabled(true);
            colorLabelTextField.setEnabled(true);
            colorButton.setEnabled(true);

            int selectedIndex = colorList.getSelectedIndex();
            DragDropColorList.ColorLabel colorLabel = colorList.getModel().getElementAt(selectedIndex);

            colorLabelTextField.setText(colorLabel.getLabel());
            ColorChooserButton.setButtonBackgroundColor(colorLabel.getColor(), colorButton);
        }

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == addNamedColorButton) {
            dragDropColorList.addNamedColor(colorButton.getBackground(), colorLabelTextField.getText());
            // clear the label
            colorLabelTextField.setText("");
        } else if (source == updateNamedColorButton) {
            dragDropColorList.updateNamedColor(colorButton.getBackground(), colorLabelTextField.getText());
            // deselect the list, so that add is more easily selected
            dragDropColorList.clearSelection();
        } else if (source == removeNamedColorButton) {
            dragDropColorList.removeSelectedNamedColor();
        }
        // add change listener so we can update any drop down button colour lists
        firePropertyChange(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, null, true);
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
