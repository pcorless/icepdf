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
package org.icepdf.ri.common.utility.annotation.markup;


import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.AbstractWorkerPanel;
import org.icepdf.ri.common.utility.annotation.AnnotationCellRender;
import org.icepdf.ri.common.utility.annotation.AnnotationTreeNode;
import org.icepdf.ri.common.views.*;
import org.icepdf.ri.common.views.annotations.*;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.regex.Pattern;

/**
 * The MarkupAnnotationHandlerPanel lists all the markup annotations in a document. A worker thread is used scan
 * the documents for annotations and any found annotations are stored for display.  The MarkupAnnotationHandlerPanel
 * has several control to sort and filter the data returned by this task.
 */
@SuppressWarnings("serial")
public class MarkupAnnotationHandlerPanel extends AbstractWorkerPanel implements PropertyChangeListener {

    // task to complete in separate thread
    private AbstractTask<FindMarkupAnnotationTask> findMarkupAnnotationTask;

    protected DefaultMutableTreeNode pageTreeNode;

    protected MarkupAnnotationPanel parentMarkupAnnotationPanel;

    private Pattern searchPattern;
    private MarkupAnnotationPanel.SortColumn sortType;
    private MarkupAnnotationPanel.FilterSubTypeColumn filterType;
    private MarkupAnnotationPanel.FilterAuthorColumn filterAuthor;
    private Color filterColor;
    private boolean isRegex;
    private boolean isCaseSensitive;

    public MarkupAnnotationHandlerPanel(Controller controller, MarkupAnnotationPanel parentMarkupAnnotationPanel) {
        super(controller);
        this.parentMarkupAnnotationPanel = parentMarkupAnnotationPanel;

        nodeSelectionListener = new AnnotationNodeSelectionListener();
        cellRenderer = new AnnotationCellRender();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.markupAnnotation.title");

        // listen for annotations changes.
        ((DocumentViewControllerImpl) controller.getDocumentViewController()).addPropertyChangeListener(this);

        // build frame of tree but SigVerificationTask does the work.
        buildUI();

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (PropertyConstants.ANNOTATION_DELETED.equals(evt.getPropertyName())) {
            if (evt.getOldValue() instanceof MarkupAnnotationComponent) {
                // find an remove the markup annotation node.
                MarkupAnnotationComponent comp = (MarkupAnnotationComponent) evt.getOldValue();
                MarkupAnnotation markupAnnotation = (MarkupAnnotation) comp.getAnnotation();
                for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                    AnnotationTreeNode node = findAnnotationTreeNode(rootTreeNode.getChildAt(i), markupAnnotation);
                    if (node != null) {
                        TreePath path = new TreePath(node.getPath());
                        DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode)
                                (path.getLastPathComponent());
                        MutableTreeNode parent = (MutableTreeNode) (currentNode.getParent());
                        if (parent != null) {
                            treeModel.removeNodeFromParent(currentNode);
                            break;
                        }
                    }
                }
            }
        } else if (PropertyConstants.ANNOTATION_UPDATED.equals(evt.getPropertyName()) ||
                PropertyConstants.ANNOTATION_SUMMARY_UPDATED.equals(evt.getPropertyName())) {
            if (evt.getNewValue() instanceof PopupAnnotationComponent) {
                // find the markup annotation
                PopupAnnotationComponent comp = (PopupAnnotationComponent) evt.getNewValue();
                PopupAnnotation popupAnnotation = comp.getAnnotation();
                if (popupAnnotation.getParent() != null) {
                    MarkupAnnotation markupAnnotation = popupAnnotation.getParent();
                    // only update root pop annotation comment
                    if (!markupAnnotation.isInReplyTo()) {
                        for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                            AnnotationTreeNode node = findAnnotationTreeNode(rootTreeNode.getChildAt(i), markupAnnotation);
                            if (node != null) {
                                node.applyMessage(markupAnnotation, messageBundle);
                                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
                                break;
                            }
                        }
                    }
                }
            } else if (evt.getNewValue() instanceof FreeTextAnnotationComponent) {
                // find the markup annotation
                FreeTextAnnotationComponent comp = (FreeTextAnnotationComponent) evt.getNewValue();
                MarkupAnnotation markupAnnotation = comp.getAnnotation();
                // only update root pop annotation comment
                if (!markupAnnotation.isInReplyTo()) {
                    for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                        AnnotationTreeNode node = findAnnotationTreeNode(rootTreeNode.getChildAt(i), markupAnnotation);
                        if (node != null) {
                            node.applyMessage(markupAnnotation, messageBundle);
                            ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
                            break;
                        }
                    }
                }
            }
            tree.repaint();
        } else if (PropertyConstants.ANNOTATION_ADDED.equals(evt.getPropertyName())) {
            // rebuild the tree so we get a good sort etc and do  worker thread setup.
            if (evt.getNewValue() instanceof MarkupAnnotationComponent) {
                refreshMarkupTree();
            }
        } else if (PropertyConstants.ANNOTATION_SELECTED.equals(evt.getPropertyName()) ||
                PropertyConstants.ANNOTATION_FOCUS_GAINED.equals(evt.getPropertyName())) {
            // on a focus or selection change we make the respective tree node visible
            if (evt.getNewValue() instanceof MarkupAnnotationComponent) {
                // try and find the node in the tree.
                MarkupAnnotationComponent comp = (MarkupAnnotationComponent) evt.getNewValue();
                MarkupAnnotation markupAnnotation = (MarkupAnnotation) comp.getAnnotation();
                for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                    AnnotationTreeNode node = findAnnotationTreeNode(rootTreeNode.getChildAt(i), markupAnnotation);
                    if (node != null) {
                        TreePath path = new TreePath(node.getPath());
                        tree.setSelectionPath(path);
                        tree.scrollPathToVisible(path);
                        break;
                    }
                }
            }
        } else if (PropertyConstants.ANNOTATION_DESELECTED.equals(evt.getPropertyName()) ||
                PropertyConstants.ANNOTATION_FOCUS_LOST.equals(evt.getPropertyName())) {
//            tree.setSelectionPath(null);
        }
    }

    public AnnotationComponent getSelectedAnnotation() {
        TreePath selectedTreePath = tree.getSelectionPath();
        if (selectedTreePath != null) {
            Object node = selectedTreePath.getLastPathComponent();
            if (node instanceof AnnotationTreeNode) {
                AnnotationTreeNode annotationTreeNode = (AnnotationTreeNode) selectedTreePath.getLastPathComponent();
                return PageComponentSelector.SelectAnnotationComponent(controller, annotationTreeNode.getAnnotation());
            }


        }
        return null;
    }


    protected AnnotationTreeNode findAnnotationTreeNode(TreeNode treeNode, MarkupAnnotation markupAnnotation) {
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            Object currentChild = treeNode.getChildAt(i);
            if (currentChild instanceof AnnotationTreeNode) {
                AnnotationTreeNode annotationTreeNode = (AnnotationTreeNode) currentChild;
                MarkupAnnotation annotation = (MarkupAnnotation) annotationTreeNode.getAnnotation();
                if (markupAnnotation.equals(annotation)) {

                    return annotationTreeNode;
                }
            }
        }
        return null;
    }

    public void sortAndFilterAnnotationData(Pattern searchPattern, MarkupAnnotationPanel.SortColumn sortType,
                                            MarkupAnnotationPanel.FilterSubTypeColumn filterType,
                                            MarkupAnnotationPanel.FilterAuthorColumn filterAuthor,
                                            Color filterColor,
                                            boolean isRegex,
                                            boolean isCaseSensitive) {
        this.searchPattern = searchPattern;
        this.sortType = sortType;
        this.filterType = filterType;
        this.filterAuthor = filterAuthor;
        this.filterColor = filterColor;
        this.isRegex = isRegex;
        this.isCaseSensitive = isCaseSensitive;

        refreshMarkupTree();
    }

    public void refreshMarkupTree() {
        resetTree();
        buildWorkerTaskUI();
    }

    public void buildUI() {
        super.buildUI();
        // setup validation progress bar and status label
        buildProgressBar();
    }

    public void addAnnotation(Annotation annotation, Pattern searchPattern) {
        if (annotation instanceof MarkupAnnotation) {
            descendFormTree(pageTreeNode, annotation, searchPattern);
            expandAllNodes();
        }
    }

    public void setProgressLabel(String label) {
        progressLabel.setText(label);
    }

    @Override
    protected void buildWorkerTaskUI() {
        // First have to stop any existing validation processes.
        stopWorkerTask();
        Document document = controller.getDocument();
        if (document != null) {
            PageTree pageTree = document.getCatalog().getPageTree();
            // build out the tree
            if (pageTree.getNumberOfPages() > 0) {
                if (!timer.isRunning()) {
                    // show the progress components.
                    progressLabel.setVisible(true);
                    progressBar.setVisible(true);
                    // start a new verification task
                    if (findMarkupAnnotationTask == null) {
                        findMarkupAnnotationTask = new FindMarkupAnnotationTask(this,
                                controller, messageBundle);
                    }
                    workerTask = findMarkupAnnotationTask;
                    progressBar.setMaximum(findMarkupAnnotationTask.getLengthOfTask());
                    // start the task and the timer
                    if (filterAuthor != null && sortType != null) {
                        findMarkupAnnotationTask.getTask().startTask(
                                searchPattern, sortType, filterType, filterAuthor, filterColor,
                                isRegex, isCaseSensitive);
                        timer.start();
                    }
                }
            }
        }
    }

    void addPageGroup(String nodeLabel) {
        pageTreeNode = new DefaultMutableTreeNode(nodeLabel);
        pageTreeNode.setAllowsChildren(true);
        treeModel.insertNodeInto(pageTreeNode, rootTreeNode, rootTreeNode.getChildCount());
    }

//    void addAnnotation(Object annotation) {
//        descendFormTree(pageTreeNode, annotation);
//        expandAllNodes();
//    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param annotationObject root form node.
     */
    private void descendFormTree(DefaultMutableTreeNode currentRoot, Object annotationObject, Pattern searchPattern) {
        if (!(annotationObject instanceof AbstractWidgetAnnotation) && annotationObject instanceof Annotation) {
            AnnotationTreeNode annotationTreeNode = new AnnotationTreeNode((Annotation) annotationObject, messageBundle, searchPattern);
            treeModel.insertNodeInto(annotationTreeNode, currentRoot, currentRoot.getChildCount());
        }
    }

    @Override
    public void selectTreeNodeUserObject(Object userObject) {

    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class AnnotationNodeSelectionListener extends NodeSelectionListener {

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
                    // on double click, navigate to page and set focus of component.
                    Annotation annotation = formNode.getAnnotation();
                    AnnotationComponent comp = PageComponentSelector.SelectAnnotationComponent(controller, annotation);
                    if (comp instanceof MarkupAnnotationComponent) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            DocumentViewController documentViewController = controller.getDocumentViewController();
                            // toggle the popup annotations visibility on double click
                            MarkupAnnotationComponent markupAnnotationComponent = (MarkupAnnotationComponent) comp;
                            if (e.getClickCount() == 1) {
                                documentViewController.firePropertyChange(PropertyConstants.ANNOTATION_SELECTED, null,
                                        markupAnnotationComponent);
                                parentMarkupAnnotationPanel.getQuickPaintAnnotationButton().setColor(
                                        markupAnnotationComponent.getAnnotation().getColor(), false);
                            } else if (e.getClickCount() == 2) {
                                markupAnnotationComponent.togglePopupAnnotationVisibility();
                            }
                        }
                        if ((e.getButton() == MouseEvent.BUTTON3 || e.getButton() == MouseEvent.BUTTON2)) {
                            contextMenu = new MarkupAnnotationPopupMenu((MarkupAnnotationComponent) comp,
                                    controller, null, true);
                            contextMenu.show(e.getComponent(), e.getX(), e.getY());
                        }
                        parentMarkupAnnotationPanel.getQuickPaintAnnotationButton().setEnabled(true);
                    } else {
                        parentMarkupAnnotationPanel.getQuickPaintAnnotationButton().setEnabled(false);
                    }
                } else {
                    parentMarkupAnnotationPanel.getQuickPaintAnnotationButton().setEnabled(false);
                }
            }
        }
    }
}
