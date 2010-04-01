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
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.core.views.DocumentView;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.views.common.PanningHandler;
import org.icepdf.core.views.common.SelectionBoxHandler;
import org.icepdf.core.views.common.ZoomHandler;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;


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
        implements DocumentView, PropertyChangeListener, MouseWheelListener {

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
    public static int verticalSpace = 2;
    public static int horizontalSpace = 1;
    public static int layoutInserts = 0;

    protected DocumentViewController documentViewController;

    protected JScrollPane documentScrollpane;

    protected Document currentDocument;

    protected DocumentViewModelImpl documentViewModel;

    // panning handler
    protected PanningHandler panningHandler;
    protected ZoomHandler zoomHandler;

    // aid for drawing selection box
    public SelectionBoxHandler selectionBox;

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

        // selectionBox paint
        selectionBox = new SelectionBoxHandler();

        setFocusable(true);
        // add focus listener
        addFocusListener(this);

        // add mouse manipulator listeners.
        addMouseListener(this);
        addMouseMotionListener(this);

        // wheel listener
        documentScrollpane.addMouseWheelListener(this);

        // add custom tools
        // panning
        panningHandler = new PanningHandler(documentViewController);
//        addMouseMotionListener(panningHandler);
//        addMouseListener(panningHandler);
        // zoom click and select zoom box.
        zoomHandler = new ZoomHandler(documentViewController);
        addMouseListener(zoomHandler);
        addMouseMotionListener(zoomHandler);

        // listen for scroll bar manipulators
        documentViewController.getHorizontalScrollBar().addAdjustmentListener(this);
        documentViewController.getVerticalScrollBar().addAdjustmentListener(this);

        // add a focus management listener.
//        KeyboardFocusManager focusManager =
//           KeyboardFocusManager.getCurrentKeyboardFocusManager();
//        focusManager.addPropertyChangeListener(this);

    }

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        if ("focusOwner".equals(prop) &&
                newValue instanceof AnnotationComponent){
            // the correct annotations for the properties pane
            if (logger.isLoggable(Level.INFO)){
                logger.info("Selected Annotation " + newValue);
            }
            DocumentViewController documentViewController =
                    getParentViewController();
                documentViewController.firePropertyChange(
                        PropertyConstants.ANNOTATION_FOCUS_GAINED,
                        evt.getOldValue(),
                        evt.getNewValue());

        }
        else if ("focusOwner".equals(prop) &&
                oldValue instanceof AnnotationComponent){
            // the correct annotations for the properties pane
            if (logger.isLoggable(Level.INFO)){
                logger.info("Deselected Annotation " + oldValue);
            }
            DocumentViewController documentViewController =
                    getParentViewController();
            documentViewController.firePropertyChange(
                    PropertyConstants.ANNOTATION_FOCUS_LOST,
                    evt.getOldValue(),
                    evt.getNewValue());
        }
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

        // remove custom handlers
//        removeMouseMotionListener(panningHandler);
//        removeMouseListener(panningHandler);
        removeMouseMotionListener(zoomHandler);
        removeMouseListener(zoomHandler);

        // wheel listener
        documentScrollpane.removeMouseWheelListener(this);
    }

    /**
     * invalidates page components
     */
    public abstract void updateDocumentView();

    /**
     * Handles mouse click events.  First any selected text is cleared and
     * then if the mouse event occured over a page component the mouse
     * coordinates are converted to page space rebroadcast.
     *
     * @param e awt mouse event
     */
    public void mouseClicked(MouseEvent e) {
        if (documentViewController != null) {
            // clear all selected text.
            documentViewController.clearSelectedText();
            // find a a page component
            AbstractPageViewComponent pageViewComponent =
                    isOverPageComponent(e);
            // found a page
            if (pageViewComponent != null) {
                // broadcast the event.
                pageViewComponent.mouseClicked(
                        SwingUtilities.convertMouseEvent(this, e,
                                pageViewComponent));
            }
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    /**
     * Mouse is press  the page selection box is removed and if the mouse is
     * over a page component its focus is gained and it becomes the current page.
     * Each individual tool mode has a different behavior afterwards.
     * <p/>
     * Annotations have first priority and the mouse event is passed on to the
     * page component that the mouse is over for processing.
     * <p/>
     * If the text selection tool is selected then the event is broadcast to all
     * pages that have selected text.  This insures the selected text on other
     * pages matches the boounds of this selection box.
     * <p/>
     * If the panning tool is selected then we pass the event of the panning
     * handler.
     *
     * @param e awt mouse event
     */
    public void mousePressed(MouseEvent e) {
        // clear all selected text. 
        documentViewController.clearSelectedText();
        // deselect any selected annotations.
        documentViewController.clearSelectedAnnotations();

        // start selection box.
        selectionBox.resetRectangle(e.getX(), e.getY());

        // check if we are over a page
        AbstractPageViewComponent pageComponent = isOverPageComponent(e);
        MouseEvent modeEvent = SwingUtilities.convertMouseEvent(this, e, pageComponent);

        // assign focus to the clicked on page
        if (pageComponent != null) {
            pageComponent.requestFocus();
        }
        // annotations always win, we have to deal with them first.
        if (pageComponent != null &&
                (documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION
                        ||
                        documentViewModel.getViewToolMode() ==
                                DocumentViewModel.DISPLAY_TOOL_SELECTION
                        ||
                        documentViewModel.getViewToolMode() ==
                                DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION)) {
            // take care of annotations and the first click for selection
            pageComponent.mousePressed(modeEvent);
        } else {
            // panning icon state
            if (documentViewController.getDocumentViewModel()
                    .isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
                panningHandler.mousePressed(e);
            }
        }
    }

    /**
     * Mouse is release the page selection box is removed and each individual
     * tool mode has a different behavior.
     * <p/>
     * Annotations have first priority and the mouse event is passed on to the
     * page component that the mouse is over for processing.
     * <p/>
     * If the text selection tool is selected then the event is broadcast to all
     * pages that have selected text.  This insures the selected text on other
     * pages matches the boounds of this selection box.
     * <p/>
     * If the panning tool is selected then we pass the event of the panning
     * handler.
     *
     * @param e awt mouse event
     */
    public void mouseReleased(MouseEvent e) {

        // update selection rectangle
        selectionBox.updateSelectionSize(e, this);

        // check if we are over a page
        AbstractPageViewComponent pageComponent = isOverPageComponent(e);
        MouseEvent modeEvent = SwingUtilities.convertMouseEvent(this, e, pageComponent);
        if (pageComponent != null &&
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            pageComponent.mouseReleased(modeEvent);
            // deselect rectangles on other selected pages.
            ArrayList<WeakReference<AbstractPageViewComponent>> selectedPages =
                    documentViewModel.getSelectedPageText();
            if (selectedPages != null &&
                    selectedPages.size() > 0) {
                for (WeakReference<AbstractPageViewComponent> page : selectedPages) {
                    AbstractPageViewComponent pageComp = page.get();
                    if (pageComp != null) {
                        pageComp.mouseReleased(modeEvent);
                    }
                }
            }
            // finally if we have selected any text then fire a property change event
            if (selectedPages != null && selectedPages.size() > 0 ){
                documentViewController.firePropertyChange(
                        PropertyConstants.TEXT_SELECTED,
                        null,null);
            }
        }
        // annotation selection box drawing.
        else if (pageComponent != null &&
                (documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_SELECTION  ||
                 documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION)) {
            pageComponent.mouseReleased(modeEvent);
        } else {
            // panning icon state
            if (documentViewController.getDocumentViewModel()
                    .isViewToolModeSelected(DocumentViewModel.DISPLAY_TOOL_PAN)) {
                panningHandler.mouseReleased(e);
            }
        }

        // clear the rectangle
        selectionBox.clearRectangle(this);
    }

    /**
     * Mouse dragged events are broad casted to this child page components
     * depending on the tool that is selected.
     * <p/>
     * When the text selection tool is selected this views selected rectangle
     * is converted to page space and updates the selection box of the page
     * that the pages that intersect the selected rectangle. Each individual
     * page component is still responsible for how it handles text selection
     * <p/>
     * When the annotations tool is selected...
     *
     * @param e awt mouse event
     */
    public void mouseDragged(MouseEvent e) {
        // handle text selection drags.
        if (documentViewController != null &&
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            // update the currently selected box
            selectionBox.updateSelectionSize(e, this);
            // clear previously selected pages
            documentViewModel.clearSelectedPageText();
            // 
            if (documentViewModel != null) {
                java.util.List<AbstractPageViewComponent> pages =
                        documentViewModel.getPageComponents();
                for (AbstractPageViewComponent page : pages) {
                    Rectangle tmp = SwingUtilities.convertRectangle(
                            this, selectionBox.getRectToDraw(), page);
                    if (page.getBounds().intersects(tmp)) {

                        // add the page to the page as it is marked for selection
                        documentViewModel.addSelectedPageText(page);

                        Rectangle selectRec =
                                SwingUtilities.convertRectangle(this,
                                        selectionBox.getRectToDraw(),
                                        page);
                        // set the selected region. 
                        page.setTextSelectionRectangle(
                                SwingUtilities.convertPoint(this, e.getPoint(), page),
                                selectRec);
                    }
                }
            }
        }
        // handles multiple selection box drawing.
        else if (documentViewController != null &&
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_SELECTION ||
                 documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_LINK_ANNOTATION) {
            // mouse -> page  broadcast .
            AbstractPageViewComponent pageViewComponent =
                    isOverPageComponent(e);
            if (pageViewComponent != null) {
                pageViewComponent.mouseDragged(
                        SwingUtilities.convertMouseEvent(this, e,
                                pageViewComponent));
            }
        } else if (documentViewController != null &&
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_PAN) {
            panningHandler.mouseDragged(e);
        }
    }

    /**
     * Mouse moved event listener,  All mouse events that are generated via
     * a mouse moves are then passed on to the page component that the mouse
     * event occured over.  The coordinates system of the mouse event Point
     * is converted to the coordinates space of the found page.
     * <p/>
     * If there is no page then we don't do anything.
     *
     * @param e mouse move mouse event.
     */
    public void mouseMoved(MouseEvent e) {
        // push all mouse events into the page, as they are used for selection
        // and annotation processing.
        if (documentViewController != null) {
            // mouse -> page  broadcast .
            AbstractPageViewComponent pageViewComponent =
                    isOverPageComponent(e);
            if (pageViewComponent != null) {
                pageViewComponent.mouseMoved(
                        SwingUtilities.convertMouseEvent(this, e,
                                pageViewComponent));
            }
        }
        // let the pan handler know about it too. 
        if (documentViewController != null &&
                documentViewModel.getViewToolMode() ==
                        DocumentViewModel.DISPLAY_TOOL_PAN) {
            panningHandler.mouseMoved(e);
        }
    }

    /**
     * Handles ctl-wheelmouse for document zooming.
     *
     * @param e mouse wheel event. 
     */
    public void mouseWheelMoved(MouseWheelEvent e) {
        int rotation = e.getWheelRotation();
        // turn off scroll on zoom and then back on again next time
        // the wheel is used with out the ctrl mask.
        if ((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK ) {
            documentScrollpane.setWheelScrollingEnabled(false);
            if (rotation > 0){
                documentViewController.setZoomOut();
            }else{
                documentViewController.setZoomIn();
            }
        }else{
            documentScrollpane.setWheelScrollingEnabled(true);
        }
    }

    /**
     * Paints the selection box for this page view.
     *
     * @param g Java graphics context to paint to.
     */
    public void paintComponent(Graphics g) {
        selectionBox.paintSelectionBox(g);
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {

    }

    public void focusGained(FocusEvent e) {

    }

    public void focusLost(FocusEvent e) {

    }

    /**
     * Utility method for determininng if the mouse event occured over a
     * page in the page view.
     *
     * @param e mouse event in this coordinates space
     * @return component that mouse event is over or null if not over a page.
     */
    private AbstractPageViewComponent isOverPageComponent(MouseEvent e) {
        // mouse -> page  broadcast .
        Component comp = findComponentAt(e.getPoint());
        if (comp instanceof AbstractPageViewComponent) {
            return (AbstractPageViewComponent) comp;
        } else {
            return null;
        }
    }

}
