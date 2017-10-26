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
package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.DragDropColorList;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class AnnotationSummaryPanel extends JPanel implements MutableDocument, PropertyChangeListener,
        MouseListener, ComponentListener {

    protected Controller controller;
    protected ResourceBundle messageBundle;

    protected MarkupAnnotation lastSelectedMarkupAnnotation;

    protected GridBagConstraints constraints;

    protected ArrayList<ColorLabelPanel> annotationNamedColorPanels;

    public AnnotationSummaryPanel(Controller controller) {
        this.controller = controller;
        messageBundle = controller.getMessageBundle();

        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setFocusable(true);
        constraints = new GridBagConstraints();

        // listen for annotations changes.
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);

        addComponentListener(this);
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
                    ColorLabelPanel annotationColumnPanel = new ColorLabelPanel(controller, colorLabel);
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
                ColorLabelPanel annotationColumnPanel = new ColorLabelPanel(controller, null);
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

    public void refreshPanelLayout() {
        removeAll();
        ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
        int numberOfPanels = colorLabels != null && colorLabels.size() > 0 ? colorLabels.size() : 1;
        constraints.weightx = 1.0 / (float) numberOfPanels;
        constraints.weighty = 1.0f;
        constraints.insets = new Insets(0, 5, 0, 0);
        constraints.fill = GridBagConstraints.BOTH;
        int k = 0;
        for (ColorLabelPanel annotationColumnPanel : annotationNamedColorPanels) {
            if (annotationColumnPanel.getNumberOfComponents() > 0) {
                addGB(this, annotationColumnPanel, ++k, 0, 1, 1);
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
                                        refreshPanelLayout();
                                        break;
                                    }
                                }
                            } else {
                                // just add the component to the single column
                                ColorLabelPanel annotationColumnPanel = annotationNamedColorPanels.get(0);
                                annotationColumnPanel.updateAnnotation(markupAnnotation);
                                refreshPanelLayout();
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
                GridBagLayout gridBagLayout = (GridBagLayout) this.getLayout();
                for (ColorLabelPanel colorLabelPanel : annotationNamedColorPanels) {
                    GridBagConstraints constraints = gridBagLayout.getConstraints(colorLabelPanel);
                    if (colorLabelPanel.equals(comp)) {
                        constraints.weightx = 1;
                    } else {
                        constraints.weightx = weightX;
                    }
                    gridBagLayout.setConstraints(colorLabelPanel, constraints);
                }
                invalidate();
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
            GridBagLayout gridBagLayout = (GridBagLayout) this.getLayout();
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
