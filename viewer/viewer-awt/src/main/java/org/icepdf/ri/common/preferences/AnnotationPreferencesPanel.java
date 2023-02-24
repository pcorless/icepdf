/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.widgets.ColorChooserButton;
import org.icepdf.ri.common.widgets.DragDropColorList;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
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
    private final GridBagConstraints constraints;

    private DragDropColorList dragDropColorList;
    private JButton addNamedColorButton;
    private JButton removeNamedColorButton;
    private JButton updateNamedColorButton;
    private ColorChooserButton colorButton;
    private JTextField colorLabelTextField;

    private final SwingController swingController;
    private final Preferences preferences;

    public AnnotationPreferencesPanel(final SwingController controller, final ViewerPropertiesManager propertiesManager,
                                      final ResourceBundle messageBundle) {

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

        final JPanel namedColorsPanel = new JPanel(new GridBagLayout());
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

        final JPanel recentColorsPanel = new JPanel(new GridBagLayout());
        recentColorsPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.recent.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(recentColorsPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.annotations.recent.colors.label")),
                0, 0, 1, 1);
        final JButton resetResentColorsButton = new JButton(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.annotations.recent.colors.button"));
        resetResentColorsButton.addActionListener(e -> {
            // clear the preferences for recent colours.
            preferences.put(ViewerPropertiesManager.PROPERTY_ANNOTATION_RECENT_COLORS, "");
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

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(5, 5, 5, 5);

        final JPanel miscSettingsPanel = new JPanel(new GridBagLayout());
        miscSettingsPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.misc.border.label"),
                TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION));
        final ToolItem[] tools = {
                new ToolItem(0,
                        messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.misc.autoselect.none")),
                new ToolItem(DocumentViewModel.DISPLAY_TOOL_SELECTION,
                        messageBundle.getString("viewer.toolbar.tool.select.tooltip")),
                new ToolItem(DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION,
                        messageBundle.getString("viewer.toolbar.tool.text.tooltip")),
                new ToolItem(DocumentViewModel.DISPLAY_TOOL_PAN,
                        messageBundle.getString("viewer.toolbar.tool.pan.tooltip"))
        };
        final JComboBox<ToolItem> autoselectCbb = new JComboBox<>(tools);
        final JLabel autoselectLabel = new JLabel(
                messageBundle.getString("viewer.dialog.viewerPreferences.section.annotations.misc.autoselect.label"));
        final int toolIdx = ViewerPropertiesManager.getInstance().getPreferences()
                .getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_INK_SELECTION_TYPE, 0);
        autoselectCbb.setSelectedItem(Arrays.stream(tools).filter(t -> t.toolIdx == toolIdx).findAny().orElse(tools[0]));
        autoselectCbb.addActionListener(actionEvent -> {
            //noinspection unchecked
            final JComboBox<ToolItem> box = (JComboBox<ToolItem>) actionEvent.getSource();
            final ToolItem selected = box.getItemAt(box.getSelectedIndex());
            ViewerPropertiesManager.ALL_SELECTION_PROPERTIES.forEach(p ->
                    ViewerPropertiesManager.getInstance().getPreferences().putInt(p, selected.toolIdx));
        });
        addGB(miscSettingsPanel, autoselectLabel, 0, 0, 1, 1);
        addGB(miscSettingsPanel, autoselectCbb, 1, 0, 1, 1);

        // add the panels.
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.insets = new Insets(5, 5, 5, 5);

        addGB(this, namedColorsPanel, 0, 0, 1, 1);
        addGB(this, recentColorsPanel, 0, 1, 1, 1);
        addGB(this, miscSettingsPanel, 0, 2, 1, 1);

        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 3, 1, 1);
    }

    private void buildNamedColors(final JPanel panel, final ResourceBundle messageBundle) {
        // build out named color model
        dragDropColorList = new DragDropColorList(swingController, preferences);

        // create current list of colours
        final JScrollPane scrollPane = new JScrollPane(dragDropColorList);
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
    public void valueChanged(final ListSelectionEvent e) {
        final DragDropColorList colorList = (DragDropColorList) e.getSource();
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

            final int selectedIndex = colorList.getSelectedIndex();
            final DragDropColorList.ColorLabel colorLabel = colorList.getModel().getElementAt(selectedIndex);

            colorLabelTextField.setText(colorLabel.getLabel());
            ColorChooserButton.setButtonBackgroundColor(colorLabel.getColor(), colorButton);
        }

    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
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

    private void addGB(final JPanel layout, final Component component,
                       final int x, final int y,
                       final int rowSpan, final int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

    private static class ToolItem {
        private final int toolIdx;
        private final String toolName;

        private ToolItem(final int toolIdx, final String toolName) {
            this.toolIdx = toolIdx;
            this.toolName = toolName;
        }

        @Override
        public String toString() {
            return toolName;
        }
    }
}
