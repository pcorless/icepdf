/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.properties;

import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.fonts.FindFontsTask;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ResourceBundle;

/**
 * This class is a reference implementation for displaying a PDF file's
 * font information.   The dialog will start a worker thread that will read all the document's font objects and
 * build a tree view of the all the fonts.  This font view is primarily for debug purposes to make it easier to track
 * font substitution results.  The dialog also provides an easy way to refresh the
 * "\.icesoft\icepdf-viewer\pdfviewerfontcache.properties' with out manually deleting the file and restarted the viewer.
 * <p>
 * {@link FindFontsTask}
 *
 * @since 6.1.3
 */
@SuppressWarnings("serial")
public class FontDialog extends EscapeJDialog implements ActionListener, WindowListener {

    // refresh rate of gui elements
    private static final int TIMER_REFRESH = 20;

    // pointer to document which will be searched
    private SwingController controller;
    private ResourceBundle messageBundle;

    private FontPanel fontPropertiesPanel;
    private JButton okButton;

    // layouts constraint
    private GridBagConstraints constraints;

    /**
     * Create a new instance of SearchPanel.
     */
    public FontDialog(JFrame frame, SwingController swingController, Document document,
                      ResourceBundle messageBundle) {
        super(frame, true);
        setFocusable(true);
        this.controller = swingController;
        this.messageBundle = messageBundle;
        setGui();
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        setTitle(messageBundle.getString("viewer.dialog.fonts.title"));
        setResizable(true);
        addWindowListener(this);

        fontPropertiesPanel = new FontPanel(controller);
        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.addActionListener(this);

        JPanel layoutPanel = new JPanel(new GridBagLayout());
        layoutPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(layoutPanel, fontPropertiesPanel, 0, 0, 1, 1);

        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.CENTER;
        addGB(layoutPanel, okButton, 0, 1, 1, 1);

        this.setLayout(new BorderLayout(15, 15));
        this.add(layoutPanel, BorderLayout.CENTER);

        setSize(640, 480);
        setLocationRelativeTo(getOwner());
    }


    /**
     * Insure the font search process is killed when the dialog is closed via the 'esc' key.
     */
    @Override
    public void dispose() {
        super.dispose();
        fontPropertiesPanel.closeWindowOperations();
    }

    /**
     * Gridbag constructor helper
     *
     * @param panel     parent adding component too.
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowspane of field
     * @param colSpan   colspane of field.
     */
    private void addGB(JPanel panel, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        panel.add(component, constraints);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == okButton) {
            // clean up the timer and worker thread.
            fontPropertiesPanel.closeWindowOperations();
            setVisible(false);
        }
    }

    public void windowOpened(WindowEvent e) {

    }

    public void windowClosing(WindowEvent e) {
        fontPropertiesPanel.closeWindowOperations();
    }

    public void windowClosed(WindowEvent e) {
        fontPropertiesPanel.closeWindowOperations();
    }

    public void windowIconified(WindowEvent e) {

    }

    public void windowDeiconified(WindowEvent e) {

    }

    public void windowActivated(WindowEvent e) {

    }

    public void windowDeactivated(WindowEvent e) {

    }


}
