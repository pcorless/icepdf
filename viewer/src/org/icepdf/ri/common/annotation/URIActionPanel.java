package org.icepdf.ri.common.annotation;

import org.icepdf.ri.common.SwingController;
import org.icepdf.core.views.swing.AnnotationComponentImpl;

import java.util.ResourceBundle;
import java.awt.*;

/**
 * URI Action panel used for setting an URI Action type uri string.  URI actions
 * are quite simple only having the String URI value.
 *
 * @since 4.0
 */
public class URIActionPanel extends AnnotationPanelAdapter {

    private SwingController controller;
    private ResourceBundle messageBundle;

    public URIActionPanel(SwingController controller) {
        super(new FlowLayout(FlowLayout.CENTER, 5, 5), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
    }

    public void setAnnotationComponent(AnnotationComponentImpl annotaiton) {
        
    }
}
