/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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

import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * TextSelectionViewHandler propagates text selection events into the the
 * views child page components.  This handle is required for multi-page text
 * selection.  On mouse click all text selection states are removed.
 *
 * @since 5.0
 */
public class TextSelectionViewHandler extends SelectionBoxHandler
        implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(TextSelectionViewHandler.class.toString());


    protected JComponent parentComponent;

    public TextSelectionViewHandler(DocumentViewController documentViewController,
                                    DocumentViewModel documentViewModel,
                                    JComponent parentComponent) {
        super(documentViewController, null, documentViewModel);
        this.parentComponent = parentComponent;
    }

    public void mouseClicked(MouseEvent e) {
        if (parentComponent != null) {
            parentComponent.requestFocus();
        }
    }

    public void mousePressed(MouseEvent e) {
        // clear all selected text.
        documentViewController.clearSelectedText();

        // start selection box.
        resetRectangle(e.getX(), e.getY());
    }

    public void mouseReleased(MouseEvent e) {
        // update selection rectangle
        updateSelectionSize(e, parentComponent);

        // deselect rectangles on other selected pages.
        ArrayList<WeakReference<AbstractPageViewComponent>> selectedPages =
                documentViewModel.getSelectedPageText();

        // check if we are over a page
        AbstractPageViewComponent pageComponent =
                isOverPageComponent(parentComponent, e);

        if (pageComponent != null) {
            MouseEvent modeEvent = SwingUtilities.convertMouseEvent(
                    parentComponent, e, pageComponent);

            if (selectedPages != null &&
                    selectedPages.size() > 0) {
                for (WeakReference<AbstractPageViewComponent> page : selectedPages) {
                    AbstractPageViewComponent pageComp = page.get();
                    if (pageComp != null) {
                        pageComp.dispatchEvent(modeEvent);
                    }
                }
            }
        }
        // finally if we have selected any text then fire a property change event
        if (selectedPages != null && selectedPages.size() > 0) {
            documentViewController.firePropertyChange(
                    PropertyConstants.TEXT_SELECTED,
                    null, null);
        }
        // clear the child rectangle
        if (selectedPages != null &&
                selectedPages.size() > 0) {
            for (WeakReference<AbstractPageViewComponent> page : selectedPages) {
                AbstractPageViewComponent pageComp = page.get();
                if (pageComp != null) {
                    pageComp.clearSelectionRectangle();
                }
            }
        }

        // clear the rectangle
        clearRectangle(parentComponent);

    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {

        // handle text selection drags.
        if (documentViewController != null) {
            // update the currently parentComponent box
            updateSelectionSize(e, parentComponent);
            // clear previously selected pages
            documentViewModel.clearSelectedPageText();
            // add selection box to child pages
            if (documentViewModel != null) {
                java.util.List<AbstractPageViewComponent> pages =
                        documentViewModel.getPageComponents();
                for (AbstractPageViewComponent page : pages) {
                    Rectangle tmp = SwingUtilities.convertRectangle(
                            parentComponent, getRectToDraw(), page);
                    if (page.getBounds().intersects(tmp)) {

                        // add the page to the page as it is marked for selection
                        documentViewModel.addSelectedPageText(page);

                        // convert the rectangle to the correct space
                        Rectangle selectRec =
                                SwingUtilities.convertRectangle(parentComponent,
                                        rectToDraw,
                                        page);
                        // set the selected region.
                        page.setSelectionRectangle(
                                SwingUtilities.convertPoint(parentComponent,
                                        e.getPoint(), page),
                                selectRec);
                    }
                }
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
        // check if we are over a page
        AbstractPageViewComponent pageComponent =
                isOverPageComponent(parentComponent, e);
        if (pageComponent != null) {
            pageComponent.dispatchEvent(SwingUtilities.convertMouseEvent(
                    parentComponent, e, pageComponent));
        }
    }

    public void paintTool(Graphics g) {

    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }
}
