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
package org.icepdf.ri.common.views.annotations.summary.colorpanel;

import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;
import org.icepdf.ri.common.widgets.DragDropColorList;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 *
 */
public class ColorLabelPanel extends JPanel {

    private final DragDropColorList.ColorLabel colorLabel;
    private final DraggableAnnotationPanel draggableAnnotationPanel;
    protected final ColorPanelController controller;
    protected final SummaryController summaryController;

    public ColorLabelPanel(final Frame frame, final DragDropColorList.ColorLabel colorLabel, final SummaryController summaryController) {
        this.controller = createColorPanelController();
        this.colorLabel = colorLabel;
        this.summaryController = summaryController;

        // setup the gui
        setLayout(new BorderLayout());
        if (colorLabel != null) {
            add(new JLabel("<html><h3>" + colorLabel.getLabel() + "</h3></html>", JLabel.CENTER), BorderLayout.NORTH);
        }
        draggableAnnotationPanel = createDraggableAnnotationPanel(frame);
        draggableAnnotationPanel.addMouseWheelListener(e -> draggableAnnotationPanel.getParent()
                .dispatchEvent(SwingUtilities.convertMouseEvent(draggableAnnotationPanel, e, draggableAnnotationPanel.getParent())));
        final JScrollPane scrollPane = new JScrollPane(draggableAnnotationPanel);
        scrollPane.addMouseWheelListener(e -> dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, ColorLabelPanel.this)));
        add(scrollPane, BorderLayout.CENTER);
        addMouseWheelListener(e -> getParent().dispatchEvent(SwingUtilities.convertMouseEvent(this, e, getParent())));
    }

    protected ColorPanelController createColorPanelController() {
        return new ColorPanelController(this);
    }

    protected DraggableAnnotationPanel createDraggableAnnotationPanel(final Frame frame) {
        return new DraggableAnnotationPanel(frame, this, summaryController);
    }

    public ColorPanelController getController() {
        return controller;
    }

    public DraggableAnnotationPanel getDraggableAnnotationPanel() {
        return draggableAnnotationPanel;
    }

    public int getNumberOfComponents() {
        return draggableAnnotationPanel.getComponentCount();
    }

    public void compact(final UUID uuid) {
        draggableAnnotationPanel.compact(uuid);
    }

    public AnnotationSummaryBox addAnnotation(final MarkupAnnotation markupAnnotation, final int y) {
        final PopupAnnotation popupAnnotation = markupAnnotation.getPopupAnnotation();
        if (popupAnnotation != null) {
            final List<AbstractPageViewComponent> pageComponents =
                    summaryController.getController().getDocumentViewController().getDocumentViewModel().getPageComponents();
            final int pageIndex = markupAnnotation.getPageIndex();
            if (pageIndex >= 0) {
                final AnnotationSummaryBox popupAnnotationComponent = createSummaryBox(popupAnnotation,
                        summaryController.getController().getDocumentViewController(), pageComponents.get(pageIndex), summaryController);
                popupAnnotationComponent.setVisible(true);
                popupAnnotationComponent.removeMouseListeners();
                final AnnotationSummaryGroup parent = summaryController.getGroupManager().getParentOf(markupAnnotation);
                if (parent != null) {
                    parent.addComponent(popupAnnotationComponent);
                } else {
                    draggableAnnotationPanel.add(popupAnnotationComponent, y);
                    draggableAnnotationPanel.revalidate();
                    draggableAnnotationPanel.repaint();
                }
                popupAnnotationComponent.fireComponentMoved(false, false, UUID.randomUUID());
                return popupAnnotationComponent;
            } else return null;
        } else return null;
    }

    protected AnnotationSummaryBox createSummaryBox(final PopupAnnotation popupAnnotation,
                                                    final DocumentViewController documentViewController,
                                                    final AbstractPageViewComponent pvc, final SummaryController controller) {
        return new AnnotationSummaryBox(popupAnnotation, documentViewController, pvc, controller);
    }

    public AnnotationSummaryComponent findComponentFor(final Predicate<AnnotationSummaryBox> filter) {
        return draggableAnnotationPanel.findComponentFor(filter);
    }

    public void addBox(final AnnotationSummaryBox box, final int y) {
        draggableAnnotationPanel.add(box, y, box.isComponentSelected());
        draggableAnnotationPanel.revalidate();
        draggableAnnotationPanel.validate();
        draggableAnnotationPanel.repaint();
    }

    public void addComponent(final AnnotationSummaryComponent component, final int y) {
        if (component instanceof AnnotationSummaryGroup) {
            addGroup((AnnotationSummaryGroup) component, y);
        } else if (component instanceof AnnotationSummaryBox) {
            addBox((AnnotationSummaryBox) component, y);
        }
    }

    public void addComponent(final AnnotationSummaryComponent component) {
        addComponent(component, -1);
    }

    public AnnotationSummaryBox addAnnotation(final MarkupAnnotation markupAnnotation) {
        if (!markupAnnotation.isInReplyTo()) {
            return addAnnotation(markupAnnotation, -1);
        } else {
            return null;
        }
    }

    public void addGroup(final AnnotationSummaryGroup group, final int y) {
        draggableAnnotationPanel.add(group, y, group.isComponentSelected());
        draggableAnnotationPanel.revalidate();
        draggableAnnotationPanel.validate();
        draggableAnnotationPanel.repaint();
    }

    public void removeComponent(final AnnotationSummaryComponent c) {
        draggableAnnotationPanel.remove(c.asComponent());
    }

    public void updateAnnotation(final MarkupAnnotation markupAnnotation) {
        draggableAnnotationPanel.update(markupAnnotation);
    }

    public void removeAnnotation(final MarkupAnnotation markupAnnotation) {
        draggableAnnotationPanel.remove(markupAnnotation);
    }

    public boolean contains(final MarkupAnnotation annotation) {
        return draggableAnnotationPanel.contains(annotation);
    }

    public DragDropColorList.ColorLabel getColorLabel() {
        return colorLabel;
    }

    public void updateFontFamily(final String familyName) {
        controller.updateFontFamily(familyName);
    }
}
