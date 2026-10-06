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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.icepdf.core.pobjects.graphics.text.TextFixtures.pageOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TextSearch on synthetic pages: every hit is checked by the canonical text it covers.
 */
public class TextSearchTest {

    private static SearchTerm literal(String term, boolean caseSensitive, boolean wholeWord) {
        return new SearchTerm(term, null, caseSensitive, wholeWord, false);
    }

    private static List<String> hitTexts(TextSequence seq, SearchTerm term) {
        return TextSearch.find(seq, term).stream().map(seq::text).collect(Collectors.toList());
    }

    @DisplayName("literal: case-insensitive by default, case-sensitive on request")
    @Test
    public void caseSensitivity() {
        TextSequence seq = pageOf("Search the search SEARCH").getTextSequence();
        assertEquals(List.of("Search", "search", "SEARCH"), hitTexts(seq, literal("search", false, false)));
        assertEquals(List.of("search"), hitTexts(seq, literal("search", true, false)));
    }

    @DisplayName("whole word excludes matches inside longer words")
    @Test
    public void wholeWord() {
        TextSequence seq = pageOf("cat catalog concat cat").getTextSequence();
        assertEquals(4, TextSearch.find(seq, literal("cat", false, false)).size());
        List<OffsetRange> whole = TextSearch.find(seq, literal("cat", false, true));
        assertEquals(List.of("cat", "cat"), whole.stream().map(seq::text).collect(Collectors.toList()));
        assertEquals(0, whole.get(0).getStart());
    }

    @DisplayName("a phrase matches across a line break and maps back to canonical offsets")
    @Test
    public void phraseAcrossLines() {
        TextSequence seq = pageOf("alpha beta", "gamma delta").getTextSequence();
        List<OffsetRange> hits = TextSearch.find(seq, literal("beta gamma", false, false));
        assertEquals(1, hits.size());
        String covered = seq.text(hits.get(0));
        assertTrue(covered.startsWith("beta") && covered.endsWith("gamma"), "covered: " + covered);
        assertTrue(covered.contains("\n"), "the canonical range spans the line break");
    }

    @DisplayName("diacritic folding: an unaccented term finds accented text, only when asked")
    @Test
    public void foldDiacritics() {
        TextSequence seq = pageOf("café résumé naïve").getTextSequence();
        SearchTerm plain = literal("resume", false, false);
        assertTrue(TextSearch.find(seq, plain).isEmpty());
        SearchTerm folded = literal("resume", false, false);
        folded.setFoldDiacritics(true);
        assertEquals(List.of("résumé"), hitTexts(seq, folded));
    }

    @DisplayName("regex terms match the canonical text; zero-width matches are skipped")
    @Test
    public void regex() {
        TextSequence seq = pageOf("item 12 and item 345").getTextSequence();
        assertEquals(List.of("12", "345"), hitTexts(seq, new SearchTerm("\\d+", null, false, false, true)));
        assertTrue(TextSearch.find(seq, new SearchTerm("\\b", null, false, false, true)).isEmpty());
    }

    @DisplayName("no hits for empty terms, null or empty sequences")
    @Test
    public void degenerate() {
        TextSequence seq = pageOf("anything").getTextSequence();
        assertTrue(TextSearch.find(seq, literal("", false, false)).isEmpty());
        assertTrue(TextSearch.find(null, literal("x", false, false)).isEmpty());
        assertNull(TextSearch.compile(null));
        assertTrue(TextSearch.find(seq, literal("absent", false, false)).isEmpty());
    }

    @DisplayName("regex-special characters in a literal term are matched literally")
    @Test
    public void literalIsQuoted() {
        TextSequence seq = pageOf("cost (USD) 3.5 or 345").getTextSequence();
        assertEquals(List.of("(USD)"), hitTexts(seq, literal("(USD)", false, false)));
        assertEquals(List.of("3.5"), hitTexts(seq, literal("3.5", false, false)));
    }
}
