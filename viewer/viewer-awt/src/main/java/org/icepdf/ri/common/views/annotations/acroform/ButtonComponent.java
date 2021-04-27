package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ButtonComponent extends AbstractAnnotationComponent<ButtonWidgetAnnotation>
        implements PropertyChangeListener {


    public ButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
                           AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
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
