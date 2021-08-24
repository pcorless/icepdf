package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;

public abstract class AbstractButtonComponent extends AbstractAnnotationComponent<ButtonWidgetAnnotation>
        implements PropertyChangeListener {


    public AbstractButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
                                   AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        isShowInvisibleBorder = true;
        isResizable = false;
        isMovable = false;

        if (!annotation.allowScreenOrPrintRenderingOrInteraction()) {
            isShowInvisibleBorder = false;
            isEditable = false;
            isRollover = false;
            isResizable = false;
            isMovable = false;
        }
        this.annotation.addPropertyChangeListener(this);
    }

    protected abstract void buttonActuated();

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        buttonActuated();
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void resetAppearanceShapes() {
        annotation.resetAppearanceStream(getToPageSpaceTransform());
    }

    public static ButtonWidgetAnnotation getButtonWidgetAnnotation(Annotation annotation) {
        ButtonWidgetAnnotation widget = null;
        if (annotation instanceof ButtonWidgetAnnotation) {
            widget = (ButtonWidgetAnnotation) annotation;
        } else {
            try {
                widget = new ButtonWidgetAnnotation(annotation);
                widget.init();
            } catch (InterruptedException e) {
                logger.fine("ButtonWidgetAnnotation initialization interrupted.");
            }
        }
        return widget;
    }
}
