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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.InputEvent;

/**
 * Handles mouse click zoom in and zoom out.
 *
 * todo add zoom selection box feature. 
 *
 * @since 4.0
 */
public class ZoomHandler implements MouseMotionListener, MouseListener {


    private DocumentViewController documentViewController;

    private boolean isDragZoom;


    public ZoomHandler(DocumentViewController documentViewController) {

        this.documentViewController = documentViewController;
    }

    public void mouseDragged(MouseEvent e) {

        if (documentViewController != null ){
            int userToolModeFlag = documentViewController
                            .getDocumentViewModel().getViewToolMode();
            if (userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN ||
                    userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
                // record that we are doing a drag box zoom.
                isDragZoom = true;
            }
        }
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void mouseClicked(MouseEvent e) {
         if ((e.getModifiers() & MouseEvent.MOUSE_PRESSED) != 0) {
            if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
                int userToolModeFlag = documentViewController
                        .getDocumentViewModel().getViewToolMode();
                // zoom in
                if (userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
                    documentViewController.setZoomIn(e.getPoint());
                }
                // zoom out.
                else if (userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
                    documentViewController.setZoomOut(e.getPoint());
                }
            }
        }
    }

    public void mousePressed(MouseEvent e) {
         // assigning correct mouse state.
        if (documentViewController != null){
            DocumentViewModel documentViewModel =
                    documentViewController.getDocumentViewModel();
            if (documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_ZOOM_IN)) {
                documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_IN);
            } else if (documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT)) {
                documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_OUT);
            }
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (isDragZoom){
            // get selection box from parent

            // calculate zoom factor  based on ration of select box to current
            // view size.

            // apply the zoom factor.

            isDragZoom = false;
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }
}
