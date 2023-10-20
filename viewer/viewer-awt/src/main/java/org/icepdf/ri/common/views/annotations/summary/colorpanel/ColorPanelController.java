package org.icepdf.ri.common.views.annotations.summary.colorpanel;

import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ColorPanelController {
    public final ColorLabelPanel panel;
    public final PropertyChangeListener listener;

    public ColorPanelController(final ColorLabelPanel panel) {
        this.panel = panel;
        this.listener = new PropertyListener();
        panel.addPropertyChangeListener(PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE, listener);
    }

    private class PropertyListener implements PropertyChangeListener {
        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
            final Object newValue = evt.getNewValue();
            final String propertyName = evt.getPropertyName();
            if (PropertyConstants.ANNOTATION_SUMMARY_BOX_FONT_SIZE_CHANGE.equals(propertyName)) {
                final Component[] comps = panel.getDraggableAnnotationPanel().getComponents();
                for (final Component component : comps) {
                    if (component instanceof AnnotationSummaryComponent) {
                        ((AnnotationSummaryComponent) component).setFontSize((float) newValue);
                    }
                }
            }

        }
    }

    public void updateFontFamily(final String familyName) {
        if (familyName != null) {
            for (final Component comp : panel.getDraggableAnnotationPanel().getComponents()) {
                if (comp instanceof AnnotationSummaryComponent) {
                    ((AnnotationSummaryComponent) comp).setFontFamily(familyName);
                }
            }
        }
    }
}
