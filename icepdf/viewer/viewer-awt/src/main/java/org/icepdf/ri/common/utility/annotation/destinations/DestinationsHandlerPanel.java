package org.icepdf.ri.common.utility.annotation.destinations;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.AbstractWorkerPanel;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AnnotationTreeNode;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.annotations.PopupListener;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class DestinationsHandlerPanel extends AbstractWorkerPanel implements PropertyChangeListener {

    // task to complete in separate thread
    private AbstractTask<FindDestinationsTask> findDestinationsTask;

    protected DefaultMutableTreeNode pageTreeNode;

    protected DestinationsPanel parentDestinationsPanel;


    public DestinationsHandlerPanel(SwingController controller, DestinationsPanel parentDestinationsPanel) {
        super(controller);
        this.parentDestinationsPanel = parentDestinationsPanel;

        nodeSelectionListener = new DestinationNodeSelectionListener();
        cellRenderer = new DefaultTreeCellRenderer();// AnnotationCellRender();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.destinations.title");

        // listen for destinations changes.
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);

        // build frame of tree but SigVerificationTask does the work.
        buildUI();

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (PropertyConstants.DESTINATION_ADDED.equals(evt.getPropertyName())) {

        } else if (PropertyConstants.DESTINATION_UPDATED.equals(evt.getPropertyName())) {


        } else if (PropertyConstants.DESTINATION_DELETED.equals(evt.getPropertyName())) {

        }
        tree.repaint();
    }

    public Destination getSelectedDestination() {
        TreePath selectedTreePath = tree.getSelectionPath();
        if (selectedTreePath != null) {
            Object node = selectedTreePath.getLastPathComponent();
//            if (node instanceof AnnotationTreeNode) {
//                AnnotationTreeNode annotationTreeNode = (AnnotationTreeNode) selectedTreePath.getLastPathComponent();
//                return AnnotationSelector.SelectAnnotationComponent(controller, annotationTreeNode.getAnnotation());
//            }
        }
        return null;
    }

    public void refreshDestinationTree() {
        resetTree();
        buildWorkerTaskUI();
    }

    public void setDocument(Document document) {
        super.setDocument(document);
    }

    public void buildUI() {
        super.buildUI();
        // setup validation progress bar and status label
        buildProgressBar();
    }

    public void addDestination(String name, Destination destination) {

    }

    public void setProgressLabel(String label) {
        progressLabel.setText(label);
    }

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
                    if (findDestinationsTask == null) {
                        findDestinationsTask = new FindDestinationsTask(this,
                                controller, messageBundle);
                    }
                    workerTask = findDestinationsTask;
                    progressBar.setMaximum(findDestinationsTask.getLengthOfTask());
                    // start the task and the timer
                    findDestinationsTask.getTask().startTask();
                    timer.start();
                }
            }
        }
    }

    @Override
    public void selectTreeNodeUserObject(Object userObject) {

    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class DestinationNodeSelectionListener extends NodeSelectionListener {

        @Override
        public void setTree(JTree tree) {
            super.setTree(tree);
            // Add listener to components that can bring up popup menus.
            MouseListener popupListener = new PopupListener(contextMenu);
            addMouseListener(popupListener);
        }

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
                }
            }
        }
    }
}
