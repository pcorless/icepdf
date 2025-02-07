package org.icepdf.ri.common.utility.outline;

import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class OutlinesPopupMenu extends JPopupMenu implements ActionListener {

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

        buildGui();
    }

    private void buildGui() {
        addMenuItem = new JMenuItem("Add");
        addMenuItem.addActionListener(this);
        add(addMenuItem);

        editMenuItem = new JMenuItem("Edit");
        editMenuItem.addActionListener(this);
        add(editMenuItem);

        deleteMenuItem = new JMenuItem("Delete");
        deleteMenuItem.addActionListener(this);
        add(deleteMenuItem);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        Object source = actionEvent.getSource();
        if (source == null) return;
        if (source == addMenuItem) {
            // Add a new child node to the selected node.

            // state is updated in treeNodesInserted() in the controller
            // add new node the state manager.
        } else if (source == editMenuItem) {
            // Edit the selected node.
            new OutlineDialog(controller, node).setVisible(true);

        } else if (source == deleteMenuItem) {
            // Delete the selected node, treeNodesRemoved() will be called in the controller and state will be updated
            DefaultTreeModel model = (DefaultTreeModel) parentTree.getModel();
            model.removeNodeFromParent(node);
            // todo make the node for deletion, and remove any child nodes.
        }
    }
}
