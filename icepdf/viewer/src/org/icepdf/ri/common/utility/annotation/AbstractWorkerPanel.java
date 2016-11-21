package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.util.AbstractTask;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * Base class for management of annotation in a document.  This class is used by the annotation, signatures and AcroForm
 * tool handlers to aid in the selection these annotation types in a document.  Each panel has a toolbar that
 * contains the respective tool handlers for creating the family of annotations.
 * <p>
 * The annotation's properties panel are located on the right utility pane and can only show the editable properties
 * for one annotation type at a time.
 */
public abstract class AbstractWorkerPanel extends JPanel {

    protected DocumentViewController documentViewController;
    protected Document currentDocument;
    protected SwingController controller;
    protected ResourceBundle messageBundle;
    protected DocumentViewModel documentViewModel;

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
    // time class to manage gui updates
    protected Timer timer;
    // refresh rate of gui elements
    private static final int REFRESH_TIME = 100;
    // tree node selection listener,
    protected NodeSelectionListener nodeSelectionListener;

    public AbstractWorkerPanel(SwingController controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
    }

    /**
     * Set the current document instance.  The method executed is the abstract {@link #buildWorkerTaskUI()} method which
     * kicks off the tree creation process.  Check {@link #buildWorkerTaskUI()} documentation for the implementing
     * class to see what thread this work is done on.
     *
     * @param document current document, can be null.
     */
    public void setDocument(Document document) {
        this.currentDocument = document;
        documentViewController = controller.getDocumentViewController();
        documentViewModel = documentViewController.getDocumentViewModel();

        // clear the previously loaded annotation tree.
        if (rootTreeNode != null) {
            resetTree();
            // set title
            rootTreeNode.setAllowsChildren(true);
            tree.setRootVisible(true);
        }

        buildWorkerTaskUI();
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
        timer = new Timer(REFRESH_TIME, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timerActionPerformed(e);
            }
        });

        // add progress label
        constraints.insets = new Insets(1, 5, 1, 5);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        progressLabel.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
        addGB(this, progressLabel, 0, 1, 1, 1);

        // add progress
        constraints.insets = new Insets(5, 5, 1, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(this, progressBar, 0, 2, 1, 1);
    }

    /**
     * Build the tree based on the current implementation of cellRenderer and the given worker task
     * found by the calling implementation of {@link #buildWorkerTaskUI()}.
     * Before this method is call the following instance variables should be set:
     * <p/>
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
        tree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
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
        // setup the optional selection listener.
        if (nodeSelectionListener != null) {
            nodeSelectionListener.setTree(tree);
            tree.addMouseListener(nodeSelectionListener);
        }

        this.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        // Build control and tree view GUI
        GridBagLayout layout = new GridBagLayout();
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 5, 1, 5);
        this.setLayout(layout);

        // add the forms tree to scroll pane
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(1, 5, 1, 5);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(this, scrollPane, 0, 1, 1, 1);
        constraints.weighty = 0;
    }

    /**
     * Builds the tree nodes for the given implementation of the worker. .
     */
    protected abstract void buildWorkerTaskUI();

    protected void stopWorkerTask() {
        if (timer != null) {
            timer.stop();
        }
        if (workerTask != null) {
            workerTask.stop();
            while (workerTask.isCurrentlyRunning()) {
                try {
                    Thread.sleep(50L);
                } catch (Exception e) {
                    // intentional
                }
            }
        }
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    public class NodeSelectionListener extends MouseAdapter {

        protected JTree tree;
        protected JPopupMenu contextMenu;

        public void setTree(JTree tree) {
            this.tree = tree;
        }
    }

    /**
     * (Optional) Sets the toolbar for the worker panel.
     *
     * @param annotationUtilityToolbar tool bar to insert above the tree view.
     */
    public void setAnnotationUtilityToolbar(JToolBar annotationUtilityToolbar) {
        addGB(this, annotationUtilityToolbar, 0, 0, 1, 1);
    }

    /**
     * Travers tree model and try and select a node that has a matching user object.
     *
     * @param userObject user object to try and find in the tree
     */
    public void selectTreeNodeUserObject(Object userObject) {
        if (userObject instanceof AnnotationComponent) {
            AnnotationComponent annotationComponent = (AnnotationComponent) userObject;
            if (annotationComponent.getAnnotation() != null) {
                Annotation annotation = annotationComponent.getAnnotation();
                DefaultMutableTreeNode node;
                Enumeration e = rootTreeNode.breadthFirstEnumeration();
                while (e.hasMoreElements()) {
                    node = (DefaultMutableTreeNode) e.nextElement();
                    if (node instanceof AbstractAnnotationTreeNode) {
                        Annotation widgetAnnotation = ((AbstractAnnotationTreeNode) node).getAnnotation();
                        if (widgetAnnotation.getPObjectReference().equals(annotation.getPObjectReference())) {
                            TreeNode[] nodes = treeModel.getPathToRoot(node);
                            TreePath path = new TreePath(nodes);
                            tree.scrollPathToVisible(path);
                            tree.setSelectionPath(path);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Reset the tree for a new document or a new validation.
     */
    protected void resetTree() {
        tree.setSelectionPath(null);
        rootTreeNode.removeAllChildren();
        treeModel.nodeStructureChanged(rootTreeNode);
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

    /**
     * Component clean on on document window tear down.
     */
    public void dispose() {
        this.removeAll();
        controller = null;
        documentViewModel = null;
        currentDocument = null;
        // clean up the timer.
        if (timer != null && timer.isRunning()) timer.stop();
    }

    /**
     * The actionPerformed method in this class is called each time the Timer "goes off".
     */
    protected void timerActionPerformed(ActionEvent evt) {
        progressBar.setValue(workerTask.getCurrentProgress());
        String s = workerTask.getMessage();
        if (s != null) {
            progressLabel.setText(s);
        }
        // update the text and stop the timer when the validation is completed or terminated.
        if (workerTask.isDone() || !workerTask.isCurrentlyRunning()) {
            // update search status
            timer.stop();
            workerTask.stop();

            // update progress bar then hide it.
            progressBar.setValue(progressBar.getMinimum());
            progressBar.setVisible(false);
        }
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
