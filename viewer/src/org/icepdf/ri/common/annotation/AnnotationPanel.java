package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.views.swing.AnnotationComponentImpl;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

/**
 * Annotation Panel is responsible for viewing and editing Annotation properties
 * which also include annotation action properties.
 * <p/>
 * Currently only Link Annotation are supported and the Action types GoTo and
 * URI.  It will be quite easy to add more properites in the future given the
 * factory nature of this class
 */
public class AnnotationPanel extends AnnotationPanelAdapter {

    private SwingController controller;
    private ResourceBundle messageBundle;

    // layouts constraint
    private GridBagConstraints constraints;

    private JPanel annotationPanel;
    private AnnotationPanelAdapter annotationPropertyPanel;
    private ActionsPanel actionsPanel;

    public AnnotationPanel(SwingController controller) {
        super(new BorderLayout(), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        // Setup the basics of the panel
        setFocusable(true);

        // setup the action view with default UI components.
        setGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);
    }

    public AnnotationPanelAdapter buildAnnotationPropertyPanel(AnnotationComponentImpl annotationComp) {
        if (annotationComp != null) {
            // check action type
            Annotation annotation = annotationComp.getAnnotation();
            if (annotation != null && annotation instanceof LinkAnnotation) {
                return new LinkAnnotationPanel(controller);
            }
            // todo add other panels for other action types.
        }
        // default panel
        return new LinkAnnotationPanel(controller);
    }

    /**
     * Sets the current annotation component to edit, building the appropriate
     * annotation panel and action panel.  If the annotation is null default
     * panels are created.
     *
     * @param annotaiton annotation properites to show in UI, can be null;
     */
    public void setAnnotationComponent(AnnotationComponentImpl annotaiton) {

        // remove and add the action panel for action type.
        if (annotationPropertyPanel != null){
            annotationPanel.remove(annotationPropertyPanel);
            annotationPropertyPanel = buildAnnotationPropertyPanel(annotaiton);
            annotationPropertyPanel.setAnnotationComponent(annotaiton);
            addGB(annotationPanel, annotationPropertyPanel, 0, 0, 1, 1);
        }

        // add the new action
        actionsPanel.setAnnotationComponent(annotaiton);

        revalidate();
    }

    private void setGUI(){
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
        actionsPanel = new ActionsPanel(controller);

        // panels to add.
        addGB(annotationPanel, annotationPropertyPanel, 0, 0, 1, 1);
        addGB(annotationPanel, actionsPanel, 0, 1, 1, 1);

    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // apply to child components.
        if (annotationPropertyPanel != null && actionsPanel != null){
            annotationPropertyPanel.setEnabled(enabled);
            actionsPanel.setEnabled(enabled);
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
