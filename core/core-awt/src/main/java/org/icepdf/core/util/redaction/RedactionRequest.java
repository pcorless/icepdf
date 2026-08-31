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

import org.icepdf.core.search.SearchTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What to redact, and how.
 * <p>
 * A redaction reaches its content two ways, and a request can carry either or both:
 * <ul>
 * <li><b>By geometry</b> - the redaction annotations already on the document's pages. Whatever is
 *     drawn under one is removed, whether that is text, an image, or part of a form.</li>
 * <li><b>By term</b> - words to remove from content that has no position and so cannot be covered by
 *     an annotation: outline titles, annotation text, field values, named destinations, metadata.
 *     These are rewritten, masked with {@link RedactionOptions#getMaskString()}.</li>
 * </ul>
 * Most callers want both, which is what {@link #ofAnnotationsAndTerms} gives them: the annotations
 * handle what is visible on the page, the terms handle the copies of the same words that live
 * elsewhere in the file and that no rectangle can cover.
 *
 * @since 7.5.0
 */
public class RedactionRequest {

    private final List<SearchTerm> terms;
    private RedactionOptions options = RedactionOptions.defaults();

    private RedactionRequest(List<SearchTerm> terms) {
        this.terms = terms != null ? new ArrayList<>(terms) : Collections.emptyList();
    }

    /**
     * Redact what the document's redaction annotations cover, and nothing else. This is what
     * {@link Redactor#burnRedactions(org.icepdf.core.pobjects.Document)} has always done.
     *
     * @return a new request
     */
    public static RedactionRequest ofAnnotations() {
        return new RedactionRequest(null);
    }

    /**
     * Redact positionless occurrences of the given terms, without touching page content.
     *
     * @param terms terms to remove
     * @return a new request
     */
    public static RedactionRequest ofTerms(List<SearchTerm> terms) {
        return new RedactionRequest(terms);
    }

    /**
     * Redact both what the annotations cover and every positionless occurrence of the terms. The
     * combination a search-and-redact workflow wants: the search produced both the annotations and
     * the terms, and the same words appear in places no annotation can reach.
     *
     * @param terms terms to remove
     * @return a new request
     */
    public static RedactionRequest ofAnnotationsAndTerms(List<SearchTerm> terms) {
        return new RedactionRequest(terms);
    }

    /**
     * @param options how the redaction should behave
     * @return this
     */
    public RedactionRequest with(RedactionOptions options) {
        this.options = options != null ? options : RedactionOptions.defaults();
        return this;
    }

    public RedactionOptions getOptions() {
        return options;
    }

    /**
     * @return terms to remove from positionless content, empty when the request is geometry only
     */
    public List<SearchTerm> getTerms() {
        return Collections.unmodifiableList(terms);
    }

    /**
     * @return true when there are terms to remove from content an annotation cannot cover
     */
    public boolean hasTerms() {
        return !terms.isEmpty();
    }
}
