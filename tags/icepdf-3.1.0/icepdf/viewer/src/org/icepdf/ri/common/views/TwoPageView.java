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
import org.icepdf.ri.common.KeyListenerPageChanger;
import org.icepdf.ri.common.MouseWheelListenerPageChanger;
import org.icepdf.ri.common.SwingController;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.PageViewComponent;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * <p>Constructs a two page view as defined in the PDF specification.
 * A two column page view displays two pages with odd numbered pages
 * on the left.</p>
 * <p/>
 * <p>Page views are basic containers which use Swing Layout Containers to
 * place pages </p>
 *
 * @since 2.5
 */
public class TwoPageView extends AbstractDocumentView {

    protected JScrollPane documentScrollpane;

    protected boolean disposing;

    protected int viewAlignment;

    protected JPanel pagesPanel;

    // specialized listeners for different gui operations
    protected Object pageChangerListener;
    protected KeyListenerPageChanger keyListenerPageChanger;
    protected CurrentPageChanger currentPageChanger;

    public TwoPageView(DocumentViewController documentDocumentViewController,
                       JScrollPane documentScrollpane,
                       DocumentViewModelImpl documentViewModel,
                       final int viewAlignment) {

        super(documentDocumentViewController, documentScrollpane, documentViewModel);

        // used to redirect mouse events
        this.documentScrollpane = documentScrollpane;

        // assign view alignment
        this.viewAlignment = viewAlignment;

        // put all the gui elements together
        buildGUI();

        // add page changing key listeners
        if (this.documentViewController.getParentController() instanceof SwingController) {
            pageChangerListener =
                    MouseWheelListenerPageChanger.install(
                            (SwingController) this.documentViewController.getParentController(),
                            this.documentScrollpane, this);

            keyListenerPageChanger =
                    KeyListenerPageChanger.install((SwingController) this.documentViewController.getParentController(),
                            this.documentScrollpane, this);
        }

        // add the first of many tools need for this views and others like it.
        currentPageChanger =
                new CurrentPageChanger(documentScrollpane, this,
                        documentViewModel.getPageComponents(),
                        false);
    }


    private void buildGUI() {
        // add all page components to gridlayout panel
        pagesPanel = new JPanel();
        pagesPanel.setBackground(backgroundColor);
        // one column equals single page view continuous
        GridLayout gridLayout = new GridLayout(0, 2, horizontalSpace, verticalSpace);
        pagesPanel.setLayout(gridLayout);

        // use a gridbag to center the page component panel
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weighty = 1.0;                  // allows vertical resizing
        gbc.weightx = 1.0;                  // allows horizontal resizing
        gbc.insets =  // component spacer [top, left, bottom, right]
                new Insets(layoutInserts, layoutInserts, layoutInserts, layoutInserts);
        gbc.gridwidth = GridBagConstraints.REMAINDER;      // one component per row

        // finally add all the components
        // add components for every page in the document
        updateDocumentView();

        this.setLayout(new GridBagLayout());
        this.add(pagesPanel, gbc);
    }

    public void updateDocumentView() {

        java.util.List<AbstractPageViewComponent> pageComponents =
                documentViewModel.getPageComponents();

        if (pageComponents != null) {
            // remove old component
            pagesPanel.removeAll();
            pagesPanel.validate();
            PageViewComponent pageViewComponent;
            int count = 0;
            int index = documentViewModel.getViewCurrentPageIndex();
            int docLength = pageComponents.size();


            if (viewAlignment == RIGHT_VIEW &&
                    ((index > 0 && index % 2 == 0) || (index > 0 && docLength == 2))) {
                index--;
            }

            for (int i = index; i < docLength && count < 2; i++) {
                // save for facing page
                if (i == 0 && docLength > 2 && viewAlignment == RIGHT_VIEW) {
                    // should be adding spacer
                    pagesPanel.add(new JLabel());
                    count++;
                }
                pageViewComponent = pageComponents.get(i);
                if (pageViewComponent != null) {
                    pageViewComponent.setDocumentViewCallback(this);
                    // add component to layout
                    pagesPanel.add(new PageViewDecorator((JComponent) pageViewComponent));
                    pageViewComponent.invalidate();
                    count++;
                }
            }
            documentScrollpane.validate();
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

    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);

        // let the current PageListener now about the mouse release
        if (currentPageChanger != null)
            currentPageChanger.mouseReleased(e);
    }

    public void dispose() {
        disposing = true;
        // remove utilities
        if (pageChangerListener != null) {
            MouseWheelListenerPageChanger.uninstall(documentScrollpane,
                    pageChangerListener);
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
        Dimension documentSize = new Dimension();
        if (pagesPanel != null) {
            documentSize.setSize(pagesPanel.getBounds().getSize());
        }
        return documentSize;
    }

    public void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        g.setColor(backgroundColor);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
    }
}
