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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.SearchTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The comments model and the comment/field search, on the Swing viewer's small search fixture. */
class AnnotationSummaryTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/search/form_fields_and_comments.pdf");
    private static Document document;

    @BeforeAll
    static void open() throws Exception {
        assumeTrue(Files.isRegularFile(FIXTURE), "viewer-awt fixture not found");
        document = new Document();
        document.setFile(FIXTURE.toString());
    }

    @AfterAll
    static void close() {
        if (document != null) document.dispose();
    }

    @DisplayName("markup annotations are listed with type, author and contents; widgets are not")
    @Test
    void collect() throws Exception {
        List<AnnotationSummary.Entry> entries = AnnotationSummary.collect(document, () -> false);
        assertNotNull(entries);
        assertEquals(3, entries.size());
        for (AnnotationSummary.Entry entry : entries) {
            assertEquals(0, entry.pageIndex());
            assertEquals("Note", entry.type());
            assertEquals("Reviewer", entry.author());
            assertNull(entry.replyTo());
        }
        assertEquals("the capybara looks wrong here", entries.get(0).contents());
        assertEquals("", entries.get(2).contents());
        assertTrue(AnnotationSummary.replies(entries).isEmpty());
        assertNull(AnnotationSummary.collect(document, () -> true), "a cancelled scan returns null");
    }

    @DisplayName("the filter matches type, author or contents, ignoring case")
    @Test
    void filter() throws Exception {
        List<AnnotationSummary.Entry> entries = AnnotationSummary.collect(document, () -> false);
        assertEquals(1, entries.stream().filter(e -> e.matches("BADGER")).count());
        assertEquals(3, entries.stream().filter(e -> e.matches("reviewer")).count());
        assertEquals(3, entries.stream().filter(e -> e.matches("note")).count());
        assertEquals(3, entries.stream().filter(e -> e.matches("  ")).count());
        assertEquals(0, entries.stream().filter(e -> e.matches("wombat")).count());
    }

    @DisplayName("orders: page, newest first (undated last), author, type")
    @Test
    void orders() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 9, 12, 0);
        AnnotationSummary.Entry a = new AnnotationSummary.Entry(2, null, "Note", "bob", t, "", null, null);
        AnnotationSummary.Entry b = new AnnotationSummary.Entry(0, null, "Highlight", "Alice", null, "", null, null);
        AnnotationSummary.Entry c = new AnnotationSummary.Entry(1, null, "Ellipse", "carol", t.plusDays(1), "", null, null);
        assertEquals(List.of(b, c, a), sorted(List.of(a, b, c), AnnotationSummary.Order.PAGE));
        assertEquals(List.of(c, a, b), sorted(List.of(a, b, c), AnnotationSummary.Order.DATE));
        assertEquals(List.of(b, a, c), sorted(List.of(a, b, c), AnnotationSummary.Order.AUTHOR));
        assertEquals(List.of(c, b, a), sorted(List.of(a, b, c), AnnotationSummary.Order.TYPE));
    }

    private static List<AnnotationSummary.Entry> sorted(List<AnnotationSummary.Entry> in, AnnotationSummary.Order order) {
        List<AnnotationSummary.Entry> out = new ArrayList<>(in);
        out.sort(order.comparator());
        return out;
    }

    @DisplayName("field search finds the comment and the form field holding a term")
    @Test
    void fieldSearch() throws Exception {
        List<DocumentFieldSearch.Hit> hits = new ArrayList<>();
        new DocumentFieldSearch(List.of(new SearchTerm("badger", null, false, false, false)))
                .run(document, true, true, false, false, () -> false, hits::add);
        assertEquals(2, hits.size(), hits.toString());
        assertTrue(hits.stream().anyMatch(h -> h.kind() == DocumentFieldSearch.Kind.COMMENT
                && h.text().equals("check the badger figure") && h.label().equals("Reviewer")));
        assertTrue(hits.stream().anyMatch(h -> h.kind() == DocumentFieldSearch.Kind.FORM_FIELD
                && h.text().equals("badger total 42") && h.label().equals("invoice")));
        // comments only
        hits.clear();
        new DocumentFieldSearch(List.of(new SearchTerm("badger", null, false, false, false)))
                .run(document, true, false, false, false, () -> false, hits::add);
        assertEquals(1, hits.size());
    }
}
