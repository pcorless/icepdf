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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.ri.common.SwingController;

import java.util.logging.Logger;

/**
 * An annotation, page object, or (beginning with PDF 1.3) interactive form field may include an entry named AA that
 * specifies an additional-actions dictionary (PDF 1.2) that extends the set of events that can trigger the execution of
 * an action. In PDF 1.4, the document catalogue dictionary (see 7.7.2, “Document Catalog”) may also contain an AA entry
 * for trigger events affecting the document as a whole. Tables 194 to 197 show the contents of this type of dictionary.
 *
 * The AdditionalActionsPanel main purpose is to allow of the assignment and editing of trigger events for a given
 * widget annotation.
 *
 * E dictionary An action that shall be performed when the cursor enters the annotation’s active area.
 * X dictionary An action that shall be performed when the cursor exits the annotation’s active area.
 * D dictionary An action that shall be performed when the mouse button is pressed inside the annotation’s active area.
 * U dictionary An action that shall be performed when the mouse button is released inside the annotation’s active area.
 *
 * @since 6.0
 */
@SuppressWarnings("serial")
public class AdditionalActionsPanel extends AnnotationPropertiesPanel {

    private static final Logger logger =
            Logger.getLogger(AdditionalActionsPanel.class.toString());


    public AdditionalActionsPanel(SwingController controller) {
        super(controller);
    }
}
