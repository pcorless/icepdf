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
package org.icepdf.core.views;

import org.icepdf.core.AnnotationCallback;
import org.icepdf.core.Controller;
import org.icepdf.core.SecurityCallback;
import org.icepdf.core.views.swing.AnnotationComponentImpl;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;

import java.awt.*;
import java.awt.event.KeyListener;


/**
 * <p>The DocumentViewControllerImpl is the controler in the MVC for multipage view
 * management.  This controller is used to manipulate the one column, one page,
 * two column and two page views.</p>
 * <p/>
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
    public static final int PAGE_FIT_NONE = 1;

    /**
     * Set the view to show the page at actual size
     */
    public static final int PAGE_FIT_ACTUAL_SIZE = 2;

    /**
     * Set the view to show the page at actual size
     */
    public static final int PAGE_FIT_WINDOW_HEIGHT = 3;

    /**
     * Set the view to show the page at actual size
     */
    public static final int PAGE_FIT_WINDOW_WIDTH = 4;


    public static final int CURSOR_HAND_OPEN = 1;

    public static final int CURSOR_HAND_CLOSE = 2;

    public static final int CURSOR_ZOOM_IN = 3;

    public static final int CURSOR_ZOOM_OUT = 4;

    public static final int CURSOR_WAIT = 6;

    public static final int CURSOR_SELECT = 7;

    public static final int CURSOR_DEFAULT = 8;

    public static final int CURSOR_HAND_ANNOTATION = 9;

    public static final int CURSOR_TEXT_SELECTION = 10;

    public void setDocument(Document document);

    public Document getDocument();

    public void closeDocument();

    public void dispose();

    public Container getViewContainer();

    public Controller getParentController();

    public void setViewType(final int documentView);

    public int getViewMode();

    public boolean setFitMode(final int fitMode);

    public int getFitMode();

    public void setDocumentViewType(final int documentView, final int fitMode);

    public boolean setCurrentPageIndex(int pageNumber);

    public int setCurrentPageNext();

    public int setCurrentPagePrevious();

    public void setDestinationTarget(Destination destination);

    public int getCurrentPageIndex();

    public int getCurrentPageDisplayValue();

    public void setZoomLevels(float[] zoomLevels);

    public float[] getZoomLevels();

    public boolean setZoom(float userZoom);

    public boolean setZoomIn();

    public boolean setZoomIn(Point point);

    public boolean setZoomOut();

    public boolean setZoomOut(Point point);

    public float getZoom();

    public boolean setRotation(float userRotation);

    public float getRotation();

    public float setRotateRight();

    public float setRotateLeft();

    public boolean setToolMode(final int viewToolMode);

    public int getToolMode();

    public boolean isToolModeSelected(final int viewToolMode);

    public void requestViewFocusInWindow();

    public void setViewCursor(final int cursorType);

    public Cursor getViewCursor(final int cursorType);

    public void setViewKeyListener(KeyListener l);

    public Adjustable getHorizontalScrollBar();

    public Adjustable getVerticalScrollBar();

    public void setAnnotationCallback(AnnotationCallback annotationCallback);

    public void setSecurityCallback(SecurityCallback securityCallback);

    public void deleteCurrentAnnotation();

    public void undo();

    public void redo();

    public AnnotationCallback getAnnotationCallback();

    public SecurityCallback getSecurityCallback();

    public DocumentViewModel getDocumentViewModel();

    public void clearSelectedText();

    public void clearHighlightedText();

    public void clearSelectedAnnotations();

    public void assignSelectedAnnotation(AnnotationComponentImpl annotationComponent);

    public void selectAllText();

    public String getSelectedText();

    public void firePropertyChange(String event, int oldValue, int newValue);

    public void firePropertyChange(String event, Object oldValue, Object newValue);
}
