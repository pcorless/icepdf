/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
     * <p/>
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

    /**
     * If clear, the Fields array (see Table 236) specifies which fields to include in the submission.
     * (All descendants of the specified fields in the field hierarchy shall be submitted as well.)
     * <p/>
     * If set, the Fields array tells which fields to exclude. All fields in the document’s interactive form shall be
     * submitted except those listed in the Fields array and those whose NoExport flag (see Table 221)
     * is set and fields with no values if the IncludeNoValueFields flag is clear.
     */
    public static int INCLUDE_EXCLUDE_BIT = 0X0000001;  // bit 1

    public FormAction(Library l, HashMap h) {
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
     * An array identifying which fields to reset or which to exclude from
     * resetting, depending on the setting of the Include/Exclude flag in the
     * Flags entry (see Table 239). Each element of the array shall be either
     * an indirect reference to a field dictionary or (PDF 1.3) a text string
     * representing the fully qualified name of a field. Elements of both kinds
     * may be mixed in the same array.
     * <p/>
     * If this entry is omitted, the Include/Exclude flag shall be ignored, and all
     * fields in the document’s interactive form shall be submitted except those whose
     * NoExport flag (see Table 221) is set. Fields with no values may also be excluded,
     * as dictated by the value of the IncludeNoValueFields flag; see Table 237.
     *
     * @return list of fields if present otherwise null.
     */
    public List getFields() {
        Object tmp = library.getArray(entries, FIELDS_KEY);
        if (tmp != null) {
            return (List) tmp;
        }
        return null;
    }

    /**
     * Execute the form action and return the appropriate return code;
     *
     * @return determined by the implementation.
     */
    public abstract int executeFormAction(int x, int y);


    /**
     * Sets the field value, asigning an array of field elements indirect references. These fields
     * will either be incluided or excluded on a form submit depending of the value of the flag
     * INCLUDE_EXCLUDE_BIT.
     *
     * @param fields array of indirect references to associated fields.
     */
    public void setFieldsValue(ArrayList<Object> fields) {
        entries.put(FIELDS_KEY, fields);
    }

    /**
     * Set the specified flag key to either enabled or disabled.
     *
     * @param flagKey flag key to set.
     * @param enable  true or false key value.
     */
    public void setFlag(final int flagKey, boolean enable) {
        int flag = getInt(FLAGS_KEY);
        boolean isEnabled = (flag & flagKey) != 0;
        if (!enable && isEnabled) {
            flag = flag ^ flagKey;
            entries.put(FLAGS_KEY, flag);
        } else if (enable && !isEnabled) {
            flag = flag | flagKey;
            entries.put(FLAGS_KEY, flag);
        }
    }

    /**
     * @see #INCLUDE_EXCLUDE_BIT
     */
    public boolean isIncludeExclude() {
        return (getFlags() & INCLUDE_EXCLUDE_BIT) == INCLUDE_EXCLUDE_BIT;
    }
}
