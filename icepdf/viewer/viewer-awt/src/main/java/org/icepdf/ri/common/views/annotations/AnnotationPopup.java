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

import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.utility.annotation.properties.NameTreeDialog;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.PageViewComponentImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * Base annotation context menu support, includes delete and properties commands.
 *
 * @since 6.3
 */
public class AnnotationPopup<T extends AnnotationComponent> extends JPopupMenu implements ActionListener {

    // properties dialog command
    protected JMenuItem propertiesMenuItem;
    protected JMenuItem deleteMenuItem;
    protected JMenuItem destinationsMenuItem;

    protected T annotationComponent;

    protected PageViewComponentImpl pageViewComponent;
    protected Controller controller;
    protected ResourceBundle messageBundle;

    public AnnotationPopup(T annotationComponent, Controller controller,
                           AbstractPageViewComponent pageViewComponent) {
        this.annotationComponent = annotationComponent;
        this.pageViewComponent = (PageViewComponentImpl) pageViewComponent;
        this.controller = controller;
        this.messageBundle = controller.getMessageBundle();
        boolean modifyDocument = controller.havePermissionToModifyDocument();

        propertiesMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.properties.label"));

        deleteMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.delete.label"));
        deleteMenuItem.setEnabled(modifyDocument);

        destinationsMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.destinations.label"));
    }

    public void buildGui() {
        add(destinationsMenuItem, -1);
        destinationsMenuItem.addActionListener(this);
        if (!(annotationComponent.getAnnotation() instanceof LinkAnnotation)) {
            destinationsMenuItem.setEnabled(false);
        }
        addSeparator();
        add(deleteMenuItem, -1);
        deleteMenuItem.addActionListener(this);
        addSeparator();
        add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(this);
    }

    protected void setDeleteMenuItemEnabledState() {
        if (destinationsMenuItem != null) {
            destinationsMenuItem.setEnabled(controller.getDocument().getCatalog().getNames() != null);
        }
    }

    @Override
    public void show(Component invoker, int x, int y) {
        setDeleteMenuItemEnabledState();
        super.show(invoker, x, y);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;

        if (source == propertiesMenuItem) {
            controller.showAnnotationProperties(annotationComponent);
        } else if (source == deleteMenuItem) {
            controller.getDocumentViewController().deleteAnnotation(annotationComponent);
        } else if (source == destinationsMenuItem) {
            // test implementation of a NameJTree for destinations.
            if (controller.getDocument().getCatalog().getNames() != null) {
                NameTree nameTree = controller.getDocument().getCatalog().getNames().getDestsNameTree();
                if (nameTree != null) {
                    // create new dialog instance.
                    NameTreeDialog nameTreeDialog = new NameTreeDialog(
                            controller,
                            true, nameTree);
                    // get existing names
                    LinkAnnotation annotation = (LinkAnnotation) annotationComponent.getAnnotation();
                    Object dest = annotation.getEntries().get(LinkAnnotation.DESTINATION_KEY);
                    String destName = "";
                    if (dest != null && dest instanceof LiteralStringObject) {
                        destName = ((LiteralStringObject) dest).getDecryptedLiteralString(
                                controller.getDocument().getSecurityManager());
                    }
                    nameTreeDialog.setDestinationName(destName);
                    // add the nameTree instance.
                    nameTreeDialog.setVisible(true);
                    // apply the new names
                    annotation.setNamedDestination(nameTreeDialog.getDestinationName());
                    // dispose the dialog
                    nameTreeDialog.dispose();
                }
            }
        }
    }
}
