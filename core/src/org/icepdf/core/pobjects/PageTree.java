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

/**
 * <p>This class represents a document's page tree which defines the ordering
 * of pages in the document.  The tree structure allows a user to quickly
 * open a document containing thousands of pages.  The tree contains nodes of
 * two types, page tree nodes and page nodes, where page tree nodes are
 * intermediate nodes and pages are leaves.  A simple example of this tree structure
 * is a single page tree node that references all of the document's page
 * objects directly.</p>
 * <p/>
 * <p>The page tree is accessible via the document catalog and can be traversed
 * to display a desired page or extracts its content.<p>
 *
 * @see org.icepdf.core.pobjects.Page
 * @see org.icepdf.core.pobjects.Catalog
 * @since 2.0
 */
public class PageTree extends Dictionary {
    // Number of leaf nodes
    private int kidsCount = 0;
    // vector of references to leafs
    private Vector kidsReferences;
    // vector of the pages associated with tree
    private Vector kidsPageAndPages;
    // pointer to parent page tree
    private PageTree parent;
    // initiated flag
    private boolean inited;
    // inheritable page boundary data.
    private PRectangle mediaBox;
    private PRectangle cropBox;
    // inheritable Resources
    private Resources resources;
    // loaded resource flag, we can't use null check as some trees don't have
    // resources. 
    private boolean loadedResources;

    /**
     * Inheritable rotation factor by child pages.
     */
    protected float rotationFactor = 0;

    /**
     * Indicates that the PageTree has a rotation factor which should be respected.
     */
    protected boolean isRotationFactor = false;

    /**
     * Creates a new instance of a PageTree.
     *
     * @param l document library.
     * @param h PageTree dictionary entries.
     */
    public PageTree(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * Dispose the PageTree.
     */
    protected synchronized void dispose(boolean cache) {
        if (kidsReferences != null) {
            if (!cache) {
                kidsReferences.clear();
            }
        }
        if (kidsPageAndPages != null) {
            for (Object pageOrPages : kidsPageAndPages) {
                if (pageOrPages instanceof Page)
                    ((Page) pageOrPages).dispose(cache);
                else if (pageOrPages instanceof PageTree)
                    ((PageTree) pageOrPages).dispose(cache);
            }
            if (!cache) {
                kidsPageAndPages.clear();
            }
        }
        /*
         * If resources is non-null, then at least one page has got a reference
         * to it. At which point we'll wait for the last page to reference it
         * do make the call to dispose.
         *
         * It is also possible for type3 fonts to use a resource, so we better
         * go through the motions to remove the resource, as we don't dispose
         * of fonts.
         */
        if (resources != null) {
            boolean disposeSuccess = resources.dispose(cache, this);
            if (disposeSuccess) {
                loadedResources = false;
            }
        }
    }

    /**
     * Initiate the PageTree.
     */
    public synchronized void init() {
        if (inited) {
            return;
        }
        Object parentTree = library.getObject(entries, "Parent");
        if (parentTree instanceof PageTree) {
            parent = (PageTree) library.getObject(entries, "Parent");
        }
        kidsCount = library.getNumber(entries, "Count").intValue();
        Vector boxDimensions = (Vector) (library.getObject(entries, "MediaBox"));
        if (boxDimensions != null) {
            mediaBox = new PRectangle(boxDimensions);
//            System.out.println("PageTree - MediaBox " + mediaBox);
        }
        boxDimensions = (Vector) (library.getObject(entries, "CropBox"));
        if (boxDimensions != null) {
            cropBox = new PRectangle(boxDimensions);
//            System.out.println("PageTree - CropBox " + cropBox);
        }
        kidsReferences = (Vector) library.getObject(entries, "Kids");
        kidsPageAndPages = new Vector(kidsReferences.size());
        kidsPageAndPages.setSize(kidsReferences.size());
        // Rotation is only respected if child pages do not have their own
        // rotation value.
        Object tmpRotation = library.getObject(entries, "Rotate");
        if (tmpRotation != null) {
            rotationFactor = ((Number) tmpRotation).floatValue();
            // mark that we have an inheritable value
            isRotationFactor = true;
        }

        inited = true;
    }

    // todo: clean up method
    void initRootPageTree() {
    }

    /**
     * Gets the media box boundary defined by this page tree.  The media box is a
     * required page entry and can be inherited from its parent page tree.
     *
     * @return media box boundary in user space units.
     */
    public PRectangle getMediaBox() {
        return mediaBox;
    }

    /**
     * Gets the crop box boundary defined by this page tree.  The media box is a
     * required page entry and can be inherited from its parent page tree.
     *
     * @return crop box boundary in user space units.
     */
    public PRectangle getCropBox() {
        return cropBox;
    }

    /**
     * Gets the Resources defined by this PageTree.  The Resources entry can
     * be inherited by the child Page objects.
     * <p/>
     * The caller is responsible for disposing of the returned Resources object.
     *
     * @return Resources associates with the PageTree
     */
    public Resources getResources() {
        if (!loadedResources) {
            loadedResources = true;
            resources = library.getResources(entries, "Resources");
        }
        return resources;
    }

    /**
     * Gets the page tree node that is the immediate parent of this one.
     *
     * @return parent page tree;  null, if this is the root page tree.
     */
    public PageTree getParent() {
        return parent;
    }

    /**
     * Gets the page number of the page specifed by a reference.
     *
     * @param r reference to a page in the page tree.
     * @return page number of the specified reference.  If no page is found, -1
     *         is returned.
     */
    public int getPageNumber(Reference r) {
        Page pg = (Page) library.getObject(r);
        if (pg == null)
            return -1;
//        pg.init();
        int globalIndex = 0;
        Reference currChildRef = r;
        Reference currParentRef = pg.getParentReference();
        PageTree currParent = pg.getParent();
        while (currParentRef != null && currParent != null) {
            currParent.init();
            int refIndex = currParent.indexOfKidReference(currChildRef);
            if (refIndex < 0)
                return -1;
            int localIndex = 0;
            for (int i = 0; i < refIndex; i++) {
                Object pageOrPages = currParent.getPageOrPagesPotentiallyNotInitedFromReferenceAt(i);
                if (pageOrPages instanceof Page) {
                    localIndex++;
                } else if (pageOrPages instanceof PageTree) {
                    PageTree peerPageTree = (PageTree) pageOrPages;
                    peerPageTree.init();
                    localIndex += peerPageTree.getNumberOfPages();
                }
            }
            globalIndex += localIndex;
            currChildRef = currParentRef;
            currParentRef = (Reference) currParent.entries.get("Parent");
            currParent = currParent.parent;
        }
        return globalIndex;
    }

    /**
     * Utility method for getting kid index.
     *
     * @param r
     * @return
     */
    private int indexOfKidReference(Reference r) {
        for (int i = 0; i < kidsReferences.size(); i++) {
            Reference ref = (Reference) kidsReferences.get(i);
            if (ref.equals(r))
                return i;
        }
        return -1;
    }

    /**
     * Utility method for initializing a page in the page tree.
     *
     * @param index index in the kids vector to initialize
     * @return
     */
    private Object getPageOrPagesPotentiallyNotInitedFromReferenceAt(int index) {
        Object pageOrPages = kidsPageAndPages.get(index);
        if (pageOrPages == null) {
            Reference ref = (Reference) kidsReferences.get(index);
            pageOrPages = library.getObject(ref);
            kidsPageAndPages.set(index, pageOrPages);
        }
        return pageOrPages;
    }

    /**
     * Utility method for initializing a page with its page number
     *
     * @param globalIndex
     * @return
     */
    private Page getPagePotentiallyNotInitedByRecursiveIndex(int globalIndex) {
        int globalIndexSoFar = 0;
        int numLocalKids = kidsPageAndPages.size();
        for (int i = 0; i < numLocalKids; i++) {
            Object pageOrPages = getPageOrPagesPotentiallyNotInitedFromReferenceAt(i);
            if (pageOrPages instanceof Page) {
                if (globalIndex == globalIndexSoFar)
                    return (Page) pageOrPages;
                globalIndexSoFar++;
            } else if (pageOrPages instanceof PageTree) {
                PageTree childPageTree = (PageTree) pageOrPages;
                childPageTree.init();
                int numChildPages = childPageTree.getNumberOfPages();
                if (globalIndex >= globalIndexSoFar && globalIndex < (globalIndexSoFar + numChildPages)) {
                    return childPageTree.getPagePotentiallyNotInitedByRecursiveIndex(
                            globalIndex - globalIndexSoFar);
                }
                globalIndexSoFar += numChildPages;
            }
        }
        return null;
    }

    /**
     * In a PDF file there is a root Pages object, which contains
     * children Page objects, as well as children PageTree objects,
     * all arranged in a tree.
     * getNumberOfPages() exists in every PageTree object, giving
     * the number of Page objects under it, recursively.
     * So, each PageTree object would have a different number of pages,
     * and only the root PageTree objects would have a number
     * representative of the whole Document.
     *
     * @return Total number of Page objects under this PageTree
     */
    public int getNumberOfPages() {
        return kidsCount;
    }

    /**
     * Gets a Page from the PDF file, locks it for the user,
     * initializes the Page, and returns it.
     * <p/>
     * ICEpdf uses a caching and memory management mechanism
     * to reduce the CPU, I/O, and time to access a Page,
     * which requires a locking and releasing protocol.
     * Calls to the <code>getPage</code> must be matched with
     * corresponding calls to <code>releasePage</code>.
     * Calls cannot be nested, meaning that <code>releasePage</code>
     * must be called before a subsequent invocation of
     * <code>getPage</code> for the same <code>pageIndex</code>.
     *
     * @param pageNumber Zero-based index of the Page to return.
     * @param user       The object that is asking for the Page to be locked on its behalf.
     * @return The requested Page.
     * @see #releasePage
     */
    public Page getPage(int pageNumber, Object user) {
        if (pageNumber < 0)
            return null;
        Page p = getPagePotentiallyNotInitedByRecursiveIndex(pageNumber);
        if (p != null) {
            // Add Page to cache, and lock it from getting disposed
            library.memoryManager.lock(user, p);
            //p.init();
        }
        return p;
    }

    /**
     * Get the page reference for the specified page number.
     *
     * @param pageNumber zero-based indox of page to find reference of.
     * @return found page reference or null if number could not be resolved.
     */
    public Reference getPageReference(int pageNumber) {
        if (pageNumber < 0)
            return null;
        Page p = getPagePotentiallyNotInitedByRecursiveIndex(pageNumber);
        if (p != null) {
            return p.getPObjectReference();
        }
        return null;
    }

    /**
     * Release the Page that was locked by <code>getPage</code>
     * for user.
     * <p/>
     * ICEpdf uses a caching and memory management mechanism
     * to reduce the CPU, I/O, and time to access a Page,
     * which requires a locking and releasing protocol.
     * Calls to the <code>getPage</code> must be matched with
     * corresponding calls to <code>releasePage</code>.
     * Calls cannot be nested, meaning that <code>releasePage</code>
     * must be called before a subsequent invocation of
     * <code>getPage</code> for the same <code>pageIndex</code>.
     *
     * @param page The Page that was locked
     * @param user The entity for whom the page was locked.
     * @see #getPage
     */
    public void releasePage(Page page, Object user) {
        // Release lock, allowing it to be disposed
        if (library != null && library.memoryManager != null)
            library.memoryManager.release(user, page);
    }

    /**
     * Release the Page that was locked by <code>getPage</code>
     * for user.
     * <p/>
     * ICEpdf uses a caching and memory management mechanism
     * to reduce the CPU, I/O, and time to access a Page,
     * which requires a locking and releasing protocol.
     * Calls to the <code>getPage</code> must be matched with
     * corresponding calls to <code>releasePage</code>.
     * Calls cannot be nested, meaning that <code>releasePage</code>
     * must be called before a subsequent invocation of
     * <code>getPage</code> for the same <code>pageIndex</code>.
     *
     * @param pageNumber The page number of the Page that was locked.
     * @param user       The entity for whom the page was locked.
     * @see #getPage
     */
    public void releasePage(int pageNumber, Object user) {
        Page page = getPagePotentiallyNotInitedByRecursiveIndex(pageNumber);

        // Release lock, allowing it to be disposed
        library.memoryManager.release(user, page);
    }

    /**
     * Returns a summary of the PageTree dictionary values.
     *
     * @return dictionary values.
     */
    public String toString() {
        return "PAGES= " + entries.toString();
    }
}
