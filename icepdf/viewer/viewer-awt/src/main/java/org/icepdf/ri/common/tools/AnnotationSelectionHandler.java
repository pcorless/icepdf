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
package org.icepdf.ri.common.tools;

import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ResourceBundle;

/**
 * The AnnotationSelectionHandler is responsible for deselecting all annotations
 * when the a mouse click event has been fired.
 *
 * @since 5.0
 */
public class AnnotationSelectionHandler extends CommonToolHandler
        implements ToolHandler, ActionListener {

    protected DocumentViewController documentViewController;

    protected AbstractPageViewComponent pageViewComponent;

    private JPopupMenu contextMenu;
    private JMenuItem addDestinationMenuItem;
    private JMenuItem freeTextMenuItem;
    private int x, y;

    public AnnotationSelectionHandler(DocumentViewController documentViewController,
                                      AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        this.documentViewController = documentViewController;
        this.pageViewComponent = pageViewComponent;

        ResourceBundle messageBundle = documentViewController.getParentController().getMessageBundle();
        contextMenu = new JPopupMenu();
        // create destination menu item
        addDestinationMenuItem = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.view.selectionTool.contextMenu.addDestination.label"));
        addDestinationMenuItem.setIcon(new ImageIcon(Images.get("destination_20.png")));
        addDestinationMenuItem.addActionListener(this);
        contextMenu.add(addDestinationMenuItem);
        // create free text menu item.
        freeTextMenuItem = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.view.selectionTool.contextMenu.addFreeText.label"));
        freeTextMenuItem.setIcon(new ImageIcon(Images.get("freetext_annot_a_20.png")));
        freeTextMenuItem.addActionListener(this);
        contextMenu.add(freeTextMenuItem);
    }

    public void mouseClicked(MouseEvent e) {
        documentViewController.clearSelectedAnnotations();
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
        if (pageViewComponent != null &&
                e.getButton() == MouseEvent.BUTTON3) {
            x = e.getX();
            y = e.getY();
            contextMenu.show(e.getComponent(), x, y);
        }
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == addDestinationMenuItem) {
            // convert bbox and start and end line points.
            new DestinationHandler(documentViewController, pageViewComponent).createNewDestination(x, y);
        } else if (e.getSource() == freeTextMenuItem) {
            new FreeTextAnnotationHandler(documentViewController, pageViewComponent).createFreeTextAnnotation(x, y);
        }
    }

    public void paintTool(Graphics g) {
        // nothing to paint
    }

    public void mouseDragged(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {

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

    @Override
    protected void checkAndApplyPreferences() {

    }


    public void installTool() {

    }

    public void uninstallTool() {

    }
}
