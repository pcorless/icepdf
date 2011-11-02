package org.icepdf.ri.common;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

/**
 *
 */
public class ThumbnailsPanel extends JPanel {

    protected DocumentViewController documentViewController;


    protected Document currentDocument;

    protected DocumentViewModel documentViewModel;


    protected static final int MAX_PAGE_SIZE_READ_AHEAD = 10;


    private SwingController controller;

    public ThumbnailsPanel(SwingController controller) {
        this.controller = controller;

    }

    public void setDocument(Document document) {
        this.currentDocument = document;
        documentViewController = controller.getDocumentViewController();

        if (document != null) {
            buildUI();
        } else {
            // tear down the old container.
            this.removeAll();
        }
    }

    public void dispose() {
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

        scrollPane.getViewport().addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JViewport tmp = (JViewport) e.getSource();
                Dimension dim = layout.computeSize(tmp.getWidth(), pageThumbsPanel);
                pageThumbsPanel.setPreferredSize(dim);
            }
        });

        scrollPane.getVerticalScrollBar().addAdjustmentListener(
                new AdjustmentListener() {
                    public void adjustmentValueChanged(AdjustmentEvent e) {
                        if (!e.getValueIsAdjusting()) {
                            repaint();
                        }
                    }
                });

        // load the page components into the layout
        PageThumbnailComponent pageThumbnailComponent = null;
        PageTree pageTree = currentDocument.getPageTree();
        int numberOfPages = currentDocument.getNumberOfPages();
        int avgPageWidth = 0;
        int avgPageHeight = 0;

        // add components for every page in the document
        for (int i = 0; i < numberOfPages; i++) {
            // also a way to pass in an average document size.
            if (i < MAX_PAGE_SIZE_READ_AHEAD) {
                pageThumbnailComponent =
                        new PageThumbnailComponent(controller, scrollPane, pageTree, i);
                avgPageWidth += pageThumbnailComponent.getPreferredSize().width;
                avgPageHeight += pageThumbnailComponent.getPreferredSize().height;
            } else if (i > MAX_PAGE_SIZE_READ_AHEAD) {
                pageThumbnailComponent =
                        new PageThumbnailComponent(controller, scrollPane, pageTree, i,
                                avgPageWidth, avgPageHeight);
            }
            // calculate average page size
            else if (i == MAX_PAGE_SIZE_READ_AHEAD) {
                avgPageWidth /= (MAX_PAGE_SIZE_READ_AHEAD);
                avgPageHeight /= (MAX_PAGE_SIZE_READ_AHEAD);
                pageThumbnailComponent =
                        new PageThumbnailComponent(controller, scrollPane, pageTree, i,
                                avgPageWidth, avgPageHeight);
            }
            pageThumbsPanel.add(pageThumbnailComponent);
        }

        pageThumbsPanel.revalidate();
        scrollPane.validate();

    }


    class ModifiedFlowLayout extends FlowLayout {

        public ModifiedFlowLayout() {
            super();
        }

        public Dimension computeSize(int w, Container target) {
            synchronized (target.getTreeLock()) {
                int hgap = getHgap();
                int vgap = getVgap();

                if (w == 0)
                    w = Integer.MAX_VALUE;

                Insets insets = target.getInsets();
                if (insets == null)
                    insets = new Insets(0, 0, 0, 0);
                int reqdWidth = 0;

                int maxwidth = w - (insets.left + insets.right + hgap * 2);
                int n = target.getComponentCount();
                int x = 0;
                int y = insets.top + vgap;
                int rowHeight = 0;

                for (int i = 0; i < n; i++) {
                    Component c = target.getComponent(i);
                    if (c.isVisible()) {
                        Dimension d = c.getPreferredSize();
                        if ((x == 0) || ((x + d.width) <= maxwidth)) {
                            // fits in current row.
                            if (x > 0) {
                                x += hgap;
                            }
                            x += d.width;
                            rowHeight = Math.max(rowHeight, d.height);
                        } else {
                            x = d.width;
                            y += vgap + rowHeight;
                            rowHeight = d.height;
                        }
                        reqdWidth = Math.max(reqdWidth, x);
                    }
                }
                y += rowHeight;
                y += insets.bottom;
                return new Dimension(reqdWidth + insets.left + insets.right, y);
            }
        }
    }
}
