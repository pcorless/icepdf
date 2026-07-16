/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.selection;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.SearchMode;
import org.icepdf.ri.common.search.DocumentSearchControllerImpl;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for document search across two PDFs (the Parra poem and the PDF
 * redaction addendum), covering WORD and PAGE modes, whole-word, phrases, across-line matches,
 * accents, regex and result-fragment context.  These pin current behavior as the safety net for
 * streamlining WORD/PAGE into one corpus engine and adding Unicode-normalized matching.
 * <p>
 * Counts are the current, observed behavior.  The {@code accentGap} test intentionally documents a
 * known limitation (accent-sensitive matching) that a later step is expected to change.
 */
public class DocumentSearchConvergenceTest {

    private static final String POEM = "/redact/test_print.pdf";
    private static final String ADDENDUM = "/redact/pdf_reference_addendum_redaction.pdf";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    private Document doc(String resource) throws Exception {
        Document d = new Document();
        d.setFile(DocumentSearchConvergenceTest.class.getResource(resource).getFile());
        return d;
    }

    private int word(Document d, int page, String term, boolean caseSensitive, boolean wholeWord) {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.WORD);
        c.addSearchTerm(term, caseSensitive, wholeWord);
        return c.searchHighlightPage(page);
    }

    private int page(Document d, int page, String term, boolean caseSensitive, boolean regex) {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.PAGE);
        c.addSearchTerm(term, caseSensitive, false, regex);
        return c.searchHighlightPage(page);
    }

    private String pageHighlighted(Document d, int page, String term, boolean regex) {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.PAGE);
        c.addSearchTerm(term, false, false, regex);
        List<WordText> words = c.searchPage(page);
        StringBuilder sb = new StringBuilder();
        if (words != null) for (WordText w : words) sb.append(w.getText());
        return sb.toString().replace(" ", "");
    }

    @DisplayName("WORD mode: substring vs whole-word counts")
    @Test
    public void wordMode() throws Exception {
        Document d = doc(POEM);
        assertEquals(10, word(d, 0, "Un", false, false));   // substring, case-insensitive
        assertEquals(7, word(d, 0, "Un", true, true));      // whole word, case-sensitive
        assertEquals(8, word(d, 0, "un", false, true));     // whole word, case-insensitive
        assertEquals(4, word(d, 0, "que", false, false));
        assertEquals(8, word(d, 0, "de", false, false));    // substring
        assertEquals(5, word(d, 0, "de", false, true));     // whole word
        assertEquals(2, word(d, 0, "de la", false, false)); // phrase across tokens
        assertEquals(0, word(d, 0, "zzznotpresent", false, false));
        d.dispose();
    }

    @DisplayName("PAGE mode: substring, phrase, across-line, regex")
    @Test
    public void pageMode() throws Exception {
        Document d = doc(POEM);
        assertEquals(10, page(d, 0, "Un", false, false));
        assertEquals(1, page(d, 0, "todo el mundo", false, false));
        assertEquals(1, page(d, 0, "Un sacerdote", false, false));   // spans a line break
        assertEquals(1, page(d, 0, "sí mismo", false, false));
        assertEquals(2, page(d, 0, "de\\s+la", false, true));        // regex
        assertEquals(8, page(d, 0, "\\bUn\\b", false, true));        // whole-word via regex
        d.dispose();
    }

    @DisplayName("accent-sensitive matching (current limitation; Unicode normalization will change these)")
    @Test
    public void accentGap() throws Exception {
        Document d = doc(POEM);
        // accented term matches, unaccented term currently does NOT.
        assertEquals(1, word(d, 0, "carácter", false, false));
        assertEquals(0, word(d, 0, "caracter", false, false));   // <- gap
        assertEquals(1, page(d, 0, "sí mismo", false, false));
        assertEquals(0, page(d, 0, "si mismo", false, false));   // <- gap
        assertEquals(1, page(d, 0, "ataúdes", false, false));
        assertEquals(0, page(d, 0, "ataudes", false, false));    // <- gap
        d.dispose();
    }

    @DisplayName("second corpus: PDF redaction addendum (multi-page, dense)")
    @Test
    public void addendumCorpus() throws Exception {
        Document d = doc(ADDENDUM);
        assertEquals(10, word(d, 1, "Redaction", false, false));
        assertEquals(13, word(d, 2, "annotation", false, false));
        // whole word: \b boundaries now also find "PDF" glued to punctuation, e.g. "(PDF 1.7)",
        // which the old token-equality matcher missed (6 -> 8, an intentional improvement).
        assertEquals(8, word(d, 1, "PDF", false, true));
        assertEquals(8, page(d, 2, "redaction annotation", false, false));
        assertEquals(3, page(d, 1, "PDF Reference", false, false));
        d.dispose();
    }

    @DisplayName("PAGE mode highlights the matched words")
    @Test
    public void pageHighlightContent() throws Exception {
        Document d = doc(POEM);
        assertTrue(pageHighlighted(d, 0, "todo el mundo", false).contains("todoelmundo"));
        d.dispose();
    }

    @DisplayName("result fragments + context padding")
    @Test
    public void resultFragmentsAndContext() throws Exception {
        Document d = doc(POEM);

        // WORD mode: one fragment per hit, padded with surrounding context words.
        DocumentSearchControllerImpl wordCtl = new DocumentSearchControllerImpl(d);
        wordCtl.setSearchMode(SearchMode.WORD);
        wordCtl.addSearchTerm("que", false, false);
        List<LineText> wordFragments = wordCtl.searchHighlightPage(0, 2);
        assertEquals(4, wordFragments.size());
        for (LineText fragment : wordFragments) {
            assertTrue(fragment.toString().toLowerCase().contains("que"));
            assertTrue(fragment.getWords().size() > 1, "fragment should carry context words");
        }

        // PAGE mode: one fragment per phrase hit, with line context.
        DocumentSearchControllerImpl pageCtl = new DocumentSearchControllerImpl(d);
        pageCtl.setSearchMode(SearchMode.PAGE);
        pageCtl.addSearchTerm("todo el mundo", false, false, false);
        List<LineText> pageFragments = pageCtl.searchHighlightPage(0, 0);
        assertEquals(1, pageFragments.size());
        StringBuilder joined = new StringBuilder();
        pageFragments.get(0).getWords().forEach(w -> joined.append(w.getText()));
        assertTrue(joined.toString().replace(" ", "").contains("todoelmundo"));
        d.dispose();
    }
}
