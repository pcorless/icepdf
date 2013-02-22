/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.views.common;

import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

/**
 * Handles mouse click zoom in and scroll box zoom in.   The zoom is handled at the
 * AbstractDocumentView level as we accept mouse clicks from anywhere in the
 * view.
 *
 * @since 4.0
 */
public class ZoomInPageHandler extends SelectionBoxHandler implements ToolHandler {

    private static final Logger logger =
            Logger.getLogger(ZoomInPageHandler.class.toString());

    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;

    public ZoomInPageHandler(DocumentViewController documentViewController,
                             AbstractPageViewComponent pageViewComponent,
                             DocumentViewModel documentViewModel) {
        this.documentViewController = documentViewController;
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    public void mouseDragged(MouseEvent e) {
        // handle text selection drags.
        if (documentViewController != null) {
            // update the currently selected box
            updateSelectionSize(e, pageViewComponent);
        }
    }

    public void mouseClicked(MouseEvent e) {
        if ((e.getModifiers() & MouseEvent.MOUSE_PRESSED) != 0) {
            if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
                // zoom in
                documentViewController.setZoomIn(e.getPoint());
            }
        }
    }

    public void mousePressed(MouseEvent e) {
        // start selection box.
        if (documentViewController != null) {
            resetRectangle(e.getX(), e.getY());
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (documentViewController != null) {
            // update selection rectangle
            updateSelectionSize(e, pageViewComponent);

            // check if we are over a page
            AbstractPageViewComponent pageComponent =
                    isOverPageComponent(pageViewComponent, e);

            // zoom in on rectangle bounds.
            double zoom = ZoomInPageHandler.calculateZoom(
                    documentViewModel.getViewZoom(),
                    rectToDraw,
                    pageViewComponent.getBounds());
            documentViewController.setZoom((float) zoom);

            // clear the rectangle
            clearRectangle(pageViewComponent);
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {
        // rectangle select tool
        setSelectionSize(selection, pageViewComponent);
    }

    public void paintTool(Graphics g) {
        paintSelectionBox(g);
    }

    public static double calculateZoom(float currentZoom,
                                       Rectangle rectangle, Rectangle parentBounds) {
        // assume rectangle is always smaller then parent bounds.
//        double widthRatio = rectangle.getWidth() / parentBounds.getWidth();
//        double heightRatio = rectangle.getHeight() / parentBounds.getHeight();
//        double zoom;
//        if (widthRatio > heightRatio){
//            zoom = currentZoom / (rectangle.getWidth() * parentBounds.getWidth());
//        } else{
//            zoom = currentZoom / (rectangle.getHeight() * parentBounds.getHeight());
//        }
        return currentZoom;
    }
}
