/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
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
