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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.common;

import org.icepdf.ri.common.views.AbstractDocumentView;

import javax.swing.*;
import java.awt.event.KeyAdapter;

/**
 * This intercepts KeyEvents for a JScrollPane, and determines if
 * they qualify to initiate a page change request for the SwingController.
 *
 * @author Mark Collette
 * @since 2.0
 */
public class KeyListenerPageChanger extends KeyAdapter {
    private SwingController controller;
    private JScrollPane scroll;
    private AbstractDocumentView documentView;

    /**
     * KeyEvents can queue up, if the user holds down a key,
     * causing us to do several page changes, unless we use
     * flagging to ignore the extraneous KeyEvents
     */
    private boolean changingPage;


    /**
     * Install a KeyListenerPageChanger as a KeyListener
     *
     * @param c SwingController that can change pages
     * @param s JScrollPane that has a vertical JScrollBar, and where events come from
     */
    public static KeyListenerPageChanger install(SwingController c, JScrollPane s,
                                                 AbstractDocumentView documentView) {
        KeyListenerPageChanger listener = null;
        if (c != null && s != null) {
            listener = new KeyListenerPageChanger(c, s, documentView);
            s.addKeyListener(listener);
        }
        return listener;
    }

    public void uninstall() {
        if (scroll != null) {
            scroll.removeKeyListener(this);
        }
    }

    protected KeyListenerPageChanger(SwingController c, JScrollPane s,
                                     AbstractDocumentView documentView) {
        controller = c;
        scroll = s;
        this.documentView = documentView;
        changingPage = false;
    }

    public void keyPressed(java.awt.event.KeyEvent e) {
        //if( !pageView.changePageWhenKeyOverscrolling() )
        //    return;
        if (changingPage)
            return;
        int deltaPage = 0;
        JScrollBar visibleVerticalScrollBar =
                (scroll.getVerticalScrollBar() != null &&
                        scroll.getVerticalScrollBar().isVisible())
                        ? scroll.getVerticalScrollBar()
                        : null;
        int keyCode = e.getKeyCode();
        if (keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN ||
                keyCode == java.awt.event.KeyEvent.VK_DOWN) {
            if (visibleVerticalScrollBar != null) {
                int value = visibleVerticalScrollBar.getModel().getValue();
                int extent = visibleVerticalScrollBar.getModel().getExtent();
                int max = visibleVerticalScrollBar.getModel().getMaximum();
                if (value + extent >= max)
                    deltaPage = documentView.getNextPageIncrement();
            } else
                deltaPage = documentView.getNextPageIncrement();
        } else if (keyCode == java.awt.event.KeyEvent.VK_PAGE_UP ||
                keyCode == java.awt.event.KeyEvent.VK_UP) {
            if (visibleVerticalScrollBar != null) {
                int value = visibleVerticalScrollBar.getModel().getValue();
                if (value <= 0)
                    deltaPage = -documentView.getPreviousPageIncrement();
            } else
                deltaPage = -documentView.getPreviousPageIncrement();
        } else if (keyCode == java.awt.event.KeyEvent.VK_HOME) {
            deltaPage = -controller.getCurrentPageNumber();
        } else if (keyCode == java.awt.event.KeyEvent.VK_END) {
            deltaPage = controller.getDocument().getNumberOfPages() - controller.getCurrentPageNumber() - 1;
        }

        if (deltaPage == 0)
            return;
        int newPage = controller.getCurrentPageNumber() + deltaPage;
        if (controller.getDocument() == null) {
            return;
        }
        if (newPage < 0) {
            deltaPage = -controller.getCurrentPageNumber();
        }
        if (newPage >= controller.getDocument().getNumberOfPages()) {
            deltaPage = controller.getDocument().getNumberOfPages() - controller.getCurrentPageNumber() - 1;
        }

        if (deltaPage == 0) {
            return;
        }

        changingPage = true;
        final int dp = deltaPage;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                changingPage = false;
                controller.goToDeltaPage(dp);
            }
        });
    }
}
