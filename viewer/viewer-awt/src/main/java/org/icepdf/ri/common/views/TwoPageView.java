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
import org.icepdf.ri.common.KeyListenerPageChanger;
import org.icepdf.ri.common.MouseWheelListenerPageChanger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static org.icepdf.ri.common.views.TwoPageViewLayout.PAGE_SPACING_HORIZONTAL;

/**
 * <p>Constructs a two page view as defined in the PDF specification.
 * A two column page view displays two pages with odd numbered pages
 * on the left.</p>
 * <br>
 * <p>Page views are basic containers which use Swing Layout Containers to
 * place pages </p>
 *
 * @since 2.5
 */
@SuppressWarnings("serial")
public class TwoPageView extends AbstractDocumentView {

    protected final int viewAlignment;

    // specialized listeners for different gui operations
    protected final Object pageChangerListener;
    protected final KeyListenerPageChanger keyListenerPageChanger;
    protected final CurrentPageChanger currentPageChanger;

    public TwoPageView(DocumentViewController documentDocumentViewController,
                       JScrollPane documentScrollpane,
                       DocumentViewModel documentViewModel,
                       final int viewAlignment) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // assign view alignment
        this.viewAlignment = viewAlignment;

        // put all the gui elements together
        buildGUI();

        // add page changing key listeners
        pageChangerListener =
                MouseWheelListenerPageChanger.install(
                        this.documentViewController.getParentController(),
                        documentScrollpane, this);

        keyListenerPageChanger =
                KeyListenerPageChanger.install(this.documentViewController.getParentController(),
                        documentScrollpane, this);

        // add the first of many tools need for this views and others like it.
        currentPageChanger =
                new CurrentPageChanger(documentScrollpane, this,
                        documentViewModel.getPageComponents(),
                        false);
    }


    private void buildGUI() {
        this.setLayout(new TwoPageViewLayout(viewAlignment, documentViewModel));
        this.setBackground(backgroundColour);
        this.setBorder(new EmptyBorder(layoutInserts, layoutInserts, layoutInserts, layoutInserts));

        // finally add all the components
        // add components for every page in the document
        updateDocumentView();
    }

    public void updateDocumentView() {

        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        java.util.List<AbstractPageViewComponent> pageComponents = documentViewModel.getPageComponents();

        if (pageComponents != null) {
            // remove old component
            this.removeAll();

            AbstractPageViewComponent pageViewComponent;
            int count = 0;
            int index = documentViewModel.getViewCurrentPageIndex();
            int docLength = pageComponents.size();

            // adjust for 2 up view, so we don't page again to the 3 pages...
            if (viewAlignment == RIGHT_VIEW &&
                    ((index > 0 && index % 2 == 0) || (index > 0 && docLength == 2))) {
                index--;
            }

            for (int i = index; i < docLength && count < 2; i++) {
                // skip for facing page
                if (i == 0 && docLength > 2 && viewAlignment == RIGHT_VIEW) {
                    count++;
                }
                pageViewComponent = pageComponents.get(i);
                if (pageViewComponent != null) {
                    // add component to layout
                    JComponent pageViewDecorator = new PageViewDecorator(pageViewComponent);
                    setLayer(pageViewDecorator, JLayeredPane.DEFAULT_LAYER);
                    add(pageViewDecorator);
                    addPopupAnnotationAndGlue(pageViewComponent);
                    count++;
                }
            }
            revalidate();
            repaint();

            // make sure we have set up all pages with callback call.
            for (PageViewComponent pageViewCom : pageComponents) {
                if (pageViewCom != null) {
                    pageViewCom.setDocumentViewCallback(this);
                }
            }
        }
    }

    /**
     * Returns a next page increment of two.
     */
    public int getNextPageIncrement() {
        return 2;
    }

    /**
     * Returns a previous page increment of two.
     */
    public int getPreviousPageIncrement() {
        return 2;
    }

    public void dispose() {
        // remove utilities
        if (pageChangerListener != null) {
            JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
            MouseWheelListenerPageChanger.uninstall(documentScrollpane, pageChangerListener);
        }
        if (keyListenerPageChanger != null) {
            keyListenerPageChanger.uninstall();
        }
        if (currentPageChanger != null) {
            currentPageChanger.dispose();
        }

        // trigger a re-layout
        removeAll();
        invalidate();

        // make sure we call super.
        super.dispose();
    }

    public Dimension getDocumentSize() {
        float pageViewWidth = 0;
        float pageViewHeight = 0;
        int count = getComponentCount();
        Component comp;
        for (int i = 0; i < count; i++) {
            comp = getComponent(i);
            if (comp instanceof PageViewDecorator) {
                PageViewDecorator pvd = (PageViewDecorator) comp;
                Dimension dim = pvd.getPreferredSize();
                pageViewWidth = dim.width;
                pageViewHeight = dim.height;
                break;
            }
        }
        // normalize the dimensions to a zoom level of zero.
        float currentZoom = documentViewController.getDocumentViewModel().getViewZoom();
        pageViewWidth = Math.abs(pageViewWidth / currentZoom);
        pageViewHeight = Math.abs(pageViewHeight / currentZoom);

        // two pages wide, generalization, pages are usually the same size we
        // don't bother to look at the second pages size for the time being.
        pageViewWidth *= 2;

        // add any horizontal padding from layout manager
        pageViewWidth += PAGE_SPACING_HORIZONTAL;
        pageViewHeight += PAGE_SPACING_HORIZONTAL * 2;

        return new Dimension((int) pageViewWidth, (int) pageViewHeight);
    }

    public void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        g.setColor(backgroundColour);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        // paint selection box
        super.paintComponent(g);
    }
}
