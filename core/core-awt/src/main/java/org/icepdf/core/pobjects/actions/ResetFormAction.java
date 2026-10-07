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
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.util.Library;


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

    // guards the field walk against a malformed /Kids cycle.
    private static final int MAX_FIELD_DEPTH = 64;

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
        FieldSelection selection = new FieldSelection(library, entries, isIncludeExclude());
        for (FieldNode field : rootFields()) {
            reset(field, selection, false, 0);
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
        FieldNode node = fieldNode(formNode);
        if (node != null) {
            reset(node, new FieldSelection(library, new DictionaryEntries(), false), false, 0);
        }
    }

    /**
     * Resets the widgets under a field that the action selects.
     *
     * @param named whether an ancestor is listed in /Fields (listing a field covers its descendants)
     */
    private void reset(FieldNode node, FieldSelection selection, boolean named, int depth) {
        if (depth > MAX_FIELD_DEPTH) {
            return;
        }
        boolean listed = named || selection.lists(node.reference, node.field());
        if (node.node instanceof AbstractWidgetAnnotation) {
            if (selection.applies(listed)) {
                ((AbstractWidgetAnnotation<?>) node.node).reset();
            }
        } else if (node.field() != null) {
            for (FieldNode kid : kidsOf(node.field())) {
                reset(kid, selection, listed, depth + 1);
            }
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
