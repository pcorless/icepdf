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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseListener;

/**
 * Container logic used for view panning via mouse dragging for page views.
 * Panning can be handle in the view and donen't need to be handleed by the
 * page components.
 *
 * @since 4.0
 */
public class PanningHandler implements MouseMotionListener, MouseListener {

    private DocumentViewController documentViewController;

    // page mouse event manipulation
    private Point lastMousePosition = new Point();

    public PanningHandler(DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    /**
     * Mouse dragged, initiates page panning if the tool is selected.
     *
     * @param e awt mouse event
     */
    public void mouseDragged(MouseEvent e) {
        if (documentViewController != null &&
                documentViewController.getDocumentViewModel().getViewToolMode()
                        == DocumentViewModel.DISPLAY_TOOL_PAN) {

            // Get data about the current view port position
            Adjustable verticalScrollbar =
                    documentViewController.getVerticalScrollBar();
            Adjustable horizontalScrollbar =
                    documentViewController.getHorizontalScrollBar();

            if (verticalScrollbar != null && horizontalScrollbar != null) {
                // calculate how much the view port should be moved
                Point p = new Point(
                        (int) e.getPoint().getX() - horizontalScrollbar.getValue(),
                        (int) e.getPoint().getY() - verticalScrollbar.getValue());
                int x = (int) (horizontalScrollbar.getValue() - (p.getX() - lastMousePosition.getX()));
                int y = (int) (verticalScrollbar.getValue() - (p.getY() - lastMousePosition.getY()));

                // apply the pan
                horizontalScrollbar.setValue(x);
                verticalScrollbar.setValue(y);

                // update last position holder
                lastMousePosition.setLocation(p);
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
        if (documentViewController != null &&
                documentViewController.getDocumentViewModel().getViewToolMode()
                        == DocumentViewModel.DISPLAY_TOOL_PAN) {

            Adjustable verticalScrollbar =
                    documentViewController.getVerticalScrollBar();
            Adjustable horizontalScrollbar =
                    documentViewController.getHorizontalScrollBar();

            lastMousePosition.setLocation(
                    e.getPoint().getX() - horizontalScrollbar.getValue(),
                    e.getPoint().getY() - verticalScrollbar.getValue());
        }
    }

    /**
     * Invoked when the mouse button has been clicked (pressed
     * and released) on a component.
     */
    public void mouseClicked(MouseEvent e) {

    }

    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent e) {

        if (documentViewController != null &&
                documentViewController.getDocumentViewModel()
                        .isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_CLOSE);
        }
    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) {
        if (documentViewController != null &&
                documentViewController.getDocumentViewModel().getViewToolMode()
                        == DocumentViewModel.DISPLAY_TOOL_PAN) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_OPEN);
        }
    }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent e) {

    }
}
