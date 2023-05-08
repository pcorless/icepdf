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

package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The CollectionDocumentView is used for documents that specify a PDF Package.
 * A PDF package contains a list of embedded files, much like a zip of related
 * documents.  When initialized each embedded document is represented as an
 * thumbnail icon in a flow layout.
 *
 * @since 5.1.0
 */
public class CollectionDocumentView extends AbstractDocumentView {

    private static final long serialVersionUID = 7220521612114533227L;


    public CollectionDocumentView(DocumentViewController documentViewController,
                                  JScrollPane documentScrollpane, DocumentViewModel documentViewModel) {
        super(documentViewController, documentScrollpane, documentViewModel);

        // put all the gui elements together
        buildGUI();
    }

    private void buildGUI() {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        final ModifiedFlowLayout layout = new ModifiedFlowLayout();
        layout.setHgap(15);
        layout.setVgap(15);
        pagesPanel = new JPanel(layout);
        pagesPanel.setBackground(backgroundColour);
        this.setLayout(new BorderLayout());
        this.add(pagesPanel,
                BorderLayout.CENTER);

        JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
        documentScrollpane.getViewport().addChangeListener(e -> {
            JViewport tmp = (JViewport) e.getSource();
            Dimension dim = layout.computeSize(tmp.getWidth(), pagesPanel);
            pagesPanel.setPreferredSize(dim);
        });

        documentScrollpane.getVerticalScrollBar().addAdjustmentListener(
                e -> {
                    if (!e.getValueIsAdjusting()) {
                        repaint();
                    }
                });

        // load the page components into the layout
        DocumentViewComponent documentViewComponent;
        Document document = documentViewController.getDocument();
        Library library = document.getCatalog().getLibrary();
        NameTree embeddedFilesNameTree = document.getCatalog().getEmbeddedFilesNameTree();
        if (embeddedFilesNameTree != null) {
            List filePairs = embeddedFilesNameTree.getNamesAndValues();

            // add components for every page in the document
            for (int i = 0, max = filePairs.size(); i < max; i += 2) {
                // get the name and document for
                // file name and file specification pairs.
                String fileName = Utils.convertStringObject(library, (StringObject) filePairs.get(i));
                DictionaryEntries tmp = (DictionaryEntries) library.getObject((Reference) filePairs.get(i + 1));

                // file specification has the document stream
                FileSpecification fileSpec = new FileSpecification(library, tmp);
                tmp = fileSpec.getEmbeddedFileDictionary();

                // create the stream instance from the embedded file streams File entry.
                Reference fileRef = (Reference) tmp.get(FileSpecification.F_KEY);

                documentViewComponent = new DocumentViewComponent(library, fileName, fileRef);
                JPanel documentViewPanel = new JPanel();
                documentViewPanel.setLayout(new BoxLayout(documentViewPanel, BoxLayout.Y_AXIS));
                documentViewPanel.setBackground(backgroundColour);
                PageViewDecorator pageViewComponent = new PageViewDecorator(documentViewComponent);
                pageViewComponent.setAlignmentX(Component.CENTER_ALIGNMENT);
                documentViewPanel.add(pageViewComponent);
                JLabel fileNameLabel = new JLabel(fileName);
                fileNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                documentViewPanel.add(fileNameLabel);
                pagesPanel.add(documentViewPanel);
            }
            pagesPanel.revalidate();
            documentScrollpane.validate();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        this.removeMouseListener(this);
        pagesPanel.removeAll();
    }

    @Override
    public void updateDocumentView() {

    }

    public int getNextPageIncrement() {
        return 0;
    }

    public int getPreviousPageIncrement() {
        return 0;
    }

    public Dimension getDocumentSize() {
        return null;
    }
}
