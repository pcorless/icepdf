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
package org.icepdf.core.search;

import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless text search over a page's reading-order {@link TextSequence}, returning hits as
 * canonical offset ranges - no highlight flags are set and nothing is retained, so callers own
 * their results the way they own a {@code DocumentSelection}.
 * <p>
 * Matching rules (the Swing viewer's search, factored out):
 * <ul>
 *     <li>literal terms match a whitespace-collapsed corpus ({@link TextSequence#searchText()}),
 *     so a phrase matches across line breaks and irregular spacing;</li>
 *     <li>with {@link SearchTerm#isFoldDiacritics()} the corpus and term are accent-folded
 *     ({@link TextSequence#foldedSearchText()}): "resume" finds "résumé";</li>
 *     <li>regex terms match the canonical text ({@link TextSequence#text()});</li>
 *     <li>whole-word wraps a literal term in Unicode-aware {@code \b} boundaries;</li>
 *     <li>case-insensitive matching is Unicode-aware.</li>
 * </ul>
 * Every match is mapped back to canonical offsets, the space selections and {@code rectsFor} use.
 *
 * @since 7.5
 */
public final class TextSearch {

    private TextSearch() {
    }

    /**
     * Compiles a term to the pattern {@link #find} matches with: the term's own regex, or a quoted
     * literal (accent-folded if the term asks), optionally whole-word, case-insensitive unless the
     * term is case-sensitive.
     *
     * @return the pattern, or null for an empty term or a regex term with no pattern
     */
    public static Pattern compile(SearchTerm term) {
        if (term == null || term.getTerm() == null || term.getTerm().isEmpty()) return null;
        if (term.isRegex()) {
            return term.getRegexPattern();
        }
        int flags = Pattern.UNICODE_CHARACTER_CLASS;
        if (!term.isCaseSensitive()) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        String expression = Pattern.quote(
                term.isFoldDiacritics() ? TextSequence.foldDiacritics(term.getTerm()) : term.getTerm());
        if (term.isWholeWord()) {
            expression = "\\b" + expression + "\\b";
        }
        return Pattern.compile(expression, flags);
    }

    /**
     * All matches of the term on a page, in reading order, as canonical offset ranges.
     *
     * @param sequence the page's text sequence; null yields no hits
     * @param term     what to look for
     * @return hits, never null; zero-width regex matches are skipped
     */
    public static List<OffsetRange> find(TextSequence sequence, SearchTerm term) {
        if (sequence == null || sequence.isEmpty()) return Collections.emptyList();
        Pattern pattern = compile(term);
        if (pattern == null) return Collections.emptyList();
        boolean regex = term.isRegex();
        boolean fold = !regex && term.isFoldDiacritics();
        String corpus = regex ? sequence.text().toString()
                : (fold ? sequence.foldedSearchText() : sequence.searchText());
        List<OffsetRange> hits = new ArrayList<>();
        Matcher matcher = pattern.matcher(corpus);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) continue;
            OffsetRange range = regex
                    ? OffsetRange.of(matcher.start(), matcher.end())
                    : (fold ? sequence.foldedToCanonicalRange(matcher.start(), matcher.end())
                    : sequence.searchToCanonicalRange(matcher.start(), matcher.end()));
            if (!range.isEmpty()) hits.add(range);
        }
        return hits;
    }
}
