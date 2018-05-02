/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.Names;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.NameJTree;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AnnotationPanel;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.destinations.DestinationComponent;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
        implements MutableDocument, TreeSelectionListener, MouseListener, ActionListener, PropertyChangeListener {

    private static final Logger logger =
            Logger.getLogger(DestinationsPanel.class.toString());

    // layouts constraint
    protected GridBagConstraints constraints;

    private PropertiesManager propertiesManager;
    private Preferences preferences;
    private org.icepdf.ri.common.views.Controller controller;
    private ResourceBundle messageBundle;

    private AnnotationPanel parentPanel;
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
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object newValue = evt.getNewValue();
        Object oldValue = evt.getOldValue();
        String propertyName = evt.getPropertyName();
        if (propertyName.equals(PropertyConstants.DESTINATION_SELECTED) ||
                propertyName.equals(PropertyConstants.DESTINATION_FOCUS_GAINED)) {
            DestinationComponent destinationComponent = (DestinationComponent) newValue;
            if (destinationComponent != null &&
                    destinationComponent.getDestination() != null) {
                // expand the tree to selected the given destination
                selectedDestinationComponentPath(destinationComponent);
            }
        }
    }

    public void setParentPanel(AnnotationPanel parentPanel) {
        this.parentPanel = parentPanel;
    }

    public void removeNameTreeNode(Destination destination) {
        Enumeration e = ((NameTreeNode) nameJTree.getModel().getRoot()).depthFirstEnumeration();
        while (e.hasMoreElements()) {
            NameTreeNode currentNode = (NameTreeNode) e.nextElement();
            if (currentNode.getName() != null &&
                    currentNode.getName().toString().equals(destination.getNamedDestination())) {
                // remove the node
                ((DefaultTreeModel) nameJTree.getModel()).removeNodeFromParent(currentNode);
            }
        }
    }

    public void refreshNameTree(Object node) {
        Names names = controller.getDocument().getCatalog().getNames();
        if (names != null && names.getDestsNameTree() != null) {
            NameTree nameTree = names.getDestsNameTree();
            if (nameTree != null) {
                nameJTree.setModel(new DefaultTreeModel(new NameTreeNode(nameTree.getRoot(), messageBundle)));
                nameJTree.setRootVisible(true);
                nameJTree.setShowsRootHandles(true);
                // try to expand back to the same path
                if (node instanceof NameTreeNode) {
                    NameTreeNode nameTreeNode = (NameTreeNode) node;
                    // find and select a node with the same node.)
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
        } else {
            nameJTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(messageBundle.getString(
                    "viewer.utilityPane.action.dialog.goto.nameTree.root.label"))));
            nameJTree.setRootVisible(true);
            nameJTree.setShowsRootHandles(true);
        }
    }

    public void selectedDestinationComponentPath(DestinationComponent node) {
        Names names = controller.getDocument().getCatalog().getNames();
        if (names != null && names.getDestsNameTree() != null) {
            // find and select a node with the same node.)
            Enumeration e = ((NameTreeNode) nameJTree.getModel().getRoot()).depthFirstEnumeration();
            while (e.hasMoreElements()) {
                NameTreeNode currentNode = (NameTreeNode) e.nextElement();
                if (currentNode.getName() != null &&
                        currentNode.getName().toString().equals(
                                node.getDestination().getNamedDestination())) {
                    // expand the node
                    selectDestinationPath(new TreePath(currentNode.getPath()));
                }
            }
        }
    }

    public void selectDestinationPath(TreePath treePath) {
        nameJTree.setSelectionPath(treePath);
        nameJTree.scrollPathToVisible(treePath);
    }

    @Override
    public void refreshDocumentInstance() {
        refreshNameTree(null);
    }

    @Override
    public void disposeDocument() {

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
                Destination destination = new Destination(controller.getDocument().getCatalog().getLibrary(),
                        nameTreeNode.getReference());
                destination.setNamedDestination(nameTreeNode.getName().toString());
                controller.getDocumentViewController().firePropertyChange(PropertyConstants.DESTINATION_DELETED,
                        destination, null);
            }
        }
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (nameJTree == null)
            return;
        TreePath treePath = nameJTree.getSelectionPath();
        if (treePath == null)
            return;

        Object node = treePath.getLastPathComponent();
        if (node instanceof NameTreeNode) {
            controller.followDestinationItem((NameTreeNode) node);
        }
        // return focus so that dropDownArrowButton keys will work on list
        nameJTree.requestFocus();
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
                        controller.followDestinationItem((NameTreeNode) node);
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
