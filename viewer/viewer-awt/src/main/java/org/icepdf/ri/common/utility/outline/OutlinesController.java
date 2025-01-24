package org.icepdf.ri.common.utility.outline;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.NamedDestinations;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.util.BareBonesBrowserLaunch;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;

public class OutlinesController implements TreeModelListener, TreeSelectionListener {

    private final SwingController viewerController;
    private final JTree outlinesTree;

    // likely going to set this to match the document permissions for encryption permissions
    private boolean editable = true;


    public OutlinesController(final SwingController viewerController, final JTree outlinesTree) {
        this.viewerController = viewerController;
        this.outlinesTree = outlinesTree;
        outlinesTree.addTreeSelectionListener(this);
    }

    // todo add editable tree node text

    // todo tree expansion event to update the branch count, basically toggling -1 for collapsed
    //  and the actual count for expanded.

    public void updateOutlineItemSate(OutlineItemTreeNode parentNode) {
        updateParentCount(parentNode);
        updateParentFirstAndLast(parentNode);
        updateChildNextAndPrevious(parentNode);
    }

    // update the parent's child count, will be off if moving in the same node but will be corrected on the remove event
    private void updateParentCount(OutlineItemTreeNode parentNode) {
        int childCount = parentNode.getChildCount();
        OutlineItem outlineItem = parentNode.getOutlineItem();
        outlineItem.setCount(childCount);
    }

    private void updateParentFirstAndLast(OutlineItemTreeNode parentNode) {
        int childCount = parentNode.getChildCount();
        // get first and last child and update the parent's first and last references
        if (childCount > 0) {
            OutlineItemTreeNode firstChild = (OutlineItemTreeNode) parentNode.getChildAt(0);
            OutlineItemTreeNode lastChild = (OutlineItemTreeNode) parentNode.getChildAt(childCount - 1);
            OutlineItem outlineItem = parentNode.getOutlineItem();
            outlineItem.setFirst(firstChild.getOutlineItem().getPObjectReference());
            outlineItem.setLast(lastChild.getOutlineItem().getPObjectReference());
        }
    }

    private void updateChildNextAndPrevious(OutlineItemTreeNode parentNode) {
        int childCount = parentNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            OutlineItemTreeNode child = (OutlineItemTreeNode) parentNode.getChildAt(i);
            OutlineItem outlineItem = child.getOutlineItem();
            if (i == 0) {
                outlineItem.setPrev(null);
            } else {
                OutlineItemTreeNode previousChild = (OutlineItemTreeNode) parentNode.getChildAt(i - 1);
                outlineItem.setPrev(previousChild.getOutlineItem().getPObjectReference());
            }
            if (i == childCount - 1) {
                // update parent's /Last reference
                outlineItem.setNext(null);
            } else {
                // update parent's /Prev reference
                OutlineItemTreeNode nextChild = (OutlineItemTreeNode) parentNode.getChildAt(i + 1);
                outlineItem.setNext(nextChild.getOutlineItem().getPObjectReference());
            }
        }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent treeModelEvent) {
        System.out.println("treeNodesInserted " + treeModelEvent.toString());
        TreePath insertTreePath = treeModelEvent.getTreePath();
        OutlineItemTreeNode parentNode = (OutlineItemTreeNode) insertTreePath.getLastPathComponent();
        updateOutlineItemSate(parentNode);
        // todo could queue treeModelEvents as the mememto token for undoing a move.
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent treeModelEvent) {
        System.out.println("treeNodesRemoved " + treeModelEvent.toString());
        TreePath insertTreePath = treeModelEvent.getTreePath();
        OutlineItemTreeNode parentNode = (OutlineItemTreeNode) insertTreePath.getLastPathComponent();
        updateOutlineItemSate(parentNode);
    }

    @Override
    public void treeNodesChanged(TreeModelEvent treeModelEvent) {
    }

    @Override
    public void treeStructureChanged(TreeModelEvent treeModelEvent) {
    }

    public void followOutlineItem(OutlineItemTreeNode node) {
        OutlineItem o = node.getOutlineItem();
        followOutlineItem(o);
    }

    /**
     * Disposes controller clearing resources.
     */
    public void dispose() {
        if (outlinesTree.getModel() != null) {
            outlinesTree.getModel().removeTreeModelListener(this);
        }
    }

    /**
     * When the user selects an OutlineItem from the Outlines (Bookmarks) JTree,
     * this displays the relevant target portion of the PDF Document
     *
     * @param outlineItem navigate to this outline item destination.
     */
    private void followOutlineItem(OutlineItem outlineItem) {
        if (outlineItem == null)
            return;
        int oldTool = viewerController.getDocumentViewToolMode();
        try {

            // set hour glass
            outlinesTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            viewerController.setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_WAIT);

            // capture the action if no destination is found and point to the
            // actions destination information
            Destination dest = outlineItem.getDest();
            if (outlineItem.getAction() != null) {
                Action action = outlineItem.getAction();
                if (action instanceof GoToAction) {
                    dest = ((GoToAction) action).getDestination();
                } else if (action instanceof URIAction) {
                    BareBonesBrowserLaunch.openURL(
                            ((URIAction) action).getURI());
                } else {
                    Library library = action.getLibrary();
                    DictionaryEntries entries = action.getEntries();
                    dest = new Destination(library, library.getObject(entries, Destination.D_KEY));
                }
            } else if (dest.getNamedDestination() != null) {
                // building the namedDestination tree can be very time-consuming, so we need
                // update the icons accordingly.
                NamedDestinations namedDestinations = viewerController.getDocument().getCatalog().getDestinations();
                if (namedDestinations != null) {
                    dest = namedDestinations.getDestination(dest.getNamedDestination());
                }
            }

            // Process the destination information
            if (dest == null)
                return;

            // let the document view controller resolve the destination
            viewerController.getDocumentViewController().setDestinationTarget(dest);
        } finally {
            // set the icon back to the pointer
            viewerController.setDisplayTool(oldTool);
            outlinesTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    public void valueChanged(TreeSelectionEvent e) {
        if (outlinesTree == null)
            return;
        TreePath treePath = outlinesTree.getSelectionPath();
        if (treePath == null)
            return;
        OutlineItemTreeNode node = (OutlineItemTreeNode) treePath.getLastPathComponent();
        followOutlineItem(node);

    }
}
