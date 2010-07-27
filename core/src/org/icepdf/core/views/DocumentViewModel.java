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
package org.icepdf.core.views;

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.views.swing.AbstractPageViewComponent;
import org.icepdf.core.views.swing.AnnotationComponentImpl;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The DocumentViewModel interface contains common accessors and modifiers needed
 * to represent a document view state.
 *
 * @see org.icepdf.ri.common.views.AbstractDocumentViewModel
 * @since 2.5
 */
public interface DocumentViewModel {
    /**
     * Display tool constant for adding a pan tool.
     */
    public int DISPLAY_TOOL_PAN = 1;
    /**
     * Display tool constant for adding a zoom in tool.
     */
    public int DISPLAY_TOOL_ZOOM_IN = 2;
    /**
     * Display tool constant for adding a zoom out tool.
     */
    public int DISPLAY_TOOL_ZOOM_OUT = 3;
    /**
     * Display tool constant for adding a text selection tool.
     */
    public int DISPLAY_TOOL_TEXT_SELECTION = 4;
    /**
     * Display tool constant for adding a text selection tool.
     */
    public int DISPLAY_TOOL_SELECTION = 5;
    /**
     * Display tool constant for adding a text selection tool.
     */
    public int DISPLAY_TOOL_LINK_ANNOTATION = 6;
    /**
     * Display tool constant for setting no tools
     */
    public int DISPLAY_TOOL_NONE = 8;
    /**
     * Display tool constant for showing user that gui is busy
     */
    public int DISPLAY_TOOL_WAIT = 9;

    /**
     * Gets the PDF document object associated with this views.
     *
     * @return PDF document which is associated with this view.
     */
    public Document getDocument();

    /**
     * Gets a list of doucment pages that have selected text elements. The
     * pages are referenced so that they will be removed automatically if
     * the memory manage needs to dispose of a page.
     *
     * @return list Weakly referenced pages
     */
    public ArrayList<WeakReference<AbstractPageViewComponent>> getSelectedPageText();

    /**
     * Adds the specified page to the list of selected pages.
     *
     * @param pageViewComponent pageview component to add to list.
     */
    public void addSelectedPageText(AbstractPageViewComponent pageViewComponent);


    /**
     * Returns true if all text in the document should be in a selected state.
     *
     * @return true if document is in select all text text state, false otherwise.
     */
    public boolean isSelectAll();

    /**
     * Sets the selected all text state.
     *
     * @param selectAll true to select all text, false otherwise.
     */
    public void setSelectAll(boolean selectAll);

    /**
     * Clears all pages in a selected state.
     */
    public void clearSelectedPageText();

    public void executePageInitialization(Runnable runnable) throws InterruptedException;

    public void executePagePainter(Runnable runnable) throws InterruptedException;

    /**
     * Gets the page components associated with this view model.
     *
     * @return vector of page components.
     */
    public List<AbstractPageViewComponent> getPageComponents();

    /**
     * Sets the view model current page index.
     *
     * @param pageIndex zero based current pages page index of the document.
     * @return true if the page index could be set, false otherwise.
     */
    public boolean setViewCurrentPageIndex(int pageIndex);

    /**
     * Gets the current page index represented in this model.
     *
     * @return zero based page page index.
     */
    public int getViewCurrentPageIndex();

    /**
     * Sets the models zoom level.
     *
     * @param viewZoom zoom value
     * @return true if the view zoom was set correctly otherwise, false.
     */
    public boolean setViewZoom(float viewZoom);

    /**
     * Gets the view model zoom level.
     *
     * @return zoom level of this view model
     */
    public float getViewZoom();

    /**
     * Sets the view rotaiton of this model.
     *
     * @param viewRotation rotation in degrees
     * @return true if the view rotation was set correctly, otherwise false.
     */
    public boolean setViewRotation(float viewRotation);

    /**
     * Gets the view rotation of the model.
     *
     * @return view rotation of the model
     */
    public float getViewRotation();

    /**
     * Invalidate the underlying Document Page models.
     */
    public void invalidate();

    /**
     * Sets the view tool mode.
     *
     * @param viewToolMode selected tool mode, pan, zoom and et.
     * @return true if the view tool was set correctly, false otherwise.
     */
    public boolean setViewToolMode(int viewToolMode);

    /**
     * Gets the tool mode.
     *
     * @return tool mode.
     */
    public int getViewToolMode();

    /**
     * Checks if the specified tool mode is set in the view model.
     *
     * @param viewToolMode tool model to check if selected.
     * @return true if specified tool mode is selected, otherwise false.
     */
    public boolean isViewToolModeSelected(int viewToolMode);

    /**
     * Gets the page bound of the specified page Index.
     *
     * @param pageIndex zero based page index.
     * @return bounds of specified page.  If page index. is not valid, null is returned.
     */
    public Rectangle getPageBounds(int pageIndex);

    /**
     * Free resources associated with this model.
     */
    public void dispose();

    /**
     * Sets the page boundtry used to paint a page.
     *
     * @param pageBoundary page bounds
     */
    public void setPageBoundary(final int pageBoundary);

    /**
     * Gets the page boundry used to paint document pages.
     *
     * @return page boundary type as defined in the class Page.
     */
    public int getPageBoundary();

    /**
     * Gets the currently selected annotation
     *
     * @return currently selected annotaitons.
     */
    public AnnotationComponentImpl getCurrentAnnotation();

    /**
     * Sets teh current annotation
     *
     * @param currentAnnotation annotation to set as current
     */
    public void setCurrentAnnotation(AnnotationComponentImpl currentAnnotation);

    /**
     * Adds memento state  to the care taker.
     *
     * @param oldMementoState origional state.
     * @param newMementoState new state.
     */
    public void addMemento(Memento oldMementoState,
                           Memento newMementoState);
}
