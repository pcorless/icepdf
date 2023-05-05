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

import org.icepdf.ri.common.KeyListenerPageChanger;
import org.icepdf.ri.common.MouseWheelListenerPageChanger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static org.icepdf.ri.common.views.BasePageViewLayout.PAGE_SPACING_HORIZONTAL;

/**
 * <p>Constructs a one  page view as defined in the PDF specification. A one
 * page view displays one page at a time.</p>
 * <br>
 * <p>Page views are basic containers which use Swing Layout Containers to
 * place pages </p>
 *
 * @since 2.5
 */
@SuppressWarnings("serial")
public class OnePageView extends AbstractDocumentView {

    protected Object pageChangerListener;

    protected KeyListenerPageChanger keyListenerPageChanger;


    public OnePageView(DocumentViewController documentDocumentViewController,
                       JScrollPane documentScrollpane,
                       DocumentViewModel documentViewModel) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // put all the gui elements together
        buildGUI();

        // add page changing key listeners
        pageChangerListener = MouseWheelListenerPageChanger.install(
                this.documentViewController.getParentController(),
                documentScrollpane, this);

        keyListenerPageChanger = KeyListenerPageChanger.install(
                this.documentViewController.getParentController(),
                documentScrollpane, this);

    }


    private void buildGUI() {
        this.setLayout(new OnePageViewLayout(documentViewModel));
        this.setBackground(backgroundColour);
        this.setBorder(new EmptyBorder(layoutInserts, layoutInserts, layoutInserts, layoutInserts));

        updateDocumentView();
    }

    public void updateDocumentView() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        java.util.List<AbstractPageViewComponent> pageComponents = documentViewModel.getPageComponents();
        if (pageComponents != null) {
            AbstractPageViewComponent pageViewComponent = pageComponents.get(documentViewModel.getViewCurrentPageIndex());
            if (pageViewComponent != null) {
                // remove old component
                this.removeAll();

                // add component to layout
                JComponent page = buildPageDecoration(pageViewComponent);
                add(page, JLayeredPane.DEFAULT_LAYER);
                addPopupAnnotationAndGlue(pageViewComponent);
            }

            // make sure we have set up all pages with callback call.
            for (PageViewComponent pageViewCom : pageComponents) {
                if (pageViewCom != null) {
                    pageViewCom.setDocumentViewCallback(this);
                }
            }

            revalidate();
            repaint();
        }
    }

    protected JComponent buildPageDecoration(AbstractPageViewComponent pageViewComponent) {
        return new PageViewDecorator(pageViewComponent);
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
        if (pageChangerListener != null) {
            JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
            MouseWheelListenerPageChanger.uninstall(documentScrollpane,
                    pageChangerListener);
        }
        if (keyListenerPageChanger != null) {
            keyListenerPageChanger.uninstall();
        }

        // make sure we call super.
        super.dispose();
    }

    public Dimension getDocumentSize() {
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
