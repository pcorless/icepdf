package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

public class ChoiceComponent extends AbstractAnnotationComponent<ChoiceWidgetAnnotation> implements
        AdjustmentListener, FocusListener, PropertyChangeListener {

    private static final Logger logger =
            Logger.getLogger(ChoiceComponent.class.toString());

    public ChoiceComponent(ChoiceWidgetAnnotation annotation, DocumentViewController documentViewController,
                           AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent e) {

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void resetAppearanceShapes() {

    }
}
