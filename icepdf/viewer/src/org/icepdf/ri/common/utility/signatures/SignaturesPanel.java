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
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.layers.LayersTreeNode;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ResourceBundle;

/**
 *
 */
public class SignaturesPanel extends JPanel {

    protected DocumentViewController documentViewController;

    protected Document currentDocument;

    private SwingController controller;

    protected LayersTreeNode nodes;
    protected DocumentViewModel documentViewModel;
    // message bundle for internationalization
    ResourceBundle messageBundle;

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
        tree.addMouseListener(new NodeSelectionListener(tree));

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
                nodes = new LayersTreeNode("Signatures");
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
        // add the base certificateChain.
        for (SignatureWidgetAnnotation signature : signatures) {
            SignatureDictionary signatureDictionary = signature.getSignatureDictionary();
            // filter any unsigned singer fields.
            if (signatureDictionary.getEntries().size() > 0) {
                tmp = new SignatureTreeNode(signature);
                tmp.setAllowsChildren(true);
                nodes.add(tmp);
            } else if (!foundUnsignedSignatureFields) {
                foundUnsignedSignatureFields = true;
            }
        }
        // todo add permission data from as new node:
        //    - field dictionary's /Lock field if present
        //    - look at Signature Reference Dictionary for /Transform Method
        // add the unsigned singer fields to there own root node.
        if (foundUnsignedSignatureFields) {
            DefaultMutableTreeNode unsignedFieldNode = new DefaultMutableTreeNode("Unsigned Signature Fields");
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
        JTree tree;

        NodeSelectionListener(JTree tree) {
            this.tree = tree;
        }

        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
//                LayersTreeNode node = (LayersTreeNode) path.getLastPathComponent();
//                boolean isSelected = !(node.isSelected());
//                node.setSelected(isSelected);
//                // the current page and repaint
//                List<AbstractPageViewComponent> pages = documentViewModel.getPageComponents();
//                AbstractPageViewComponent page = pages.get(documentViewModel.getViewCurrentPageIndex());
//                page.invalidatePageBuffer();
//                // resort page text as layer visibility will have changed.
//                try {
//                    page.getPage().getText().sortAndFormatText();
//                } catch (InterruptedException e1) {
//                    // silent running for now.
//                }
//                // repaint the page.
//                page.repaint();
//                // repaint the tree so the checkbox states are show correctly.
//                tree.repaint();
//                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
//                if (row == 0) {
//                    tree.revalidate();
//                    tree.repaint();
//                }
            }
        }
    }
}
