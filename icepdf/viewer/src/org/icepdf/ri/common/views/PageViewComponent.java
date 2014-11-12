/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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

import java.awt.*;

/**
 * <p>The <code>PageViewComponent</code> interaces should be used by any page view
 * implementation to represent a single page view.  The methods defined in this
 * interface are the most commonly used methods and are used by the
 * <code>AbstractDocumentView</code> and <code>AbstractDocumentViewModel</code>.</p>
 *
 * @see org.icepdf.ri.common.views.PageViewComponentImpl
 * @since 2.0
 */
public interface PageViewComponent {

    /**
     * Set the parent Document View class which is resbonsible for drawing and
     * the general management of PageViewComponents for a particular view.
     *
     * @param parentDocumentView type of view, single page, continuous, etc.
     */
    public void setDocumentViewCallback(DocumentView parentDocumentView);

    /**
     * Gets the page index which this PageViewComponent is drawing.
     *
     * @return zero pages page index of the page drawn by this component.
     */
    public int getPageIndex();

    /**
     * Called to initialize resources used by this class.
     */
    public void init();

    /**
     * Invalidates the underling document page and resepctive resources.
     * Subsiquent page calls will reinitialize the page data.
     */
    public void invalidatePage();

    /**
     * Invalidates the page buffer used for bufferer paints forcing a clean
     * repaint of the pge. .
     */
    public void invalidatePageBuffer();

    /**
     * Called to free resources used by this component.
     */
    public void dispose();

    /**
     * Called to invalidate the component.
     */
    public void invalidate();

    /**
     * Indicates that the page is showing;
     *
     * @return true if the page is showing, otherwise; false.
     */
    public boolean isShowing();

    /**
     * Clear any internal data structures that represent selected text and
     * repaint the component.
     */
    public void clearSelectedText();


    /**
     * Sets the text that is contained in the specified recttangle and the
     * given mouse pointer.  The cursor and selection rectangel must be in
     * in page space.
     *
     * @param cursorLocation location of cursor or mouse.
     * @param selection      rectangle of text to include in selection.
     */
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection);

    public void clearSelectionRectangle();

    /**
     * Add a new annotation object to this page view comnponent.
     *
     * @param annotation annotation to add.
     */
    public void addAnnotation(AnnotationComponent annotation);

    /**
     * Remove the specified annotation from this page view.
     *
     * @param annotationComp annotation to be removed.
     */
    public void removeAnnotation(AnnotationComponent annotationComp);

    public void setToolMode(final int viewToolMode);
}
