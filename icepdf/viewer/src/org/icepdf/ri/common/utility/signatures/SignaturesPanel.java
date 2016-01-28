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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.acroform.SignatureFieldDictionary;
import org.icepdf.core.pobjects.acroform.signature.Validator;
import org.icepdf.core.pobjects.acroform.signature.exceptions.SignatureIntegrityException;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.signatures.SignaturePropertiesDialog;
import org.icepdf.ri.common.views.annotations.signatures.SignatureValidationDialog;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The SignaturesPanel lists all the digital signatures in a document as well as the signature fields components
 * that are just placeholders.
 */
public class SignaturesPanel extends JPanel {

    private static final Logger logger =
            Logger.getLogger(SignaturesPanel.class.toString());

    protected DocumentViewController documentViewController;

    protected Document currentDocument;

    private SwingController controller;

    protected DefaultMutableTreeNode nodes;
    protected DocumentViewModel documentViewModel;
    // message bundle for internationalization
    ResourceBundle messageBundle;
    protected NodeSelectionListener nodeSelectionListener;

    public SignaturesPanel(SwingController controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
    }

    private void buildUI() {
        JTree tree = new SignaturesTree(nodes);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        nodeSelectionListener = new NodeSelectionListener(tree);
        tree.addMouseListener(nodeSelectionListener);

        this.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        this.add(scrollPane,
                BorderLayout.CENTER);
    }

    public void setDocument(Document document) {
        this.currentDocument = document;
        documentViewController = controller.getDocumentViewController();
        documentViewModel = documentViewController.getDocumentViewModel();

        if (this.currentDocument != null &&
                currentDocument.getCatalog().getInteractiveForm() != null) {
            InteractiveForm interactiveForm = currentDocument.getCatalog().getInteractiveForm();
            ArrayList<Object> fields = interactiveForm.getFields();
            // capture the document signatures.
            ArrayList<SignatureWidgetAnnotation> signatures = new ArrayList<SignatureWidgetAnnotation>();
            if (fields != null) {
                for (Object field : fields) {
                    if (field instanceof SignatureWidgetAnnotation) {
                        signatures.add((SignatureWidgetAnnotation) field);
                    }
                }
            }
            // build out the tree
            if (signatures.size() > 0) {
                nodes = new DefaultMutableTreeNode(messageBundle.getString("viewer.utilityPane.signatures.tab.title"));
                nodes.setAllowsChildren(true);
                buildTree(signatures);
                buildUI();
            }
        } else {
            // tear down the old container.
            this.removeAll();
        }
    }

    @SuppressWarnings("unchecked")
    public void buildTree(ArrayList<SignatureWidgetAnnotation> signatures) {
        SignatureTreeNode tmp;
        boolean foundUnsignedSignatureFields = false;
        boolean signaturesCoverDocument = false;
        // add the base certificateChain.
        for (SignatureWidgetAnnotation signature : signatures) {
            SignatureDictionary signatureDictionary = signature.getSignatureDictionary();
            // filter any unsigned singer fields.
            if (signatureDictionary.getEntries().size() > 0) {
                tmp = new SignatureTreeNode(signature, messageBundle);
                tmp.setAllowsChildren(true);
                nodes.add(tmp);
                // looking for one match as this will indicate the signature(s) cover the whole document,  if not
                // then we have a document that has had modification but hasn't been signed for.
                if (!tmp.getValidator().isDocumentDataModified()) {
                    signaturesCoverDocument = true;
                }
            } else if (!foundUnsignedSignatureFields) {
                foundUnsignedSignatureFields = true;
            }
        }
        // we want to make sure we show the correct node icons, so we'll iterate of the nodes and set the icon
        // state.
        Enumeration treePath = nodes.breadthFirstEnumeration();
        Object path;
        while (treePath.hasMoreElements()) {
            path = treePath.nextElement();
            if (path instanceof SignatureTreeNode) {
                ((SignatureTreeNode) path).getValidator().setSignaturesCoverDocumentLength(signaturesCoverDocument);
                // update labels.
                ((SignatureTreeNode) path).refreshSignerNode();
            }
        }


        // todo add permission data from as new node:
        //    - field dictionary's /Lock field if present
        //    - look at Signature Reference Dictionary for /Transform Method
        // add the unsigned singer fields to there own root node.
        if (foundUnsignedSignatureFields) {
            DefaultMutableTreeNode unsignedFieldNode = new DefaultMutableTreeNode(
                    messageBundle.getString("viewer.utilityPane.signatures.tab.certTree.unsigned.label"));
            nodes.add(unsignedFieldNode);
            for (SignatureWidgetAnnotation signature : signatures) {
                SignatureDictionary signatureDictionary = signature.getSignatureDictionary();
                // filter any unsigned singer fields.
                if (signatureDictionary.getEntries().size() == 0) {
                    DefaultMutableTreeNode field =
                            new DefaultMutableTreeNode(signature.getFieldDictionary().getPartialFieldName());
                    field.setAllowsChildren(false);
                    unsignedFieldNode.add(field);
                }
            }
        }
    }

    public void dispose() {
        this.removeAll();
    }

    class NodeSelectionListener extends MouseAdapter {
        protected JTree tree;
        protected JPopupMenu contextMenu;
        private SignatureTreeNode signatureTreeNode;

        NodeSelectionListener(JTree tree) {
            this.tree = tree;

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

        public SignatureTreeNode getSignatureTreeNode() {
            return signatureTreeNode;
        }
    }

    /**
     * Shows the SignatureValidationDialog dialog.
     */
    class validationActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            // validate the signature and show the summary dialog.
            final SignatureTreeNode signatureTreeNode = nodeSelectionListener.getSignatureTreeNode();
            SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
            SignatureFieldDictionary fieldDictionary = signatureWidgetAnnotation.getFieldDictionary();
            if (fieldDictionary != null) {
                Validator validator = signatureTreeNode.getValidator();
                if (validator != null) {
                    try {
                        validator.validate();
                        new SignatureValidationDialog(controller.getViewerFrame(),
                                messageBundle, signatureWidgetAnnotation, validator).setVisible(true);
                    } catch (SignatureIntegrityException e1) {
                        logger.fine("Error validating annotation " + signatureWidgetAnnotation.toString());
                    }
                }
            }
        }
    }

    class SignaturesPageNavigationListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (nodeSelectionListener.getSignatureTreeNode() != null) {
                final SignatureTreeNode signatureTreeNode = nodeSelectionListener.getSignatureTreeNode();
                SignatureWidgetAnnotation signatureWidgetAnnotation = signatureTreeNode.getOutlineItem();
                // turn out the parent is seldom used correctly and generally just points to page zero.
//                Page parentPage = signatureWidgetAnnotation.getPage();
                Document document = controller.getDocument();
                int pages = controller.getPageTree().getNumberOfPages();
                boolean found = false;
                for (int i = 0; i < pages && !found; i++) {
                    // check is page's annotation array for a matching reference.
                    ArrayList<Reference> annotationReferences = document.getPageTree().getPage(i).getAnnotationReferences();
                    if (annotationReferences != null) {
                        for (Reference reference : annotationReferences) {
                            if (reference.equals(signatureWidgetAnnotation.getPObjectReference())) {
                                controller.showPage(i);
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Command object for displaying the SignaturePropertiesDialog.
     */
    class SignaturesPropertiesActionListener implements ActionListener {
        protected JTree tree;

        public SignaturesPropertiesActionListener(JTree tree) {
            this.tree = tree;
        }

        public void actionPerformed(ActionEvent e) {
            if (nodeSelectionListener.getSignatureTreeNode() != null) {
                final SignatureTreeNode signatureTreeNode = nodeSelectionListener.getSignatureTreeNode();
                new SignaturePropertiesDialog(controller.getViewerFrame(),
                        messageBundle, signatureTreeNode.getOutlineItem(),
                        signatureTreeNode.getValidator()).setVisible(true);

            }
        }
    }
}
