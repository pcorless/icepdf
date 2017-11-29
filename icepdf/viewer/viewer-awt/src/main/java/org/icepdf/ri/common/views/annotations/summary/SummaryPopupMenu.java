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

import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.AnnotationPopup;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.logging.Logger;

/**
 * The summary view is made up of annotation contents for markup annotations.  The view however is built independently
 * of the the page view and the component state may not be in correct state to use the default MarkupAnnotationPopupMenu
 * <p>
 * This class takes into account that the component state is not guaranteed.
 *
 * @since 6.3
 */
public class SummaryPopupMenu extends AnnotationPopup<MarkupAnnotationComponent> implements ItemListener {

    private static final Logger logger =
            Logger.getLogger(SummaryPopupMenu.class.toString());

    protected AnnotationSummaryBox annotationSummaryBox;
    protected MarkupAnnotation markupAnnotation;
    protected Frame frame;
    protected JCheckBoxMenuItem showHideTextBlockMenuItem;
    protected DraggableAnnotationPanel.MouseHandler mouseHandler;

    public SummaryPopupMenu(AnnotationSummaryBox annotationSummaryBox, MarkupAnnotation markupAnnotation, MarkupAnnotationComponent annotationComponent,
                            Controller controller, Frame frame, DraggableAnnotationPanel.MouseHandler mouseHandler) {
        super(annotationComponent, controller, null);
        this.frame = frame;
        this.markupAnnotation = markupAnnotation;
        this.annotationSummaryBox = annotationSummaryBox;
        this.mouseHandler = mouseHandler;
        this.buildGui();
    }

    public void buildGui() {
        showHideTextBlockMenuItem = new JCheckBoxMenuItem(
                messageBundle.getString("viewer.annotation.popup.showHidTextBlock.label"));
        showHideTextBlockMenuItem.setSelected(annotationSummaryBox.isShowTextBlockVisible());
        showHideTextBlockMenuItem.addItemListener(this);
        add(showHideTextBlockMenuItem);
        addSeparator();
        add(deleteMenuItem);
        deleteMenuItem.addActionListener(this);
        deleteMenuItem.setEnabled(controller.havePermissionToModifyDocument());
        addSeparator();
        add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;
        if (source == propertiesMenuItem) {
            controller.showAnnotationProperties(annotationComponent, frame);
        } else if (source == deleteMenuItem) {
            controller.getDocumentViewController().deleteAnnotation(annotationComponent);
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getSource() == showHideTextBlockMenuItem) {
            annotationSummaryBox.toggleTextBlockVisibility();
            annotationSummaryBox.invalidate();
            annotationSummaryBox.validate();
            SwingUtilities.invokeLater(() -> mouseHandler.checkForOverlap(annotationSummaryBox));
        }
    }
}
