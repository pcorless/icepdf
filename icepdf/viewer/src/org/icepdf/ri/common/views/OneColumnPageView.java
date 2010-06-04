/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.common.views;

import org.icepdf.ri.common.CurrentPageChanger;
import org.icepdf.ri.common.KeyListenerPageColumnChanger;
import org.icepdf.ri.common.SwingController;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.PageViewComponent;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;


/**
 * <p>Constructs a one column page view as defined in the PDF specification. A one
 * column page view displays pages continuously in one column.</p>
 * <p/>
 * <p>Page views are basic containers which use Swing Layout Containers to
 * place pages </p>
 *
 * @since 2.5
 */
public class OneColumnPageView extends AbstractDocumentView {

    protected JScrollPane documentScrollpane;

    protected boolean disposing;

    protected JPanel pagesPanel;

    // specialized listeners for different gui operations
    protected CurrentPageChanger currentPageChanger;

    protected KeyListenerPageColumnChanger keyListenerPageChanger;

    public OneColumnPageView(DocumentViewController documentDocumentViewController,
                             JScrollPane documentScrollpane,
                             DocumentViewModelImpl documentViewModel) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // used to redirect mouse events
        this.documentScrollpane = documentScrollpane;

        // put all the gui elements together
        buildGUI();

        // add the first of many tools need for this views and others like it.
        currentPageChanger =
                new CurrentPageChanger(documentScrollpane, this,
                        documentViewModel.getPageComponents());

        // add page changing key listeners
        if (this.documentViewController.getParentController() instanceof SwingController) {
            keyListenerPageChanger =
                    KeyListenerPageColumnChanger.install((SwingController) this.documentViewController.getParentController(),
                            this.documentScrollpane, this, currentPageChanger);
        }
    }


    private void buildGUI() {
        // add all page components to grid layout panel
        pagesPanel = new JPanel();
        pagesPanel.setBackground(backgroundColor);
        // one column equals single page view continuous
        GridLayout gridLayout = new GridLayout(0, 1, horizontalSpace, verticalSpace);
        pagesPanel.setLayout(gridLayout);

        // use a grid bag to center the page component panel
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weighty = 1.0;                  // allows vertical resizing
        gbc.weightx = 1.0;                  // allows horizontal resizing
        gbc.insets =  // component spacer [top, left, bottom, right]
                new Insets(layoutInserts, layoutInserts, layoutInserts, layoutInserts);
        gbc.gridwidth = GridBagConstraints.REMAINDER;      // one component per row

        this.setLayout(new GridBagLayout());
        this.add(pagesPanel, gbc);

        // finally add all the components
        // add components for every page in the document
        List<AbstractPageViewComponent> pageComponents =
                documentViewModel.getPageComponents();

        if (pageComponents != null) {
            for( PageViewComponent pageViewComponent : pageComponents ){
                if (pageViewComponent != null){
                    pageViewComponent.setDocumentViewCallback(this);
                    // add component to layout
                    pagesPanel.add(new PageViewDecorator(
                            (AbstractPageViewComponent)pageViewComponent));
                }
            }
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

    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);

        // let the current PageListener now about the mouse release
        currentPageChanger.mouseReleased(e);
    }

    public void dispose() {
        disposing = true;
        // remove utilities
        if (currentPageChanger != null) {
            currentPageChanger.dispose();
        }
        if (keyListenerPageChanger != null) {
            keyListenerPageChanger.uninstall();
        }

        // trigger a re-layout
        pagesPanel.removeAll();
        pagesPanel.invalidate();

        // make sure we call super.
        super.dispose();
    }

    public Dimension getDocumentSize() {
       float pageViewWidth = 0;
        float pageViewHeight = 0;
        if (pagesPanel != null) {
            int currCompIndex = documentViewController.getCurrentPageIndex();
            int numComponents = pagesPanel.getComponentCount();
            if (currCompIndex >= 0 && currCompIndex < numComponents) {
                Component comp = pagesPanel.getComponent(currCompIndex);
                if (comp instanceof PageViewDecorator) {
                    PageViewDecorator pvd = (PageViewDecorator) comp;
                    Dimension dim = pvd.getPreferredSize();
                    pageViewWidth = dim.width;
                    pageViewHeight = dim.height;
                }
            }
        }
        // normalize the dimensions to a zoom level of zero.
        float currentZoom = documentViewModel.getViewZoom();
        pageViewWidth = Math.abs(pageViewWidth / currentZoom);
        pageViewHeight = Math.abs(pageViewHeight / currentZoom);

        // add any horizontal padding from layout manager
        pageViewWidth += AbstractDocumentView.horizontalSpace *2;
        pageViewHeight += AbstractDocumentView.verticalSpace *2;
        return new Dimension((int)pageViewWidth, (int)pageViewHeight);
    }

    public void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        // paint background gray
        g.setColor(backgroundColor);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        // paint selection box
        super.paintComponent(g);
    }
}
