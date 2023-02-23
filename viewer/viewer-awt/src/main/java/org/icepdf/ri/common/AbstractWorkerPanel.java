/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common;


import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.util.ResourceBundle;

/**
 * Base class for management of annotation in a document.  This class is used by the annotation, signatures and AcroForm
 * tool handlers to aid in the selection these annotation types in a document.  Each panel has a toolbar that
 * contains the respective tool handlers for creating the family of annotations.
 * <p>
 * The annotation's properties panel are located on the right utility pane and can only show the editable properties
 * for one annotation type at a time.
 */
public abstract class AbstractWorkerPanel extends JPanel implements MutableDocument {

    protected final Controller controller;
    protected final ResourceBundle messageBundle;

    // main tree of annotation hierarchy
    protected JTree tree;
    protected DefaultMutableTreeNode rootTreeNode;
    protected String rootNodeLabel;
    protected DefaultTreeModel treeModel;
    protected DefaultTreeCellRenderer cellRenderer;

    protected GridBagConstraints constraints;

    // main worker task contains timer and SwingWorker pass off.
    protected AbstractTask workerTask;
    // optional, show progress of the signature validation process.
    protected JProgressBar progressBar;
    // optional, status label for validation progress reporting.
    protected JLabel progressLabel;

    // tree node selection listener,
    protected NodeSelectionListener nodeSelectionListener;

    public AbstractWorkerPanel(Controller controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

    }

    /**
     * Set the current document instance.  The method executed is the abstract {@link #buildWorkerTaskUI()} method which
     * kicks off the tree creation process.  Check {@link #buildWorkerTaskUI()} documentation for the implementing
     * class to see what thread this work is done on.
     */
    @Override
    public void refreshDocumentInstance() {
        // clear the previously loaded annotation tree.
        if (rootTreeNode != null) {
            resetTree();
            // set title
            rootTreeNode.setAllowsChildren(true);
            tree.setRootVisible(true);
        }
        buildWorkerTaskUI();
    }

    @Override
    public void disposeDocument() {
        // stop the task
        if (workerTask != null && !workerTask.isDone()) {
            workerTask.cancel(true);
        }
    }

    /**
     * Optionally builds the progress bar and addes to the layout container just after the JTree and parent scroll pane
     */
    protected void buildProgressBar() {
        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        progressLabel = new JLabel("");
        progressLabel.setVisible(false);

        // add progress label
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        progressLabel.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
        addGB(this, progressLabel, 0, 1, 1, 1);

        // add progress
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(this, progressBar, 1, 1, 1, 1);
    }

    /**
     * Build the tree based on the current implementation of cellRenderer and the given worker task
     * found by the calling implementation of {@link #buildWorkerTaskUI()}.
     * Before this method is call the following instance variables should be set:
     * <ul>
     * <li>nodeSelectionListener - event handler for node clicks</li>
     * <li>cellRenderer - cell render, implements DefaultTreeCellRenderer</li>
     * <li>rootNodeLabel - label for root node</li>
     * </ul>
     */
    protected void buildUI() {
        rootTreeNode = new DefaultMutableTreeNode(rootNodeLabel);
        rootTreeNode.setAllowsChildren(true);
        treeModel = new DefaultTreeModel(rootTreeNode);
        tree = new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.setScrollsOnExpand(true);
        // setup a custom cell render
        if (cellRenderer != null) tree.setCellRenderer(cellRenderer);
        tree.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
        tree.setRowHeight(18);
        tree.setRootVisible(false);
        tree.setExpandsSelectedPaths(true);
        tree.setShowsRootHandles(true);
        tree.setScrollsOnExpand(true);
        // set up the optional selection listener.
        if (nodeSelectionListener != null) {
            nodeSelectionListener.setTree(tree);
            tree.addMouseListener(nodeSelectionListener);
        }

        JScrollPane scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        // Build control and tree view GUI
        this.setLayout(new GridBagLayout());

        // add the forms tree to scroll pane
        constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(this, scrollPane, 0, 0, 2, 1);
    }

    /**
     * Builds the tree nodes for the given implementation of the worker
     */
    protected abstract void buildWorkerTaskUI();

    protected void stopWorkerTask() {
        if (workerTask != null) {
            workerTask.cancel(true);
        }
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    public static class NodeSelectionListener extends MouseAdapter {
        protected JTree tree;
        protected JPopupMenu contextMenu;

        public void setTree(JTree tree) {
            this.tree = tree;
        }
    }

    /**
     * Travers tree model and try and select a node that has a matching user object.
     *
     * @param userObject user object to try and find in the tree
     */
    public abstract void selectTreeNodeUserObject(Object userObject);

    /**
     * Reset the tree for a new document or a new validation.
     */
    protected void resetTree() {
        tree.setSelectionPath(null);
        rootTreeNode.removeAllChildren();
        treeModel.nodeStructureChanged(rootTreeNode);
        treeModel.reload();
    }

    // quick and dirty expand all.
    public void expandAllNodes() {
        int rowCount = tree.getRowCount();
        int i = 0;
        while (i < rowCount) {
            tree.expandRow(i);
            i += 1;
            rowCount = tree.getRowCount();
        }
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public abstract void startProgressControls(int lengthOfTask);

    public abstract void updateProgressControls(int progress);

    public abstract void updateProgressControls(int progress, String label);

    public abstract void updateProgressControls(String label);

    public abstract void endProgressControls();

    /**
     * Utility for getting the document title.
     *
     * @return document title, if no title then a simple search results
     * label is returned;
     */
    public String getDocumentTitle() {
        String documentTitle = null;
        Document document = controller.getDocument();
        if (document != null && document.getInfo() != null) {
            documentTitle = document.getInfo().getTitle();
        }
        if ((documentTitle == null) || (documentTitle.trim().length() == 0)) {
            return null;
        }
        return documentTitle;
    }

    /**
     * GridBag constructor helper
     *
     * @param panel     parent adding component too.
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowSpan of field
     * @param colSpan   colSpan of field.
     */
    protected void addGB(JPanel panel, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        panel.add(component, constraints);
    }

}
