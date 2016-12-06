/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.ri.common.utility.annotation.acroform;

import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AnnotationPanelAdapter;
import org.icepdf.ri.common.utility.annotation.AnnotationPropertiesPanel;
import org.icepdf.ri.common.utility.annotation.BorderPanel;
import org.icepdf.ri.common.utility.annotation.FlagsPanel;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;

/**
 * Main properties panel for editing widget annotations and signatures.  When a widget annotation
 * component is selected the panel is enabled and the respective properties panels are made visible and populated
 * with the widgets properties.
 */
@SuppressWarnings("serial")
public class AcroFormPropertiesPanel extends AnnotationPanelAdapter {

    private static final Logger logger =
            Logger.getLogger(AcroFormPropertiesPanel.class.toString());

    // layouts constraint
    private GridBagConstraints constraints;

    private PropertiesManager propertiesManager;

    private JPanel annotationPanel;
    private AnnotationPanelAdapter annotationPropertyPanel;
    private AnnotationPropertiesPanel actionsPanel;
    private BorderPanel borderPanel;
    private FlagsPanel flagsPanel;

    public AcroFormPropertiesPanel(SwingController controller) {
        this(controller, null);
    }

    public AcroFormPropertiesPanel(SwingController controller, PropertiesManager propertiesManager) {
        super(controller);
        setLayout(new BorderLayout());
        this.propertiesManager = propertiesManager;

        // Setup the basics of the panel
        setFocusable(true);

        // setup the action view with default UI components.
        setGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);
    }

    /**
     * Sets the current annotation component to edit, building the appropriate
     * annotation panel and action panel.  If the annotation is null default
     * panels are created.
     *
     * @param annotation annotation properties to show in UI, can be null;
     */
    public void setAnnotationComponent(AnnotationComponent annotation) {

        // remove and add the action panel for action type.
        if (annotationPropertyPanel != null) {
            annotationPanel.remove(annotationPropertyPanel);
        }
        annotationPropertyPanel = buildAnnotationPropertyPanel(annotation);
        if (annotationPropertyPanel != null) {
            annotationPropertyPanel.setAnnotationComponent(annotation);
            addGB(annotationPanel, annotationPropertyPanel, 0, 1, 1, 1);
        }

        // add the new action
        actionsPanel.setAnnotationComponent(annotation);
        if (annotationPropertyPanel instanceof ButtonWidgetAnnotationPanel) {
            actionsPanel.setVisible(true);
        } else {
            actionsPanel.setVisible(false);
        }
        // check if flags should be shown.
        if (flagsPanel != null) {
            flagsPanel.setAnnotationComponent(annotation);
        }

        // hide border panel for line components
        borderPanel.setAnnotationComponent(annotation);

        // disable the component if the annotation is readonly.
        if (!annotation.isEditable()) {
            setEnabled(annotation.isEditable());
        }

        revalidate();
    }

    private void setGUI() {
        annotationPanel = new JPanel(new GridBagLayout());
        add(annotationPanel, BorderLayout.NORTH);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 1, 5, 1);

        // add everything back again.
        annotationPropertyPanel = buildAnnotationPropertyPanel(null);
        actionsPanel = new AnnotationPropertiesPanel(controller);
        borderPanel = new BorderPanel(controller);

        if (propertiesManager == null ||
                PropertiesManager.checkAndStoreBooleanProperty(propertiesManager,
                        PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_FLAGS)) {
            flagsPanel = new FlagsPanel(controller);
        }

        // panels to add.
        if (annotationPropertyPanel != null) {
            addGB(annotationPanel, annotationPropertyPanel, 0, 1, 1, 1);
        }
        addGB(annotationPanel, borderPanel, 0, 2, 1, 1);
        if (flagsPanel != null) {
            addGB(annotationPanel, flagsPanel, 0, 3, 1, 1);
        }
        addGB(annotationPanel, actionsPanel, 0, 4, 1, 1);

    }

    public AnnotationPanelAdapter buildAnnotationPropertyPanel(AnnotationComponent annotationComp) {
        if (annotationComp != null) {
            // check action type
            Annotation annotation = annotationComp.getAnnotation();
            if (annotation != null && annotation instanceof TextWidgetAnnotation) {
                return new TextWidgetAnnotationPanel(controller);
            } else if (annotation != null && annotation instanceof ChoiceWidgetAnnotation) {
                return new ChoiceWidgetAnnotationPanel(controller);
            } else if (annotation != null && annotation instanceof ButtonWidgetAnnotation) {
                return new ButtonWidgetAnnotationPanel(controller);
            } else if (annotation != null && annotation instanceof SignatureWidgetAnnotation) {
                return new SignatureWidgetAnnotationPanel(controller);
            }
        }
        return null;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // apply to child components.
        if (annotationPropertyPanel != null && actionsPanel != null) {
            annotationPropertyPanel.setEnabled(enabled);
            actionsPanel.setEnabled(enabled);
            flagsPanel.setEnabled(enabled);
            borderPanel.setEnabled(enabled);
        }
    }

}

