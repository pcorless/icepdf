/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.examples.jsf.viewer.view;

import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.util.Library;

import javax.swing.tree.DefaultMutableTreeNode;

import com.icesoft.faces.component.tree.IceUserObject;

import java.util.HashMap;

/**
 * PDF document outline which can be used by the ice:tree component.
 * 
 * @since 3.0
 */
public class OutlineItemTreeNode extends DefaultMutableTreeNode {

    private OutlineItem item;
    private boolean loadedChildren;
    private PageTree pageTree;

    /**
     * Creates a new instance of an OutlineItemTreeNode
     *
     * @param item Contains PDF Outline item data
     */
    public OutlineItemTreeNode(PageTree pageTree, OutlineItem item) {
        super();
        this.item = item;
        loadedChildren = false;
        this.pageTree = pageTree;

        // build the tree
        NodeUserObject tmp = new NodeUserObject(this.pageTree, this);
        // set callback
        setUserObject(tmp);
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
                OutlineItemTreeNode childTreeNode =
                        new OutlineItemTreeNode(pageTree, child);
                add(childTreeNode);
            }
        }
    }

    public class NodeUserObject extends IceUserObject {

        private int goToPage;

        public NodeUserObject(PageTree pageTree, OutlineItemTreeNode outlineItemTreeNode){
            super(outlineItemTreeNode);

            // append the destination page number
            if (outlineItemTreeNode.getOutlineItem().getDest() != null) {
                goToPage = pageTree.getPageNumber(
                        outlineItemTreeNode.getOutlineItem().getDest()
                                .getPageReference());
            }
            else if (outlineItemTreeNode.getOutlineItem().getAction() != null) {
                OutlineItem item  = outlineItemTreeNode.getOutlineItem();
                Destination dest;
                if (item.getAction() != null) {
                    Action action = item.getAction();
                    if (action instanceof GoToAction) {
                        dest = ((GoToAction) action).getDestination();
                    }  else {
                        Library library = action.getLibrary();
                        HashMap entries = action.getEntries();
                        dest = new Destination(library, library.getObject(entries, Destination.D_KEY));
                    }
                    goToPage = pageTree.getPageNumber(dest.getPageReference());
                }
            }

            // set title
            setText(outlineItemTreeNode.getOutlineItem().getTitle());

            // setup not state.
            setLeafIcon("tree_document.gif");
            setBranchContractedIcon("tree_document.gif");
            setBranchExpandedIcon("tree_document.gif");
            
            // is item a node or a leaf.
            if (outlineItemTreeNode.getOutlineItem().getSubItemCount() > 0){
                setLeaf(false);
            }
            else{
                setLeaf(true);
            }

        }

        public int getGoToPage() {
            return goToPage;
        }

        public void setGoToPage(int goToPage) {
            this.goToPage = goToPage;
        }
    }

}
