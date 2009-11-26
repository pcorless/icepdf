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

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.views.swing.AnnotationComponentImpl;

import java.awt.*;

/**
 * <p>The <code>PageViewComponent</code> interaces should be used by any page view
 * implementation to represent a single page view.  The methods defined in this
 * interface are the most commonly used methods and are used by the
 * <code>AbstractDocumentView</code> and <code>AbstractDocumentViewModel</code>.</p>
 *
 * @see org.icepdf.core.views.swing.PageViewComponentImpl
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
     * Clear any internal data stractures that represent selected text and
     * repaint the component. 
     */
    public void clearSelectedText();

    /**
     * Sets the text that is contained in the specified recttangle and the
     * given mouse pointer.  The cursor and selection rectangel must be in
     * in page space.
     *
     * @param cursorLocation location of cursor or mouse.
     * @param selection rectangle of text to include in selection.
     */
    public void setTextSelectionRectangle(Point cursorLocation, Rectangle selection);

    /**
     * Add a new annotation object to this page view comnponent.
     *
     * @param annotation annotation to add. 
     */
    public AnnotationComponent addAnnotation(Annotation annotation );

    /**
     * Remove the specified annotation from this page view.
     *
     * @param annotationComp annotation to be removed.
     */
    public void removeAnnotation(AnnotationComponent annotationComp);
}
