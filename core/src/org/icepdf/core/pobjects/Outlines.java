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

/**
 * <p>This class represents a PDF document outline.  A document outline is
 * an optional component of a PDF document and is accessible from the document's
 * Catalog.  The outline consists of a tree-structured hierarchy of outline items
 * (sometimes called bookmarks) which can be used to display a documents
 * structure to the user.</p>
 * <p/>
 * <p>The outlines class does not build a visible structure; it only represents the
 * non-visual structure of the outline.  The OutlineItemTreeNode available in
 * the packageorg.icepdf.core.ri.common provides an example on converting
 * this hierarchy to a Swing JTree.</p>
 *
 * @see org.icepdf.ri.common.OutlineItemTreeNode
 * @see org.icepdf.core.pobjects.OutlineItem
 * @since 1.0
 */
public class Outlines extends Dictionary {
    // number of child outline items
    private Integer count;

    // needed for future dispose implementation.
    //private OutlineItem rootOutlineItem;

    /**
     * Creates a new instance of Outlines.
     *
     * @param l document library.
     * @param h Outlines dictionary entries.
     */
    public Outlines(Library l, Hashtable h) {
        super(l, h);
        if (entries != null) {
            count = library.getInt(entries, "Count");
        }
    }

    /**
     * Gets the root OutlineItem.  The root outline item can be traversed to build
     * a visible outline of the hierarchy.
     *
     * @return root outline item.
     */
    public OutlineItem getRootOutlineItem() {
        if (count == null)
            return null;
        return new OutlineItem(library, entries);
    }

    /**
     * Dispose the Outlines.
     */
    public void dispose() {
        // todo  implement a cleanup strategy.
    }


}
