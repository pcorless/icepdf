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
    protected CurrentPageChanger currentPageChanger;

    protected KeyListenerPageColumnChanger keyListenerPageChanger;

    public OneColumnPageView(DocumentViewController documentDocumentViewController,
                             JScrollPane documentScrollpane,
                             DocumentViewModel documentViewModel) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // put all the gui elements together
        buildGUI();

        // add the first of many tools need for this views and others like it.
        currentPageChanger =
                new CurrentPageChanger(documentScrollpane, this,
                        documentViewModel.getPageComponents());

        // add page changing key listeners
        keyListenerPageChanger =
                KeyListenerPageColumnChanger.install(this.documentViewController.getParentController(),
                        documentViewModel.getDocumentViewScrollPane(), this, currentPageChanger);
    }

    private void buildGUI() {
        this.setLayout(new SingleColumnPageViewLayout());
        this.setBackground(backgroundColour);
        this.setBorder(new EmptyBorder(layoutInserts, layoutInserts, layoutInserts, layoutInserts));

        List<AbstractPageViewComponent> pageComponents =
                documentViewController.getDocumentViewModel().getPageComponents();

        if (pageComponents != null) {
            for (AbstractPageViewComponent pageViewComponent : pageComponents) {
                if (pageViewComponent != null) {
                    pageViewComponent.setDocumentViewCallback(this);
                    // add component to layout
                    JComponent page = new PageViewDecorator(pageViewComponent);
                    page.addComponentListener(this);
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
        // todo this might not be needed anymore?
        float pageViewWidth = 0;
        float pageViewHeight = 0;
        int currCompIndex = documentViewController.getCurrentPageIndex();
        int numComponents = getComponentCount();
        if (currCompIndex >= 0 && currCompIndex < numComponents) {
            Component comp = getComponent(currCompIndex);
            if (comp instanceof PageViewDecorator) {
                PageViewDecorator pvd = (PageViewDecorator) comp;
                Dimension dim = pvd.getPreferredSize();
                pageViewWidth = dim.width;
                pageViewHeight = dim.height;
            }
        }
        // normalize the dimensions to a zoom level of zero.
        float currentZoom = documentViewController.getDocumentViewModel().getViewZoom();
        pageViewWidth = Math.abs(pageViewWidth / currentZoom);
        pageViewHeight = Math.abs(pageViewHeight / currentZoom);

        // add any horizontal padding from layout manager
        pageViewWidth += AbstractDocumentView.horizontalSpace * 2;
        pageViewHeight += AbstractDocumentView.verticalSpace * 2;
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
