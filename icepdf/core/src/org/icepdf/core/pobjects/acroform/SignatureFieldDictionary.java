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

package org.icepdf.core.pobjects.acroform;

import org.icepdf.core.util.Library;

import java.util.HashMap;

/**
 * Signature field (PDF 1.3) is a form field that contains a digital signature (see 12.8, “Digital Signatures”). The field
 * dictionary representing a signature field may contain the additional entries listed in Table 232, as well as the
 * standard entries described in Table 220. The field type (FT) shall be Sig, and the field value (V), if present, shall
 * be a signature dictionary containing the signature and specifying various attributes of the signature field
 * (see Table 252).
 * <p/>
 * NOTE 1This signature form field serves two primary purposes. The first is to define the form field that will provide
 * the visual signing properties for display but it also may hold information needed later when the actual signing takes
 * place, such as the signature technology to use. This carries information from the author of the document to the
 * software that later does the signing.
 * <p/>
 * NOTE 2Filling in (signing) the signature field entails updating at least the V entry and usually also the AP entry of
 * the associated widget annotation. Exporting a signature field typically exports the T, V, and AP entries.
 * <p/>
 * Like any other field, a signature field may be described by a widget annotation dictionary containing entries pertaining
 * to an annotation as well as a field (see 12.5.6.19, “Widget Annotations”). The annotation rectangle (Rect) in such a
 * dictionary shall give the position of the field on its page. Signature fields that are not intended to be visible shall
 * have an annotation rectangle that has zero height and width. Conforming readers shall treat such signatures as not
 * visible. Conforming readers shall also treat signatures as not visible if either the Hidden bit or the NoView bit of
 * the F entry is true. The F entry is described in Table 164, and annotation flags are described in Table 165.
 *
 * @since 5.2
 */
public class SignatureFieldDictionary extends FieldDictionary {

    // todo implement class.
    public SignatureFieldDictionary(Library library, HashMap entries) {
        super(library, entries);
    }
}
