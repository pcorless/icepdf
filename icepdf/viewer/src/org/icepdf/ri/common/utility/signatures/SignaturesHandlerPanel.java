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
package org.icepdf.ri.common.utility.signatures;

import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AbstractWorkerPanel;
import org.icepdf.ri.common.views.AnnotationSelector;
import org.icepdf.ri.common.views.annotations.signatures.CertificatePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignaturePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationDialog;
import org.icepdf.ri.util.AbstractTask;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SignaturesHandlerPanel lists all the digital signatures in a document as well as the signature fields components
 * that are just placeholders.  A worker thread is used build and verify the signatures in the tree.
 */
@SuppressWarnings("serial")
public class SignaturesHandlerPanel extends AbstractWorkerPanel {

    private static final Logger logger =
            Logger.getLogger(SignaturesHandlerPanel.class.toString());

    // task to complete in separate thread
    private AbstractTask<SigVerificationTask> sigVerificationTask;

    public SignaturesHandlerPanel(SwingController controller) {
        super(controller);
        nodeSelectionListener = new NodeSelectionListener();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.signatures.tab.title");
        cellRenderer = new SignatureCellRender();
        // build frame of tree but SigVerificationTask does the work.
        buildUI();
    }

    public void buildUI() {
        super.buildUI();
        // setup validation progress bar and status label
        buildProgressBar();

    }

    /**
     * Called from the worker task to add a new signature node to the tree.  It is assumed that
     * this call is made from the AWT thread.
     *
     * @param signatureWidgetAnnotation annotation to add to tree.
     */
    void addSignature(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        if (signatureWidgetAnnotation != null) {
            SignatureDictionary signatureDictionary = signatureWidgetAnnotation.getSignatureDictionary();
            // filter any unsigned signer fields.
            if (signatureDictionary.getEntries().size() > 0) {
                SignatureTreeNode tmp = new SignatureTreeNode(signatureWidgetAnnotation, messageBundle);
                tmp.refreshSignerNode();
                tmp.setAllowsChildren(true);
                // insert and expand the root node.
                treeModel.insertNodeInto(tmp, rootTreeNode, rootTreeNode.getChildCount());
            }
        }
    }

    /**
     * Called from the worker task to add a new unsigned signature node to the tree.  It is assumed that
     * this call is made from the AWT thread.
     *
     * @param signatures list off unsigned signatures annotation to add to tree.
     */
    void addUnsignedSignatures(ArrayList<SignatureWidgetAnnotation> signatures) {
        DefaultMutableTreeNode unsignedFieldNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.signatures.tab.certTree.unsigned.label"));
        treeModel.insertNodeInto(unsignedFieldNode, rootTreeNode,
                rootTreeNode.getChildCount());
        for (SignatureWidgetAnnotation signature : signatures) {
            SignatureDictionary signatureDictionary = signature.getSignatureDictionary();
            // filter for only unsigned signer fields.
            if (signatureDictionary.getEntries().size() == 0) {
                DefaultMutableTreeNode field =
                        new DefaultMutableTreeNode(signature.getFieldDictionary().getPartialFieldName());
                field.setAllowsChildren(false);
                unsignedFieldNode.add(field);
            }
        }
        tree.expandPath(new TreePath(rootTreeNode));
        tree.expandPath(new TreePath(unsignedFieldNode));
        revalidate();
    }

    /**
     * Updates the data fields on a signature tree node after verification has taken place.  It is assumed
     * this method is always called from the AWT thread.
     *
     * @param signatureWidgetAnnotation annotation to update
     * @param signatureTreeNode         node that will be updated.
     */
    void updateSignature(SignatureWidgetAnnotation signatureWidgetAnnotation,
                         SignatureTreeNode signatureTreeNode) {
        if (signatureWidgetAnnotation != null) {
            try {
                TreePath treePath = new TreePath(signatureTreeNode.getPath());
                boolean isExpanded = tree.isExpanded(treePath);
                signatureTreeNode.validateSignatureNode();
                signatureTreeNode.refreshSignerNode();
                treeModel.reload();
                if (isExpanded) {
                    tree.expandPath(new TreePath(signatureTreeNode.getPath()));
                }
            } catch (SignatureIntegrityException e) {
                logger.log(Level.WARNING, "Could not build signature node.", e);
            }
        }
    }

    /**
     * Shows the signatureValidationDialog for the given SignatureWidgetAnnotation.  This method should
     * be called from the AWT thread.
     *
     * @param signatureWidgetAnnotation annotation to show the properties of.
     */
    void showSignatureValidationDialog(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        if (signatureWidgetAnnotation != null) {
            // show the dialog
            SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();
            if (fieldDictionary != null) {
                SignatureValidator signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
                if (signatureValidator != null) {
                    new SignatureValidationDialog(controller.getViewerFrame(),
                            messageBundle, signatureWidgetAnnotation, signatureValidator).setVisible(true);
                }
            }

        }
    }

    @Override
    protected void buildWorkerTaskUI() {
        // First have to stop any existing validation processes.
        stopWorkerTask();

        if (this.currentDocument != null &&
                currentDocument.getCatalog().getInteractiveForm() != null) {
            InteractiveForm interactiveForm = currentDocument.getCatalog().getInteractiveForm();
            final ArrayList<SignatureWidgetAnnotation> signatures = interactiveForm.getSignatureFields();
            // build out the tree
            if (signatures.size() > 0) {
                if (!timer.isRunning()) {
                    // show the progress components.
                    progressLabel.setVisible(true);
                    progressBar.setVisible(true);
                    // start a new verification task
                    sigVerificationTask = new SigVerificationTask(this, controller, messageBundle);
                    workerTask = sigVerificationTask;
                    progressBar.setMaximum(sigVerificationTask.getLengthOfTask());
                    // start the task and the timer
                    sigVerificationTask.getTask().verifyAllSignatures();
                    timer.start();
                }
            }
        }
    }

    /**
     * Component clean on on document window tear down.
     */
    public void dispose() {
        super.dispose();
        sigVerificationTask = null;
        timer = null;
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class NodeSelectionListener extends AbstractWorkerPanel.NodeSelectionListener {
        private SignatureTreeNode signatureTreeNode;

        @Override
        public void setTree(JTree tree) {
            super.setTree(tree);
            // add context menu for quick access to validating and signature properties.
            contextMenu = new JPopupMenu();
            JMenuItem validateMenu = new JMenuItem(messageBundle.getString(
                    "viewer.annotation.signature.menu.validateSignature.label"));
            validateMenu.addActionListener(new validationActionListener());
            contextMenu.add(validateMenu);
            contextMenu.add(new JPopupMenu.Separator());
            JMenuItem signaturePropertiesMenu = new JMenuItem(messageBundle.getString(
                    "viewer.annotation.signature.menu.signatureProperties.label"));
            signaturePropertiesMenu.addActionListener(new SignaturesPropertiesActionListener(tree));
            contextMenu.add(signaturePropertiesMenu);
            contextMenu.add(new JPopupMenu.Separator());
            JMenuItem signaturePageNavigationMenu = new JMenuItem(messageBundle.getString(
                    "viewer.annotation.signature.menu.signaturePageNavigation.label"));
            signaturePageNavigationMenu.addActionListener(new SignaturesPageNavigationListener());
            contextMenu.add(signaturePageNavigationMenu);
        }

        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof SignatureCertTreeNode) {
                    // someone clicked on the show certificate node.
                    // create new dialog to show certificate properties.
                    SignatureCertTreeNode selectedSignatureCert = (SignatureCertTreeNode) node;
                    new CertificatePropertiesDialog(controller.getViewerFrame(), messageBundle,
                            selectedSignatureCert.getCertificateChain())
                            .setVisible(true);
                } else if (node instanceof SignatureTreeNode &&
                        (e.getButton() == MouseEvent.BUTTON3 || e.getButton() == MouseEvent.BUTTON2)) {
                    signatureTreeNode = (SignatureTreeNode) node;
                    // show context menu.
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }

            }
        }

        private SignatureTreeNode getSignatureTreeNode() {
            return signatureTreeNode;
        }
    }

    /**
     * Shows the SignatureValidationDialog dialog.
     */
    private class validationActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            if (!sigVerificationTask.isCurrentlyRunning()) {
                // validate the signature and show the summary dialog.
                final SignatureTreeNode signatureTreeNode = ((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode();
                SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
                if (!timer.isRunning()) {
                    // update gui components
                    progressLabel.setVisible(true);
                    progressBar.setVisible(true);
                    progressBar.setMaximum(1);
                    // start the task and the timer
                    sigVerificationTask.getTask().verifySignature(signatureWidgetAnnotation, signatureTreeNode);
                    timer.start();
                }
            }
        }
    }

    /**
     * Navigates to the page the selected signature annotation exists on.
     */
    private class SignaturesPageNavigationListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode() != null) {
                final SignatureTreeNode signatureTreeNode = ((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode();
                SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
                AnnotationSelector.SelectAnnotationComponent(controller, signatureWidgetAnnotation);
            }
        }
    }

    /**
     * Command object for displaying the SignaturePropertiesDialog.
     */
    private class SignaturesPropertiesActionListener implements ActionListener {

        protected JTree tree;

        SignaturesPropertiesActionListener(JTree tree) {
            this.tree = tree;
        }

        public void actionPerformed(ActionEvent e) {
            if (((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode() != null) {
                final SignatureTreeNode signatureTreeNode = ((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode();
                new SignaturePropertiesDialog(controller.getViewerFrame(),
                        messageBundle, signatureTreeNode.getOutlineItem()).setVisible(true);
            }
        }
    }

}
