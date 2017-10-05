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
package org.icepdf.ri.common.utility.annotation.destinations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.Names;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.NameJTree;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * DestinationsPanel show the destinations that are associated with the Names tree in the Document catalog. The ability
 * to manipulate this name tree is important when creating annotation and assigning an accompanying action or in the
 * case of link annotation's destination.
 * <p>
 * This panel displays all the names in the tree and allows for basic updates, creation and deletion.  The idea is that
 * a user can add destinations as they see fit and then go back to the annotation properties dialog/pane and assingn
 * an action or link annotation destination.
 *
 * @since 6.3
 */
public class DestinationsPanel extends JPanel implements MutableDocument {

    private static final Logger logger =
            Logger.getLogger(DestinationsPanel.class.toString());

    // layouts constraint
    protected GridBagConstraints constraints;

    private PropertiesManager propertiesManager;
    private Preferences preferences;
    private SwingController controller;
    private ResourceBundle messageBundle;

    private Document document;
    private NameJTree nameJTree;

    public DestinationsPanel(SwingController controller, PropertiesManager propertiesManager) {
        messageBundle = controller.getMessageBundle();
        preferences = propertiesManager.getPreferences();
        setLayout(new GridBagLayout());
        setAlignmentY(JPanel.TOP_ALIGNMENT);

        this.controller = controller;
        this.propertiesManager = propertiesManager;

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;

        nameJTree = new NameJTree();
        JScrollPane scrollPane = new JScrollPane(nameJTree);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        addGB(this, scrollPane, 0, 0, 1, 1);

        setFocusable(true);
    }

    @Override
    public void setDocument(Document document) {
        this.document = document;
        Names names = document.getCatalog().getNames();
        if (names != null && names.getDestsNameTree() != null) {
            NameTree nameTree = names.getDestsNameTree();
            if (nameTree != null) {
                nameJTree.setModel(new DefaultTreeModel(new NameTreeNode(nameTree.getRoot(), messageBundle)));
                nameJTree.setRootVisible(true);
                nameJTree.setShowsRootHandles(true);
            }
        }
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
