package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class PushButtonComponent extends AbstractButtonComponent implements PropertyChangeListener {

    public PushButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
                               AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        ActionListener actionListener = actionEvent -> buttonActuated();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        registerKeyboardAction(actionListener, stroke, JComponent.WHEN_FOCUSED);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // nothing to do, buttons don't have a value
    }

    @Override
    protected void buttonActuated() {
        this.getParent().repaint();
    }
}
