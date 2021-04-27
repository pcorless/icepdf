package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

public class TexComponent extends AbstractAnnotationComponent<TextWidgetAnnotation>
        implements PropertyChangeListener {

    private static final Logger logger =
            Logger.getLogger(TexComponent.class.toString());

    public TexComponent(TextWidgetAnnotation annotation,
                        final DocumentViewController documentViewController,
                        final AbstractPageViewComponent pageViewComponent) {
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
