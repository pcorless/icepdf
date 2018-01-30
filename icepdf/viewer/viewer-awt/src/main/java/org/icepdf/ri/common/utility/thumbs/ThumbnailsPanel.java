/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

package org.icepdf.ri.common.utility.thumbs;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.PageThumbnailComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.ModifiedFlowLayout;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;

/**
 * The ThumbnailsPanel class is responsible for showing a document preview
 * of all pages in a document.  This panel is show as a tab in the utility
 * panel of the Viewer RI.
 *
 * @since 4.3
 */
@SuppressWarnings("serial")
public class ThumbnailsPanel extends JPanel implements MutableDocument {

    protected PropertiesManager propertiesManager;
    protected float thumbNailZoom = 0.1f; // default zoom is 10%

    protected static final int MAX_PAGE_SIZE_READ_AHEAD = 10;

    private Controller controller;

    public ThumbnailsPanel(Controller controller,
                           PropertiesManager propertiesManager) {
        this.controller = controller;
        this.propertiesManager = propertiesManager;
        // assign thumbnail zoom from propertiesManager if available
        if (propertiesManager != null) {
            thumbNailZoom = propertiesManager.getPreferences().getFloat(
                    PropertiesManager.PROPERTY_UTILITYPANE_THUMBNAILS_ZOOM, 1.0f);
        }
    }

    @Override
    public void refreshDocumentInstance() {
        Document document = controller.getDocument();
        if (document != null) {
            buildUI();
        } else {
            // tear down the old container.
            this.removeAll();
        }
    }

    @Override
    public void disposeDocument() {
        this.removeAll();
    }

    private void buildUI() {

        final ModifiedFlowLayout layout = new ModifiedFlowLayout();
        final JPanel pageThumbsPanel = new JPanel(layout);
        this.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(pageThumbsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        this.add(scrollPane,
                BorderLayout.CENTER);

        scrollPane.getViewport().addChangeListener(e -> {
            JViewport tmp = (JViewport) e.getSource();
            Dimension dim = layout.computeSize(tmp.getWidth(), pageThumbsPanel);
            pageThumbsPanel.setPreferredSize(dim);
        });

        scrollPane.getVerticalScrollBar().addAdjustmentListener(
                e -> {
                    if (!e.getValueIsAdjusting()) {
                        repaint();
                    }
                });

        // load the page components into the layout
        PageThumbnailComponent pageThumbnailComponent;
        Document document = controller.getDocument();
        PageTree pageTree = document.getPageTree();
        int numberOfPages = document.getNumberOfPages();
        int avgPageWidth = 0;
        int avgPageHeight = 0;

        // add components for every page in the document
        for (int i = 0; i < numberOfPages; i++) {
            // also a way to pass in an average document size.
            if (i < MAX_PAGE_SIZE_READ_AHEAD) {
                pageThumbnailComponent =
                        new PageThumbnailComponent(
                                controller, scrollPane, pageTree, i, thumbNailZoom);
                avgPageWidth += pageThumbnailComponent.getPreferredSize().width;
                avgPageHeight += pageThumbnailComponent.getPreferredSize().height;
            } else if (i > MAX_PAGE_SIZE_READ_AHEAD) {
                pageThumbnailComponent =
                        new PageThumbnailComponent(controller, scrollPane, pageTree, i,
                                avgPageWidth, avgPageHeight, thumbNailZoom);
            }
            // calculate average page size
            else {
                avgPageWidth /= (MAX_PAGE_SIZE_READ_AHEAD);
                avgPageHeight /= (MAX_PAGE_SIZE_READ_AHEAD);
                pageThumbnailComponent =
                        new PageThumbnailComponent(controller, scrollPane, pageTree, i,
                                avgPageWidth, avgPageHeight, thumbNailZoom);
            }
            pageThumbsPanel.add(pageThumbnailComponent);
        }

        pageThumbsPanel.revalidate();
        scrollPane.validate();

    }
}
