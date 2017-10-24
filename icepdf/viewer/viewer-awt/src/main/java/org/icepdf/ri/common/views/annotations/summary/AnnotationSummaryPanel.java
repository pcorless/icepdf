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
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.DragDropColorList;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class AnnotationSummaryPanel extends JPanel implements MutableDocument, PropertyChangeListener {

    protected Controller controller;
    protected ResourceBundle messageBundle;

    protected GridBagConstraints constraints;

    protected ArrayList<AnnotationColumnPanel> annotationNamedColorPanels;

    public AnnotationSummaryPanel(Controller controller) {
        this.controller = controller;
        messageBundle = controller.getMessageBundle();

        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);
        setFocusable(true);
        constraints = new GridBagConstraints();

        // listen for annotations changes.
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);
    }

    @Override
    public void refreshDocumentInstance() {
        removeAll();
        if (controller.getDocument() != null) {
            // get the named colour and build out the draggable panels.
            Document document = controller.getDocument();
            ArrayList<DragDropColorList.ColorLabel> colorLabels = DragDropColorList.retrieveColorLabels();
            int numberOfPanels = colorLabels != null ? colorLabels.size() : 1;
            if (annotationNamedColorPanels != null) annotationNamedColorPanels.clear();
            annotationNamedColorPanels = new ArrayList<>(numberOfPanels);
            constraints.weightx = 1.0 / (float) numberOfPanels;
            constraints.weighty = 1.0f;
            constraints.insets = new Insets(2, 2, 2, 2);
            constraints.fill = GridBagConstraints.BOTH;

            if (colorLabels != null && colorLabels.size() > 0) {
                if (annotationNamedColorPanels != null) annotationNamedColorPanels.clear();
                annotationNamedColorPanels = new ArrayList<>(colorLabels.size());
                constraints.weightx = 1.0 / (float) colorLabels.size();
                constraints.weighty = 1.0f;
                constraints.insets = new Insets(2, 2, 2, 2);
                constraints.fill = GridBagConstraints.BOTH;
                // build a panel for each color
                int k = 0;
                for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
                    AnnotationColumnPanel annotationColumnPanel = new AnnotationColumnPanel();
                    annotationNamedColorPanels.add(annotationColumnPanel);
                    constraints.weighty = 0.001;
                    addGB(this, new JLabel(
                            "<html><h3>" + colorLabel.getLabel() + "</h3></html>", JLabel.CENTER), k, 0, 1, 1);
                    constraints.weighty = 1.5f;
                    addGB(this, new JScrollPane(annotationColumnPanel), k, 1, 1, 1);
                    for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                        List<AbstractPageViewComponent> pageComponents =
                                controller.getDocumentViewController().getDocumentViewModel().getPageComponents();
                        List<Annotation> annotations = document.getPageTree().getPage(i).getAnnotations();
                        if (annotations != null) {
                            for (Annotation annotation : annotations) {
                                if (annotation instanceof MarkupAnnotation
                                        && colorLabel.getColor().equals(annotation.getColor())) {
                                    MarkupAnnotation markupAnnotation = (MarkupAnnotation) annotation;
                                    PopupAnnotation popupAnnotation = markupAnnotation.getPopupAnnotation();
                                    if (popupAnnotation != null) {
                                        AnnotationSummaryBox popupAnnotationComponent =
                                                new AnnotationSummaryBox(popupAnnotation,
                                                        controller.getDocumentViewController(), pageComponents.get(i));
                                        popupAnnotationComponent.setVisible(true);
                                        popupAnnotationComponent.removeMouseListeners();
                                        annotationColumnPanel.add(popupAnnotationComponent);
                                    }
                                }
                            }
                        }
                    }
                    k++;
                }
            } else {
                constraints.weightx = 1.0f;
                constraints.weighty = 1.0f;
                constraints.insets = new Insets(2, 2, 2, 2);
                constraints.fill = GridBagConstraints.BOTH;
                // other wise just one big panel with all the named colors.
                AnnotationColumnPanel annotationColumnPanel = new AnnotationColumnPanel(10);
                annotationNamedColorPanels = new ArrayList<>(1);
                annotationNamedColorPanels.add(annotationColumnPanel);
                addGB(this, annotationColumnPanel, 0, 0, 1, 1);
                for (int i = 0, max = document.getNumberOfPages(); i < max; i++) {
                    List<AbstractPageViewComponent> pageComponents =
                            controller.getDocumentViewController().getDocumentViewModel().getPageComponents();
                    List<Annotation> annotations = document.getPageTree().getPage(i).getAnnotations();
                    if (annotations != null) {
                        for (Annotation annotation : annotations) {
                            if (annotation instanceof MarkupAnnotation) {
                                MarkupAnnotation markupAnnotation = (MarkupAnnotation) annotation;
                                PopupAnnotation popupAnnotation = markupAnnotation.getPopupAnnotation();
                                if (popupAnnotation != null) {
                                    AnnotationSummaryBox popupAnnotationComponent =
                                            new AnnotationSummaryBox(popupAnnotation,
                                                    controller.getDocumentViewController(), pageComponents.get(i));
                                    popupAnnotationComponent.setVisible(true);
                                    popupAnnotationComponent.removeMouseListeners();
                                    annotationColumnPanel.add(popupAnnotationComponent);
                                }
                            }
                        }
                    }
                }
            }
        }
        invalidate();
        revalidate();
        repaint();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (PropertyConstants.ANNOTATION_DELETED.equals(evt.getPropertyName())) {
            if (evt.getOldValue() instanceof MarkupAnnotationComponent) {
                // find an remove the markup annotation node.
                MarkupAnnotationComponent comp = (MarkupAnnotationComponent) evt.getOldValue();
                MarkupAnnotation markupAnnotation = comp.getAnnotation();
                if (annotationNamedColorPanels != null) {
                    for (AnnotationColumnPanel annotationColumnPanel : annotationNamedColorPanels) {
                        for (Component component : annotationColumnPanel.getComponents()) {
                            if (component instanceof AnnotationSummaryBox) {
                                AnnotationSummaryBox annotationSummaryBox = (AnnotationSummaryBox) component;
                                MarkupAnnotation currentMarkupAnnotation = annotationSummaryBox.getAnnotation().getParent();
                                if (markupAnnotation.getPObjectReference().equals(currentMarkupAnnotation.getPObjectReference())) {
                                    annotationColumnPanel.remove(component);
                                    annotationColumnPanel.revalidate();
                                    annotationColumnPanel.repaint();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } else if (PropertyConstants.ANNOTATION_UPDATED.equals(evt.getPropertyName())) {
            if (evt.getNewValue() instanceof PopupAnnotationComponent) {
                // find the markup annotation
                PopupAnnotationComponent comp = (PopupAnnotationComponent) evt.getNewValue();
                PopupAnnotation popupAnnotation = comp.getAnnotation();
                if (popupAnnotation.getParent() != null) {
                    MarkupAnnotation markupAnnotation = popupAnnotation.getParent();
                    // only update root pop annotation comment
//                    if (!markupAnnotation.isInReplyTo()) {
//                        for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
//                            AnnotationTreeNode node = findAnnotationTreeNode(rootTreeNode.getChildAt(i), markupAnnotation);
//                            if (node != null) {
//                                node.applyMessage(markupAnnotation, messageBundle);
//                                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
//                                break;
//                            }
//                        }
//                    }
                }
                System.out.println("updated");
            }
        } else if (PropertyConstants.ANNOTATION_ADDED.equals(evt.getPropertyName())) {
            // rebuild the tree so we get a good sort etc and do  worker thread setup.
//            if (evt.getNewValue() instanceof MarkupAnnotationComponent) {
//                refreshMarkupTree();
//            }
            System.out.println("added");
        }
    }

    @Override
    public void disposeDocument() {

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
