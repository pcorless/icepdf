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

package org.icepdf.core.pobjects.actions;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Execute interface for Form actions.
 *
 * @since 5.1
 */
public abstract class FormAction extends Action {

    /**
     * (Required) A URL file specification (see 7.11.5, "URL Specifications") giving the uniform resource locator
     * (URL) of the script at the Web server that will process the submission.
     */
    public static final Name F_KEY = new Name("F");

    /**
     * An array identifying which fields to reset or which to exclude from
     * resetting, depending on the setting of the Include/Exclude flag in the
     * Flags entry (see Table 239). Each element of the array shall be either
     * an indirect reference to a field dictionary or (PDF 1.3) a text string
     * representing the fully qualified name of a field. Elements of both kinds
     * may be mixed in the same array.
     * <br>
     * If this entry is omitted, the Include/Exclude flag shall be ignored, and all
     * fields in the document’s interactive form shall be submitted except those whose
     * NoExport flag (see Table 221) is set. Fields with no values may also be excluded,
     * as dictated by the value of the IncludeNoValueFields flag; see Table 237.
     */
    public static final Name FIELDS_KEY = new Name("Fields");

    /**
     * (Optional; inheritable) A set of flags specifying various characteristics
     * of the action (see Table 239). Default value: 0.
     */
    public static final Name FLAGS_KEY = new Name("Flags");

    public FormAction(Library l, DictionaryEntries h) {
        super(l, h);
    }

    /**
     * (Optional; inheritable) A set of flags specifying various characteristics of the action (see Table 239).
     * Default value: 0.
     *
     * @return flag value
     */
    public int getFlags() {
        // behaviour flags
        return library.getInt(entries, FLAGS_KEY);
    }

    /**
     * Execute the form action and return the appropriate return code;
     *
     * @param x x coordinate of the actuating input device.
     * @param y y coordinate of the actuating input device.
     * @return determined by the implementation.
     */
    public abstract int executeFormAction(int x, int y);

    /** A field or widget in the field tree, with the reference it was reached by (may be null). */
    protected static final class FieldNode {
        final Object node;
        final Reference reference;

        FieldNode(Object node, Reference reference) {
            this.node = node;
            this.reference = reference;
        }

        /** The node's field dictionary (a widget's merged or parent field), or null. */
        FieldDictionary field() {
            if (node instanceof AbstractWidgetAnnotation) {
                return ((AbstractWidgetAnnotation<?>) node).getFieldDictionary();
            }
            return node instanceof FieldDictionary ? (FieldDictionary) node : null;
        }

        /** True for a node that names a field of its own (a kid without /T is a widget of its parent). */
        boolean isNamed() {
            FieldDictionary field = field();
            String partial = field != null ? field.getPartialFieldName() : null;
            return partial != null && !partial.isEmpty();
        }
    }

    /** The form's top-level fields (the AcroForm /Fields array), with their references. */
    protected List<FieldNode> rootFields() {
        InteractiveForm form = library.getCatalog().getInteractiveForm();
        return form == null ? new ArrayList<>() : nodes(library.getObject(form.getEntries(), FIELDS_KEY));
    }

    /** A field's kids, with their references. */
    protected List<FieldNode> kidsOf(FieldDictionary field) {
        return nodes(library.getObject(field.getEntries(), FieldDictionary.KIDS_KEY));
    }

    /**
     * Resolves a /Fields or /Kids array.  Walking the arrays rather than the built field lists keeps
     * each entry's reference, which /Fields may name: a field built from a bare dictionary has none.
     */
    private List<FieldNode> nodes(Object array) {
        List<FieldNode> out = new ArrayList<>();
        if (!(array instanceof List)) {
            return out;
        }
        for (Object entry : (List<?>) array) {
            FieldNode node = fieldNode(entry);
            if (node != null) {
                out.add(node);
            }
        }
        return out;
    }

    /**
     * Resolves one field-tree entry: a reference, a bare field dictionary, a built field or a widget.
     *
     * @return the node, or null when the entry is not a field or widget
     */
    protected FieldNode fieldNode(Object entry) {
        Reference reference = entry instanceof Reference ? (Reference) entry : null;
        Object node = reference != null ? library.getObject(reference) : entry;
        if (node instanceof PObject) {
            node = ((PObject) node).getObject();
        }
        if (node instanceof DictionaryEntries) {
            FieldDictionary field = FieldDictionaryFactory.buildField(library, (DictionaryEntries) node);
            if (reference != null) {
                field.setPObjectReference(reference);
            }
            node = field;
        }
        if (node instanceof AbstractWidgetAnnotation || node instanceof FieldDictionary) {
            return new FieldNode(node, reference != null ? reference : referenceOf(node));
        }
        return null;
    }

    private static Reference referenceOf(Object node) {
        if (node instanceof AbstractWidgetAnnotation) {
            return ((AbstractWidgetAnnotation<?>) node).getPObjectReference();
        }
        return node instanceof FieldDictionary ? ((FieldDictionary) node).getPObjectReference() : null;
    }

    /**
     * Which fields a form action applies to (ISO 32000-1 12.7.5.2-3): every field when there is no
     * /Fields array; otherwise the listed fields and all their descendants, or (Include/Exclude flag
     * set) every field except those.  /Fields entries are indirect references to fields or fully
     * qualified field names, mixed freely.
     */
    protected static final class FieldSelection {
        private final Set<Reference> references = new HashSet<>();
        private final Set<String> names = new HashSet<>();
        private final boolean exclude;

        protected FieldSelection(Library library, DictionaryEntries entries, boolean exclude) {
            this.exclude = exclude;
            Object fields = library.getObject(entries, FIELDS_KEY);
            if (fields instanceof List) {
                for (Object entry : (List<?>) fields) {
                    if (entry instanceof Reference) {
                        references.add((Reference) entry);
                    } else if (entry instanceof StringObject) {
                        names.add(((StringObject) entry).getDecryptedLiteralString(library.getSecurityManager()));
                    } else if (entry instanceof String) {
                        names.add((String) entry);
                    }
                }
            }
        }

        /** Whether /Fields names this field (by reference or fully qualified name). */
        protected boolean lists(Reference reference, FieldDictionary field) {
            if (reference != null && references.contains(reference)) {
                return true;
            }
            return field != null && !names.isEmpty() && names.contains(field.getFullyQualifiedFieldName());
        }

        /**
         * Whether the action applies to a field.
         *
         * @param listed whether the field or one of its ancestors is listed in /Fields
         */
        protected boolean applies(boolean listed) {
            if (references.isEmpty() && names.isEmpty()) {
                return true;
            }
            return listed != exclude;
        }
    }
}
