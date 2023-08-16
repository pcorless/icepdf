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

import org.icepdf.ri.common.CurrentPageChanger;
import org.icepdf.ri.common.KeyListenerPageColumnChanger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static org.icepdf.ri.common.views.BasePageViewLayout.PAGE_SPACING_HORIZONTAL;


/**
 * <p>Constructs a one column page view as defined in the PDF specification. A one
 * column page view displays pages continuously in one column.</p>
 * <br>
 * <p>Page views are basic containers which use Swing Layout Containers to
 * place pages </p>
 *
 * @since 2.5
 */
@SuppressWarnings("serial")
public class OneColumnPageView extends AbstractDocumentView {

    // specialized listeners for different gui operations
    protected final CurrentPageChanger currentPageChanger;

    protected final KeyListenerPageColumnChanger keyListenerPageChanger;

    public OneColumnPageView(DocumentViewController documentDocumentViewController,
                             JScrollPane documentScrollpane,
                             DocumentViewModel documentViewModel) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // put all the gui elements together
        buildGUI();

        // add the first of many tools need for this views and others like it.
        currentPageChanger =
                new CurrentPageChanger(documentScrollpane, this,
                        documentViewModel.getAllPageComponents());

        // add page changing key listeners
        keyListenerPageChanger =
                KeyListenerPageColumnChanger.install(this.documentViewController.getParentController(),
                        documentViewModel.getDocumentViewScrollPane(), this, currentPageChanger);
    }

    private void buildGUI() {
        this.setLayout(new OneColumnPageViewLayout(documentViewModel));
        this.setBackground(backgroundColour);
        this.setBorder(new EmptyBorder(layoutInserts, layoutInserts, layoutInserts, layoutInserts));

        List<AbstractPageViewComponent> pageComponents =
                documentViewController.getDocumentViewModel().getFilteredPageComponents();

        if (pageComponents != null) {
            for (AbstractPageViewComponent pageViewComponent : pageComponents) {
                if (pageViewComponent != null) {
                    pageViewComponent.setDocumentViewCallback(this);
                    // add component to layout
                    JComponent page = new PageViewDecorator(pageViewComponent);
                    setLayer(page, JLayeredPane.DEFAULT_LAYER);
                    add(page);
                    addPopupAnnotationAndGlue(pageViewComponent);
                }
            }
            revalidate();
            repaint();
        }
    }

    // nothing needs to be done for a column view as all components are already
    // available
    public void updateDocumentView() {
    }

    @Override
    public void pagesListChanged() {
        dispose();
        disposing = false;
        buildGUI();
    }

    /**
     * Returns a next page increment of one.
     */
    public int getNextPageIncrement() {
        return 1;
    }

    /**
     * Returns a previous page increment of one.
     */
    public int getPreviousPageIncrement() {
        return 1;
    }

    public void dispose() {
        // remove utilities
        if (currentPageChanger != null) {
            currentPageChanger.dispose();
        }
        if (keyListenerPageChanger != null) {
            keyListenerPageChanger.uninstall();
        }

        // trigger a re-layout
        removeAll();
        invalidate();

        // make sure we call super.
        super.dispose();
    }

    public Dimension getDocumentSize() {
        // still used by page fit code
        float pageViewWidth = 0;
        float pageViewHeight = 0;
        int currCompIndex = documentViewController.getCurrentPageIndex();
        Rectangle bounds = documentViewModel.getPageBounds(currCompIndex);
        if (bounds != null) {
            pageViewWidth = bounds.width;
            pageViewHeight = bounds.height;
        }
        // normalize the dimensions to a zoom level of zero.
        float currentZoom = documentViewController.getDocumentViewModel().getViewZoom();
        pageViewWidth = Math.abs(pageViewWidth / currentZoom);
        pageViewHeight = Math.abs(pageViewHeight / currentZoom);

        // add any horizontal padding from layout manager
        pageViewWidth += PAGE_SPACING_HORIZONTAL;
        pageViewHeight += PAGE_SPACING_HORIZONTAL * 2;
        return new Dimension((int) pageViewWidth, (int) pageViewHeight);
    }

    public void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        // paint background gray
        g.setColor(backgroundColour);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        // paint selection box
        super.paintComponent(g);
    }
}
