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
package org.icepdf.ri.common.views;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays a single page view of the document contained by the current controller instance in full screen mode. This
 * view is similar to the other views in that we reuse the page view implementation as the document views are swapped.
 *
 * @since 6.3.1
 */
public class FullScreenDocumentView extends OnePageView implements WindowListener {

    private static final Logger logger =
            Logger.getLogger(FullScreenDocumentView.class.toString());

    private static Object instance;
    private boolean disposed;

    private GraphicsDevice defaultScreenDevice;

    private DocumentViewController controller;
    private JFrame frame;

    public FullScreenDocumentView(DocumentViewController controller) {
        super(controller, new JScrollPane(), controller.getDocumentViewModel());
        this.controller = controller;
    }

    public void display() {

        if (instance != null) {
            logger.warning("Only one full screen view is allowed at a time.");
            return;
        }
        instance = this;

        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        defaultScreenDevice = graphicsEnvironment.getDefaultScreenDevice();
        if (defaultScreenDevice != null && defaultScreenDevice.isFullScreenSupported()) {
            try {
                FullScreenDocumentView fullScreenDocumentView = this;
                frame = new JFrame() {
                    protected JRootPane createRootPane() {
                        // setup a closing action
                        ActionListener actionListener = actionEvent -> fullScreenDocumentView.dispose();
                        // setup the esc key mapping.
                        JRootPane rootPane = new JRootPane();
                        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
                        rootPane.registerKeyboardAction(actionListener, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
                        return rootPane;
                    }
                };
                frame.addWindowListener(this);
                frame.setUndecorated(true);
                buildFullScreenDocumentView();
                defaultScreenDevice.setFullScreenWindow(frame);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Could not build fullscreen view: ", e);
            }
        } else {
            ResourceBundle messageBundle = documentViewController.getParentController().getMessageBundle();
            org.icepdf.ri.util.Resources.showMessageDialog(documentViewController.getViewContainer(),
                    JOptionPane.INFORMATION_MESSAGE,
                    messageBundle,
                    "viewer.dialog.fullScreen.error.title",
                    "viewer.dialog.fullScreen.error.msg");
        }
    }

    @Override
    protected JComponent buildPageDecoration(AbstractPageViewComponent pageViewComponent) {
        return pageViewComponent;
    }

    private void buildFullScreenDocumentView() {

        JScrollPane documentScrollpane = documentViewModel.getDocumentViewScrollPane();
        documentScrollpane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        documentScrollpane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        documentScrollpane.getVerticalScrollBar().setUnitIncrement(20);
        documentScrollpane.getHorizontalScrollBar().setUnitIncrement(20);

        setBackground(Color.BLACK);
        documentScrollpane.getViewport().setBackground(Color.BLACK);
        documentScrollpane.setViewportView(this);
        frame.setBackground(Color.BLACK);
        documentScrollpane.setBorder(BorderFactory.createEmptyBorder());
        frame.setContentPane(documentScrollpane);
    }

    public void dispose() {

        if (disposed) return;
        // avoid double dispose.
        disposed = true;

        super.dispose();
        try {
            // change the view before we close full screen so we don't get a flicker
            controller.revertViewType();
            // get out of full screen mode
            defaultScreenDevice.setFullScreenWindow(null);
        } finally {
            // close the current view
            frame.setVisible(false);
            frame.dispose();
            // clear our singleton flag.
            instance = null;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        Rectangle clipBounds = g.getClipBounds();
        // paint background gray
        g.setColor(Color.BLACK);
        g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        // close the window as we don't want to keep two open windows of the same document.
        dispose();
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

}
