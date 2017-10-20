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
package org.icepdf.ri.common.utility.annotation.properties;

import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

public class AnnotationPropertiesDialog extends EscapeJDialog implements AnnotationProperties {

    // layouts constraint
    private GridBagConstraints constraints;

    private JTabbedPane propertiesTabbedPane;
    private Controller controller;
    private ResourceBundle messageBundle;

    public AnnotationPropertiesDialog(JFrame frame, SwingController controller,
                                      ResourceBundle messageBundle) {
        super(frame, true);
        this.controller = controller;
        this.messageBundle = messageBundle;

        setTitle(messageBundle.getString("viewer.dialog.annotationProperties.tab.title"));

        // Create GUI elements
        final JButton okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(e -> {
            if (e.getSource() == okButton) {
                setVisible(false);
                dispose();
            }
        });

        propertiesTabbedPane = new JTabbedPane();
        propertiesTabbedPane.setAlignmentY(JPanel.TOP_ALIGNMENT);

        JPanel layoutPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.NORTH;
        addGB(layoutPanel, propertiesTabbedPane, 0, 0, 1, 1);

        constraints.fill = GridBagConstraints.NONE;
        addGB(layoutPanel, okButton, 0, 1, 1, 1);

        this.setLayout(new BorderLayout(15, 15));
        this.add(layoutPanel, BorderLayout.NORTH);

        setSize(350, 350);
        setLocationRelativeTo(frame);
    }

    @Override
    public void setAnnotationComponent(AnnotationComponent annotation) {

        AnnotationPanelAdapter annotationPropertyPanel =
                AnnotationPanel.buildAnnotationPropertyPanel(annotation, controller);

        if (annotationPropertyPanel != null) {
            annotationPropertyPanel.setAnnotationComponent(annotation);
            annotationPropertyPanel.setEnabled(true);
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.annotationProperties.tab.default.title"),
                    annotationPropertyPanel);
        }

        ActionsPanel actionsPanel = new ActionsPanel(controller);
        BorderPanel borderPanel = new BorderPanel(controller);
        FlagsPanel flagsPanel = null;

        PropertiesManager propertiesManager = PropertiesManager.getInstance();
        if (propertiesManager == null || propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_FLAGS)) {
            flagsPanel = new FlagsPanel(controller);
        }

        // hide border panel for line components
        if (!(annotationPropertyPanel instanceof LineAnnotationPanel ||
                annotationPropertyPanel instanceof SquareAnnotationPanel ||
                annotationPropertyPanel instanceof CircleAnnotationPanel ||
                annotationPropertyPanel instanceof InkAnnotationPanel ||
                annotationPropertyPanel instanceof FreeTextAnnotationPanel ||
                annotation instanceof PopupAnnotationComponent)) {
            borderPanel.setEnabled(true);
            borderPanel.setAnnotationComponent(annotation);
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.annotationProperties.tab.border.title"),
                    borderPanel);
        }

        // add the new action
        propertiesTabbedPane.addTab(
                messageBundle.getString("viewer.dialog.annotationProperties.tab.action.title"),
                actionsPanel);
        actionsPanel.setEnabled(true);
        actionsPanel.setAnnotationComponent(annotation);

        // check if flags should be shown.
        if (flagsPanel != null) {
            flagsPanel.setEnabled(true);
            flagsPanel.setAnnotationComponent(annotation);
            propertiesTabbedPane.addTab(
                    messageBundle.getString("viewer.dialog.annotationProperties.tab.flags.title"),
                    flagsPanel);
        }

        // disable the component if the annotation is readonly.
        if (annotation.getAnnotation().getFlagReadOnly()) {
            if (annotationPropertyPanel != null) annotationPropertyPanel.setEnabled(false);
            if (actionsPanel != null) actionsPanel.setEnabled(false);
            if (borderPanel != null) borderPanel.setEnabled(false);
            if (flagsPanel != null) flagsPanel.setEnabled(false);
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
