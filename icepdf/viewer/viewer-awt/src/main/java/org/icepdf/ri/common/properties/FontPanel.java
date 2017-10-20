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

import org.icepdf.ri.common.fonts.FontHandlerPanel;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.font.ClearFontCacheWorker;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ResourceBundle;

/**
 * Panel display of document font properties.   The panel can be used as a fragment in any user interface.
 *
 * @since 6.3
 */
public class FontPanel extends JPanel implements ActionListener, WindowListener {


    // clear and rescan system for fonts and rewrite file.
    private JButton resetFontCacheButton;
    // panel that does the font lookup.
    private FontHandlerPanel fontHandlerPanel;

    // message bundle for internationalization
    private ResourceBundle messageBundle;


    // layouts constraint
    private GridBagConstraints constraints;

    public FontPanel(Controller controller) {

        setFocusable(true);
        this.messageBundle = controller.getMessageBundle();
        fontHandlerPanel = new FontHandlerPanel(controller);
        // kicks off the swing worker to do the font lookup off the awt thread.
        fontHandlerPanel.refreshDocumentInstance();
        setGui();
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        // content Panel
        JPanel fontPropertiesPanel = new JPanel(new GridBagLayout());
        fontPropertiesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.fonts.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        setLayout(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(1, 5, 5, 5);

        // add te font tree panel
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        fontHandlerPanel.setPreferredSize(new Dimension(150, 150));
        addGB(fontPropertiesPanel, fontHandlerPanel, 0, 0, 2, 1);

        resetFontCacheButton = new JButton(messageBundle.getString("viewer.dialog.fonts.resetCache.label"));
        resetFontCacheButton.setToolTipText(messageBundle.getString("viewer.dialog.fonts.resetCache.tip"));
        resetFontCacheButton.addActionListener(this);
        constraints.insets = new Insets(0, 5, 5, 5);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        addGB(fontPropertiesPanel, resetFontCacheButton, 0, 1, 1, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, fontPropertiesPanel, 0, 0, 1, 1);

    }

    /**
     * Two main actions are handle here, ok/close and reset the font cache.
     *
     * @param event awt action event.
     */
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == resetFontCacheButton) {
            // reset the font properties cache.
            resetFontCacheButton.setEnabled(false);
            org.icepdf.ri.common.SwingWorker worker = new ClearFontCacheWorker(resetFontCacheButton);
            worker.start();
        }
    }

    protected void closeWindowOperations() {
        // clean up the timer and worker thread.
        fontHandlerPanel.disposeDocument();
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

    public void windowOpened(WindowEvent e) {
    }

    public void windowClosing(WindowEvent e) {
        closeWindowOperations();
    }

    public void windowClosed(WindowEvent e) {
        closeWindowOperations();
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
