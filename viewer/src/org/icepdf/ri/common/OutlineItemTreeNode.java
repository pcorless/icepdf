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
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.OutlineItem;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A PDF document may optionally display a document outline on the screen,
 * allowing the user to navigate interactively from one part of the document to
 * another. The outline consists of a tree-structured hierarchy of outline
 * items (sometimes called bookmarks), which serve as a "visual table of
 * contents" to display the document's structure to the user. The user can
 * interactively open and close individual items by clicking them with the
 * mouse. When an item is open, its immediate children in the hierarchy become
 * visible on the screen; each child may in turn be open or closed, selectively
 * revealing or hiding further parts of the hierarchy. When an item is closed,
 * all of its descendants in the hierarchy are hidden. Clicking the text of any
 * visible item with the mouse activates the item, causing the viewer
 * application to jump to a destination or trigger an action associated with
 * the item.
 * An OutlineItemTreeNode object represents the bookmarks or leaves which makes up
 * the actual Outline JTree.
 */
public class OutlineItemTreeNode extends DefaultMutableTreeNode {
    private OutlineItem item;
    private boolean loadedChildren;

    /**
     * Creates a new instance of an OutlineItemTreeNode
     *
     * @param item Contains PDF Outline item data
     */
    public OutlineItemTreeNode(OutlineItem item) {
        super();
        this.item = item;
        loadedChildren = false;

        // build the tree
        setUserObject(item.getTitle());
    }

    public OutlineItem getOutlineItem() {
        return item;
    }

    public void recursivelyClearOutlineItems() {
        item = null;
        if (loadedChildren) {
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                OutlineItemTreeNode node = (OutlineItemTreeNode) getChildAt(i);
                node.recursivelyClearOutlineItems();
            }
        }
    }

    public int getChildCount() {
        ensureChildrenLoaded();
        return super.getChildCount();
    }

    /**
     * Only load children as needed, so don't have to load
     * OutlineItems that the user has not even browsed to
     */
    private void ensureChildrenLoaded() {
        if (!loadedChildren) {
            loadedChildren = true;

            int count = item.getSubItemCount();
            for (int i = 0; i < count; i++) {
                OutlineItem child = item.getSubItem(i);
                OutlineItemTreeNode childTreeNode = new OutlineItemTreeNode(child);
                add(childTreeNode);
            }
        }
    }
}
