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

import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.destinations.DestinationsPanel;
import org.icepdf.ri.common.utility.annotation.markup.MarkupAnnotationPanel;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;

public class AnnotationPanel extends JPanel implements MutableDocument {

    // layouts constraint
    private GridBagConstraints constraints;

    private JTabbedPane annotationTabbedPane;

    private MarkupAnnotationPanel markupAnnotationPanel;
    private DestinationsPanel destinationsPanel;

    public AnnotationPanel(SwingController controller) {

        annotationTabbedPane = new JTabbedPane(SwingConstants.TOP);
        annotationTabbedPane.setAlignmentY(JPanel.TOP_ALIGNMENT);

        setLayout(new GridBagLayout());
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(0, 0, 0, 0);
        addGB(this, annotationTabbedPane, 0, 0, 1, 1);
    }

    public void addMarkupAnnotationPanel(MarkupAnnotationPanel panel, String title) {
        markupAnnotationPanel = panel;
        annotationTabbedPane.add(markupAnnotationPanel, title);
    }

    public void addDestinationPanel(DestinationsPanel panel, String title) {
        destinationsPanel = panel;
        annotationTabbedPane.add(destinationsPanel, title);
    }

    @Override
    public void refreshDocumentInstance() {
        if (markupAnnotationPanel != null) {
            markupAnnotationPanel.refreshDocumentInstance();
        }
        if (destinationsPanel != null) {
            destinationsPanel.refreshDocumentInstance();
        }
    }

    @Override
    public void disposeDocument() {
        if (markupAnnotationPanel != null) {
            markupAnnotationPanel.disposeDocument();
        }
        if (destinationsPanel != null) {
            destinationsPanel.disposeDocument();
        }
    }

    /**
     * Allows for the selection of a specific preference panel on first view.
     *
     * @param selectedAnnotationPanel selects the give annotation panel tab.
     */
    public void setSelectedTab(final String selectedAnnotationPanel) {
        PropertiesManager propertiesManager = PropertiesManager.getInstance();
        if (PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_MARKUP.equals(selectedAnnotationPanel)) {
            annotationTabbedPane.setSelectedIndex(0);
        } else if (PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_DESTINATIONS.equals(selectedAnnotationPanel)) {
            annotationTabbedPane.setSelectedIndex(1);
        } else {
            annotationTabbedPane.setSelectedIndex(0);
        }
    }

    public MarkupAnnotationPanel getMarkupAnnotationPanel() {
        return markupAnnotationPanel;
    }

    public DestinationsPanel getDestinationsPanel() {
        return destinationsPanel;
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y, int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}
