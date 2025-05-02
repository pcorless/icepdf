package org.icepdf.ri.common.utility.outline;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.Outlines;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.util.BareBonesBrowserLaunch;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ResourceBundle;

/**
 * The OutlinesController class is responsible for managing the Outlines (Bookmarks) JTree.  When editing is enabled
 * a user can drag and drop outline items to new locations in the tree as well as editing the title of the outline item.
 * Expansion state will also be updated when the tree is expanded or collapsed.  Changes will persist when the
 * document is saved.
 */
public class OutlinesController extends MouseAdapter implements TreeModelListener, TreeSelectionListener,
        TreeExpansionListener {

    private final ResourceBundle messageBundle;

    private final SwingController controller;
    private final JTree outlinesTree;

    // match the document permissions for encryption permissions
    private boolean editable = true;

    private static boolean outlineEditingEnabled = Defs.booleanProperty(
            "org.icepdf.viewer.outlineEdit.enabled", false);

    public OutlinesController(final SwingController controller, final JTree outlinesTree) {
        this.controller = controller;
        this.outlinesTree = outlinesTree;
        this.messageBundle = this.controller.getMessageBundle();
        outlinesTree.addMouseListener(this);
        outlinesTree.addTreeSelectionListener(this);
        outlinesTree.addTreeExpansionListener(this);
    }

    public void insertNewOutline() throws InterruptedException {
        Document document = controller.getDocument();
        OutlineItem outline = Outlines.createNewOutlineItem(document.getCatalog().getLibrary());
        outline.setTitle(messageBundle.getString("viewer.utilityPane.outline.contextMenu.new.label"));
        document.getCatalog().createOutlines(outline);
    }

    public void updateOutlineItemState(OutlineItemTreeNode parentNode) {
        if (editable) {
            updateParentCount(parentNode);
            updateParentFirstAndLast(parentNode);
            updateChildNextAndPrevious(parentNode);
        }
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        outlinesTree.setEditable(editable);
        if (editable) {
            outlinesTree.setTransferHandler(new TreeTransferHandler());
        } else {
            outlinesTree.setTransferHandler(null);
        }
    }

    /**
     * Returns true if outline editing is enabled.  The returned value is controlled by the state of the system
     * property org.icepdf.viewer.outlineEdit.enabled.
     *
     * @return true if enabled, false otherwise.
     */
    public static boolean isOutlineEditingEnabled() {
        return outlineEditingEnabled;
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
            outlineItem.setParent(parentNode.getOutlineItem().getPObjectReference());
        }
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        if (editable && (mouseEvent.getButton() == MouseEvent.BUTTON3 || mouseEvent.getButton() == MouseEvent.BUTTON2)) {
            int x = mouseEvent.getX();
            int y = mouseEvent.getY();
            int row = outlinesTree.getRowForLocation(x, y);
            TreePath path = outlinesTree.getPathForRow(row);
            if (path != null) {
                OutlineItemTreeNode node = (OutlineItemTreeNode) path.getLastPathComponent();
                OutlinesPopupMenu contextMenu = new OutlinesPopupMenu(controller, outlinesTree, node);
                contextMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
            }
        }
    }

    @Override
    public void treeExpanded(TreeExpansionEvent treeExpansionEvent) {
        if (editable) {
            TreePath expandedTreePath = treeExpansionEvent.getPath();
            OutlineItemTreeNode node = (OutlineItemTreeNode) expandedTreePath.getLastPathComponent();
            OutlineItem outlineItem = node.getOutlineItem();
            outlineItem.setCount(node.getChildCount());
        }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent treeExpansionEvent) {
        if (editable) {
            TreePath collapsedTreePath = treeExpansionEvent.getPath();
            OutlineItemTreeNode node = (OutlineItemTreeNode) collapsedTreePath.getLastPathComponent();
            OutlineItem outlineItem = node.getOutlineItem();
            outlineItem.setCount(-1);
        }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent treeModelEvent) {
        TreePath insertTreePath = treeModelEvent.getTreePath();
        OutlineItemTreeNode parentNode = (OutlineItemTreeNode) insertTreePath.getLastPathComponent();
        updateOutlineItemState(parentNode);
        // todo could queue treeModelEvents as the memento token for undoing a move.  Will circle back on this some day.
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent treeModelEvent) {
        TreePath insertTreePath = treeModelEvent.getTreePath();
        OutlineItemTreeNode parentNode = (OutlineItemTreeNode) insertTreePath.getLastPathComponent();
        updateOutlineItemState(parentNode);
    }

    @Override
    public void treeNodesChanged(TreeModelEvent treeModelEvent) {
        Object[] children = treeModelEvent.getChildren();
        for (Object child : children) {
            OutlineItemTreeNode node = (OutlineItemTreeNode) child;
            OutlineItem outlineItem = node.getOutlineItem();
            outlineItem.setTitle(node.getUserObject().toString());
        }
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
        int oldTool = controller.getDocumentViewToolMode();
        try {
            // set hour glass
            outlinesTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            controller.setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_WAIT);

            // capture the action if no destination is found and point to the
            // actions destination information
            Destination dest = outlineItem.getDest();
            if (dest == null && outlineItem.getAction() != null) {
                Action action = outlineItem.getAction();
                if (action instanceof URIAction) {
                    BareBonesBrowserLaunch.openURL(
                            ((URIAction) action).getURI());
                }
            }
            // Process the destination information
            if (dest != null) {
                // let the document view controller resolve the destination
                controller.getDocumentViewController().setDestinationTarget(dest);
            }
        } finally {
            // set the icon back to the pointer
            controller.setDisplayTool(oldTool);
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
