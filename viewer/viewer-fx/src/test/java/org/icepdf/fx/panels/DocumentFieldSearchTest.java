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
package org.icepdf.fx.panels;

import org.icepdf.core.search.SearchTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Matching for comments, fields, bookmarks and destinations: the text search's rules on plain strings. */
class DocumentFieldSearchTest {

    private static DocumentFieldSearch search(SearchTerm... terms) {
        return new DocumentFieldSearch(List.of(terms));
    }

    private static SearchTerm term(String text, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return new SearchTerm(text, null, caseSensitive, wholeWord, regex);
    }

    @DisplayName("case-insensitive by default; the match is placed in the whitespace-collapsed text")
    @Test
    void caseAndPlacement() {
        Optional<DocumentFieldSearch.Match> match = search(term("review", false, false, false))
                .match("  Please\n\tREVIEW  this ");
        assertTrue(match.isPresent());
        assertEquals("Please REVIEW this", match.get().text());
        assertEquals("REVIEW", match.get().text().substring(match.get().start(), match.get().end()));
        assertTrue(search(term("review", true, false, false)).match("REVIEW").isEmpty());
    }

    @DisplayName("whole word, regex, and accent folding")
    @Test
    void options() {
        assertTrue(search(term("form", false, true, false)).match("formula").isEmpty());
        assertTrue(search(term("form", false, true, false)).match("a form field").isPresent());
        assertTrue(search(term("G1[0-9]+", false, false, true)).match("ref G1500945").isPresent());
        // a literal term is quoted, never a pattern.
        assertTrue(search(term("G1.15", false, false, false)).match("G1x15").isEmpty());
        SearchTerm folded = term("resume", false, false, false);
        folded.setFoldDiacritics(true);
        Optional<DocumentFieldSearch.Match> match = search(folded).match("my résumé");
        assertTrue(match.isPresent());
        assertEquals("résumé", match.get().text().substring(match.get().start(), match.get().end()));
    }

    @DisplayName("every term is tried, not only the first; blank and null text never match")
    @Test
    void allTerms() {
        DocumentFieldSearch search = search(term("alpha", false, false, false), term("beta", false, false, false));
        assertTrue(search.match("only beta here").isPresent());
        assertTrue(search.match(null).isEmpty());
        assertTrue(search.match("   ").isEmpty());
    }
}
