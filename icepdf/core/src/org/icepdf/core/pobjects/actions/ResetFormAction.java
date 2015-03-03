/*
 * Copyright 2006-2015 ICEsoft Technologies Inc.
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

import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * Upon invocation of a reset-form action, a conforming processor shall reset
 * selected interactive form fields to their default values; that is, it shall
 * set the value of the V entry in the field dictionary to that of the DV entry.
 * If no default value is defined for a field, its V entry shall be removed.
 * For fields that can have no value (such as pushbuttons), the action has no
 * effect. Table 238 shows the action dictionary entries specific to this type
 * of action.
 * <p/>
 * The value of the action dictionary’s Flags entry is a non-negative containing
 * flags specifying various characteristics of the action. Bit positions within
 * the flag word shall be numbered starting from 1 (low-order). Only one flag is
 * defined for this type of action. All undefined flag bits shall be reserved
 * and shall be set to 0.
 *
 * @since 5.1
 */
public class ResetFormAction extends Action implements FormAction {

    /**
     * If clear, the Fields array specifies which fields to reset.
     * (All descendants of the specified fields in the field hierarchy are
     * reset as well.) If set, the Fields array indicates which fields to
     * exclude from resetting; that is, all fields in the document’s interactive
     * form shall be reset except those listed in the Fields array.
     */
    public int INCLUDE_EXCLUDE_BIT = 0X0000001;

    public ResetFormAction(Library l, HashMap h) {
        super(l, h);
    }

    public int executeFormAction() {
        return 0;
    }
}
