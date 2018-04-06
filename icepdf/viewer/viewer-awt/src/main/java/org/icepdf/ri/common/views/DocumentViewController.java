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

import org.icepdf.core.SecurityCallback;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;


/**
 * <p>The DocumentViewControllerImpl is the controller in the MVC for multipage view
 * management.  This controller is used to manipulate the one column, one page,
 * two column and two page views.</p>
 * <br>
 * <p>The Swing implementation of multiple view usesa the folowing MVC base
 * classes:
 * </P>
 *
 * @see org.icepdf.ri.common.views.AbstractDocumentView
 * @see org.icepdf.ri.common.views.AbstractDocumentViewModel
 * @see org.icepdf.ri.common.views.DocumentViewControllerImpl
 * @since 2.5
 */
public interface DocumentViewController {

    /**
     * Set the view to show the page at the specified zoom level.
     */
    int PAGE_FIT_NONE = 1;

    /**
     * Set the view to show the page at actual size
     */
    int PAGE_FIT_ACTUAL_SIZE = 2;

    /**
     * Set the view to show the page at actual size
     */
    int PAGE_FIT_WINDOW_HEIGHT = 3;

    /**
     * Set the view to show the page at actual size
     */
    int PAGE_FIT_WINDOW_WIDTH = 4;


    int CURSOR_HAND_OPEN = 1;

    int CURSOR_HAND_CLOSE = 2;

    int CURSOR_ZOOM_IN = 3;

    int CURSOR_ZOOM_OUT = 4;

    int CURSOR_WAIT = 6;

    int CURSOR_SELECT = 7;

    int CURSOR_DEFAULT = 8;

    int CURSOR_HAND_ANNOTATION = 9;

    int CURSOR_TEXT_SELECTION = 10;

    int CURSOR_CROSSHAIR = 11;

    int CURSOR_MAGNIFY = 12;

    void setDocument(Document document);

    Document getDocument();

    void closeDocument();

    void dispose();

    Container getViewContainer();

    Controller getParentController();

    void setViewType(final int documentView);

    int getViewMode();

    boolean setFitMode(final int fitMode);

    int getFitMode();

    void setDocumentViewType(final int documentView, final int fitMode);

    boolean setCurrentPageIndex(int pageNumber);

    int setCurrentPageNext();

    int setCurrentPagePrevious();

    void setComponentTarget(PageViewComponent pageComponent, Component component);

    void setDestinationTarget(Destination destination);

    int getCurrentPageIndex();

    int getCurrentPageDisplayValue();

    void setZoomLevels(float[] zoomLevels);

    float[] getZoomLevels();

    boolean setZoom(float userZoom);

    boolean setZoomIn();

    boolean setZoomIn(Point point);

    boolean setZoomCentered(float zoom, Point centeringPoint, boolean becauseOfValidFitMode);

    boolean setZoomToViewPort(float zoom, Point viewPortPosition, int pageIndex, boolean becauseOfValidFitMode);

    boolean setZoomOut();

    boolean setZoomOut(Point point);

    /**
     * The Page being shown may be zoomed in or out, to show more detail,
     * or provide an overview.
     *
     * @return The user's requested zoom
     */
    float getZoom();

    boolean setRotation(float userRotation);

    /**
     * Each Page may have its own rotation, but on top of that, the user
     * may select to have the Page further rotated by 90, 180 or 270 degrees.
     *
     * @return The user's requested rotation
     */
    float getRotation();

    float setRotateRight();

    float setRotateLeft();

    boolean setToolMode(final int viewToolMode);

    int getToolMode();

    boolean isToolModeSelected(final int viewToolMode);

    void requestViewFocusInWindow();

    void setViewCursor(final int cursorType);

    Cursor getViewCursor(final int cursorType);

    int getViewCursor();

    void setViewKeyListener(KeyListener l);

    Adjustable getHorizontalScrollBar();

    Adjustable getVerticalScrollBar();

    JViewport getViewPort();

    void setAnnotationCallback(AnnotationCallback annotationCallback);

    void setSecurityCallback(SecurityCallback securityCallback);

    void addNewAnnotation(AnnotationComponent annotationComponent);

    void updateAnnotation(AnnotationComponent annotationComponent);

    void updatedSummaryAnnotation(AnnotationComponent annotationComponent);

    void deleteCurrentAnnotation();

    void deleteAnnotation(AnnotationComponent annotationComponent);

    void addNewDestination(Destination destination);

    void updateDestination(Destination oldDestination, Destination destination);

    void deleteDestination(Destination destination);

    void undo();

    void redo();

    AnnotationCallback getAnnotationCallback();

    SecurityCallback getSecurityCallback();

    DocumentViewModel getDocumentViewModel();

    DocumentView getDocumentView();

    void clearSelectedText();

    void clearHighlightedText();

    void clearSelectedAnnotations();

    void assignSelectedAnnotation(AnnotationComponent annotationComponent);

    void selectAllText();

    String getSelectedText();

    void revertViewType();

    void firePropertyChange(String event, int oldValue, int newValue);

    void firePropertyChange(String event, Object oldValue, Object newValue);
}
