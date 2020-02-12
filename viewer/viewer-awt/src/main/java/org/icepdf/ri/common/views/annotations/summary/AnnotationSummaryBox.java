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

import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.PageViewComponentImpl;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;

public class AnnotationSummaryBox extends PopupAnnotationComponent implements FocusListener {

    protected boolean showTextBlock = true;

    public AnnotationSummaryBox(PopupAnnotation annotation, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent, true);

        setFocusable(false);
        removeFocusListener(this);

        adjustBounds = false;

        commentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        // hides a bunch of the controls.
        commentPanel.removeMouseListener(popupListener);
        commentPanel.removeMouseListener(this);
        commentPanel.removeMouseMotionListener(this);

        privateToggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        minimizeButton.setVisible(false);
        textArea.setEditable(true);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        commentPanel.getInsets().set(10, 10, 10, 10);
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        ViewerPropertiesManager propertiesManager = documentViewController.getParentController().getPropertiesManager();
        setFontSize(propertiesManager.getPreferences().getInt(
                ViewerPropertiesManager.PROPERTY_ANNOTATION_SUMMARY_FONT_SIZE, new JLabel().getFont().getSize()));
        // remove super mouse listener as it interferes with the drag and drop.
        removeMouseWheelListener(this);
    }

    public void toggleTextBlockVisibility() {
        showTextBlock = !showTextBlock;
        textArea.setVisible(showTextBlock);
    }

    public boolean isShowTextBlockVisible() {
        return showTextBlock;
    }

    protected void updateContent(DocumentEvent e) {
        // get the next text and save it to the selected markup annotation.
        Document document = e.getDocument();
        try {
            if (document.getLength() > 0) {
                selectedMarkupAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
                selectedMarkupAnnotation.setContents(
                        document.getText(0, document.getLength()));
                // add them to the container, using absolute positioning.
                documentViewController.updatedSummaryAnnotation(this);
            }
        } catch (BadLocationException ex) {
            logger.log(Level.FINE, "Error updating markup annotation content", ex);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null)
            return;
        if (source == privateToggleButton) {
            boolean selected = privateToggleButton.isSelected();
            MarkupAnnotation markupAnnotation = annotation.getParent();
            if (markupAnnotation != null) {
                markupAnnotation.setFlag(Annotation.FLAG_PRIVATE_CONTENTS, selected);
                markupAnnotation.setModifiedDate(PDate.formatDateTime(new Date()));
                documentViewController.updatedSummaryAnnotation(this);
            }
        }
    }

    public Controller getController() {
        return documentViewController.getParentController();
    }

    public JPopupMenu getContextMenu(Frame frame, DraggableAnnotationPanel.MouseHandler mouseHandler) {
        MarkupAnnotationComponent comp = (MarkupAnnotationComponent) getAnnotationParentComponent();
        // page may not have been initialized and thus we don't have a component
        if (comp == null) {
            int pageIndex = annotation.getParent().getPageIndex();
            PageViewComponentImpl pageViewComponent = (PageViewComponentImpl)
                    documentViewController.getParentController().getDocumentViewController()
                            .getDocumentViewModel().getPageComponents().get(pageIndex);
            pageViewComponent.refreshAnnotationComponents(pageViewComponent.getPage(), false);
            ArrayList<AbstractAnnotationComponent> comps = pageViewComponent.getAnnotationComponents();
            for (AbstractAnnotationComponent abstractComp : comps) {
                Annotation pageAnnotation = abstractComp.getAnnotation();
                Annotation parent = annotation.getParent();
                if (pageAnnotation != null && parent != null &&
                        pageAnnotation.getPObjectReference().equals(parent.getPObjectReference())) {
                    comp = (MarkupAnnotationComponent) abstractComp;
                    break;
                }
            }
        }
        return new SummaryPopupMenu(this, (MarkupAnnotation) comp.getAnnotation(), comp,
                documentViewController.getParentController(),
                frame, mouseHandler);
    }

}
