/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

/**
 * <p>This class represents a PDF document outline.  A document outline is
 * an optional component of a PDF document and is accessible from the document's
 * Catalog.  The outline consists of a tree-structured hierarchy of outline items
 * (sometimes called bookmarks) which can be used to display a documents
 * structure to the user.</p>
 * <br>
 * <p>The outlines class does not build a visible structure; it only represents the
 * non-visual structure of the outline.  The OutlineItemTreeNode available in
 * the packageorg.icepdf.core.ri.common provides an example on converting
 * this hierarchy to a Swing JTree.</p>
 *
 * {@link org.icepdf.core.pobjects.OutlineItem}
 * @since 1.0
 */
public class Outlines extends Dictionary {

    public static final Name D_KEY = new Name("D");
    public static final Name COUNT_KEY = new Name("Count");

    /**
     * Creates a new instance of Outlines.
     *
     * @param l document library.
     * @param h Outlines dictionary entries.
     */
    public Outlines(Library l, DictionaryEntries h) {
        super(l, h);
    }

    /**
     * Gets the root OutlineItem.  The root outline item can be traversed to build
     * a visible outline of the hierarchy.
     * <p>
     * Whether a document has an outline at all is decided by {@link Catalog#getOutlines()}, which
     * answers null unless the catalog carries an /Outlines reference.  An outline that exists but
     * has nothing in it is reported by {@link OutlineItem#isEmpty()}, not by a null here.
     *
     * @return root outline item, never null.
     */
    public OutlineItem getRootOutlineItem() {
        // This used to read /Count into an Integer field and return null when that was null, which
        // reads as "no /Count, no outline" but could never mean it, twice over: Library.getInt
        // answers a primitive 0 for a missing key, and Dictionary substitutes an empty
        // DictionaryEntries for a null one, so neither the count nor the entries were ever null.
        // The guard was unreachable.  Worth knowing it never fired, because /Count really is
        // optional - three documents in a 648 document corpus sample carry /First without it - so
        // had it ever meant what it read as, those outlines would have gone missing.
        OutlineItem outlineItem = new OutlineItem(library, entries);
        outlineItem.setPObjectReference(getPObjectReference());
        return outlineItem;
    }

    /**
     * Creates a new instance of an OutlineItem and sets the reference number
     *
     * @param library document library
     * @return new instance of an OutlineItem that is not registered with the state manager.
     */
    public static OutlineItem createNewOutlineItem(Library library) {
        OutlineItem outlineItem = new OutlineItem(library, new DictionaryEntries());
        Reference reference = library.getStateManager().getNewReferenceNumber();
        outlineItem.setPObjectReference(reference);
        return outlineItem;
    }

}
