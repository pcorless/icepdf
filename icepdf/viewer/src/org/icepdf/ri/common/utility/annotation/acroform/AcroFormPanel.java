/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.ri.common.utility.annotation.acroform;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationSelector;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The AcroformPanel list all form widget in a document and arranges them using the hierarchy defined by the document,
 * not attemps are mode to flatten the form structure or change order.
 */
@SuppressWarnings("serial")
public class AcroFormPanel extends JPanel {

    private static final Logger logger =
            Logger.getLogger(AcroFormPanel.class.toString());

    protected DocumentViewController documentViewController;

    protected Document currentDocument;

    private SwingController controller;

    protected JScrollPane scrollPane;
    private GridBagConstraints constraints;
    protected DocumentViewModel documentViewModel;

    protected JTree interactiveFieldTree;
    private DefaultMutableTreeNode rootTreeNode;
    private DefaultTreeModel treeModel;
    private JPanel acroFormPanel;

    // message bundle for internationalization
    protected ResourceBundle messageBundle;
    protected NodeSelectionListener nodeSelectionListener;

    public AcroFormPanel(SwingController controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        buildUI();
    }

    private void buildUI() {
        rootTreeNode = new DefaultMutableTreeNode(messageBundle.getString("viewer.utilityPane.acroform.tab.title"));
        rootTreeNode.setAllowsChildren(true);
        treeModel = new DefaultTreeModel(rootTreeNode);
        interactiveFieldTree = new JTree(treeModel);
        interactiveFieldTree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        interactiveFieldTree.setRootVisible(true);
        interactiveFieldTree.setScrollsOnExpand(true);
        // setup a custom cell render
        interactiveFieldTree.setCellRenderer(new AcroFormCellRender());
        // old font was Arial with is no go for linux.
        interactiveFieldTree.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
        interactiveFieldTree.setRowHeight(18);

        interactiveFieldTree.setRootVisible(false);
        interactiveFieldTree.setExpandsSelectedPaths(true);
        interactiveFieldTree.setShowsRootHandles(true);
        interactiveFieldTree.setScrollsOnExpand(true);
        nodeSelectionListener = new NodeSelectionListener(interactiveFieldTree);
        interactiveFieldTree.addMouseListener(nodeSelectionListener);

        this.setLayout(new BorderLayout());
        scrollPane = new JScrollPane(interactiveFieldTree,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        /**
         * Build acroForm GUI
         */
        GridBagLayout layout = new GridBagLayout();
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 5, 1, 5);

        acroFormPanel = new JPanel(layout);
        this.add(acroFormPanel);

        // add the forms tree to scroll pane
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(1, 5, 1, 5);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(acroFormPanel, scrollPane, 0, 1, 1, 1);
        constraints.weighty = 0;
    }

    /**
     * Set the current document instance and starts the validation process of any found signature annotations.
     *
     * @param document current document, can be null.
     */
    public void setDocument(Document document) {

        this.currentDocument = document;
        documentViewController = controller.getDocumentViewController();
        documentViewModel = documentViewController.getDocumentViewModel();

        // clear the previously loaded signature tree.
        if (rootTreeNode != null) {
            resetTree();
            // set title
            rootTreeNode.setAllowsChildren(true);
            interactiveFieldTree.setRootVisible(true);
        }

        if (this.currentDocument != null &&
                currentDocument.getCatalog().getInteractiveForm() != null) {
            InteractiveForm interactiveForm = currentDocument.getCatalog().getInteractiveForm();
            final ArrayList<Object> widgets = interactiveForm.getFields();
            // build out the tree
            if (widgets != null) {
                Library library = document.getCatalog().getLibrary();
                for (Object widget : widgets) {
                    descendFormTree(library, rootTreeNode, widget);
                }
            }
            interactiveFieldTree.expandPath(new TreePath(rootTreeNode));
            revalidate();
        }
    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param formNode root form node.
     */
    private void descendFormTree(Library library, DefaultMutableTreeNode currentRoot, Object formNode) {

        if (formNode instanceof AbstractWidgetAnnotation) {
            AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((AbstractWidgetAnnotation) formNode, messageBundle);
            treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
        } else if (formNode instanceof FieldDictionary) {
            // iterate over the kid's array.
            FieldDictionary child = (FieldDictionary) formNode;
            formNode = child.getKids();
            if (formNode != null) {
                ArrayList kidsArray = (ArrayList) formNode;
                for (Object kid : kidsArray) {
                    if (kid instanceof Reference) {
                        kid = library.getObject((Reference) kid);
                    }
                    if (kid instanceof AbstractWidgetAnnotation) {
                        AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((AbstractWidgetAnnotation) kid, messageBundle);
                        treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
                    } else if (kid instanceof FieldDictionary) {
                        AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((FieldDictionary) kid, messageBundle);
                        treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
                        descendFormTree(library, unsignedFieldNode, kid);
                    }
                }
            }

        }
    }

    public void setAcroFromUtilityToolbar(JToolBar annotationUtilityToolbar) {
        addGB(acroFormPanel, annotationUtilityToolbar, 0, 0, 1, 1);
    }

    /**
     * Reset the tree for a new document or a new validation.
     */
    protected void resetTree() {
        interactiveFieldTree.setSelectionPath(null);
        rootTreeNode.removeAllChildren();
        treeModel.nodeStructureChanged(rootTreeNode);
    }

    /**
     * Component clean on on document window tear down.
     */
    public void dispose() {
        this.removeAll();
        controller = null;
        documentViewModel = null;
        currentDocument = null;
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    class NodeSelectionListener extends MouseAdapter {
        protected JTree tree;
        protected JPopupMenu contextMenu;

        NodeSelectionListener(JTree tree) {
            this.tree = tree;
        }

        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof AcroFormTreeNode) {
                    AcroFormTreeNode formNode = (AcroFormTreeNode) node;
                    // on double click, navigate to page and set focus of component.
                    if (e.getClickCount() == 2) {
                        AbstractWidgetAnnotation widgetAnnotation = formNode.getWidgetAnnotation();
                        AnnotationSelector.SelectAnnotationComponent(controller, widgetAnnotation);
                    }
                }
            }
        }

        public DefaultMutableTreeNode getFieldTreeNode() {
            return getFieldTreeNode();
        }
    }

    /**
     * GridBag constructor helper
     *
     * @param panel     parent adding component too.
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowspane of field
     * @param colSpan   colspane of field.
     */
    private void addGB(JPanel panel, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        panel.add(component, constraints);
    }
}
