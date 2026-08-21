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
 * Something the verification pass could not check.
 * <p>
 * Being explicit about these is what makes the rest of the report worth anything. A pass that
 * silently folded "I could not look here" into "I found nothing" would report a clean result for a
 * document it never examined.
 *
 * @since 7.5.0
 */
public class UnverifiableRegion {

    /**
     * Why the pass could not check something.
     */
    public enum Reason {
        /**
         * A redaction was burned into a raster. There was never any text to search for, so the pass
         * can confirm the pixels changed but not that the right pixels changed. Short of running
         * OCR, nothing here is checkable.
         */
        RASTER_CONTENT,
        /**
         * A content stream could not be parsed, so its text was never extracted and a search cannot
         * see into it.
         */
        STREAM_NOT_PARSED,
        /**
         * The redaction was driven by annotations alone and nothing recorded what came out, so
         * there is no text to search the result for.
         */
        NOTHING_TO_SEARCH_FOR
    }

    private final Reason reason;
    private final String detail;

    public UnverifiableRegion(Reason reason, String detail) {
        this.reason = reason;
        this.detail = detail;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * @return where in the document this was, as specifically as is known
     */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return reason + ": " + detail;
    }
}
