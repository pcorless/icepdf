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
package org.icepdf.ri.common;

import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.PropertiesManager.PROPERTY_ANNOTATION_RECENT_COLOR_LABEL;

/**
 * DragDropColorList allows for a list of ColorLabels to be easily sorted by a simple drag and drop.  This list
 * facilitates the management of named colours when selecting annotations colours.
 *
 * @since 6.3
 */
public class DragDropColorList extends JList<DragDropColorList.ColorLabel> {

    private DefaultListModel<ColorLabel> model;

    private Preferences preferences;

    public DragDropColorList(Controller controller, Preferences preferences) {
        super(new DefaultListModel<>());
        this.preferences = preferences;
        model = (DefaultListModel<ColorLabel>) getModel();
        addPropertyChangeListener(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, controller);

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setDragEnabled(true);
        setDropMode(DropMode.INSERT);
        ListItemTransferHandler listItemTransferHandler = new ListItemTransferHandler() {
            @Override
            protected void exportDone(JComponent c, Transferable data, int action) {
                super.exportDone(c, data, action);
                ArrayList<ColorLabel> colorLabels = new ArrayList<>();
                for (int i = 0, max = model.getSize(); i < max; i++) {
                    colorLabels.add(model.get(i));
                }
                storeColorLabels(colorLabels);
            }
        };
        setTransferHandler(listItemTransferHandler);

        setCellRenderer(new ListCellRenderer<ColorLabel>() {
            private final JPanel panel = new JPanel(new BorderLayout(3, 3));
            private final ColorChooserButton colorButton = new ColorChooserButton(Color.lightGray);
            private final JLabel label = new JLabel("", JLabel.LEFT);

            @Override
            public Component getListCellRendererComponent(
                    JList<? extends ColorLabel> list, ColorLabel value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                ColorChooserButton.setButtonBackgroundColor(value.color, colorButton);
                label.setText(value.label);
                label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                panel.add(colorButton, BorderLayout.WEST);
                panel.add(label, BorderLayout.CENTER);
                panel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                return panel;
            }
        });
        // parse out the preferences color data and build the color labels.
        ArrayList<ColorLabel> colorLabels = retrieveColorLabels();
        for (ColorLabel colorLabel : colorLabels) {
            model.addElement(colorLabel);
        }
    }

    public void addNamedColor(Color color, String label) {
        label = cleanLabel(label);
        model.addElement(new ColorLabel(color, label));
        // add the new name/value pair to the preferences
        String currentColorLabels = preferences.get(PROPERTY_ANNOTATION_RECENT_COLOR_LABEL, "");
        if (currentColorLabels.length() == 0) {
            currentColorLabels = color.getRGB() + PropertiesManager.PROPERTY_TOKEN_SEPARATOR + label;
        } else {
            currentColorLabels += PropertiesManager.PROPERTY_TOKEN_SEPARATOR + color.getRGB() +
                    PropertiesManager.PROPERTY_TOKEN_SEPARATOR + label;
        }
        preferences.put(PROPERTY_ANNOTATION_RECENT_COLOR_LABEL, currentColorLabels);
    }

    public void updateNamedColor(Color color, String label) {
        int selectedIndex = getSelectedIndex();
        label = cleanLabel(label);
        ColorLabel selectedColorLabel = new ColorLabel(color, label);
        model.set(selectedIndex, selectedColorLabel);
        // find and update the name/value pair
        // remove and update the preferences list.
        ArrayList<ColorLabel> colorLabels = retrieveColorLabels();
        colorLabels.set(selectedIndex, selectedColorLabel);
        storeColorLabels(colorLabels);
    }

    private String cleanLabel(String label) {
        label = label.isEmpty() ? " " : label;
        // make sure the label doesn't contain the delimiter.
        if (label.contains(PropertiesManager.PROPERTY_TOKEN_SEPARATOR)) {
            label = label.replace(PropertiesManager.PROPERTY_TOKEN_SEPARATOR, " ");
        }
        return label;
    }

    public void removeSelectedNamedColor() {
        int selectedIndex = getSelectedIndex();
        model.removeElementAt(selectedIndex);
        if (selectedIndex > 0) {
            setSelectedIndex(selectedIndex - 1);
        }
        // remove and update the preferences list.
        ArrayList<ColorLabel> colorLabels = retrieveColorLabels();
        colorLabels.remove(selectedIndex);
        storeColorLabels(colorLabels);
    }

    public static ArrayList<ColorLabel> retrieveColorLabels() {
        String currentColorLabels = PropertiesManager.getInstance().getPreferences().get(
                PROPERTY_ANNOTATION_RECENT_COLOR_LABEL, "");
        ArrayList<ColorLabel> colorLabels = null;
        try {
            StringTokenizer toker = new StringTokenizer(currentColorLabels, PropertiesManager.PROPERTY_TOKEN_SEPARATOR);
            colorLabels = new ArrayList<>();
            while (toker.hasMoreTokens()) {
                int rgb = Integer.parseInt(toker.nextToken());
                String label = toker.nextToken();
                colorLabels.add(new ColorLabel(new Color(rgb), label));
            }
        } catch (NumberFormatException e) {
            PropertiesManager.getInstance().getPreferences().put(PROPERTY_ANNOTATION_RECENT_COLOR_LABEL, "");
        }
        return colorLabels;
    }

    private void storeColorLabels(ArrayList<ColorLabel> colorLabels) {
        StringBuilder encodedColorLabels = new StringBuilder();
        int size = colorLabels.size();
        int i = 0;
        for (ColorLabel colorLabel : colorLabels) {
            encodedColorLabels.append(colorLabel.getColor().getRGB()).append(
                    PropertiesManager.PROPERTY_TOKEN_SEPARATOR).append(colorLabel.getLabel());
            i++;
            if (i != size) encodedColorLabels.append(PropertiesManager.PROPERTY_TOKEN_SEPARATOR);
        }
        preferences.put(PROPERTY_ANNOTATION_RECENT_COLOR_LABEL, encodedColorLabels.toString());
        firePropertyChange(PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE, null, true);
    }

    public static class ColorLabel {
        private Color color;
        private String label;

        ColorLabel(Color color, String label) {
            this.color = color;
            this.label = label;
        }

        public Color getColor() {
            return color;
        }

        public String getLabel() {
            return label;
        }
    }

}
