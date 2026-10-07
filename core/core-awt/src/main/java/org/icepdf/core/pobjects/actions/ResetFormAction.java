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
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.util.Library;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Upon invocation of a reset-form action, a conforming processor shall reset
 * selected interactive form fields to their default values; that is, it shall
 * set the value of the V entry in the field dictionary to that of the DV entry.
 * If no default value is defined for a field, its V entry shall be removed.
 * For fields that can have no value (such as pushButtons), the action has no
 * effect. Table 238 shows the action dictionary entries specific to this type
 * of action.
 * <br>
 * The value of the action dictionary’s Flags entry is a non-negative containing
 * <p>
 * flags specifying various characteristics of the action. Bit positions within
 * the flag word shall be numbered starting from 1 (low-order). Only one flag is
 * defined for this type of action. All undefined flag bits shall be reserved
 * and shall be set to 0.
 *
 * @since 5.1
 */
public class ResetFormAction extends FormAction {

    /**
     * If clear, the Fields array specifies which fields to reset.
     * (All descendants of the specified fields in the field hierarchy are
     * reset as well.) If set, the Fields array indicates which fields to
     * exclude from resetting; that is, all fields in the document’s interactive
     * form shall be reset except those listed in the Fields array.
     */
    public final int INCLUDE_EXCLUDE_BIT = 0X0000001;

    public ResetFormAction(Library l, DictionaryEntries h) {
        super(l, h);
    }

    /**
     * Upon invocation of a reset-form action, a conforming processor shall reset
     * selected interactive form fields to their default values; that is, it shall
     * set the value of the V entry in the field dictionary to that of the DV entry.
     * If no default value is defined for a field, its V entry shall be removed.
     * For fields that can have no value (such as pushButtons), the action has no
     * effect. Table 238 shows the action dictionary entries specific to this type
     * of action.
     *
     * @param x x-coordinate of the mouse event that actuated the submit.
     * @param y y-coordinate of the mouse event that actuated the submit.
     * @return value of one if reset was successful, zero if not.
     */
    public int executeFormAction(int x, int y) {
        // get a reference to the form data
        InteractiveForm interactiveForm = library.getCatalog().getInteractiveForm();
        if (interactiveForm == null || interactiveForm.getFields() == null) {
            return 0;
        }
        Selection selection = new Selection(library, entries, isIncludeExclude());
        for (Object tmp : interactiveForm.getFields()) {
            descendFormTree(tmp, selection, false);
        }
        // update the annotation an component values.
        return 0;
    }

    /**
     * Recursively reset all the form fields.
     *
     * @param formNode root form node.
     */
    protected void descendFormTree(Object formNode) {
        descendFormTree(formNode, Selection.ALL, false);
    }

    /**
     * Resets the fields under {@code formNode} that the action selects.
     *
     * @param named whether an ancestor of {@code formNode} is listed in /Fields (listing a field
     *              covers its descendants)
     */
    private void descendFormTree(Object formNode, Selection selection, boolean named) {
        if (formNode instanceof Reference) {
            formNode = library.getObject((Reference) formNode);
        }
        if (formNode instanceof AbstractWidgetAnnotation) {
            AbstractWidgetAnnotation<?> widget = (AbstractWidgetAnnotation<?>) formNode;
            boolean listed = named || selection.lists(widget.getPObjectReference(), widget.getFieldDictionary());
            if (selection.resets(listed)) {
                widget.reset();
            }
        } else if (formNode instanceof FieldDictionary) {
            // iterate over the kid's array.
            FieldDictionary field = (FieldDictionary) formNode;
            boolean listed = named || selection.lists(field.getPObjectReference(), field);
            List<Object> kids = field.getKids();
            if (kids != null) {
                for (Object kid : kids) {
                    descendFormTree(kid, selection, listed);
                }
            }
        }
    }

    /**
     * Which fields the action resets: all of them with no /Fields; with /Fields, the listed fields
     * and their descendants, or (Include/Exclude flag set) everything else.  /Fields entries are
     * indirect references to fields or fully qualified names.
     */
    private static final class Selection {
        static final Selection ALL = new Selection(Set.of(), Set.of(), false);

        private final Set<Reference> references;
        private final Set<String> names;
        private final boolean exclude;

        private Selection(Set<Reference> references, Set<String> names, boolean exclude) {
            this.references = references;
            this.names = names;
            this.exclude = exclude;
        }

        Selection(Library library, DictionaryEntries entries, boolean exclude) {
            this(new HashSet<>(), new HashSet<>(), exclude);
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

        boolean lists(Reference reference, FieldDictionary field) {
            if (reference != null && references.contains(reference)) {
                return true;
            }
            return field != null && !names.isEmpty() && names.contains(field.getFullyQualifiedFieldName());
        }

        boolean resets(boolean listed) {
            if (references.isEmpty() && names.isEmpty()) {
                return true;
            }
            return listed != exclude;
        }
    }

    /**
     * @return true if bit is set, otherwise false.
     * @see #INCLUDE_EXCLUDE_BIT
     */
    public boolean isIncludeExclude() {
        return (getFlags() & INCLUDE_EXCLUDE_BIT) == INCLUDE_EXCLUDE_BIT;
    }
}
