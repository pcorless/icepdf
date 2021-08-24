package org.icepdf.ri.common.views.annotations.acroform;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.ButtonFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;

public class RadioButtonComponent extends AbstractButtonComponent implements PropertyChangeListener {

    public RadioButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);

        ActionListener actionListener = actionEvent -> buttonActuated();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
        registerKeyboardAction(actionListener, stroke, JComponent.WHEN_FOCUSED);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if ("valueFieldReset".equals(propertyName)) {
            ButtonFieldDictionary fieldDictionary = annotation.getFieldDictionary();
            ButtonFieldDictionary parentFieldDictionary = (ButtonFieldDictionary) fieldDictionary.getParent();
            // turn everything off
            if (parentFieldDictionary != null && !parentFieldDictionary.hasFieldValue()) {
                for (Object childWidget : parentFieldDictionary.getKids()) {
                    if (childWidget instanceof ButtonWidgetAnnotation) {
                        ((ButtonWidgetAnnotation) childWidget).turnOff();
                    }
                }
            } // find a default value and turn on.
            else if (parentFieldDictionary != null && parentFieldDictionary.hasFieldValue()) {
                Name defaultValue = (Name) parentFieldDictionary.getDefaultFieldValue();
                parentFieldDictionary.setFieldValue(defaultValue, parentFieldDictionary.getPObjectReference());
                ButtonWidgetAnnotation buttonWidgetAnnotation;
                for (Object childWidget : parentFieldDictionary.getKids()) {
                    if (childWidget instanceof ButtonWidgetAnnotation) {
                        buttonWidgetAnnotation = (ButtonWidgetAnnotation) childWidget;
                        buttonWidgetAnnotation.turnOff();
                        // update selected state
                        Name currentAppearance = buttonWidgetAnnotation.getCurrentAppearance();
                        HashMap<Name, Appearance> appearances = buttonWidgetAnnotation.getAppearances();
                        Appearance appearance = appearances.get(currentAppearance);
                        if (appearance.getOnName().equals(defaultValue)) {
                            appearance.setSelectedName(appearance.getOnName());
                        }
                    }
                }
            }
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
        ButtonFieldDictionary parentButtonFieldDictionary = null;
        if (parentFieldDictionary instanceof ButtonFieldDictionary) {
            parentButtonFieldDictionary = (ButtonFieldDictionary) parentFieldDictionary;
        }
        if (fieldDictionary.isRadioInUnison() ||
                (parentButtonFieldDictionary != null && parentButtonFieldDictionary.isRadioInUnison())) {
            if (fieldDictionary.getParent() != null && fieldDictionary.getParent().getKids() != null) {
                for (Object childWidget : fieldDictionary.getParent().getKids()) {
                    if (childWidget instanceof ButtonWidgetAnnotation) {
                        Name newValue = ((ButtonWidgetAnnotation) childWidget).toggle();
                        parentFieldDictionary.setFieldValue(newValue, annotation.getPObjectReference());
                    }
                }
            }
        } else {
            if (annotation.isOn()) {
                return;
            }
            // turn off all children elements
            else {
                if (fieldDictionary.getParent() != null && fieldDictionary.getParent().getKids() != null) {
                    ArrayList kids = parentFieldDictionary.getKids();
                    for (Object childWidget : kids) {
                        if (childWidget instanceof ButtonWidgetAnnotation) {
                            ((ButtonWidgetAnnotation) childWidget).turnOff();
                            ((ButtonWidgetAnnotation) childWidget).resetAppearanceStream(getToPageSpaceTransform());
                        }
                    }
                }
                // save value to parent
                Name newValue = annotation.toggle();
                parentFieldDictionary.setFieldValue(newValue, annotation.getPObjectReference());
            }
        }
        resetAppearanceShapes();

        this.getParent().repaint();
    }
}
