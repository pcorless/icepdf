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

/**
 * Something a redaction did that the caller should know about.
 * <p>
 * These exist because redaction is the one operation in this library where quiet partial success is
 * a failure: a caller who is told nothing assumes everything was removed. Anything that degrades
 * rather than throwing - a shared object burned in place, a stream the encoder could not round-trip -
 * gets a warning here instead of a log line nobody reads.
 *
 * @since 7.5.0
 */
public class RedactionWarning {

    /**
     * What kind of thing went not-quite-right, so a caller can act on a class of warning without
     * matching on message text.
     */
    public enum Kind {
        /**
         * A shared object was burned where it stood, so the redaction shows at every use of it.
         */
        SHARED_OBJECT_BURNED_IN_PLACE,
        /**
         * An image had to be re-encoded with a different filter than it arrived with.
         */
        IMAGE_RE_ENCODED,
        /**
         * A content stream could not be parsed, so nothing in it was examined.
         */
        STREAM_NOT_PARSED,
        /**
         * Content was found that this redaction does not know how to remove.
         */
        UNSUPPORTED_CONTENT
    }

    private final Kind kind;
    private final String detail;

    public RedactionWarning(Kind kind, String detail) {
        this.kind = kind;
        this.detail = detail;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * @return what specifically happened, naming the object where one is known
     */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return kind + ": " + detail;
    }
}
