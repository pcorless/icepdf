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
package org.icepdf.core;

import org.icepdf.core.pobjects.Document;

/**
 * A Controller is the glue between the model and view components.
 * These methods allow the different parts of the view to remain
 * in lock-step with each other and have access to the model,
 * as necessary
 *
 * @since 2.0
 */
public interface Controller {
    /**
     * A Document is the root of the object hierarchy, giving access
     * to the contents of a PDF file.
     * Significantly, getDocument().getCatalog().getPageTree().getPage(int pageNumber)
     * gives access to each Page, so that it might be drawn.
     *
     * @return Document root of the PDF file.
     */
    public Document getDocument();

    /**
     * When viewing a PDF file, one or more pages may be viewed at
     * a single time, but this page is the single page which is most
     * predominantly being displayed.
     *
     * @return The zero-based index of the current Page being displayed
     */
    public int getCurrentPageNumber();

    /**
     * Each Page may have its own rotation, but on top of that, the user
     * may select to have the Page further rotated by 90, 180 or 270 degrees.
     *
     * @return The user's requested rotation
     */
    public float getUserRotation();

    /**
     * The Page being shown may be zoomed in or out, to show more detail,
     * or provide an overview.
     *
     * @return The user's requested zoom
     */
    public float getUserZoom();
}
