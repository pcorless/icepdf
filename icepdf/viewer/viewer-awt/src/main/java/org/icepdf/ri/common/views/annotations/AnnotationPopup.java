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
package org.icepdf.ri.common.views.annotations;

import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * Base annotation context menu support, includes delete and properties commands.
 *
 * @since 6.3
 */
public class AnnotationPopup extends JPopupMenu implements ActionListener {

    // properties dialog command
    protected JMenuItem propertiesMenuItem;
    protected JMenuItem deleteMenuItem;

    protected AnnotationComponent annotationComponent;

    protected PageViewComponentImpl pageViewComponent;
    protected DocumentViewController documentViewController;
    protected DocumentViewModel documentViewModel;
    protected ResourceBundle messageBundle;

    public AnnotationPopup(AnnotationComponent annotationComponent, DocumentViewController documentViewController,
                           AbstractPageViewComponent pageViewComponent, DocumentViewModel documentViewModel) {
        this.annotationComponent = annotationComponent;
        this.pageViewComponent = (PageViewComponentImpl) pageViewComponent;
        this.documentViewModel = documentViewModel;
        this.documentViewController = documentViewController;
        this.messageBundle = documentViewController.getParentController().getMessageBundle();

        propertiesMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.properties.label"));

        deleteMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.delete.label"));
    }

    public void buildGui() {

        add(deleteMenuItem, -1);
        deleteMenuItem.addActionListener(this);
        addSeparator();
        add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(this);
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;

        if (source == propertiesMenuItem) {
            SwingController swingController = (SwingController) documentViewController.getParentController();
            swingController.showAnnotationProperties(annotationComponent);
        }
        if (source == deleteMenuItem) {
            documentViewController.deleteAnnotation(annotationComponent);
        }
    }
}
