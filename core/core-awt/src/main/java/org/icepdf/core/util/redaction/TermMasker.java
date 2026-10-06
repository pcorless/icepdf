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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces search terms in a string with the redaction mask.
 * <p>
 * This is the term-driven half of a redaction: content with no position on the page - an outline
 * title, a field value, a metadata entry - cannot be covered by a rectangle, so the string itself is
 * rewritten. It honours the same matching rules the search that found the terms used, so what gets
 * masked here is what the user saw highlighted.
 * <p>
 * The mask is a fixed string, not one character per character removed. Length-matched masking would
 * leak the length of every redacted term, which for a name, an account number or a date is a
 * meaningful amount of what the redaction was supposed to remove.
 *
 * @since 7.5.0
 */
public class TermMasker {

    private final List<SearchTerm> terms;
    private final String mask;

    public TermMasker(List<SearchTerm> terms, String mask) {
        this.terms = terms;
        this.mask = mask;
    }

    /**
     * @param value string to redact
     * @return the string with every occurrence of every term replaced by the mask, or the original
     * instance when nothing matched
     */
    public String mask(String value) {
        if (value == null || value.isEmpty() || terms.isEmpty()) {
            return value;
        }
        String masked = value;
        for (SearchTerm term : terms) {
            masked = maskTerm(masked, term);
        }
        return masked;
    }

    /**
     * @param value string to test
     * @return true when masking would change it
     */
    public boolean matches(String value) {
        return value != null && !value.equals(mask(value));
    }

    private String maskTerm(String value, SearchTerm term) {
        Pattern pattern = patternFor(term);
        if (pattern == null) {
            return value;
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.replaceAll(Matcher.quoteReplacement(mask)) : value;
    }

    /**
     * Builds the pattern for one term, honouring the flags the search used.
     * <p>
     * A non-regex term is quoted, so a term containing regex punctuation - a file name, an account
     * number with dots - matches itself rather than being read as a pattern.
     */
    private Pattern patternFor(SearchTerm term) {
        if (term.isRegex()) {
            return term.getRegexPattern();
        }
        String text = term.getTerm();
        if (text == null || text.isEmpty()) {
            return null;
        }
        String quoted = Pattern.quote(text);
        // \b is a word boundary, so it only means anything either side of a word character; for a
        // term starting or ending in punctuation it would never match.
        if (term.isWholeWord()) {
            String prefix = Character.isLetterOrDigit(text.charAt(0)) ? "\\b" : "";
            String suffix = Character.isLetterOrDigit(text.charAt(text.length() - 1)) ? "\\b" : "";
            quoted = prefix + quoted + suffix;
        }
        int flags = term.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(quoted, flags);
    }
}
