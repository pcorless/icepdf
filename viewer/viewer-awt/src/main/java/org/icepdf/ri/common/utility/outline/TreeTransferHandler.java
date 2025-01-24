package org.icepdf.ri.common.utility.outline;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.*;

class TreeTransferHandler extends TransferHandler {
    DataFlavor nodesFlavor;
    DataFlavor[] flavors = new DataFlavor[1];
    OutlineItemTreeNode[] nodesToRemove;

    public TreeTransferHandler() {
        try {
            String mimeType =
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + OutlineItemTreeNode[].class.getName() +
                            "\"";
            nodesFlavor = new DataFlavor(mimeType);
            flavors[0] = nodesFlavor;
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFound: " + e.getMessage());
        }
    }

    public boolean canImport(TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        support.setShowDropLocation(true);
        if (!support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }
        // Do not allow a drop on the drag source selections.
        JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
        JTree tree = (JTree) support.getComponent();
        int dropRow = tree.getRowForPath(dropLocation.getPath());
        int[] selRows = tree.getSelectionRows();
        for (int selRow : Objects.requireNonNull(selRows)) {
            if (selRow == dropRow) {
                return false;
            }
            OutlineItemTreeNode treeNode =
                    (OutlineItemTreeNode) tree.getPathForRow(selRow).getLastPathComponent();
            for (TreeNode offspring : Collections.list(treeNode.depthFirstEnumeration())) {
                int offspringRow = tree.getRowForPath(new TreePath(((OutlineItemTreeNode) offspring).getPath()));
                if (offspringRow >= 0 && offspringRow == dropRow) {
                    return false;
                }
            }
        }
        return true;
    }

    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) {
            return null;
        }
        // Make up a node array of copies for transfer and
        // another for/of the nodes that will be removed in
        // exportDone after a successful drop.
        List<OutlineItemTreeNode> copies = new ArrayList<>();
        List<OutlineItemTreeNode> toRemove = new ArrayList<>();
        OutlineItemTreeNode firstNode = (OutlineItemTreeNode) paths[0].getLastPathComponent();
        HashSet<TreeNode> doneItems = new LinkedHashSet<>(paths.length);
        OutlineItemTreeNode copy = copy(firstNode, doneItems, tree);
        copies.add(copy);
        toRemove.add(firstNode);
        for (int i = 1; i < paths.length; i++) {
            OutlineItemTreeNode next = (OutlineItemTreeNode) paths[i].getLastPathComponent();
            if (doneItems.contains(next)) {
                continue;
            }
            // Do not allow higher level nodes to be added to list.
            if (next.getLevel() < firstNode.getLevel()) {
                break;
            } else if (next.getLevel() > firstNode.getLevel()) {  // child node
                copy.add(copy(next, doneItems, tree));
                // node already contains child
            } else {                                        // sibling
                copies.add(copy(next, doneItems, tree));
                toRemove.add(next);
            }
            doneItems.add(next);
        }
        OutlineItemTreeNode[] nodes = copies.toArray(new OutlineItemTreeNode[0]);
        nodesToRemove = toRemove.toArray(new OutlineItemTreeNode[0]);
        return new TreeTransferHandler.NodesTransferable(nodes);
    }

    private OutlineItemTreeNode copy(OutlineItemTreeNode node, HashSet<TreeNode> doneItems, JTree tree) {
        OutlineItemTreeNode copy = new OutlineItemTreeNode(node);
        doneItems.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            copy.add(copy((OutlineItemTreeNode) node.getChildAt(i), doneItems, tree));
        }
        int row = tree.getRowForPath(new TreePath(copy.getPath()));
        tree.expandRow(row);
        return copy;
    }

    protected void exportDone(JComponent source, Transferable data, int action) {
        if ((action & MOVE) == MOVE) {
            JTree tree = (JTree) source;
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            // Remove nodes saved in nodesToRemove in createTransferable.
            for (OutlineItemTreeNode OutlineItemTreeNode : nodesToRemove) {
                model.removeNodeFromParent(OutlineItemTreeNode);
            }
        }
    }

    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        // Extract transfer data.
        OutlineItemTreeNode[] nodes = null;
        try {
            Transferable t = support.getTransferable();
            nodes = (OutlineItemTreeNode[]) t.getTransferData(nodesFlavor);
        } catch (UnsupportedFlavorException ufe) {
            System.out.println("UnsupportedFlavor: " + ufe.getMessage());
        } catch (java.io.IOException ioe) {
            System.out.println("I/O error: " + ioe.getMessage());
        }
        // Get drop location info.
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        int childIndex = dl.getChildIndex();
        TreePath dest = dl.getPath();
        OutlineItemTreeNode parent = (OutlineItemTreeNode) dest.getLastPathComponent();
        JTree tree = (JTree) support.getComponent();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        // Configure for drop mode.
        int index = childIndex;    // DropMode.INSERT
        if (childIndex == -1) {     // DropMode.ON
            index = parent.getChildCount();
        }
        // Add data to model.
        for (int i = 0; i < Objects.requireNonNull(nodes).length; i++) {
            model.insertNodeInto(nodes[i], parent, index++);
        }
        return true;
    }

    public String toString() {
        return getClass().getName();
    }

    public class NodesTransferable implements Transferable {
        OutlineItemTreeNode[] nodes;

        public NodesTransferable(OutlineItemTreeNode[] nodes) {
            this.nodes = nodes;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return nodes;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return nodesFlavor.equals(flavor);
        }
    }
}