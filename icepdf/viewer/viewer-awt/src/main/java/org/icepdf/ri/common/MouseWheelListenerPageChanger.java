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
package org.icepdf.ri.common;

import org.icepdf.ri.common.tools.DynamicZoomHandler;
import org.icepdf.ri.common.views.AbstractDocumentView;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * This intercepts MouseWheelEvent for a JScrollPane, and determines if
 * they qualify to initiate a page change request for the Controller.
 *
 * @author Mark Collette
 * @since 2.0
 */
public class MouseWheelListenerPageChanger implements MouseWheelListener {
    private org.icepdf.ri.common.views.Controller controller;
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
     * @param documentView document view that contains the wheel listener
     * @param c            Controller that can change pages
     * @param s            JScrollPane that has a vertical JScrollBar, and where events come from
     * @return MouseWheelListenerPageChanger that was installed.
     */
    public static Object install(Controller c, JScrollPane s,
                                 AbstractDocumentView documentView) {
        MouseWheelListenerPageChanger listener = null;
        if (c != null && s != null) {
            listener = new MouseWheelListenerPageChanger(c, s, documentView);
            s.addMouseWheelListener(listener);
        }
        return listener;
    }

    protected MouseWheelListenerPageChanger(Controller c, JScrollPane s,
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
        if (!((e.getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK ||
                documentView.getCurrentToolHandler() instanceof DynamicZoomHandler)) {
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
        SwingUtilities.invokeLater(() -> {
            changingPage = false;
            controller.goToDeltaPage(dp);
        });
    }
}
