package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

public abstract class AbstractChoiceComponent extends AbstractAnnotationComponent<ChoiceWidgetAnnotation> implements
        FocusListener, PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(AbstractChoiceComponent.class.toString());

    public AbstractChoiceComponent(ChoiceWidgetAnnotation annotation, DocumentViewController documentViewController,
                                   AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
        setFocusable(false);

        isShowInvisibleBorder = true;
        isResizable = false;
        isMovable = false;

        if (!annotation.allowScreenOrPrintRenderingOrInteraction()) {
            isEditable = false;
            isRollover = false;
            isMovable = false;
            isResizable = false;
            isShowInvisibleBorder = false;
        }

        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener(this);

        revalidate();
    }

    @Override
    public void focusGained(FocusEvent e) {
        super.focusGained(e);
    }

    @Override
    public abstract void validate();

    @Override
    public void dispose() {
        super.dispose();
        KeyboardFocusManager focusManager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.removePropertyChangeListener(this);
    }

    @Override
    public void paintComponent(Graphics g) {
        // look into this, seems wrong
        isShowInvisibleBorder = false;
    }

    @Override
    public void resetAppearanceShapes() {
        annotation.resetAppearanceStream(getToPageSpaceTransform());
    }

    public abstract boolean isActive();

    public static ChoiceWidgetAnnotation getButtonWidgetAnnotation(Annotation annotation) {
        ChoiceWidgetAnnotation widget = null;
        if (annotation instanceof ChoiceWidgetAnnotation) {
            widget = (ChoiceWidgetAnnotation) annotation;
        } else {
            try {
                widget = new ChoiceWidgetAnnotation(annotation);
                widget.init();
            } catch (InterruptedException e) {
                logger.fine("ChoiceWidgetAnnotation initialization interrupted.");
            }
        }
        return widget;
    }

}

