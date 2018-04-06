/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views;

import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.tools.*;
import org.icepdf.ri.common.views.destinations.DestinationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        implements DocumentView, PropertyChangeListener, MouseListener, MouseMotionListener, ActionListener {

    private static final Logger logger =
            Logger.getLogger(AbstractDocumentView.class.toString());

    // background colour
    public static Color backgroundColour;

    // auto scroll refresh interval
    private static int SCROLL_REFRESH_DELAY;

    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.background.color", "#808080");
            int colorValue = ColorUtil.convertColor(color);
            backgroundColour =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("808080", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page shadow colour");
            }
        }
        try {
            SCROLL_REFRESH_DELAY = Defs.sysPropertyInt("org.icepdf.core.views.autoScroll.interval", 10);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error auto scroll speed value");
            }
        }
    }

    private Timer autoScrollTimer;
    private Point lastMouseLocation;

    // general layout of page component spacing.
    public static int verticalSpace = 2;
    public static int horizontalSpace = 1;
    public static int layoutInserts = 0;

    protected DocumentViewController documentViewController;
    protected DocumentViewModel documentViewModel;
    protected JPanel pagesPanel;
    protected boolean disposing;

    // current page view tool.
    protected ToolHandler currentTool;

    // mouse wheel zoom, always on regardless of tool ctr-wheel mouse rotation
    // for zoom in and out.
    protected MouseWheelZoom mouseWheelZoom;

    /**
     * Creates a new instance of AbstractDocumentView.
     *
     * @param documentViewController controller for MVC
     * @param documentScrollpane     scrollpane used to view pages
     * @param documentViewModel      model to represent view
     */
    public AbstractDocumentView(DocumentViewController documentViewController,
                                JScrollPane documentScrollpane,
                                DocumentViewModel documentViewModel) {
        this.documentViewController = documentViewController;
        this.documentViewModel = documentViewModel;
        // update the scroll pane reference,  this is mainly just for the full screen mode so the tools work as expected
        documentViewModel.setDocumentViewScrollPane(documentScrollpane);

        setFocusable(true);
        // add focus listener
        addFocusListener(this);

        // add mouse listener
        addMouseListener(this);
        addMouseMotionListener(this);

        // wheel listener
        mouseWheelZoom = new MouseWheelZoom(documentViewController, documentScrollpane);
        documentScrollpane.addMouseWheelListener(mouseWheelZoom);

        // timer for auto scroll
        autoScrollTimer = new Timer(SCROLL_REFRESH_DELAY, this);
        autoScrollTimer.setInitialDelay(50);

        // listen for scroll bar manipulators
        documentViewController.getHorizontalScrollBar().addAdjustmentListener(this);
        documentViewController.getVerticalScrollBar().addAdjustmentListener(this);

        // add a focus management listener.
        KeyboardFocusManager focusManager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener(this);

    }

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        if ("focusOwner".equals(prop)) {
            DocumentViewController documentViewController = getParentViewController();
            if (newValue instanceof AnnotationComponent) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Selected Annotation " + newValue);
                }
                documentViewController.firePropertyChange(
                        PropertyConstants.ANNOTATION_FOCUS_GAINED, evt.getOldValue(), evt.getNewValue());
            } else if (newValue instanceof DestinationComponent) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Selected destination " + newValue);
                }
                documentViewController.firePropertyChange(
                        PropertyConstants.DESTINATION_FOCUS_GAINED,
                        evt.getOldValue(),
                        evt.getNewValue());
            }

        } else if ("focusOwner".equals(prop)) {
            DocumentViewController documentViewController = getParentViewController();
            if (oldValue instanceof AnnotationComponent) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Deselected Annotation " + oldValue);
                }
                documentViewController.firePropertyChange(
                        PropertyConstants.ANNOTATION_FOCUS_LOST, evt.getOldValue(), evt.getNewValue());
            } else if (oldValue instanceof AnnotationComponent) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Deselected destination " + oldValue);
                }
                documentViewController.firePropertyChange(
                        PropertyConstants.DESTINATION_FOCUS_LOST, evt.getOldValue(), evt.getNewValue());
            }
        }
    }

    public DocumentViewController getParentViewController() {
        return documentViewController;
    }

    public DocumentViewModel getViewModel() {
        return documentViewController.getDocumentViewModel();
    }

    public void invalidate() {
        super.invalidate();
        pagesPanel.invalidate();
    }

    public void dispose() {
        // clean up scroll listeners
        documentViewController.getHorizontalScrollBar().removeAdjustmentListener(this);
        documentViewController.getVerticalScrollBar().removeAdjustmentListener(this);

        // remove custom handlers
        if (currentTool != null) {
            removeMouseListener(currentTool);
            removeMouseMotionListener(currentTool);
        }

        // mouse/wheel listener
        documentViewModel.getDocumentViewScrollPane().removeMouseWheelListener(mouseWheelZoom);
        removeMouseListener(this);
        removeMouseMotionListener(this);
        // stop the auto scroll timer
        autoScrollTimer.stop();

        // focus management
        removeFocusListener(this);
        // add a focus management listener.
        KeyboardFocusManager focusManager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.removePropertyChangeListener(this);
    }

    /**
     * invalidates page components
     */
    public abstract void updateDocumentView();

    public ToolHandler uninstallCurrentTool() {
        if (currentTool != null) {
            currentTool.uninstallTool();
            removeMouseListener(currentTool);
            removeMouseMotionListener(currentTool);
            if (currentTool instanceof TextSelectionViewHandler) {
                documentViewModel.getDocumentViewScrollPane().removeMouseWheelListener((TextSelectionViewHandler) currentTool);
            }
        }
        return currentTool;
    }

    public void installCurrentTool(ToolHandler currentTool) {
        if (currentTool != null) {
            currentTool.installTool();
            addMouseListener(currentTool);
            addMouseMotionListener(currentTool);
            this.currentTool = currentTool;
        }
    }

    public ToolHandler getCurrentToolHandler() {
        return currentTool;
    }

    public void setToolMode(final int viewToolMode) {
        uninstallCurrentTool();
        // assign the correct tool handler
        JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
        switch (viewToolMode) {
            case DocumentViewModel.DISPLAY_TOOL_PAN:
                currentTool = new PanningHandler(documentViewController,
                        documentViewModel, this);
                break;
            case DocumentViewModel.DISPLAY_TOOL_ZOOM_IN:
                currentTool = new ZoomInViewHandler(documentViewController, this);
                break;
            case DocumentViewModel.DISPLAY_TOOL_ZOOM_DYNAMIC:
                currentTool = new DynamicZoomHandler(documentViewController,
                        documentScrollpane);
                break;
            case DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION:
                currentTool = new TextSelectionViewHandler(documentViewController, this);
                documentScrollpane.addMouseWheelListener((TextSelectionViewHandler) currentTool);
                break;
            case DocumentViewModel.DISPLAY_TOOL_SELECTION:
                currentTool = new AnnotationSelectionHandler(
                        documentViewController,
                        null);
                break;
            default:
                currentTool = null;
                break;
        }
        if (currentTool != null) {
            currentTool.installTool();
            addMouseListener(currentTool);
            addMouseMotionListener(currentTool);
        }
    }

    /**
     * Paints the selection box for this page view.
     *
     * @param g Java graphics context to paint to.
     */
    public void paintComponent(Graphics g) {
        if (currentTool != null) {
            currentTool.paintTool(g);
        }
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {

    }

    public void focusGained(FocusEvent e) {

    }

    public void focusLost(FocusEvent e) {

    }

    public void mouseClicked(MouseEvent e) {
        requestFocus();
    }

    public void mousePressed(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {
        // make sure we stop the scroll timer once the mouse is released.
        if (autoScrollTimer != null && autoScrollTimer.isRunning()) {
            autoScrollTimer.stop();
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseMoved(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {
        // as soon as we have drag event we can start the auto scroll timer.
        lastMouseLocation = e.getPoint();
        if (!autoScrollTimer.isRunning()) {
            autoScrollTimer.start();
        }
    }

    protected boolean isTextSelectionTool() {
        return getCurrentToolHandler() != null &&
                (getCurrentToolHandler() instanceof TextSelectionViewHandler ||
                        getCurrentToolHandler() instanceof TextSelectionPageHandler ||
                        getCurrentToolHandler() instanceof HighLightAnnotationHandler);
    }

    /**
     * Checks to see if the mouse has exited the scroll pane viewport on the vertical plane.
     *
     * @return true if the mouse is north or south of the view port, false otherwise.
     */
    private boolean autoScrollViewVertical() {
        JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
        if (documentScrollpane != null && isTextSelectionTool()) {
            Rectangle viewportBounds = documentScrollpane.getViewport().getViewRect();
            Rectangle viewBounds = getBounds();
            // check for northern edge
            if (viewportBounds.getY() > 0 && lastMouseLocation.y < viewportBounds.getY()) {
                JScrollBar verticalScrollBar = documentScrollpane.getVerticalScrollBar();
                if (verticalScrollBar != null) {
                    verticalScrollBar.setValue(verticalScrollBar.getValue() - verticalScrollBar.getBlockIncrement());
                    lastMouseLocation.y -= verticalScrollBar.getBlockIncrement();
                    return true;
                }
            }
            // check for southern edge.
            double bottom = viewportBounds.getY() + viewportBounds.getHeight();
            if (bottom < viewBounds.getHeight() && lastMouseLocation.y > bottom) {
                JScrollBar verticalScrollBar = documentScrollpane.getVerticalScrollBar();
                if (verticalScrollBar != null) {
                    verticalScrollBar.setValue(verticalScrollBar.getValue() + verticalScrollBar.getBlockIncrement());
                    lastMouseLocation.y += verticalScrollBar.getBlockIncrement();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks to see if the mouse has exited the scroll pane viewport on the horizontal plane.
     *
     * @return true if the mouse is east or west of the view port, false otherwise.
     */
    private boolean autoScrollViewHorizontal() {
        JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
        if (documentScrollpane != null && isTextSelectionTool()) {
            Rectangle viewportBounds = documentScrollpane.getViewport().getViewRect();
            Rectangle viewBounds = getBounds();
            // check for eastern edge
            if (viewportBounds.getX() > 0 && lastMouseLocation.x < viewportBounds.getX()) {
                JScrollBar horizontalScrollBar = documentScrollpane.getHorizontalScrollBar();
                if (horizontalScrollBar != null) {
                    horizontalScrollBar.setValue(horizontalScrollBar.getValue() - horizontalScrollBar.getBlockIncrement());
                    lastMouseLocation.x -= horizontalScrollBar.getBlockIncrement();
                    return true;
                }
            }
            // check for western edge.
            double right = viewportBounds.getX() + viewportBounds.getWidth();
            if (right < viewBounds.getWidth() && lastMouseLocation.x > right) {
                JScrollBar horizontalScrollBar = documentScrollpane.getHorizontalScrollBar();
                if (horizontalScrollBar != null) {
                    horizontalScrollBar.setValue(horizontalScrollBar.getValue() + horizontalScrollBar.getBlockIncrement());
                    lastMouseLocation.x += horizontalScrollBar.getBlockIncrement();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Auto scroll timer action event, calls method that determine if mouse has left the main viewport on a mouse
     * drag event.
     *
     * @param e mouse event
     */
    public void actionPerformed(ActionEvent e) {
        if (!(autoScrollViewVertical() || autoScrollViewHorizontal())) {
            autoScrollTimer.stop();
        }
    }

    /**
     * Utility method for determining if the mouse event occurred over a
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
