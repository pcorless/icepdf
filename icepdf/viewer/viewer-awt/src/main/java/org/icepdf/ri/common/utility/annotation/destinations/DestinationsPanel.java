/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.annotation.destinations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.NameJTree;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * DestinationsPanel show the destinations that are associated with the Names tree in the Document catalog. The ability
 * to manipulate this name tree is important when creating annotation and assigning an accompanying action or in the
 * case of link annotation's destination.
 * <p>
 * This panel displays all the names in the tree and allows for basic updates, creation and deletion.  The idea is that
 * a user can add destinations as they see fit and then go back to the annotation properties dialog/pane and assingn
 * an action or link annotation destination.
 *
 * @since 6.3
 */
public class DestinationsPanel extends JPanel
        implements MutableDocument, TreeSelectionListener, MouseListener, ActionListener {

    private static final Logger logger =
            Logger.getLogger(DestinationsPanel.class.toString());

    // layouts constraint
    protected GridBagConstraints constraints;

    private PropertiesManager propertiesManager;
    private Preferences preferences;
    private SwingController controller;
    private ResourceBundle messageBundle;

    private Document document;
    private NameJTree nameJTree;

    private JPopupMenu contextMenu;
    private JMenuItem deleteNameTreeNode;
    private JMenuItem editNameTreeNode;

    public DestinationsPanel(SwingController controller, PropertiesManager propertiesManager) {
        messageBundle = controller.getMessageBundle();
        preferences = propertiesManager.getPreferences();
        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        this.controller = controller;
        this.propertiesManager = propertiesManager;

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;

        nameJTree = new NameJTree();
        nameJTree.addTreeSelectionListener(this);
        nameJTree.addMouseListener(this);
        JScrollPane scrollPane = new JScrollPane(nameJTree);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        contextMenu = new JPopupMenu();
        editNameTreeNode = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.destinations.view.contextMenu.edit.label"));
        editNameTreeNode.addActionListener(this);
        contextMenu.add(editNameTreeNode);
        contextMenu.addSeparator();
        deleteNameTreeNode = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.destinations.view.contextMenu.delete.label"));
        deleteNameTreeNode.addActionListener(this);
        contextMenu.add(deleteNameTreeNode);

        addGB(this, scrollPane, 0, 0, 1, 1);

        setFocusable(true);

        addPropertyChangeListener(PropertyConstants.DESTINATION_UPDATED, controller);
    }

    public void refreshNameTree(Object node) {
        Names names = document.getCatalog().getNames();
        if (names != null && names.getDestsNameTree() != null) {
            NameTree nameTree = names.getDestsNameTree();
            if (nameTree != null) {
                nameJTree.setModel(new DefaultTreeModel(new NameTreeNode(nameTree.getRoot(), messageBundle)));
                nameJTree.setRootVisible(true);
                nameJTree.setShowsRootHandles(true);
                // try to expand back to the same path
                if (node instanceof NameTreeNode) {
                    NameTreeNode nameTreeNode = (NameTreeNode) node;
                    // find and select a node with the same node.
                    Enumeration e = ((NameTreeNode) nameJTree.getModel().getRoot()).depthFirstEnumeration();
                    while (e.hasMoreElements()) {
                        NameTreeNode currentNode = (NameTreeNode) e.nextElement();
                        if (currentNode.getName() != null &&
                                currentNode.getName().toString().equals(
                                        nameTreeNode.getName().toString())) {
                            // expand the node
                            nameJTree.setSelectionPath(new TreePath(currentNode.getPath()));
                        }
                    }

                }
            }
        }
    }

    @Override
    public void setDocument(Document document) {
        this.document = document;
        refreshNameTree(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        TreePath selectedTreePath = nameJTree.getSelectionPath();
        if (selectedTreePath != null) {
            NameTreeNode nameTreeNode = (NameTreeNode) selectedTreePath.getLastPathComponent();
            if (source == editNameTreeNode) {
                NameTreeEditDialog nameTreeEditDialog = new NameTreeEditDialog(controller, nameTreeNode);
                nameTreeEditDialog.setVisible(true);
            } else if (source == deleteNameTreeNode) {
                Catalog catalog = controller.getDocument().getCatalog();
                catalog.deleteNamedDestination(nameTreeNode.getName().toString());
                controller.getDocumentViewController().firePropertyChange(PropertyConstants.DESTINATION_DELETED,
                        nameTreeNode, null);
                ((DefaultTreeModel) nameJTree.getModel()).removeNodeFromParent(nameTreeNode);
            }
        }
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {

    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        int row = nameJTree.getRowForLocation(x, y);
        TreePath path = nameJTree.getPathForRow(row);
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof NameTreeNode) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    // on double click we navigate to the nameTree's node
                    NameTreeNode selectedNode = (NameTreeNode) node;
                    if (selectedNode.getReference() != null && selectedNode.isLeaf()) {
                        Object tmp = selectedNode.getReference();
                        Library library = controller.getDocument().getCatalog().getLibrary();
                        if (tmp instanceof Reference) {
                            tmp = library.getObject((Reference) tmp);
                        }
                        Destination dest = new Destination(library, tmp);
                        controller.getDocumentViewController().setDestinationTarget(dest);
                    }
                } else if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
                    NameTreeNode selectedNode = (NameTreeNode) node;
                    nameJTree.setSelectionPath(new TreePath(selectedNode.getPath()));
                    if (selectedNode.getReference() != null && selectedNode.isLeaf()) {
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    /**
     * Gridbag constructor helper
     *
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan
     * @param colSpan
     */
    protected void addGB(JPanel layout, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
