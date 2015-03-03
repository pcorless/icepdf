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

import org.icepdf.core.pobjects.Name;

/**
 * Execute interface for Form actions.
 *
 * @since 5.1
 */
public interface FormAction {

    /**
     * An array identifying which fields to reset or which to exclude from
     * resetting, depending on the setting of the Include/Exclude flag in the
     * Flags entry (see Table 239). Each element of the array shall be either
     * an indirect reference to a field dictionary or (PDF 1.3) a text string
     * representing the fully qualified name of a field. Elements of both kinds
     * may be mixed in the same array.
     * <p/>
     * If this entry is omitted, the Include/Exclude flag shall be ignored; all
     * fields in the document’s interactive form are reset.
     */
    public static final Name FIELDS_KEY = new Name("Fields");

    /**
     * (Optional; inheritable) A set of flags specifying various characteristics
     * of the action (see Table 239). Default value: 0.
     */
    public static final Name FLAGS_KEY = new Name("Flags");

    /**
     * Execute the form action and return the appropriate return code;
     *
     * @return determined by the implementation.
     */
    public int executeFormAction();
}
