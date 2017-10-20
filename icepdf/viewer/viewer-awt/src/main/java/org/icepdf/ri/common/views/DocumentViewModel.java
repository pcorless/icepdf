/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;

import java.awt.*;
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
    int DISPLAY_TOOL_PAN = 1;
    /**
     * Display tool constant for adding a zoom in tool.
     */
    int DISPLAY_TOOL_ZOOM_IN = 2;
    /**
     * Display tool constant for adding a zoom out tool.
     */
    int DISPLAY_TOOL_ZOOM_OUT = 3;
    /**
     * Display tool constant for adding a zoom out tool.
     */
    int DISPLAY_TOOL_ZOOM_DYNAMIC = 4;
    /**
     * Display tool constant for adding a text selection tool.
     */
    int DISPLAY_TOOL_TEXT_SELECTION = 5;
    /**
     * Display tool constant for adding a text selection tool.
     */
    int DISPLAY_TOOL_SELECTION = 6;
    /**
     * Display tool constant for creating new link annotation.
     */
    int DISPLAY_TOOL_LINK_ANNOTATION = 7;
    /**
     * Display tool constant for creating new highlight annotation.
     */
    int DISPLAY_TOOL_HIGHLIGHT_ANNOTATION = 8;
    /**
     * Display tool constant for creating new underline annotation.
     */
    int DISPLAY_TOOL_UNDERLINE_ANNOTATION = 9;
    /**
     * Display tool constant for creating new squiggly annotation.
     */
    int DISPLAY_TOOL_SQUIGGLY_ANNOTATION = 10;
    /**
     * Display tool constant for creating new strikeout annotation.
     */
    int DISPLAY_TOOL_STRIKEOUT_ANNOTATION = 11;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_LINE_ANNOTATION = 12;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_LINE_ARROW_ANNOTATION = 13;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_SQUARE_ANNOTATION = 14;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_CIRCLE_ANNOTATION = 15;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_INK_ANNOTATION = 16;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_FREE_TEXT_ANNOTATION = 17;

    /**
     * Display tool constant for creating new line  annotation.
     */
    int DISPLAY_TOOL_TEXT_ANNOTATION = 18;

    /**
     * Display tool constant for setting no tools
     */
    int DISPLAY_TOOL_NONE = 50;
    /**
     * Display tool constant for showing user that gui is busy
     */
    int DISPLAY_TOOL_WAIT = 51;

    /**
     * Gets the PDF document object associated with this views.
     *
     * @return PDF document which is associated with this view.
     */
    Document getDocument();

    /**
     * Gets a list of document pages that have selected text elements. The
     * pages are referenced so that they will be removed automatically if
     * the memory manage needs to dispose of a page.
     *
     * @return list Weakly referenced pages
     */
    ArrayList<AbstractPageViewComponent> getSelectedPageText();

    /**
     * Adds the specified page to the list of selected pages.
     *
     * @param pageComponent pageView component to add to list.
     */
    void addSelectedPageText(AbstractPageViewComponent pageComponent);

    /**
     * Remove the specified page to the list of selected pages.
     *
     * @param pageComponent pageView component to add to list.
     */
    void removeSelectedPageText(AbstractPageViewComponent pageComponent);

    /**
     * Returns true if all text in the document should be in a selected state.
     *
     * @return true if document is in select all text text state, false otherwise.
     */
    boolean isSelectAll();

    /**
     * Sets the selected all text state.
     *
     * @param selectAll true to select all text, false otherwise.
     */
    void setSelectAll(boolean selectAll);

    /**
     * Clears all pages in a selected state.
     */
    void clearSelectedPageText();

    /**
     * Gets the page components associated with this view model.
     *
     * @return vector of page components.
     */
    List<AbstractPageViewComponent> getPageComponents();

    /**
     * Sets the view model current page index.
     *
     * @param pageIndex zero based current pages page index of the document.
     * @return true if the page index could be set, false otherwise.
     */
    boolean setViewCurrentPageIndex(int pageIndex);

    /**
     * Gets the current page index represented in this model.
     *
     * @return zero based page page index.
     */
    int getViewCurrentPageIndex();

    /**
     * Sets the models zoom level.
     *
     * @param viewZoom zoom value
     * @return true if the view zoom was set correctly otherwise, false.
     */
    boolean setViewZoom(float viewZoom);

    /**
     * Gets the view model zoom level.
     *
     * @return zoom level of this view model
     */
    float getViewZoom();

    /**
     * Sets the view rotation of this model.
     *
     * @param viewRotation rotation in degrees
     * @return true if the view rotation was set correctly, otherwise false.
     */
    boolean setViewRotation(float viewRotation);

    /**
     * Gets the view rotation of the model.
     *
     * @return view rotation of the model
     */
    float getViewRotation();

    /**
     * Sets the view tool mode.
     *
     * @param viewToolMode selected tool mode, pan, zoom and et.
     * @return true if the view tool was set correctly, false otherwise.
     */
    boolean setViewToolMode(int viewToolMode);

    /**
     * Gets the tool mode.
     *
     * @return tool mode.
     */
    int getViewToolMode();

    /**
     * Checks if the specified tool mode is set in the view model.
     *
     * @param viewToolMode tool model to check if selected.
     * @return true if specified tool mode is selected, otherwise false.
     */
    boolean isViewToolModeSelected(int viewToolMode);

    /**
     * Gets the page bound of the specified page Index.
     *
     * @param pageIndex zero based page index.
     * @return bounds of specified page.  If page index. is not valid, null is returned.
     */
    Rectangle getPageBounds(int pageIndex);

    /**
     * Free resources associated with this model.
     */
    void dispose();

    /**
     * Sets the page boundry used to paint a page.
     *
     * @param pageBoundary page bounds
     */
    void setPageBoundary(final int pageBoundary);

    /**
     * Gets the page boundary used to paint document pages.
     *
     * @return page boundary type as defined in the class Page.
     */
    int getPageBoundary();

    /**
     * Gets the currently selected annotation
     *
     * @return currently selected annotations.
     */
    AnnotationComponent getCurrentAnnotation();

    /**
     * Sets teh current annotation
     *
     * @param currentAnnotation annotation to set as current
     */
    void setCurrentAnnotation(AnnotationComponent currentAnnotation);

    /**
     * Adds memento state  to the care taker.
     *
     * @param oldMementoState original state.
     * @param newMementoState new state.
     */
    void addMemento(Memento oldMementoState,
                    Memento newMementoState);

}
