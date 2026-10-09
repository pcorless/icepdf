/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.util.Library;

/**
 * The FieldDictionaryFactory is responsible for building out the interactive form field tree.  When a none terminal
 * field is encountered this factor can be used to build an appropriate field dictionary for the given /FT key.
 *
 * @since 5.2
 */
public class FieldDictionaryFactory {

    public static final Name TYPE_BUTTON = new Name("Btn");
    public static final Name TYPE_TEXT = new Name("Tx");
    public static final Name TYPE_CHOICE= new Name("Ch");
    public static final Name TYPE_SIGNATURE = new Name("Sig");

    private FieldDictionaryFactory() {}

    /**
     * Creates a new field dictionary object of the type specified by the type constant.
     *
     * @param library library to register action with
     * @param entries field name value pairs.
     * @return new field dictionary object of the specified field type.
     */
    /**
     * A form field, or its widget annotation when the dictionary is a field merged with its widget
     * (it has /Subtype /Widget).  /Type /Annot is optional on a widget, and without it the parser
     * leaves the dictionary as plain entries, so the field list held a bare field dictionary where a
     * typed one gives a widget: a signature field showed no signature.  The widget is registered with
     * the library under its reference, so the page that shows it uses the same object.
     *
     * @param reference the dictionary's object reference, or null for a direct object
     * @return the widget annotation, or a field dictionary
     */
    public static Object buildFieldOrWidget(Library library, DictionaryEntries entries, Reference reference) {
        if (Annotation.SUBTYPE_WIDGET.equals(library.getName(entries, Annotation.SUBTYPE_KEY))) {
            Annotation widget = Annotation.buildAnnotation(library, entries);
            if (widget instanceof AbstractWidgetAnnotation) {
                if (reference != null) {
                    widget.setPObjectReference(reference);
                    library.addObject(widget, reference);
                }
                return widget;
            }
        }
        return buildField(library, entries);
    }

    public static FieldDictionary buildField(Library library,
                                             DictionaryEntries entries) {
        FieldDictionary fieldDictionary;
        Name fieldType = library.getName(entries, FieldDictionary.FT_KEY);
        if (TYPE_BUTTON.equals(fieldType)) {
            fieldDictionary = new ButtonFieldDictionary(library, entries);
        } else if (TYPE_TEXT.equals(fieldType)) {
            fieldDictionary = new TextFieldDictionary(library, entries);
        } else if (TYPE_CHOICE.equals(fieldType)) {
            fieldDictionary = new ChoiceFieldDictionary(library, entries);
        } else if (TYPE_SIGNATURE.equals(fieldType)) {
            fieldDictionary = new SignatureFieldDictionary(library, entries);
        }else{
            fieldDictionary = new FieldDictionary(library, entries);
        }
        return fieldDictionary;
    }
}
