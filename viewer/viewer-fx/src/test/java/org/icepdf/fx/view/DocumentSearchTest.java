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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.search.SearchTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Whole-document search and navigation, no toolkit.  The parity test pins the core matcher to the
 * Swing viewer's recorded counts (viewer-awt DocumentSearchConvergenceTest) on the same fixtures.
 */
class DocumentSearchTest {

    private static final Path FIXTURES = Paths.get("../viewer-awt/src/test/resources/redact");
    private static Document poem;
    private static Document addendum;

    @BeforeAll
    static void open() throws Exception {
        assumeTrue(Files.isDirectory(FIXTURES), "viewer-awt fixtures not found");
        poem = new Document();
        poem.setFile(FIXTURES.resolve("test_print.pdf").toString());
        addendum = new Document();
        addendum.setFile(FIXTURES.resolve("pdf_reference_addendum_redaction.pdf").toString());
    }

    @AfterAll
    static void close() {
        if (poem != null) poem.dispose();
        if (addendum != null) addendum.dispose();
    }

    private static IntFunction<TextSequence> texts(Document document) {
        return page -> {
            try {
                return document.getPageViewText(page).getTextSequence();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static SearchTerm term(String text, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return new SearchTerm(text, null, caseSensitive, wholeWord, regex);
    }

    private static int count(Document document, int page, SearchTerm term) {
        return DocumentSearch.searchPage(texts(document).apply(page), page, List.of(term)).size();
    }

    @DisplayName("parity with the Swing viewer's search counts on the same fixtures")
    @Test
    void parityWithSwingSearch() {
        assertEquals(10, count(poem, 0, term("Un", false, false, false)));
        assertEquals(7, count(poem, 0, term("Un", true, true, false)));
        assertEquals(8, count(poem, 0, term("un", false, true, false)));
        assertEquals(4, count(poem, 0, term("que", false, false, false)));
        assertEquals(8, count(poem, 0, term("de", false, false, false)));
        assertEquals(5, count(poem, 0, term("de", false, true, false)));
        assertEquals(2, count(poem, 0, term("de la", false, false, false)));
        assertEquals(1, count(poem, 0, term("Un sacerdote", false, false, false)), "across a line break");
        assertEquals(2, count(poem, 0, term("de\\s+la", false, false, true)));
        SearchTerm folded = term("que", false, false, false);
        folded.setFoldDiacritics(true);
        assertEquals(5, count(poem, 0, folded));
        assertEquals(10, count(addendum, 1, term("Redaction", false, false, false)));
        assertEquals(13, count(addendum, 2, term("annotation", false, false, false)));
        assertEquals(8, count(addendum, 1, term("PDF", false, true, false)));
        assertEquals(3, count(addendum, 1, term("PDF Reference", false, false, false)));
    }

    @DisplayName("hits carry their text, a selectable range and bounds on the page")
    @Test
    void hitContents() {
        List<SearchHit> hits = DocumentSearch.searchPage(texts(addendum).apply(1), 1,
                List.of(term("Redaction", false, false, false)));
        TextSequence sequence = texts(addendum).apply(1);
        for (SearchHit hit : hits) {
            assertEquals(1, hit.pageIndex());
            assertEquals(sequence.text(hit.range()), hit.text());
            assertTrue(hit.text().equalsIgnoreCase("redaction"), hit.text());
            assertTrue(hit.width() > 0 && hit.height() > 0);
        }
        List<SearchHit> sorted = new ArrayList<>(hits);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, hits, "document order");
    }

    @DisplayName("run reports every page in order, then completes; a cancel stops between pages")
    @Test
    void runAndCancel() {
        int pages = addendum.getNumberOfPages();
        List<Integer> seen = new ArrayList<>();
        List<SearchHit> all = new ArrayList<>();
        boolean finished = DocumentSearch.run(texts(addendum), List.of(term("redaction", false, false, false)),
                pages, () -> false, (page, hits, done, total) -> {
                    seen.add(page);
                    all.addAll(hits);
                    assertEquals(page + 1, done);
                    assertEquals(pages, total);
                });
        assertTrue(finished);
        assertEquals(pages, seen.size());
        assertTrue(all.size() > 20, "the addendum is about redaction: " + all.size());

        List<Integer> partial = new ArrayList<>();
        boolean completed = DocumentSearch.run(texts(addendum), List.of(term("redaction", false, false, false)),
                pages, () -> partial.size() >= 2, (page, hits, done, total) -> partial.add(page));
        assertFalse(completed);
        assertEquals(List.of(0, 1), partial);
    }

    @DisplayName("several terms merge in document order")
    @Test
    void multipleTerms() {
        List<SearchHit> hits = DocumentSearch.searchPage(texts(poem).apply(0), 0,
                List.of(term("que", false, false, false), term("Un", false, false, false)));
        assertEquals(14, hits.size());
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).compareTo(hits.get(i)) <= 0);
        }
    }

    @DisplayName("next/previous wrap, and start from the current page when nothing is current")
    @Test
    void navigation() {
        List<SearchHit> hits = new ArrayList<>();
        for (int page : new int[]{0, 0, 2, 5}) {
            hits.add(new SearchHit(page, org.icepdf.core.pobjects.graphics.text.OffsetRange.of(hits.size(), hits.size() + 1),
                    "x", 0, 0, 1, 1));
        }
        assertEquals(1, DocumentSearch.next(hits, 0, 0));
        assertEquals(0, DocumentSearch.next(hits, 3, 0), "wraps to the first");
        assertEquals(3, DocumentSearch.previous(hits, 0, 0), "wraps to the last");
        assertEquals(2, DocumentSearch.next(hits, -1, 1), "first hit at or after page 1");
        assertEquals(0, DocumentSearch.next(hits, -1, 9), "none after page 9: first hit");
        assertEquals(2, DocumentSearch.previous(hits, -1, 4), "last hit at or before page 4");
        assertEquals(-1, DocumentSearch.next(List.of(), -1, 0));
        assertEquals(-1, DocumentSearch.previous(List.of(), -1, 0));
    }

    @DisplayName("hits carry the words around them, from their own line only")
    @Test
    void hitsCarryLineContext() {
        TextSequence sequence = texts(addendum).apply(0);
        List<SearchHit> hits = DocumentSearch.searchPage(sequence, 0, List.of(term("redaction", false, false, false)));
        assertFalse(hits.isEmpty());
        boolean anyContext = false;
        for (SearchHit hit : hits) {
            assertFalse(hit.before().contains("\n") || hit.after().contains("\n"), "context stays on one line");
            assertTrue(hit.before().length() <= DocumentSearch.CONTEXT + 1, hit.before());
            assertTrue(hit.after().length() <= DocumentSearch.CONTEXT + 1, hit.after());
            anyContext |= !hit.before().isEmpty() || !hit.after().isEmpty();
            // the context is the text either side of the hit, as written on the line.
            String line = sequence.text(sequence.lineRange(hit.range().getStart())).replace('\n', ' ');
            String before = hit.before().replace("\u2026", "");
            assertTrue(line.contains(before), before + " | " + line);
        }
        assertTrue(anyContext);
    }
}
