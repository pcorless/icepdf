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
package org.icepdf.core.pobjects;

import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public static final Name TYPE = new Name("Catalog");
    public static final Name DESTS_KEY = new Name("Dests");
    public static final Name VIEWERPREFERENCES_KEY = new Name("ViewerPreferences");
    public static final Name NAMES_KEY = new Name("Names");
    public static final Name OUTLINES_KEY = new Name("Outlines");
    public static final Name OCPROPERTIES_KEY = new Name("OCProperties");
    public static final Name PAGES_KEY = new Name("Pages");
    public static final Name PAGELAYOUT_KEY = new Name("PageLayout");
    public static final Name PAGEMODE_KEY = new Name("PageMode");
    public static final Name COLLECTION_KEY = new Name("Collection");

    private PageTree pageTree;
    private Outlines outlines;
    private Names names;
    private OptionalContent optionalContent;
    private Dictionary dests;
    private ViewerPreferences viewerPref;

    private boolean outlinesInited = false;
    private boolean namesTreeInited = false;
    private boolean destsInited = false;
    private boolean viewerPrefInited = false;
    private boolean optionalContentInited = false;

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
    public Catalog(Library l, HashMap<Object, Object> h) {
        super(l, h);
    }

    /**
     * Initiate the PageTree.
     */
    public synchronized void init() {
        Object tmp = library.getObject(entries, PAGES_KEY);
        pageTree = null;
        if (tmp instanceof PageTree) {
            pageTree = (PageTree) tmp;
        }
        // malformed core corner case, pages must not be references, but we
        // have a couple cases that break the spec.
        else if (tmp instanceof HashMap) {
            pageTree = new PageTree(library, (HashMap) tmp);
        }
        // malformed cornercase, just have a page object, instead of tree.
        else if (tmp instanceof Page) {
            Page tmpPage = (Page) tmp;
            HashMap<String, Object> tmpPages = new HashMap<String, Object>();
            List<Reference> kids = new ArrayList<Reference>();
            kids.add(tmpPage.getPObjectReference());
            tmpPages.put("Kids", kids);
            tmpPages.put("Count", 1);
            pageTree = new PageTree(library, tmpPages);
        }

        // let any exception bubble up.
        if (pageTree != null) {
            pageTree.init();
        }

        // check for the collections dictionary for the presence of a portable collection
        tmp = library.getObject(entries, NAMES_KEY);
        if (tmp != null) {
            names = new Names(library, (HashMap) tmp);
            names.init();
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
            Object o = library.getObject(entries, OUTLINES_KEY);
            if (o != null)
                outlines = new Outlines(library, (HashMap) o);
        }
        return outlines;
    }

    /**
     * Gets the document's Names dictionary.  The Names dictionary contains
     * a category of objects in a PDF file which can be referred to by name
     * rather than by object reference.
     *
     * @return names object entry.  If no names entries exists null
     * is returned.
     */
    public Names getNames() {
        return names;
    }

    /**
     * Gets a dictionary of names and corresponding destinations.
     *
     * @return A Dictionary of Destinations; if none, null is returned.
     */
    @SuppressWarnings("unchecked")
    public Dictionary getDestinations() {
        if (!destsInited) {
            destsInited = true;
            Object o = library.getObject(entries, DESTS_KEY);
            if (o != null) {
                dests = new Dictionary(library, (HashMap<Object, Object>) o);
                dests.init();
            }
        }
        return dests;
    }

    /**
     * Gets a dictionary of keys and corresponding viewer preferences
     * This can be used to pull information based on the PDF specification,
     * such as HideToolbar or FitWindow
     *
     * @return the constructed ViewerPreferences object
     */
    public ViewerPreferences getViewerPreferences() {
        if (!viewerPrefInited) {
            viewerPrefInited = true;
            Object o = library.getObject(entries, VIEWERPREFERENCES_KEY);
            if (o != null) {
                viewerPref = new ViewerPreferences(library, (HashMap) o);
                viewerPref.init();
            }
        }
        return viewerPref;
    }

    /**
     * Gets the the optional content properties dictionary if present.
     *
     * @return OptionalContent dictionary, null if none exists.
     */
    public OptionalContent getOptionalContent() {
        if (!optionalContentInited) {
            optionalContentInited = true;
            Object o = library.getObject(entries, OCPROPERTIES_KEY);
            if (o != null && o instanceof HashMap) {
                optionalContent = new OptionalContent(library, ((HashMap) o));
                optionalContent.init();
            } else {
                optionalContent = new OptionalContent(library, new HashMap());
                optionalContent.init();
            }
        }
        return optionalContent;
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
