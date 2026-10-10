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
package org.icepdf.fx.view;

import javafx.event.EventTarget;
import javafx.event.EventType;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;

/**
 * A form field's value changed ({@link PdfView#setOnFormFieldChanged}): by the user, by
 * {@link PdfView#setFieldValue}, by a reset, or by undo/redo (old and new swapped).
 */
public class FormFieldChangeEvent extends PdfViewEvent {

    public static final EventType<FormFieldChangeEvent> FIELD_CHANGED =
            new EventType<>(PdfViewEvent.ANY, "FIELD_CHANGED");

    private final transient AbstractWidgetAnnotation field;
    private final String name;
    private final transient Object oldValue;
    private final transient Object newValue;

    public FormFieldChangeEvent(Object source, EventTarget target, AbstractWidgetAnnotation field, String name,
                                Object oldValue, Object newValue) {
        super(source, target, FIELD_CHANGED);
        this.field = field;
        this.name = name;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /** The widget edited. */
    public AbstractWidgetAnnotation getField() {
        return field;
    }

    /** The field's fully-qualified name. */
    public String getName() {
        return name;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return "FormFieldChangeEvent[name=" + name + ", oldValue=" + oldValue + ", newValue=" + newValue + "]";
    }
}
