/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.redaction;

import java.io.IOException;

/**
 * Thrown by a redacting write that can't be done without leaving a term in the file or quietly
 * dropping something that matters, so nothing is written.
 * <p>
 * The one case so far: a digital signature that names a redacted term, in its signer details, its
 * certificates or its appearance. A signature can't be masked, as the masked text would no longer
 * match what was signed. Redact without that term, or remove the signature first.
 * <p>
 * The message names the field, with any term in its name masked, and never a term or other text
 * from the document.
 *
 * @since 8.1
 */
public class RedactionRefusedException extends IOException {

    private final String fieldName;

    /**
     * @param fieldName fully qualified name of the signature field, terms masked, or null
     */
    public RedactionRefusedException(String fieldName) {
        super("Redaction refused: the signature in field '" + (fieldName != null ? fieldName : "(unnamed)")
                + "' names a redacted term, and a signature can't be masked. Redact without that term, "
                + "or remove the signature first");
        this.fieldName = fieldName;
    }

    /**
     * @return the fully qualified name of the signature field, with any term in it masked, or null
     */
    public String getFieldName() {
        return fieldName;
    }
}
