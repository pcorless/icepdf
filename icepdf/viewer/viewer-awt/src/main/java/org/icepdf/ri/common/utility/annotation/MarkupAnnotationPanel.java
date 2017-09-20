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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * MarkupAnnotationPanel allows users to easily search, sort, filter and view markup annotations and their popup
 * annotation children.  The view main purpose to make working with a large number of markup annotations as easy
 * as possible.  The view shows all markup annotation in an open document.
 *
 * @since 6.3
 */
public class MarkupAnnotationPanel extends JPanel {

    // layouts constraint
    protected GridBagConstraints constraints;

    private PropertiesManager propertiesManager;
    private SwingController controller;
    protected ResourceBundle messageBundle;

    private JPanel markupAnnotationPanel;

    private JPanel searchPanel;
    private JTextField searchTextField;
    private JButton searchButton;
    private JButton clearSearchButton;

    private JPanel filterSortToolPanel;
    private JButton sortDropDownButton;
    private JButton filterDropDownButton;
    private JButton quickColorDropDownButton;

    private JPanel markupAnnotationPageView;

    public MarkupAnnotationPanel(SwingController controller, PropertiesManager propertiesManager) {
        this.messageBundle = controller.getMessageBundle();

        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        this.controller = controller;
        this.propertiesManager = propertiesManager;

        setFocusable(true);

        buildGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);

    }

    public void setAnnotationUtilityToolbar(JToolBar annotationUtilityToolbar) {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        addGB(this, annotationUtilityToolbar, 0, 0, 1, 1);
    }

    private void buildGUI() {
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);

        markupAnnotationPanel = new JPanel(new GridBagLayout());
        markupAnnotationPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.markupAnnotation.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        addGB(this, markupAnnotationPanel, 0, 1, 1, 1);

        buildSearchBar();

        buildSortFilterToolBar();

        buildMarkupAnnotationCommentView();
    }

    protected void buildSearchBar() {

    }

    protected void buildSortFilterToolBar() {

    }

    protected void buildMarkupAnnotationCommentView() {

    }

    /**
     * Gridbag constructor helper
     *
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan
     * @param colSpan
     */
    protected void addGB(JPanel layout, Component component,
                         int x, int y,
                         int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
