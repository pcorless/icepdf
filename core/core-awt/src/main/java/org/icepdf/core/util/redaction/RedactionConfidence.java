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
 * How much the verification pass was able to establish about a redaction.
 * <p>
 * Deliberately a level with evidence behind it rather than a bare number. A percentage invites a
 * confidence it cannot support - there is no meaningful sense in which a redaction is 94% done - and
 * is hard to defend to somebody asking whether a document is safe to release. The numeric
 * {@link RedactionReport#getScore()} exists only for sorting a batch.
 * <p>
 * The ordering here is worst to best, so {@link #compareTo} can be used to take the weakest result
 * across a set of documents.
 *
 * @since 7.5.0
 */
public enum RedactionConfidence {

    /**
     * Something that should have been removed is still in the file: found in the text, or found in
     * the bytes. Set only by a concrete surviving match, never by a low score, so no threshold can
     * turn a real leak into a pass.
     */
    FAILED,

    /**
     * Nothing was found, but parts of the document could not be checked - a burn over a raster, a
     * stream that would not parse. The redaction may well be complete; the pass simply cannot say
     * so. Treat as needing a human.
     */
    UNVERIFIED,

    /**
     * Nothing was found anywhere the pass could look, but the redaction took a degraded path
     * somewhere: a shared object burned in place, an appearance stream that could not be
     * regenerated. Worth reading the warnings before releasing the document.
     */
    VERIFIED_WITH_WARNINGS,

    /**
     * Nothing was found, everything could be checked, and nothing degraded.
     */
    VERIFIED
}
