/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.ArrayList;

import static org.icepdf.core.util.SystemProperties.INTERACTIVE_ANNOTATIONS;

public class RadioButtonComponent extends AbstractButtonComponent implements PropertyChangeListener {

    public RadioButtonComponent(ButtonWidgetAnnotation annotation, DocumentViewController documentViewController,
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
            // ButtonWidgetAnnotation.reset() has already reset the value and the on/off state.
            resetAppearanceShapes();
            repaint();
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
            else {
                if (fieldDictionary.getParent() != null && fieldDictionary.getParent().getKids() != null) {
                    ArrayList<Object> kids = parentFieldDictionary.getKids();
                    for (Object childWidget : kids) {
                        if (childWidget instanceof ButtonWidgetAnnotation) {
                            ((ButtonWidgetAnnotation) childWidget).turnOff();
                            ((ButtonWidgetAnnotation) childWidget).resetAppearanceStream(getToPageSpaceTransform());
                        }
                    }
                }
                Name newValue = annotation.toggle();
                parentFieldDictionary.setFieldValue(newValue, annotation.getPObjectReference());
            }
        }
        resetAppearanceShapes();

        this.getParent().repaint();
    }
}
