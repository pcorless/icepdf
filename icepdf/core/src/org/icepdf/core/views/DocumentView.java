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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusListener;

/**
 * <p>The DocumentView interface should be used when create a new multipage view. </p>
 *
 * @see org.icepdf.ri.common.views.AbstractDocumentView
 * @since 2.5
 */
public interface DocumentView extends
        MouseInputListener, AdjustmentListener, FocusListener {
    /**
     * Indicates that a two column view will have odd-numbered pages on the left.
     */
    public int LEFT_VIEW = 0;
    /**
     * Indicates that a two column view will have odd-numbered pages on the right.
     */
    public int RIGHT_VIEW = 1;

    /**
     * Get the next page index.  This will number will very depending on the
     * page view type.  Two column page views usually increment page counts by 2
     * and single page views by 1 page.
     *
     * @return number of pages to increment page count on a page increment command.
     */
    public int getNextPageIncrement();

    /**
     * Get the previous page index.  This will number will very depending on the
     * page view type.  Two column page views usually increment page counts by 2
     * and single page views by 1 page.
     *
     * @return number of pages to increment page count on a page increment command.
     */
    public int getPreviousPageIncrement();

    /**
     * Gets the total size of the document view.  This size will very depending
     * on the view type.  The size dimension has been normalized to a zoom
     * factor of 1.0f and rotation is taken care off.
     *
     * @return size of document in pixels for all pages represented in the view.
     */
    public Dimension getDocumentSize();

    /**
     * Parent document view controller
     *
     * @return document view controller
     */
    public DocumentViewController getParentViewController();

    /**
     * Gets the view model associated with this document view.
     *
     * @return document view model used by this view.
     */
    public DocumentViewModel getViewModel();

    /**
     * Dispose all resources associated with this views.
     */
    public void dispose();

    /**
     * Update the child components which make up this view.
     */
    public void updateDocumentView();
}
