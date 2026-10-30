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
package org.icepdf.fx.ri;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.fx.panels.AnnotationSummary;
import org.icepdf.fx.panels.MarkedText;
import org.icepdf.fx.view.internal.annot.AnnotationCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The annotation board's columns, and the text a highlight quotes. */
class BoardModelTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final ColorLabels.Label TODO = new ColorLabels.Label(new Color(255, 255, 0), "To do");
    private static final ColorLabels.Label DONE = new ColorLabels.Label(new Color(0, 176, 80), "Done");

    private Document document;
    private Page page;

    @BeforeEach
    void open() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        document = new Document();
        document.setFile(FIXTURE.toString());
        page = document.getPageTree().getPage(0);
        page.init();
    }

    @AfterEach
    void close() {
        if (document != null) document.dispose();
    }

    private MarkupAnnotation note(Color color) {
        MarkupAnnotation note = AnnotationCreator.note(page.getLibrary(), 50, 50,
                page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1), page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                new AnnotationCreator.Style("t", Color.YELLOW, 255, 1f));
        if (color != null) note.setColor(color);
        return note;
    }

    private static AnnotationSummary.Entry entry(int page, MarkupAnnotation a, String contents, MarkupAnnotation replyTo) {
        return new AnnotationSummary.Entry(page, a, "Note", "t", LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(page),
                contents, a.getColor(), replyTo);
    }

    private static List<BoardModel.Column> columns(List<AnnotationSummary.Entry> entries, List<ColorLabels.Label> labels,
                                                   String filter) {
        return BoardModel.columns(entries, Map.of(), labels, AnnotationSummary.Order.PAGE.comparator(),
                card -> card.matches(filter));
    }

    @DisplayName("comments go under the label they match (within tolerance), then Other, then No colour")
    @Test
    void fileByLabel() {
        MarkupAnnotation yellow = note(new Color(255, 255, 0));
        MarkupAnnotation nearlyYellow = note(new Color(250, 252, 5));
        MarkupAnnotation blue = note(new Color(0, 0, 255));
        MarkupAnnotation none = note(null);
        List<AnnotationSummary.Entry> entries = List.of(entry(2, yellow, "a", null), entry(1, nearlyYellow, "b", null),
                entry(0, blue, "c", null), new AnnotationSummary.Entry(3, none, "Note", "t", null, "d", null, null));

        List<BoardModel.Column> columns = columns(entries, List.of(TODO, DONE), "");
        assertEquals(List.of(BoardModel.Kind.LABEL, BoardModel.Kind.LABEL, BoardModel.Kind.OTHER, BoardModel.Kind.NONE),
                columns.stream().map(BoardModel.Column::kind).toList());
        assertSame(TODO, columns.get(0).label());
        assertEquals(List.of(nearlyYellow, yellow), annotations(columns.get(0)), "in page order");
        assertTrue(columns.get(1).cards().isEmpty(), "an empty label keeps its column");
        assertEquals(List.of(blue), annotations(columns.get(2)));
        assertEquals(List.of(none), annotations(columns.get(3)));
    }

    @DisplayName("with no labels there is one column of every comment")
    @Test
    void noLabels() {
        MarkupAnnotation a = note(new Color(255, 0, 0)), b = note(null);
        List<BoardModel.Column> columns = columns(List.of(entry(1, a, "a", null),
                new AnnotationSummary.Entry(0, b, "Note", "t", null, "b", null, null)), List.of(), "");
        assertEquals(1, columns.size());
        assertEquals(BoardModel.Kind.ALL, columns.get(0).kind());
        assertEquals(List.of(b, a), annotations(columns.get(0)));
    }

    @DisplayName("replies are counted on the comment they answer, at any depth, not shown as cards")
    @Test
    void replies() {
        MarkupAnnotation parent = note(TODO.color()), reply = note(DONE.color()), replyToReply = note(DONE.color());
        List<BoardModel.Column> columns = columns(List.of(entry(0, parent, "p", null), entry(0, reply, "r", parent),
                entry(0, replyToReply, "rr", reply)), List.of(TODO, DONE), "");
        assertEquals(List.of(parent), annotations(columns.get(0)));
        assertEquals(2, columns.get(0).cards().get(0).replies());
        assertTrue(columns.get(1).cards().isEmpty(), "a reply isn't filed by its own colour");
    }

    @DisplayName("the filter matches the quoted text as well as the contents")
    @Test
    void filterQuote() {
        MarkupAnnotation a = note(TODO.color()), b = note(TODO.color());
        List<AnnotationSummary.Entry> entries = List.of(entry(0, a, "", null), entry(1, b, "other", null));
        List<BoardModel.Column> columns = BoardModel.columns(entries, Map.of(a, "Quarterly results"), List.of(TODO),
                AnnotationSummary.Order.PAGE.comparator(), card -> card.matches("quarterly"));
        assertEquals(List.of(a), annotations(columns.get(0)));
        assertEquals("Quarterly results", columns.get(0).cards().get(0).quote());
    }

    @DisplayName("a highlight quotes the page text under its quads")
    @Test
    void markedText() throws Exception {
        TextSequence sequence = page.getViewText().getTextSequence();
        OffsetRange range = OffsetRange.of(0, Math.min(20, sequence.length()));
        String expected = sequence.extractText(range).strip();
        assumeTrue(!expected.isEmpty(), "the fixture has text");
        AffineTransform pageToView = page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1);
        List<Rectangle2D> viewRects = new ArrayList<>();
        for (Rectangle2D r : sequence.rectsFor(range)) viewRects.add(pageToView.createTransformedShape(r).getBounds2D());
        TextMarkupAnnotation highlight = AnnotationCreator.textMarkup(page.getLibrary(),
                TextMarkupAnnotation.SUBTYPE_HIGHLIGHT, viewRects, "", page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                new AnnotationCreator.Style("t", Color.YELLOW, TextMarkupAnnotation.HIGHLIGHT_ALPHA, 1f));
        assertEquals(expected.replaceAll("\\s+", " "), MarkedText.of(page, highlight).replaceAll("\\s+", " "));
    }

    @DisplayName("grouped by page or author: a read-only column each, pages in order, authors by name")
    @Test
    void groupBy() {
        MarkupAnnotation a = note(TODO.color()), b = note(DONE.color()), c = note(null);
        List<AnnotationSummary.Entry> entries = List.of(
                new AnnotationSummary.Entry(4, a, "Note", "zoe", null, "a", a.getColor(), null),
                new AnnotationSummary.Entry(1, b, "Highlight", "Adam", null, "b", b.getColor(), null),
                new AnnotationSummary.Entry(4, c, "Note", "", null, "c", null, null));
        List<BoardModel.Column> pages = BoardModel.columns(BoardModel.GroupBy.PAGE, entries, Map.of(), List.of(TODO),
                AnnotationSummary.Order.PAGE.comparator(), card -> true);
        assertEquals(List.of("Page 2", "Page 5"), pages.stream().map(BoardModel.Column::title).toList());
        assertEquals(List.of(b), annotations(pages.get(0)));
        assertEquals(2, pages.get(1).cards().size());
        assertTrue(pages.stream().allMatch(col -> col.kind() == BoardModel.Kind.GROUP));

        List<BoardModel.Column> authors = BoardModel.columns(BoardModel.GroupBy.AUTHOR, entries, Map.of(), List.of(),
                AnnotationSummary.Order.PAGE.comparator(), card -> true);
        assertEquals(List.of("Adam", "No author", "zoe"), authors.stream().map(BoardModel.Column::title).toList());
    }

    @DisplayName("Markdown has a heading per column, the quote once, and escapes markup; CSV quotes per RFC 4180")
    @Test
    void export() {
        MarkupAnnotation a = note(TODO.color()), b = note(TODO.color());
        List<AnnotationSummary.Entry> entries = List.of(
                new AnnotationSummary.Entry(0, a, "Highlight", "Ann", LocalDateTime.of(2026, 10, 28, 9, 30),
                        "Same *text*", a.getColor(), null),
                new AnnotationSummary.Entry(2, b, "Note", "Bob", null, "Line one\nsays \"hi\", ok", b.getColor(), null));
        List<BoardModel.Column> columns = BoardModel.columns(entries, Map.of(a, "Same *text*"), List.of(TODO, DONE),
                AnnotationSummary.Order.PAGE.comparator(), card -> true);

        String md = BoardExport.markdown("report.pdf", columns);
        assertTrue(md.startsWith("# "), md);
        assertTrue(md.contains("## To do (2)"), md);
        assertTrue(md.contains("## Done (0)"), md);
        assertTrue(md.contains("- **Highlight**, p. 1 \u2014 Ann, 2026-10-28T09:30"), md);
        assertTrue(md.contains("  > Same \\*text\\*"), "quote, escaped: " + md);
        assertEquals(1, md.split("Same", -1).length - 1, "contents equal to the quote are written once");
        assertTrue(md.contains("  Line one\n  says \"hi\", ok\n"), md);

        String csv = BoardExport.csv(columns);
        assertTrue(csv.startsWith("\uFEFF"), "byte order mark");
        String[] rows = csv.substring(1).split("\r\n");
        assertEquals(3, rows.length, csv);
        assertEquals("Column,Page,Type,Author,Date,Private,Marked text,Comment,Replies", rows[0]);
        assertEquals("To do,1,Highlight,Ann,2026-10-28T09:30:00,false,Same *text*,Same *text*,0", rows[1]);
        assertEquals("To do,3,Note,Bob,,false,,\"Line one\nsays \"\"hi\"\", ok\",0", rows[2]);
    }

    private static List<MarkupAnnotation> annotations(BoardModel.Column column) {
        return column.cards().stream().map(c -> c.entry().annotation()).toList();
    }
}
