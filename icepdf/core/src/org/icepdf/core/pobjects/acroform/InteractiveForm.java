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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.WidgetAnnotation;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * An interactive form (PDF 1.2)—sometimes referred to as an AcroForm is a
 * collection of fields for gathering information interactively from the user.
 * A PDF document may contain any number of fields appearing on any combination
 * of pages, all of which make up a single, global interactive form spanning
 * the entire document.
 * <p/>
 * Each field in a document’s interactive form shall be defined by a field
 * dictionary (see 12.7.3, “Field Dictionaries”). For purposes of definition and
 * naming, the fields can be organized hierarchically and can inherit attributes
 * from their ancestors in the field hierarchy. A field’s children in the hierarchy
 * may also include widget annotations (see 12.5.6.19, “Widget Annotations”) that
 * define its appearance on the page. A field that has children that are fields
 * is called a non-terminal field. A field that does not have children that are
 * fields is called a terminal field.
 * <p/>
 * The contents and properties of a document’s interactive form shall be defined
 * by an interactive form dictionarythat shall be referenced from the AcroForm
 * entry in the document catalogue (see 7.7.2, “Document Catalog”).
 *
 * @since 5.1
 */
public class InteractiveForm extends Dictionary {

    /**
     * (Required) An array of references to the document’s root fields(those with
     * no ancestors in the field hierarchy).
     */
    public static final Name FIELDS_KEY = new Name("Fields");
    /**
     * (Optional) A flag specifying whether to construct appearance streams and
     * appearance dictionaries for all widget annotations in the document (see
     * 12.7.3.3, “Variable Text”). Default value: false.
     */
    public static final Name NEEDS_APPEARANCES_KEY = new Name("NeedAppearances");
    /**
     * (Optional; PDF 1.3) A set of flags specifying various document-level
     * characteristics related to signature fields (see Table 219, and 12.7.4.5,
     * “Signature Fields”). Default value: 0.
     */
    public static final Name SIG_FLAGS_KEY = new Name("SigFlags");
    /**
     * (Required if any fields in the document have additional-actions
     * dictionaries containing a C entry; PDF 1.3) An array of indirect
     * references to field dictionaries with calculation actions, defining the
     * calculation order in which their values will be recalculated when the
     * value of any field changes (see 12.6.3, “Trigger Events”).
     */
    public static final Name CO_KEY = new Name("CO");
    /**
     * (Optional) A resource dictionary (see 7.8.3, “Resource Dictionaries”)
     * containing default resources (such as fonts, patterns, or colour spaces)
     * that shall be used by form field appearance streams. At a minimum, this
     * dictionary shall contain a Font entry specifying the resource name and
     * font dictionary of the default font for displaying text.
     */
    public static final Name DR_KEY = new Name("DR");
    /**
     * (Optional) A document-wide default value for the DA attribute of variable
     * text fields (see 12.7.3.3, “Variable Text”).
     */
    public static final Name DA_KEY = new Name("DA");
    /**
     * (Optional) A document-wide default value for the Q attribute of variable
     * text fields (see 12.7.3.3, “Variable Text”).
     */
    public static final Name Q_KEY = new Name("Q");
    /**
     * If set, the document contains at least one signature field. This flag
     * allows a conforming reader to enable user interface items (such as menu
     * items or pushbuttons) related to signature processing without having to
     * scan the entire document for the presence of signature fields.
     */
    public static final int SIG_FLAGS_SIGNATURES_EXIST = 1;
    /**
     * If set, the document contains signatures that may be invalidated if the
     * file is saved (written) in a way that alters its previous contents, as
     * opposed to an incremental update. Merely updating the file by appending
     * new information to the end of the previous version is safe
     * (see H.7, “Updating Example”). Conforming readers may use this flag to
     * inform a user requesting a full save that signatures will be invalidated
     * and require explicit confirmation before continuing with the operation.
     */
    public static final int SIG_FLAGS_APPEND_ONLY = 2;
    private static final Logger logger =
            Logger.getLogger(InteractiveForm.class.toString());
    // field list, we keep reference as we don't want these garbage collected.
    private ArrayList<AbstractWidgetAnnotation> fields;
    private boolean needAppearances;
    private int sigFlags;
    private ArrayList calculationOrder;
    private Resources resources;
    private String defaultVariableTextDAField;
    private int defaultVariableTextQField;
    // ignore XFA for now as it isn't in the ISO yet.


    public InteractiveForm(Library library, HashMap entries) {
        super(library, entries);
    }

    public void init() {
        // get the fields in the document.
        Object tmp = library.getObject(entries, FIELDS_KEY);
        if (tmp instanceof List) {
            List tmpFields = (List) tmp;
            fields = new ArrayList<AbstractWidgetAnnotation>(tmpFields.size());
            AbstractWidgetAnnotation annotation;
            Object annotObj;
            for (Object fieldRef : tmpFields) {
                if (fieldRef instanceof Reference) {
                    annotObj = library.getObject((Reference) fieldRef);
                    // find the terminal fields.
                    if (annotObj instanceof AbstractWidgetAnnotation) {
                        annotation = (AbstractWidgetAnnotation) annotObj;
                        fields.add(annotation);

                    } else if (annotObj instanceof HashMap) {
                        annotation = new WidgetAnnotation(library, (HashMap) annotObj);
                        fields.add(annotation);
                    }
                    // check for non-terminal fields, if found execute
                    // flag logic.
//                    if (annotation.getFieldDictionary().getKids() != null){
//                        System.out.println();
//                    }
                }
            }
        }
        // load the resources
        tmp = library.getObject(entries, DR_KEY);
        if (tmp instanceof HashMap) {
            resources = library.getResources(entries, DR_KEY);
        }

        // load the default appearance.
        tmp = library.getObject(entries, DA_KEY);
        if (tmp instanceof StringObject) {
            org.icepdf.core.pobjects.security.SecurityManager securityManager =
                    library.getSecurityManager();
            defaultVariableTextDAField = ((StringObject) tmp)
                    .getDecryptedLiteralString(securityManager);
        }
    }
}
