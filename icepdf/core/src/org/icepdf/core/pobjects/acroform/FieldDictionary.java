/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.WidgetAnnotation;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Each field in a document’s interactive form shall be defined by a field
 * dictionary, which shall be an indirect object. The field dictionaries may be
 * organized hierarchically into one or more tree structures. Many field
 * attributes are inheritable, meaning that if they are not explicitly specified
 * for a given field, their values are taken from those of its parent in the field
 * hierarchy. Such inheritable attributes shall be designated as such in the
 * Tables 220 and 221. The designation (Required; inheritable) means that an
 * attribute shall be defined for every field, whether explicitly in its own
 * field dictionary or by inheritance from an ancestor in the hierarchy. Table
 * 220 shows those entries that are common to all field dictionaries, regardless
 * of type. Entries that pertain only to a particular type of field are described
 * in the relevant sub-clauses in Table 220.
 *
 * @since 5.0
 */
public class FieldDictionary extends Dictionary {

    /**
     * Required for terminal fields; inheritable) The type of field that this
     * dictionary describes:
     * <p/>
     * Btn -> Button (see 12.7.4.2, “Button Fields”)
     * <p/>
     * Tx -> Text (see 12.7.4.3, “Text Fields”)
     * <p/>
     * Ch -> Choice (see 12.7.4.4, “Choice Fields”)
     * <p/>
     * Sig(PDF 1.3) -> Signature (see 12.7.4.5, “Signature Fields”)
     * <p/>
     * This entry may be present in a non-terminal field (one whose descendants
     * are fields) to provide an inheritable FT value. However, a non-terminal
     * field does not logically have a type of its own; it is merely a container
     * for inheritable attributes that are intended for descendant terminal
     * fields of any type.
     */
    public static final Name FT_KEY = new Name("FT");
    /**
     * Button terminal field.
     */
    public static final Name FT_BUTTON_VALUE = new Name("Btn");
    /**
     * Text terminal field.
     */
    public static final Name FT_TEXT_VALUE = new Name("Tx");
    /**
     * Choice terminal field.
     */
    public static final Name FT_CHOICE_VALUE = new Name("Ch");
    /**
     * Signature terminal field.
     */
    public static final Name FT_SIGNATURE_VALUE = new Name("Sig");
    /**
     * (Sometimes required, as described below) An array of indirect references
     * to the immediate children of this field.
     * <p/>
     * In a non-terminal field, the Kids array shall refer to field dictionaries
     * that are immediate descendants of this field. In a terminal field, the Kids
     * array ordinarily shall refer to one or more separate widget annotations that
     * are associated with this field. However, if there is only one associated
     * widget annotation, and its contents have been merged into the field dictionary,
     * Kids shall be omitted.
     */
    public static final Name KIDS_KEY = new Name("Kids");
    /**
     * (Required if this field is the child of another in the field hierarchy;
     * absent otherwise) The field that is the immediate parent of this one (the
     * field, if any, whose Kids array includes this field). A field can have at
     * most one parent; that is, it can be included in the Kids array of at most
     * one other field.
     */
    public static final Name PARENT_KEY = new Name("Parent");
    /**
     * (Optional) The partial field name (see 12.7.3.2, “Field Names”).
     */
    public static final Name T_KEY = new Name("T");
    /**
     * (Optional; PDF 1.3) An alternate field name that shall be used in place
     * of the actual field name wherever the field shall be identified in the
     * user interface (such as in error or status messages referring to the field).
     * This text is also useful when extracting the document’s contents in support
     * of accessibility to users with disabilities or for other purposes
     * (see 14.9.3, “Alternate Descriptions”).
     */
    public static final Name TU_KEY = new Name("TU");
    /**
     * (Optional; PDF 1.3) The mapping name that shall be used when exporting
     * interactive form field data from the document.
     */
    public static final Name TM_KEY = new Name("TM");
    /**
     * (Optional; inheritable) A set of flags specifying various characteristics
     * of the field (see Table 221). Default value: 0.
     */
    public static final Name Ff_KEY = new Name("Ff");
    /**
     * (Optional; inheritable) The field’s value, whose format varies depending
     * on the field type. See the descriptions of individual field types for
     * further information.
     */
    public static final Name V_KEY = new Name("V");
    /**
     * (Optional; inheritable) The default value to which the field reverts when
     * a reset-form action is executed (see 12.7.5.3, “Reset-Form Action”). The
     * format of this value is the same as that of V.
     */
    public static final Name DV_KEY = new Name("DV");
    /**
     * (Optional; PDF 1.2) An additional-actions dictionary defining the field’s
     * behaviour in response to various trigger events (see 12.6.3, “Trigger Events”).
     * This entry has exactly the same meaning as the AA entry in an annotation
     * dictionary (see 12.5.2, “Annotation Dictionaries”).
     */
    public static final Name AA_KEY = new Name("AA");
    /**
     * If set, the user may not change the value of the field. Any associated
     * widget annotations will not interact with the user; that is, they will
     * not respond to mouse clicks or change their appearance in response to mouse
     * motions. This flag is useful for fields whose values are computed or
     * imported from a database.
     */
    public static final int READ_ONLY_BIT_FLAG = 0x1;

    /** general field flags **/
    /**
     * If set, the field shall have a value at the time it is exported by a
     * submit-form action (see 12.7.5.2, “Submit-Form Action”).
     */
    public static final int REQUIRED_BIT_FLAG = 0x2;
    /**
     * If set, the field shall not be exported by a submit-form action (see 12.7.5.2, “Submit-Form Action”).
     */
    public static final int NO_EXPORT_BIT_FLAG = 0x4;
    private static final Logger logger =
            Logger.getLogger(FieldDictionary.class.toString());
    protected Name fieldType;
    protected VariableText variableText;
    protected String partialFieldName;
    protected String alternativeFieldName;
    protected String exportMappingName;
    protected int flags;
    protected Object fieldValue;
    protected Object defaultFieldValue;
    protected WidgetAnnotation parentField;
    protected ArrayList<AbstractWidgetAnnotation> kids;
    private org.icepdf.core.pobjects.security.SecurityManager securityManager;


    public FieldDictionary(Library library, HashMap entries) {
        super(library, entries);

        securityManager = library.getSecurityManager();

        Object value = library.getName(entries, FT_KEY);
        if (value != null) {
            fieldType = (Name) value;
        }
        // field name
        value = library.getObject(entries, T_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            partialFieldName = text.getDecryptedLiteralString(securityManager);
        } else if (value instanceof String) {
            partialFieldName = (String) value;
        }
        // alternate field name.
        value = library.getObject(entries, TU_KEY);
        if (value != null && value instanceof StringObject) {
            StringObject text = (StringObject) value;
            alternativeFieldName = text.getDecryptedLiteralString(securityManager);
        } else if (value instanceof String) {
            alternativeFieldName = (String) value;
        }
        // value field
        value = library.getObject(entries, V_KEY);
        if (value instanceof Name) {
            fieldValue = (Name) value;
        } else if (value instanceof StringObject) {
            StringObject text = (StringObject) value;
            fieldValue = text.getDecryptedLiteralString(securityManager);
        } else if (value instanceof String) {
            fieldValue = value;
        }
        // default value
        value = library.getObject(entries, DV_KEY);
        if (value != null) {
            defaultFieldValue = value;
        }

        // load the default appearance.
        variableText = new VariableText(library, entries);

        // behaviour flags
        flags = library.getInt(entries, Ff_KEY);
    }

    public ArrayList<AbstractWidgetAnnotation> getKids() {
        return kids;
    }

    public AbstractWidgetAnnotation getParent() {
        Object value = library.getObject(entries, PARENT_KEY);
        if (value instanceof HashMap) {
            return new ButtonWidgetAnnotation(library, (HashMap) value);
        }
        return null;
    }


    public Name getFieldType() {
        return fieldType;
    }

    public String getPartialFieldName() {
        return partialFieldName;
    }

    public String getAlternativeFieldName() {
        return alternativeFieldName;
    }

    public String getExportMappingName() {
        return exportMappingName;
    }

    public int getFlags() {
        return flags;
    }

    public Object getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(Object fieldValue) {
        this.fieldValue = fieldValue;
    }

    public VariableText getVariableText() {
        return variableText;
    }

    public Object getDefaultFieldValue() {
        return defaultFieldValue;
    }
}
