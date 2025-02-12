package org.icepdf.ri.common.utility.outline;

import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.Outlines;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

public class OutlinesPopupMenu extends JPopupMenu implements ActionListener {

    private final ResourceBundle messageBundle;

    private final JTree parentTree;
    private final OutlineItemTreeNode node;
    private JMenuItem addMenuItem;
    private JMenuItem editMenuItem;
    private JMenuItem deleteMenuItem;

    private SwingController controller;

    public OutlinesPopupMenu(SwingController controller, JTree parentTree, OutlineItemTreeNode node) {
        this.parentTree = parentTree;
        this.node = node;
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        buildGui();
    }

    private void buildGui() {
        addMenuItem = new JMenuItem(messageBundle.getString("viewer.utilityPane.outline.contextMenu.add.label"));
        addMenuItem.addActionListener(this);
        add(addMenuItem);

        editMenuItem = new JMenuItem(messageBundle.getString("viewer.utilityPane.outline.contextMenu.edit.label"));
        editMenuItem.addActionListener(this);
        add(editMenuItem);

        deleteMenuItem = new JMenuItem(messageBundle.getString("viewer.utilityPane.outline.contextMenu.delete.label"));
        deleteMenuItem.addActionListener(this);
        add(deleteMenuItem);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        Object source = actionEvent.getSource();
        if (source == null) return;
        if (source == addMenuItem) {
            // Add a new child node to the selected node.
            OutlineItem outlines = Outlines.createNewOutlineItem(controller.getDocument().getCatalog().getLibrary());
            outlines.setTitle(messageBundle.getString("viewer.utilityPane.outline.contextMenu.new.label"));
            OutlineItemTreeNode newNode = new OutlineItemTreeNode(outlines);
            DefaultTreeModel model = (DefaultTreeModel) parentTree.getModel();
            OutlineItemTreeNode parent = (OutlineItemTreeNode) node.getParent();
            model.insertNodeInto(newNode, parent, parent.getIndex(node) + 1);
            new OutlineDialog(controller, parentTree, newNode, true).setVisible(true);
        } else if (source == editMenuItem) {
            // Edit the selected node.
            new OutlineDialog(controller, parentTree, node).setVisible(true);
        } else if (source == deleteMenuItem) {
            // Delete the selected node, treeNodesRemoved() will be called in the controller and state will be updated
            DefaultTreeModel model = (DefaultTreeModel) parentTree.getModel();
            model.removeNodeFromParent(node);
            markNodeForDeletion(node);
        }
    }

    public void markNodeForDeletion(OutlineItemTreeNode node) {
        OutlineItem outlineItem = node.getOutlineItem();
        outlineItem.setDeleted(true);
        for (int i = 0; i < node.getChildCount(); i++) {
            markNodeForDeletion((OutlineItemTreeNode) node.getChildAt(i));
        }
    }
}
