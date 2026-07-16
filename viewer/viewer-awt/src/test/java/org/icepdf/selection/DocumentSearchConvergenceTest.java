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
 * Characterization tests for document search against a real PDF (the Parra poem in test_print.pdf).
 * The existing DocumentSearchControllerImplTest only covers term parsing/management; this pins the
 * actual match/highlight behavior so the TextSequence search-corpus convergence (Step 4) is
 * behavior-preserving.  Counts here are the current, observed behavior.
 */
public class DocumentSearchConvergenceTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    private Document doc() throws Exception {
        Document d = new Document();
        d.setFile(DocumentSearchConvergenceTest.class.getResource("/redact/test_print.pdf").getFile());
        return d;
    }

    private int wordHits(Document d, String term, boolean caseSensitive, boolean wholeWord) {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.WORD);
        c.addSearchTerm(term, caseSensitive, wholeWord);
        return c.searchHighlightPage(0);
    }

    private int pageHits(Document d, String term, boolean caseSensitive, boolean regex) {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.PAGE);
        c.addSearchTerm(term, caseSensitive, false, regex);
        return c.searchHighlightPage(0);
    }

    private String pageHighlightedText(Document d, String term, boolean regex) throws Exception {
        DocumentSearchControllerImpl c = new DocumentSearchControllerImpl(d);
        c.setSearchMode(SearchMode.PAGE);
        c.addSearchTerm(term, false, false, regex);
        List<WordText> words = c.searchPage(0);
        StringBuilder sb = new StringBuilder();
        if (words != null) for (WordText w : words) sb.append(w.getText());
        return sb.toString().replace(" ", "");
    }

    @DisplayName("WORD-mode hit counts (current behavior)")
    @Test
    public void wordModeCounts() throws Exception {
        Document d = doc();
        assertEquals(10, wordHits(d, "Un", false, false));   // case-insensitive un/Un
        assertEquals(7, wordHits(d, "Un", true, true));      // case-sensitive whole word "Un"
        assertEquals(4, wordHits(d, "que", false, false));
        assertEquals(8, wordHits(d, "de", false, false));
        assertEquals(0, wordHits(d, "zzznotpresent", false, false));
        d.dispose();
    }

    @DisplayName("PAGE-mode hit counts + regex + phrase (current behavior)")
    @Test
    public void pageModeCounts() throws Exception {
        Document d = doc();
        assertEquals(10, pageHits(d, "Un", false, false));
        assertEquals(1, pageHits(d, "todo el mundo", false, false));   // multi-word phrase
        assertEquals(2, pageHits(d, "de\\s+la", false, true));         // regex
        assertEquals(1, pageHits(d, "ataúdes", false, false));         // accented
        d.dispose();
    }

    @DisplayName("PAGE-mode highlights the matched words")
    @Test
    public void pageModeHighlightContent() throws Exception {
        Document d = doc();
        assertTrue(pageHighlightedText(d, "todo el mundo", false).contains("todoelmundo"),
                "phrase match should highlight the phrase words");
        d.dispose();
    }

    @DisplayName("result fragments + context padding (current behavior)")
    @Test
    public void resultFragmentsAndContext() throws Exception {
        Document d = doc();

        // WORD mode: one fragment per hit, padded with surrounding context words.
        DocumentSearchControllerImpl word = new DocumentSearchControllerImpl(d);
        word.setSearchMode(SearchMode.WORD);
        word.addSearchTerm("que", false, false);
        List<LineText> wordFragments = word.searchHighlightPage(0, 2);
        assertEquals(4, wordFragments.size(), "one fragment per 'que' hit");
        for (LineText fragment : wordFragments) {
            assertTrue(fragment.toString().toLowerCase().contains("que"), "fragment should contain the hit");
            assertTrue(fragment.getWords().size() > 1, "fragment should carry context words");
        }

        // PAGE mode: one fragment per phrase hit, with line context.
        DocumentSearchControllerImpl page = new DocumentSearchControllerImpl(d);
        page.setSearchMode(SearchMode.PAGE);
        page.addSearchTerm("todo el mundo", false, false, false);
        List<LineText> pageFragments = page.searchHighlightPage(0, 0);
        assertEquals(1, pageFragments.size());
        StringBuilder joined = new StringBuilder();
        pageFragments.get(0).getWords().forEach(w -> joined.append(w.getText()));
        assertTrue(joined.toString().replace(" ", "").contains("todoelmundo"));
        d.dispose();
    }
}
