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
package org.icepdf.ri.common.utility.signatures;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.SignatureValidator;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.AbstractWorkerPanel;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.PageComponentSelector;
import org.icepdf.ri.common.views.annotations.signatures.CertificatePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignaturePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationService;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SignaturesHandlerPanel lists all the digital signatures in a document as well as the signature fields components
 * that are just placeholders.  A worker thread is used build and verify the signatures in the tree.
 */
@SuppressWarnings("serial")
public class SignaturesHandlerPanel extends AbstractWorkerPanel {

    private static final Logger logger =
            Logger.getLogger(SignaturesHandlerPanel.class.getName());

    // the tree node of each signed field, to update when its validation finishes.
    private final Map<SignatureWidgetAnnotation, SignatureTreeNode> signatureNodes = new IdentityHashMap<>();

    public SignaturesHandlerPanel(Controller controller) {
        super(controller);
        nodeSelectionListener = new NodeSelectionListener();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.signatures.tab.title");
        cellRenderer = new SignatureCellRender();
        // build frame of tree but VerifySignatureTask does the work.
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
                tmp.setAllowsChildren(true);
                signatureNodes.put(signatureWidgetAnnotation, tmp);
                // insert and expand the root node.
                treeModel.insertNodeInto(tmp, rootTreeNode, rootTreeNode.getChildCount());
                tree.expandPath(new TreePath(rootTreeNode));
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
     * Called from the worker task, on the AWT thread, when a signature's validation has finished:
     * the node swaps "validating" for the result.
     *
     * @param signatureWidgetAnnotation annotation that was validated
     * @param failed                    true if validation failed outright
     */
    void signatureValidated(SignatureWidgetAnnotation signatureWidgetAnnotation, boolean failed) {
        SignatureTreeNode signatureTreeNode = signatureNodes.get(signatureWidgetAnnotation);
        if (signatureTreeNode != null) {
            signatureTreeNode.setValidationFailed(failed);
            refreshNode(signatureTreeNode);
        }
    }

    /**
     * Runs a fresh validation of a signature off the AWT thread; the node shows "validating" until
     * it finishes.  It is assumed this method is always called from the AWT thread.
     *
     * @param signatureWidgetAnnotation annotation to update
     * @param signatureTreeNode         node that will be updated.
     * @return the validation, completing on the AWT thread after the node is updated
     */
    CompletableFuture<SignatureValidator> updateSignature(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                                          SignatureTreeNode signatureTreeNode) {
        SignatureValidator validator = signatureTreeNode.getSignatureValidator();
        if (signatureWidgetAnnotation == null || validator == null) {
            return CompletableFuture.completedFuture(validator);
        }
        signatureTreeNode.setVerifyingSignature(true);
        signatureTreeNode.setValidationFailed(false);
        refreshNode(signatureTreeNode);
        return SignatureValidationService.validate(validator, null).whenComplete((v, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, "Could not validate signature.", SignatureValidationService.cause(failure));
            }
            signatureTreeNode.setVerifyingSignature(false);
            signatureTreeNode.setValidationFailed(failure != null);
            refreshNode(signatureTreeNode);
        });
    }

    /**
     * The tree node of a signed field.
     *
     * @return node, or null if the field isn't listed
     */
    SignatureTreeNode getSignatureTreeNode(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        return signatureNodes.get(signatureWidgetAnnotation);
    }

    /**
     * @return the running or last validation task, or null
     */
    java.util.concurrent.Future<?> getWorkerTask() {
        return workerTask;
    }

    // rebuilds a node from its validator's state and tells the tree, keeping it open if it was.
    private void refreshNode(SignatureTreeNode signatureTreeNode) {
        if (signatureTreeNode.getParent() == null) {
            // a new document replaced the tree.
            return;
        }
        TreePath treePath = new TreePath(signatureTreeNode.getPath());
        boolean isExpanded = tree.isExpanded(treePath);
        signatureTreeNode.refreshSignerNode();
        treeModel.nodeStructureChanged(signatureTreeNode);
        if (isExpanded) {
            tree.expandPath(treePath);
        }
    }

    /**
     * Shows the signatureValidationDialog for the given SignatureWidgetAnnotation.  This method should
     * be called from the AWT thread.
     *
     * @param signatureWidgetAnnotation annotation to show the properties of.
     */
    void showSignatureValidationDialog(SignatureWidgetAnnotation signatureWidgetAnnotation) {
        showSignatureValidationDialog(signatureWidgetAnnotation, null);
    }

    /**
     * Shows the signatureValidationDialog, with progress until the given validation completes.
     *
     * @param signatureWidgetAnnotation annotation to show the properties of.
     * @param validation                a running validation of it, or null
     */
    void showSignatureValidationDialog(SignatureWidgetAnnotation signatureWidgetAnnotation,
                                       CompletableFuture<SignatureValidator> validation) {
        if (signatureWidgetAnnotation != null) {
            // show the dialog
            SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();
            if (fieldDictionary != null) {
                SignatureValidator signatureValidator = signatureWidgetAnnotation.getSignatureValidator();
                if (signatureValidator != null) {
                    SignatureValidationDialog dialog = new SignatureValidationDialog(controller.getViewerFrame(),
                            messageBundle, signatureWidgetAnnotation, signatureValidator);
                    if (validation != null && !validation.isDone()) {
                        dialog.showWhenValidated(validation);
                    }
                    dialog.setVisible(true);
                }
            }
        }
    }

    @Override
    public void selectTreeNodeUserObject(Object userObject) {

    }

    @Override
    protected void buildWorkerTaskUI() {
        // First have to stop any existing validation processes.
        stopWorkerTask();
        signatureNodes.clear();
        endProgressControls();

        Document document = controller.getDocument();
        if (document != null &&
                document.getCatalog().getInteractiveForm() != null) {
            InteractiveForm interactiveForm = document.getCatalog().getInteractiveForm();
            final ArrayList<SignatureWidgetAnnotation> signatures = interactiveForm.getSignatureFields();
            // build out the tree
            if (signatures.size() > 0) {
                // start a new verification task
                workerTask = new VerifyAllSignaturesTask(controller, this, messageBundle);
                workerTask.execute();
            }
        }
    }

    /**
     * Shows the SignatureValidationDialog dialog.
     */
    private class validationActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {

            // validate the signature, off the AWT thread, and show the summary dialog; the dialog
            // and the node both show progress until the validation finishes.
            final SignatureTreeNode signatureTreeNode = ((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode();
            SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
            CompletableFuture<SignatureValidator> validation =
                    updateSignature(signatureWidgetAnnotation, signatureTreeNode);
            showSignatureValidationDialog(signatureWidgetAnnotation, validation);
        }
    }

    @Override
    public void startProgressControls(int lengthOfTask) {
        progressBar.setMaximum(Math.max(1, lengthOfTask));
        progressLabel.setVisible(true);
        progressBar.setVisible(true);
    }

    @Override
    public void updateProgressControls(int progress) {
        progressBar.setValue(progress);
    }

    @Override
    public void updateProgressControls(int progress, String label) {
        progressBar.setValue(progress);
        progressLabel.setText(label);
    }

    @Override
    public void updateProgressControls(String label) {
        progressLabel.setText(label);
    }

    @Override
    public void endProgressControls() {
        progressLabel.setVisible(false);
        progressBar.setVisible(false);
        progressBar.setValue(0);
        progressLabel.setText("");
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
     * Navigates to the page the selected signature annotation exists on.
     */
    private class SignaturesPageNavigationListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode() != null) {
                final SignatureTreeNode signatureTreeNode = ((NodeSelectionListener) nodeSelectionListener).getSignatureTreeNode();
                SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
                PageComponentSelector.SelectAnnotationComponent(controller, signatureWidgetAnnotation);
            }
        }
    }

    /**
     * Command object for displaying the SignaturePropertiesDialog.
     */
    private class SignaturesPropertiesActionListener implements ActionListener {

        protected final JTree tree;

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
