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
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * This intercepts MouseWheelEvent for a JScrollPane, and determines if
 * they qualify to initiate a page change request for the SwingController.
 *
 * @author Mark Collette
 * @since 2.0
 */
public class MouseWheelListenerPageChanger implements MouseWheelListener {
    private SwingController controller;
    private JScrollPane scrollpane;
    private AbstractDocumentView documentView;

    /**
     * KeyEvents can queue up, if the user holds down a key,
     * causing us to do several page changes, unless we use
     * flagging to ignore the extraneous KeyEvents
     */
    private boolean changingPage;


    /**
     * Install a MouseWheelListenerPageChanger as a MouseWheelListener
     *
     * @param c SwingController that can change pages
     * @param s JScrollPane that has a vertical JScrollBar, and where events come from
     */
    public static Object install(SwingController c, JScrollPane s,
                                 AbstractDocumentView documentView) {
        MouseWheelListenerPageChanger listener = null;
        if (c != null && s != null) {
            listener = new MouseWheelListenerPageChanger(c, s, documentView);
            s.addMouseWheelListener(listener);
        }
        return listener;
    }

    protected MouseWheelListenerPageChanger(SwingController c, JScrollPane s,
                                            AbstractDocumentView documentView) {
        controller = c;
        scrollpane = s;
        this.documentView = documentView;
        changingPage = false;
    }

    public static void uninstall(JScrollPane scrollpane, Object listener) {
        if (scrollpane != null && listener != null &&
                listener instanceof MouseWheelListenerPageChanger) {
            scrollpane.removeMouseWheelListener((MouseWheelListenerPageChanger) listener);
        }
    }

    public void mouseWheelMoved(MouseWheelEvent e) {

        if (changingPage)
            return;
        int deltaPage = 0;
        JScrollBar visibleVerticalScrollBar =
                (scrollpane.getVerticalScrollBar() != null &&
                        scrollpane.getVerticalScrollBar().isVisible())
                        ? scrollpane.getVerticalScrollBar()
                        : null;

        // Scrolling down but only if the ctrl mask isn't present.
        if (!((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK)){
            int amount = e.getScrollAmount();
            int rotation = e.getWheelRotation();
            
            if (amount > 0 && rotation > 0) {
                if (visibleVerticalScrollBar != null) {
                    int value = visibleVerticalScrollBar.getModel().getValue();
                    int extent = visibleVerticalScrollBar.getModel().getExtent();
                    int max = visibleVerticalScrollBar.getModel().getMaximum();
                    if (value + extent >= max)
                        deltaPage = documentView.getPreviousPageIncrement();
                } else
                    deltaPage = documentView.getPreviousPageIncrement();
            }
            // Up
            else if (amount > 0 && rotation < 0) {
                if (visibleVerticalScrollBar != null) {
                    int value = visibleVerticalScrollBar.getModel().getValue();
                    if (value <= 0)
                        deltaPage = -documentView.getPreviousPageIncrement();
                } else
                    deltaPage = -documentView.getPreviousPageIncrement();
            }
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
//        System.out.println("Delta " + deltaPage + " " + controller.getCurrentPageNumber() + " " + visibleVerticalScrollBar);

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
