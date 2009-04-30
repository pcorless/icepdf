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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.views.DocumentView;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.ColorUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * <p>The AbstractDocumentView class is implemented by the four predefined page views;
 * OneColumnPageView, OnePageView, TwoColumnPageView and TwoPageView. Most of
 * common work is implemented in this class which aid developers in defining their
 * own custom page views.<p>
 *
 * @since 2.5
 */
public abstract class AbstractDocumentView
        extends JComponent
        implements DocumentView {

    private static final Logger logger =
            Logger.getLogger(AbstractDocumentView.class.toString());

    // background colour
    public static Color backgroundColor;

    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.background.color", "#808080");
            int colorValue = ColorUtil.convertColor(color);
            backgroundColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("808080", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page shadow colour");
            }
        }
    }

    // general layout of page component spacing.
    protected static int verticalSpace = 6;
    protected static int horizontalSpace = 6;
    protected static int layoutInserts = 6;

    // page mouse evnet manipulation
    protected Point lastMousePosition = new Point();

    protected DocumentViewController documentViewController;

    protected JScrollPane documentScrollpane;

    protected Document currentDocument;

    protected DocumentViewModelImpl documentViewModel;


    /**
     * Creates a new instance of AbstractDocumentView.
     *
     * @param documentViewController controller for MVC
     * @param documentScrollpane     scrollpane used to view pages
     * @param documentViewModel      model to represent view
     */
    public AbstractDocumentView(DocumentViewController documentViewController,
                                JScrollPane documentScrollpane,
                                DocumentViewModelImpl documentViewModel) {
        this.documentViewController = documentViewController;
        this.documentScrollpane = documentScrollpane;
        this.documentViewModel = documentViewModel;

        currentDocument = this.documentViewModel.getDocument();

        // add mouse manipulator listeners.
        addMouseListener(this);
        addMouseMotionListener(this);

        // listen for scroll bar manaipulators
        documentViewController.getHorizontalScrollBar().addAdjustmentListener(this);
        documentViewController.getVerticalScrollBar().addAdjustmentListener(this);
    }

    public DocumentViewController getParentViewController() {
        return documentViewController;
    }

    public DocumentViewModel getViewModel() {
        return documentViewModel;
    }

    public void dispose() {

        currentDocument = null;

        // clean up mouse listeners.
        removeMouseListener(this);
        removeMouseMotionListener(this);

        // clean up scroll listeners
        documentViewController.getHorizontalScrollBar().removeAdjustmentListener(this);
        documentViewController.getVerticalScrollBar().removeAdjustmentListener(this);

    }

    /**
     * invalidates page components
     */
    public abstract void updateDocumentView();

    /**
     * Handler for mouse clicks.  Zoom-in and Zoom-out tools are handled here.
     *
     * @param e  awt mouse event
     */
    public void mouseClicked(MouseEvent e) {
        if ((e.getModifiers() & MouseEvent.MOUSE_PRESSED) != 0) {
            if ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
                int userToolModeFlag = documentViewModel.getViewToolMode();
                if (userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
                    documentViewController.setZoomIn(e.getPoint());
                } else if (userToolModeFlag == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
                    documentViewController.setZoomOut(e.getPoint());
                }
            }
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    /**
     * Mouse pressed, changes the mouse cursor depending on the tool selected.
     *
     * @param e awt mouse event
     */
    public void mousePressed(MouseEvent e) {
        if (currentDocument == null) {
            return;
        }
        // assing correct mouse state.
        if (documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_CLOSE);
        } else if (documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_ZOOM_IN)) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_IN);
//            viewerModel.isZoomBoxDrawingEnabled = true;
//            viewerModel.zoomBoxStart.setLocation(e.getPoint());
        } else if (documentViewModel.isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT)) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_OUT);
//            viewerModel.isZoomBoxDrawingEnabled = true;
//            viewerModel.zoomBoxStart.setLocation(e.getPoint());
        }
    }

    /**
     * Mouse released, changes the mouse cursor depending on the tool selected.
     *
     * @param e  awt mouse event
     */
    public void mouseReleased(MouseEvent e) {
        if (currentDocument == null) {
            return;
        }
        if (documentViewController.isToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_OPEN);
        }
        /*
         * Zoom box related
        else{
            // release box draw flag
            isZoomBoxDrawingEnabled = false;
            pageComponent.repaint();
        }
        */
    }

    /**
     * Mouse dragged, initiates page panning if the tool is selected.
     *
     * @param e awt mouse event
     */
    public void mouseDragged(MouseEvent e) {
        if (documentViewController != null) {

            // Get data about the current view port position
            Adjustable verticalScrollbar = documentViewController.getVerticalScrollBar();
            Adjustable horizontalScrollbar = documentViewController.getHorizontalScrollBar();

            if (verticalScrollbar != null && horizontalScrollbar != null) {
                // calculate how much the view port should be moved
                Point p = new Point(
                        (int) e.getPoint().getX() - horizontalScrollbar.getValue(),
                        (int) e.getPoint().getY() - verticalScrollbar.getValue());
                int x = (int) (horizontalScrollbar.getValue() - (p.getX() - lastMousePosition.getX()));
                int y = (int) (verticalScrollbar.getValue() - (p.getY() - lastMousePosition.getY()));

                // if mouse is selected we want to move the view port
                if (documentViewController.isToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
                    horizontalScrollbar.setValue(x);
                    verticalScrollbar.setValue(y);
                }

                // update last position holder
                lastMousePosition.setLocation(p);

                // grab focus for keyboard events
                documentViewController.requestViewFocusInWindow();
            }
        }
    }

    public void mouseMoved(MouseEvent e) {
        if (documentViewController != null) {

            Adjustable verticalScrollbar = documentViewController.getVerticalScrollBar();
            Adjustable horizontalScrollbar = documentViewController.getHorizontalScrollBar();

            lastMousePosition.setLocation(
                    e.getPoint().getX() - horizontalScrollbar.getValue(),
                    e.getPoint().getY() - verticalScrollbar.getValue());

        }
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
        if (e.getAdjustable().getOrientation() == Adjustable.HORIZONTAL) {
//              System.out.println("horizontal");
        } else if (e.getAdjustable().getOrientation() == Adjustable.VERTICAL) {
//              System.out.println("vertical");
        }
    }

    public void focusGained(FocusEvent e) {

    }

    public void focusLost(FocusEvent e) {

    }

}
