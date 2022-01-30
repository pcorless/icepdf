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
package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.widgets.DragDropColorList;
import org.icepdf.ri.common.utility.annotation.properties.FreeTextAnnotationPanel;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class AnnotationSummaryPanel extends JPanel implements MutableDocument, PropertyChangeListener,
        MouseListener, ComponentListener, ItemListener {

    protected Frame frame;
    protected Controller controller;
    protected ResourceBundle messageBundle;

    protected MarkupAnnotation lastSelectedMarkupAnnotation;

    protected GridBagConstraints constraints;
    protected JPanel annotationsPanel;

    // font configuration
    private JComboBox<ValueLabelItem> fontNameBox;
    private JComboBox<ValueLabelItem> fontSizeBox;
    protected JPanel statusToolbarPanel;

    private static final int DEFAULT_FONT_SIZE = 5;
    private static final int DEFAULT_FONT_FAMILY = 0;


    protected ArrayList<ColorLabelPanel> annotationNamedColorPanels;

    public AnnotationSummaryPanel(Frame frame, Controller controller) {
        this.frame = frame;
        this.controller = controller;
        messageBundle = controller.getMessageBundle();

        setLayout(new BorderLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setFocusable(true);
        constraints = new GridBagConstraints();

        buildStatusToolBarPanel();

        // listen for annotations changes.
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);
        addComponentListener(this);

        // add key listeners for ctr, 0, -, = : reset, decrease and increase font size.
        addFontSizeBindings();
    }

    @Override
    public void refreshDocumentInstance() {
        if (controller.getDocument() != null) {
            // get the named colour and build out the draggable panels.
            Document document = controller.getDocument();
            ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
            int numberOfPanels = colorLabels != null ? colorLabels.size() : 1;
            if (annotationNamedColorPanels != null) annotationNamedColorPanels.clear();
            annotationNamedColorPanels = new ArrayList<>(numberOfPanels);

            if (colorLabels != null && colorLabels.size() > 0) {
                // build a panel for each color
                for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
                    ColorLabelPanel annotationColumnPanel = new ColorLabelPanel(frame, controller, colorLabel);
                    annotationColumnPanel.addPropertyChangeListener(
                            PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE,
                            annotationColumnPanel);
                    annotationNamedColorPanels.add(annotationColumnPanel);
                    annotationColumnPanel.addMouseListener(this);
                    for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                        List<Annotation> annotations = document.getPageTree().getPage(i).getAnnotations();
                        if (annotations != null) {
                            for (Annotation annotation : annotations) {
                                if (annotation instanceof MarkupAnnotation
                                        && colorLabel.getColor().equals(annotation.getColor())) {
                                    annotationColumnPanel.addAnnotation((MarkupAnnotation) annotation);
                                }
                            }
                        }
                    }

                }
                // check to make sure a label has
            } else {
                // other wise just one big panel with all the named colors.
                ColorLabelPanel annotationColumnPanel = new ColorLabelPanel(frame, controller, null);
                annotationColumnPanel.addPropertyChangeListener(
                        PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE,
                        annotationColumnPanel);
                annotationNamedColorPanels.add(annotationColumnPanel);
                for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                    List<Annotation> annotations = document.getPageTree().getPage(i).getAnnotations();
                    if (annotations != null) {
                        for (Annotation annotation : annotations) {
                            if (annotation instanceof MarkupAnnotation) {
                                annotationColumnPanel.addAnnotation((MarkupAnnotation) annotation);
                            }
                        }
                    }
                }
            }
        }
        refreshPanelLayout();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == fontSizeBox) {
            ViewerPropertiesManager propertiesManager = controller.getPropertiesManager();
            propertiesManager.getPreferences().putInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE,
                    (int) fontSizeBox.getModel().getElementAt(fontSizeBox.getSelectedIndex()).getValue());
            ValueLabelItem tmp = (ValueLabelItem) fontSizeBox.getSelectedItem();
            // fire the font size property change event.
            updateSummaryFontSizes(0, (int) tmp.getValue());
        }
    }

    private void updateSummaryFontSizes(int oldFontSizeIndex, int newFontSizeIndex) {
        if (annotationNamedColorPanels != null) {
            for (ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                colorLabelPanel.firePropertyChange(PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE,
                        oldFontSizeIndex, newFontSizeIndex);
            }
        }
    }

    private void addFontSizeBindings() {
        InputMap inputMap = getInputMap(WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();

        /// ctrl-- to increase font size.
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-increase");
        actionMap.put("font-size-increase", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fontSizeBox.getSelectedIndex() + 1 < fontSizeBox.getItemCount()) {
                    fontSizeBox.setSelectedIndex(fontSizeBox.getSelectedIndex() + 1);
                }
            }
        });

        // ctrl-0 to dfeault font size.
        key = KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-default");
        actionMap.put("font-size-default", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fontSizeBox.setSelectedIndex(DEFAULT_FONT_SIZE);
            }
        });

        // ctrl-- to decrease font size.
        key = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_MASK);
        inputMap.put(key, "font-size-decrease");
        actionMap.put("font-size-decrease", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fontSizeBox.getSelectedIndex() - 1 >= 0) {
                    fontSizeBox.setSelectedIndex(fontSizeBox.getSelectedIndex() - 1);
                }
            }
        });
    }

    protected void buildStatusToolBarPanel() {

        ViewerPropertiesManager propertiesManager = controller.getPropertiesManager();

        fontSizeBox = new JComboBox<>(FreeTextAnnotationPanel.generateFontSizeNameList(messageBundle));
        applySelectedValue(fontSizeBox, propertiesManager.checkAndStoreIntProperty(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE, new JLabel().getFont().getSize()));
        fontSizeBox.addItemListener(this);

        statusToolbarPanel = new JPanel(new GridBagLayout());
        statusToolbarPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 0);
        addGB(statusToolbarPanel, new JLabel(messageBundle.getString("viewer.annotationSummary.fontSize.label")),
                0, 0, 1, 1);
        addGB(statusToolbarPanel, fontSizeBox, 1, 0, 1, 1);
        constraints.weightx = 1;
        addGB(statusToolbarPanel, new JLabel(), 2, 0, 1, 1);
    }

    public void refreshPanelLayout() {
        removeAll();

        annotationsPanel = new JPanel(new GridBagLayout());
        annotationsPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        add(annotationsPanel, BorderLayout.CENTER);
        add(statusToolbarPanel, BorderLayout.SOUTH);

        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        int numberOfPanels = colorLabels != null && colorLabels.size() > 0 ? colorLabels.size() : 1;
        constraints.weightx = 1.0 / (float) numberOfPanels;
        constraints.weighty = 1.0f;
        constraints.insets = new Insets(0, 5, 0, 0);
        constraints.fill = GridBagConstraints.BOTH;
        int k = 0;
        for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
            if (annotationColumnPanel.getNumberOfComponents() > 0) {
                addGB(annotationsPanel, annotationColumnPanel, ++k, 0, 1, 1);
            }
        }
        invalidate();
        revalidate();
        repaint();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        String propertyName = evt.getPropertyName();
        switch (propertyName) {
            case PropertyConstants.ANNOTATION_DELETED:
                if (oldValue instanceof MarkupAnnotationComponent) {
                    // find an remove the markup annotation node.
                    MarkupAnnotationComponent comp = (MarkupAnnotationComponent) oldValue;
                    MarkupAnnotation markupAnnotation = (MarkupAnnotation) comp.getAnnotation();
                    if (annotationNamedColorPanels != null) {
                        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
                        if (colorLabels != null) {
                            for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                                if (annotationColumnPanel.getColorLabel() != null &&
                                        annotationColumnPanel.getColorLabel().getColor().equals(markupAnnotation.getColor())) {
                                    annotationColumnPanel.removeAnnotation(markupAnnotation);
                                    refreshPanelLayout();
                                    break;
                                }
                            }
                        } else {
                            // just add the component to the single column
                            ColorLabelPanel annotationColumnPanel = annotationNamedColorPanels.get(0);
                            annotationColumnPanel.removeAnnotation(markupAnnotation);
                            refreshPanelLayout();
                            break;
                        }
                    }
                }
                break;
            case PropertyConstants.ANNOTATION_UPDATED:
                if (newValue instanceof PopupAnnotationComponent) {
                    // find an remove the markup annotation node.
                    if (annotationNamedColorPanels != null) {
                        PopupAnnotationComponent comp = (PopupAnnotationComponent) newValue;
                        MarkupAnnotation markupAnnotation = comp.getAnnotation().getParent();
                        if (markupAnnotation != null) {
                            ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
                            if (colorLabels != null) {
                                for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                                    if (annotationColumnPanel.getColorLabel() != null &&
                                            annotationColumnPanel.getColorLabel().getColor().equals(markupAnnotation.getColor())) {
                                        annotationColumnPanel.updateAnnotation(markupAnnotation);
                                        break;
                                    }
                                }
                            } else {
                                // just add the component to the single column
                                ColorLabelPanel annotationColumnPanel = annotationNamedColorPanels.get(0);
                                annotationColumnPanel.updateAnnotation(markupAnnotation);
                                break;
                            }
                        }
                    }
                }
                break;
            case PropertyConstants.ANNOTATION_ADDED:
                // rebuild the tree so we get a good sort etc and do  worker thread setup.
                if (newValue instanceof PopupAnnotationComponent) {
                    // find an remove the markup annotation node.
                    if (annotationNamedColorPanels != null) {
                        PopupAnnotationComponent comp = (PopupAnnotationComponent) newValue;
                        MarkupAnnotation markupAnnotation = comp.getAnnotation().getParent();
                        if (markupAnnotation != null) {
                            ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
                            if (colorLabels != null) {
                                for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                                    if (annotationColumnPanel.getColorLabel() != null &&
                                            annotationColumnPanel.getColorLabel().getColor().equals(markupAnnotation.getColor())) {
                                        annotationColumnPanel.addAnnotation(markupAnnotation);
                                        refreshPanelLayout();
                                        break;
                                    }
                                }
                            } else {
                                ColorLabelPanel annotationColumnPanel = annotationNamedColorPanels.get(0);
                                annotationColumnPanel.addAnnotation(markupAnnotation);
                                refreshPanelLayout();
                            }
                        }
                    }
                }
                break;
            case PropertyConstants.ANNOTATION_QUICK_COLOR_CHANGE:
                if (lastSelectedMarkupAnnotation != null) {
                    // find and remove,
                    if (annotationNamedColorPanels != null) {
                        for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                            annotationColumnPanel.removeAnnotation(lastSelectedMarkupAnnotation);
                            refreshPanelLayout();
                        }
                        // and then add back in.
                        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
                        if (colorLabels != null) {
                            for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
                                if (annotationColumnPanel.getColorLabel().getColor().equals(lastSelectedMarkupAnnotation.getColor())) {
                                    annotationColumnPanel.addAnnotation(lastSelectedMarkupAnnotation);
                                    refreshPanelLayout();
                                    break;
                                }
                            }
                        } else {
                            ColorLabelPanel annotationColumnPanel = annotationNamedColorPanels.get(0);
                            annotationColumnPanel.addAnnotation(lastSelectedMarkupAnnotation);
                            refreshPanelLayout();
                        }
                    }

                }
                break;
            case PropertyConstants.ANNOTATION_COLOR_PROPERTY_PANEL_CHANGE:
                // no choice but to do a full refresh, order will be lost.
                refreshDocumentInstance();
                break;
            case PropertyConstants.ANNOTATION_SELECTED:
            case PropertyConstants.ANNOTATION_FOCUS_GAINED:
                if (newValue instanceof MarkupAnnotationComponent) {
                    lastSelectedMarkupAnnotation = (MarkupAnnotation) ((MarkupAnnotationComponent) newValue).getAnnotation();
                }
                break;

        }
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

    @Override
    public void disposeDocument() {
        annotationNamedColorPanels.clear();
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

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            Component comp = (Component) e.getSource();
            if (annotationNamedColorPanels != null) {
                double weightX = 1.0 / (float) annotationNamedColorPanels.size();
                GridBagLayout gridBagLayout = (GridBagLayout) annotationsPanel.getLayout();
                for (ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                    GridBagConstraints constraints = gridBagLayout.getConstraints(colorLabelPanel);
                    if (colorLabelPanel.equals(comp)) {
                        constraints.weightx = 0.9;
                    } else {
                        constraints.weightx = weightX;
                    }
                    gridBagLayout.setConstraints(colorLabelPanel, constraints);
                    colorLabelPanel.invalidate();
                }
                revalidate();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {
        // reset the constraint back to an even division of
        if (annotationNamedColorPanels != null) {
            double weightX = 1.0 / (float) annotationNamedColorPanels.size();
            GridBagLayout gridBagLayout = (GridBagLayout) annotationsPanel.getLayout();
            for (ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                GridBagConstraints constraints = gridBagLayout.getConstraints(colorLabelPanel);
                constraints.weightx = weightX;
                gridBagLayout.setConstraints(colorLabelPanel, constraints);
            }
            invalidate();
            revalidate();
        }
    }

    @Override
    public void componentMoved(ComponentEvent e) {

    }

    @Override
    public void componentShown(ComponentEvent e) {

    }

    @Override
    public void componentHidden(ComponentEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
