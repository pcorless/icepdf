package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.ButtonFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static org.icepdf.core.util.SystemProperties.INTERACTIVE_ANNOTATIONS;

public class CheckButtonComponent extends AbstractButtonComponent implements PropertyChangeListener {

    public CheckButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        if (INTERACTIVE_ANNOTATIONS &&
                annotation.allowScreenOrPrintRenderingOrInteraction()) {
            ActionListener actionListener = actionEvent -> buttonActuated();
            KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
            registerKeyboardAction(actionListener, stroke, JComponent.WHEN_FOCUSED);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if ("valueFieldReset".equals(propertyName)) {
            ButtonFieldDictionary fieldDictionary = annotation.getFieldDictionary();
            ButtonFieldDictionary parentFieldDictionary = (ButtonFieldDictionary) fieldDictionary.getParent();
            Object defaultFieldValue = fieldDictionary.getDefaultFieldValue();
            if (defaultFieldValue instanceof Name) {
                fieldDictionary.setFieldValue(defaultFieldValue, annotation.getPObjectReference());
                return;
            }
            if (parentFieldDictionary != null && parentFieldDictionary.getDefaultFieldValue() != null) {
                fieldDictionary.setFieldValue(parentFieldDictionary.getDefaultFieldValue(),
                        parentFieldDictionary.getPObjectReference());
                return;
            }
            annotation.turnOff();
            resetAppearanceShapes();
        }
    }

    @Override
    protected void buttonActuated() {
        ButtonFieldDictionary fieldDictionary = annotation.getFieldDictionary();
        FieldDictionary parentFieldDictionary = fieldDictionary.getParent();
        if (parentFieldDictionary == null) {
            parentFieldDictionary = fieldDictionary;
        }
        Name newValue = annotation.toggle();
        fieldDictionary.setFieldValue(newValue, annotation.getPObjectReference());
        parentFieldDictionary.setFieldValue(newValue, annotation.getPObjectReference());
        resetAppearanceShapes();
        this.getParent().repaint();
    }
}
