package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationSelector;
import org.icepdf.ri.util.AbstractTask;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

/**
 * The AnnotationHandlerPanel list all annotation in a document and grouping them by page association.  The annotation
 * tree lists all types that aren't digital signatures or AcroForm widgets.
 */
@SuppressWarnings("serial")
public class AnnotationHandlerPanel extends AbstractWorkerPanel {

    private static final Logger logger =
            Logger.getLogger(AnnotationHandlerPanel.class.toString());

    // task to complete in separate thread
    private AbstractTask<AnnotationLoaderTask> annotationLoaderTask;

    protected DefaultMutableTreeNode pageTreeNode;

    public AnnotationHandlerPanel(SwingController controller) {
        super(controller);
        nodeSelectionListener = new AnnotationNodeSelectionListener();
        // todo create new cell renderer for annotations.
        cellRenderer = new AnnotationCellRender();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.annotation.tab.title");

        // finally construct the annotation tree of nodes.
        buildUI();
    }

    @Override
    protected void buildUI() {
        super.buildUI();
        buildProgressBar();
    }

    /**
     * Builds the annotation tree.  The process happens on a worker thread as each page in the document
     * must be checked an partially initialized.
     */
    @Override
    protected void buildWorkerTaskUI() {

        // First have to stop any existing validation processes.
        stopWorkerTask();

        if (currentDocument != null) {
            PageTree pageTree = currentDocument.getCatalog().getPageTree();
            // build out the tree
            if (pageTree.getNumberOfPages() > 0) {
                if (!timer.isRunning()) {
                    // show the progress components.
                    progressLabel.setVisible(true);
                    progressBar.setVisible(true);
                    // start a new verification task
                    annotationLoaderTask = new AnnotationLoaderTask(this, controller, messageBundle);
                    workerTask = annotationLoaderTask;
                    progressBar.setMaximum(annotationLoaderTask.getLengthOfTask());
                    // start the task and the timer
                    annotationLoaderTask.getTask().startTask();
                    timer.start();
                }
            }
        }
    }

    void addPageGroup(String nodeLabel) {
        pageTreeNode = new DefaultMutableTreeNode(nodeLabel);
        pageTreeNode.setAllowsChildren(true);
        treeModel.insertNodeInto(pageTreeNode, rootTreeNode, rootTreeNode.getChildCount());
    }

    void addAnnotation(Object annotation) {
        descendFormTree(pageTreeNode, annotation);
        expandAllNodes();
    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param annotationObject root form node.
     */
    private void descendFormTree(DefaultMutableTreeNode currentRoot, Object annotationObject) {
        if (!(annotationObject instanceof AbstractWidgetAnnotation) && annotationObject instanceof Annotation) {
            AnnotationTreeNode annotationTreeNode = new AnnotationTreeNode((Annotation) annotationObject, messageBundle);
            treeModel.insertNodeInto(annotationTreeNode, currentRoot, currentRoot.getChildCount());
        }
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class AnnotationNodeSelectionListener extends NodeSelectionListener {
        @Override
        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof AnnotationTreeNode) {
                    AnnotationTreeNode formNode = (AnnotationTreeNode) node;
                    // on double click, navigate to page and set focus of component.
                    if (e.getClickCount() == 2) {
                        Annotation annotation = formNode.getAnnotation();
                        AnnotationSelector.SelectAnnotationComponent(controller, annotation);
                    }
                }
            }
        }
    }

}
