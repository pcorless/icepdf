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

import org.icepdf.core.application.ProductInfo;
import org.icepdf.core.util.Library;

import java.util.Hashtable;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The <code>Catalog</code> object represents the root of a PDF document's
 * object heirarchy.  The <code>Catalog</code> is located by means of the
 * <b>Root</b> entry in the trailer of the PDF file.  The catalog contains
 * references to other objects defining the document's contents, outline, names,
 * destinations, and other attributes.</p>
 * <p/>
 * <p>The <code>Catalog</code> class can be accessed from the {@see Document}
 * class for convenience, but can also be accessed via the {@see PTrailer} class.
 * Useful information about the document can be extracted from the Catalog
 * Dictionary, such as PDF version information and Viewer Preferences.  All
 * Catalog dictionary properties can be accesed via the getEntries method.
 * See section 3.6.1 of the PDF Reference version 1.6 for more information on
 * the properties available in the Catalog Object. </p>
 *
 * @since 1.0
 */
public class Catalog extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Catalog.class.toString());

    private PageTree pageTree;
    private Outlines outlines;
    private NameTree nameTree;
    private Dictionary dests;
    private ViewerPreferences viewerPref;

    private boolean outlinesInited = false;
    private boolean namesTreeInited = false;
    private boolean destsInited = false;
    private boolean viewerPrefInited = false;

    // Announce ICEpdf Core
    static {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ICEsoft ICEpdf Core " + Document.getLibraryVersion());
        }
    }

    /**
     * Creates a new instance of a Catalog.
     *
     * @param l document library.
     * @param h Catalog dictionary entries.
     */
    public Catalog(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * Initiate the PageTree.
     */
    public synchronized void init() {
        Object tmp = library.getObject(entries, "Pages");
        pageTree = null;
        if (tmp instanceof PageTree) {
            pageTree = (PageTree) tmp;
        }
        // malformed core corner case, pages must not be references, but we
        // have a couple cases that break the spec.
        else if (tmp instanceof Hashtable) {
            pageTree = new PageTree(library, (Hashtable) tmp);
        }

        try {
            pageTree.init();
            pageTree.initRootPageTree();
        }
        catch (NullPointerException e) {
            logger.log(Level.FINE, "Error parsing page tree.", e);
        }
    }

    /**
     * Dispose the Catalog.
     *
     * @param cache if true, cached files are removed, otherwise objects are freed
     *              but object caches are left intact.
     */
    public void dispose(boolean cache) {
        // dispose the nameTree
        if (nameTree != null) {
            nameTree.dispose();
            namesTreeInited = false;
            if (!cache)
                nameTree = null;
        }

        if (pageTree != null) {
            pageTree.dispose(cache);
            if (!cache)
                pageTree = null;
        }
        if (outlines != null) {
            if (!cache) {
                outlines.dispose();
                outlines = null;
            }
        }
    }

    /**
     * Gets PageTree node that is the root of the document's page tree.
     * The PageTree can be traversed to access child PageTree and Page objects.
     *
     * @return Catalogs PageTree.
     * @see org.icepdf.core.pobjects.Page
     */
    public PageTree getPageTree() {
        return pageTree;
    }

    /**
     * Gets the Outlines Dictionary that is the root of the document's outline
     * hierarchy. The Outline can be traversed to access child OutlineItems.
     *
     * @return Outlines object if one exists; null, otherwise.
     * @see org.icepdf.core.pobjects.OutlineItem
     */
    public Outlines getOutlines() {
        if (!outlinesInited) {
            outlinesInited = true;
            Object o = library.getObject(entries, "Outlines");
            if (o != null)
                outlines = new Outlines(library, (Hashtable) o);
        }
        return outlines;
    }

    /**
     * Gets the document's Names dictionary.  The Names dictionary contains
     * a category of objects in a PDF file which can be referred to by name
     * rather than by object reference.
     *
     * @return name dictionary for document.  If no name dictionary exists null
     *         is returned.
     */
    public NameTree getNameTree() {
        if (!namesTreeInited) {
            namesTreeInited = true;
            Object o = library.getObject(entries, "Names");
            if (o != null && o instanceof Hashtable) {
                Hashtable dest = (Hashtable) o;
                Object names = library.getObject(dest, "Dests");
                if (names != null && names instanceof Hashtable) {
                    nameTree = new NameTree(library, (Hashtable) names);
                    nameTree.init();
                }
            }
        }
        return nameTree;
    }

    /**
     * Gets a dictionary of names and corresponding destinations.
     *
     * @return A Dictionary of Destinations; if none, null is returned.
     */
    public Dictionary getDestinations() {
        if (!destsInited) {
            destsInited = true;
            Object o = library.getObject(entries, "Dests");
            if (o != null) {
                dests = new Dictionary(library, (Hashtable) o);
                dests.init();
            }
        }
        return dests;
    }

    /**
     * Gets a dictionary of keys and corresponding viewer preferences
     * This can be used to pull information based on the PDF specification,
     *  such as HideToolbar or FitWindow
     *
     * @return the constructed ViewerPreferences object
     */
    public ViewerPreferences getViewerPreferences() {
        if (!viewerPrefInited) {
            viewerPrefInited = true;
            Object o = library.getObject(entries, "ViewerPreferences");
            if (o != null) {
                viewerPref = new ViewerPreferences(library, (Hashtable) o);
                viewerPref.init();
            }
        }
        return viewerPref;
    }

    /**
     * Returns a summary of the Catalog dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "CATALOG= " + entries.toString();
    }
}
