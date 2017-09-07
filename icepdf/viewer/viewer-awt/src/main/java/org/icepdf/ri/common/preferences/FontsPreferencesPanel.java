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
package org.icepdf.ri.common.preferences;

import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;
import org.icepdf.ri.util.font.ClearFontCacheWorker;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * Contains fonts setting for the viewer reference implementation.
 *
 * @since 6.3
 */
public class FontsPreferencesPanel extends JPanel implements ActionListener {

    // layouts constraint
    private GridBagConstraints constraints;

    // clear and rescan system for fonts and rewrite file.
    private JButton resetFontCacheButton;

    public FontsPreferencesPanel(SwingController swingController, PropertiesManager propertiesManager,
                                 ResourceBundle messageBundle) {
        super(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);

        // build out the font cache reset button.
        JPanel fontCachePreferencesPanel = new JPanel(new GridBagLayout());
        fontCachePreferencesPanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        fontCachePreferencesPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.viewerPreferences.section.fonts.fontCache.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        resetFontCacheButton = new JButton(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.fonts.fontCache.button.label"));
        resetFontCacheButton.addActionListener(this);

        constraints.anchor = GridBagConstraints.WEST;
        addGB(fontCachePreferencesPanel, new JLabel(messageBundle.getString(
                "viewer.dialog.viewerPreferences.section.fonts.fontCache.label")), 0, 0, 1, 1);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(fontCachePreferencesPanel, resetFontCacheButton, 1, 0, 1, 1);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.BOTH;
        addGB(this, fontCachePreferencesPanel, 0, 0, 1, 1);
        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 1, 1, 1);

    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == resetFontCacheButton) {
            // reset the font properties cache.
            resetFontCacheButton.setEnabled(false);
            org.icepdf.ri.common.SwingWorker worker = new ClearFontCacheWorker(resetFontCacheButton);
            worker.start();
        }
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }

}
