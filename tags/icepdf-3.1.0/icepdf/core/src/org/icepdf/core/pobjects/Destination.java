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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The <code>Destination</code> class defines a particular view of a
 * PDF document consisting of the following items:</p>
 * <ul>
 * <li>The page of the document to be displayed.</li>
 * <li>The location of the document window on that page.</li>
 * <li>The magnification (zoom) factor to use when displaying the page
 * Destinations may be associated with outline items, annotations,
 * or actions. </li>
 * </ul>
 * <p>Destination can be associated with outline items, annotations, or actions.
 * In each case the destination specifies the view of the document to be presented
 * when one of the respective objects is activated.</p>
 * <p>The Destination class currently only supports the Destination syntaxes,
 * [page /XYZ left top zoom], other syntax will be added in future releases. The
 * syntax [page /XYZ left top zoom] is defined as follows:</p>
 * <ul>
 * <li>page - designated page to show (Reference to a page).</li>
 * <li>/XYZ - named format of destination syntax.</li>
 * <li>left - x value of the upper left-and coordinate.</li>
 * <li>top - y value of the upper left-hand coordinate.</li>
 * <li>zoom - zoom factor to magnify page by.</li>
 * </ul>
 * <p>A null value for left, top or zoom specifies that the current view values
 * will be unchanged when navigating to the specified page. </p>
 *
 * @see org.icepdf.core.pobjects.annotations.Annotation
 * @see org.icepdf.core.pobjects.OutlineItem
 * @see org.icepdf.core.pobjects.actions.Action
 * @since 1.0
 */
public class Destination {

    private static final Logger logger =
            Logger.getLogger(Destination.class.toString());

    // library of all PDF document objects
    private Library library;

    // object containing all of the destinations parameters
    private Object object;

    // Reference object for destination
    private Reference ref;

    // Specified by /XYZ in the core, /(left)(top)(zoom)
    private float left = Float.NaN;
    private float top = Float.NaN;
    private float zoom = Float.NaN;

    // initiated flag
    private boolean inited = false;

    /**
     * Creates a new instance of a Destination.
     *
     * @param l document library.
     * @param h Destination dictionary entries.
     */
    public Destination(Library l, Object h) {
        library = l;
        object = h;
    }

    /**
     * Initiate the Destination. Retrieve any needed attributes.
     */
    void init() {

        // check for initiation
        if (inited) {
            return;
        }
        inited = true;

        // if vector we have found /XYZ
        if (object instanceof Vector) {
            parse((Vector) object);
        }

        // find named Destinations, this however is incomplete
        // @see #parser for more detailed information
        else if (object instanceof Name || object instanceof StringObject) {
            String s;
            // Make sure to decrypt this attribute
            if (object instanceof StringObject) {
                StringObject stringObject = (StringObject) object;
                stringObject.getDecryptedLiteralString(library.securityManager);
                s = stringObject.getDecryptedLiteralString(library.securityManager);
            } else {
                s = object.toString();
            }

            boolean found = false;
            Catalog catalog = library.getCatalog();
            if (catalog != null) {
                NameTree nameTree = catalog.getNameTree();
                if (nameTree != null) {
                    Object o = nameTree.searchName(s);
                    if (o != null) {
                        if (o instanceof Vector) {
                            parse((Vector) o);
                            found = true;
                        } else if (o instanceof Hashtable) {
                            Hashtable h = (Hashtable) o;
                            Object o1 = h.get("D");
                            if (o1 instanceof Vector) {
                                parse((Vector) o1);
                                found = true;
                            }
                        }
                    }
                }
                if (!found) {
                    Dictionary dests = catalog.getDestinations();
                    if (dests != null) {
                        Object ob = dests.getObject(s);
                        if (ob instanceof Hashtable) {
                            parse((Vector) (((Hashtable) ob).get("D")));
                        } else {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.warning("Destination type missed=" + ob);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility method for parsing the Destination attributes
     * todo implement other destination syntax
     *
     * @param v vector of attributes associated with the Destination
     */
    private void parse(Vector v) {

        // Assign a Reference
        if (v.elementAt(0) instanceof Reference) {
            ref = (Reference) v.elementAt(0);
        }
        // coordinate of the destination
        if (v.elementAt(1).equals("XYZ")) {
            if (!v.elementAt(2).equals("null")) {
                left = ((Number) v.elementAt(2)).floatValue();
            }
            if (!v.elementAt(3).equals("null")) {
                top = ((Number) v.elementAt(3)).floatValue();
            }
            if (!v.elementAt(4).equals("null") && !v.elementAt(4).equals("0")) {
                zoom = ((Number) v.elementAt(4)).floatValue();
            }
        }
        /**
         * This section is still very incomplete.   The spec is as follows
         *
         * [page /Fit]  Display the page designated by page, with its contents
         *    magnified just enough to fit the entire page within the window both
         *    horizontally and vertically. If the required horizontal and vertical
         *    magnification factors are different, use the smaller of the two,
         *    centering the page within the window in the other dimension.
         *
         * [page /FitH top] Display the page designated by page, with the vertical
         *     coordinate top positioned at the top edge of the window and the
         *     contents of the page magnified just enough to fit the entire width
         *     of the page within the window. [page /FitV left] Display the page
         *     designated by page, with the horizontal coordinate left positioned
         *     at the left edge of the window and the contents of the page magnified
         *     just enough to fit the entire height of the page within the window.
         *
         * [page /FitR left bottom right top] Display the page designated by page,
         *     with its contents magnified just enough to fit the rectangle
         *     specified by the coordinates left, bottom, right, and top entirely
         *     within the window both horizontally and vertically. If the required
         *     horizontal and vertical magnification factors are different, use the smaller of
         *     the two, centering the rectangle within the window in the other dimension.
         *
         * [page /FitB] (PDF 1.1) Display the page designated by page, with its
         *     contents magnified just enough to fit its bounding box entirely
         *     within the window both horizontally and vertically. If the required
         *     horizontal and vertical magnification factors are different, use
         *     the smaller of the two, centering the bounding box within the window
         *     in the other dimension.
         *
         * [page /FitBH top] (PDF 1.1) Display the page designated by page, with
         *     the vertical coordinate top positioned at the top edge of the window
         *     and the contents of the page magnified just enough to fit the
         *     entire width of its bounding box within the window.
         *
         * [page /FitBV left] (PDF 1.1) Display the page designated by page, with
         *     the horizontal coordinate left positioned at the left edge of the
         *     window and the contents of the page magnified just enough to fit
         *     the entire height of its bounding box within the window.
         *
         */
        else if (v.elementAt(1).equals("Fit")) {
        }
    }

    /**
     * Gets the Page Reference specified by the destination.
     *
     * @return a Reference to the Page Object associated with this destination.
     */
    public Reference getPageReference() {
        if (!inited)
            init();
        return ref;
    }

    /**
     * Gets the left offset from the top, left position of the page specified by
     * this destination.
     *
     * @return the left offset from the top, left position  of the page.  If not
     *         specified Float.NaN is returned.
     */
    public float getLeft() {
        if (!inited)
            init();
        return left;
    }

    /**
     * Gets the top offset from the top, left position of the page specified by
     * this destination.
     *
     * @return the top offset from the top, left position of the page.  If not
     *         specified Float.NaN is returned.
     */
    public float getTop() {
        if (!inited)
            init();
        return top;
    }

    /**
     * Gets the zoom level specifed by the destination.
     *
     * @return the specified zoom level, Float.NaN if not specified.
     */
    public float getZoom() {
        if (!inited)
            init();
        return zoom;
    }

    /**
     * Returns a summary of the annotation dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "Destination  ref: " + getPageReference() + " ,  top: " +
                getTop() + " ,  left: " + getLeft() + " ,  zoom: " + getZoom();
    }
}



