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

import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;

/**
 * A form field's value changed, handed to {@link PdfView#onFormFieldChangedProperty()}: by the user
 * (typing, a click, a choice), by a reset, by {@link PdfView#setFieldValue}, or by undo/redo
 * (which report the change being made, so old and new swap on undo).  Values are plain Java, as
 * {@link PdfView#getFieldValue} returns them.
 *
 * @param field    a widget of the field (a radio group reports one of its kids)
 * @param name     the field's fully-qualified name
 * @param oldValue the value before
 * @param newValue the value after
 */
public record FormFieldChangeEvent(AbstractWidgetAnnotation field, String name, Object oldValue, Object newValue) {
}
