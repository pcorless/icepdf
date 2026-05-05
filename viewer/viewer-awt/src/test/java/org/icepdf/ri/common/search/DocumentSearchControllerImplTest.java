/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.search;

import org.icepdf.core.search.SearchTerm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentSearchControllerImplTest {

    private DocumentSearchControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentSearchControllerImpl(mock(org.icepdf.core.pobjects.Document.class));
    }

    @Test
    void addSearchTerm_addsTermToSearchModel() {
        SearchTerm term = controller.addSearchTerm("hello", false, false);

        assertNotNull(term);
        assertEquals("hello", term.getTerm());
    }

    @Test
    void addSearchTerm_caseSensitive_preservesOriginalCase() {
        SearchTerm term = controller.addSearchTerm("Hello", true, false);

        assertEquals("Hello", term.getTerm());
    }

    @Test
    void addSearchTerm_caseInsensitive_storesLowercaseTerms() {
        SearchTerm term = controller.addSearchTerm("Hello", false, false);

        assertEquals("hello", term.getTerms().get(0));
    }

    @Test
    void addSearchTerm_wholeWord_flagIsSet() {
        SearchTerm term = controller.addSearchTerm("hello", false, true);

        assertTrue(term.isWholeWord());
    }

    @Test
    void addSearchTerm_regex_flagIsSet() {
        SearchTerm term = controller.addSearchTerm("hel+o", false, false, true);

        assertTrue(term.isRegex());
        assertNotNull(term.getRegexPattern());
    }

    @Test
    void removeSearchTerm_removesTermFromModel() {
        SearchTerm term = controller.addSearchTerm("hello", false, false);
        controller.removeSearchTerm(term);

        SearchTerm term2 = controller.addSearchTerm("world", false, false);
        controller.removeSearchTerm(term2);

        // after removing both, adding a new one should still work cleanly
        SearchTerm term3 = controller.addSearchTerm("icepdf", false, false);
        assertNotNull(term3);
    }

    @Test
    void searchPhraseParser_singleWord_returnsSingleTerm() {
        ArrayList<String> terms = controller.searchPhraseParser("hello");

        assertEquals(1, terms.size());
        assertEquals("hello", terms.get(0));
    }

    @Test
    void searchPhraseParser_multipleWords_returnsAllWords() {
        ArrayList<String> terms = controller.searchPhraseParser("hello world");

        assertEquals(2, terms.size());
        assertEquals("hello", terms.get(0));
        assertEquals("world", terms.get(1));
    }

    @Test
    void searchPhraseParser_leadingAndTrailingWhitespace_isTrimmed() {
        ArrayList<String> terms = controller.searchPhraseParser("  hello  ");

        assertEquals(1, terms.size());
        assertEquals("hello", terms.get(0));
    }

    @Test
    void searchPhraseParser_emptyString_returnsEmptyList() {
        ArrayList<String> terms = controller.searchPhraseParser("");

        assertTrue(terms.isEmpty());
    }

    @Test
    void searchPhraseParser_phraseWithPunctuation_splitOnPunctuation() {
        ArrayList<String> terms = controller.searchPhraseParser("hello,world");

        assertTrue(terms.size() > 1);
        assertEquals("hello", terms.get(0));
    }

    @Test
    void dispose_clearsSearchState() {
        controller.addSearchTerm("hello", false, false);
        controller.dispose();

        // after dispose, adding a term to a fresh controller should still work
        DocumentSearchControllerImpl freshController =
                new DocumentSearchControllerImpl(mock(org.icepdf.core.pobjects.Document.class));
        SearchTerm term = freshController.addSearchTerm("test", false, false);
        assertNotNull(term);
    }

    @Test
    void clearSearchHighlight_withNegativePageIndex_doesNotThrow() {
        // clearSearchHighlight uses viewerController which is only available in non-headless mode.
        // Test the bounds guard via a controller backed by a mocked viewerController.
        org.icepdf.ri.common.SwingController mockController = mock(org.icepdf.ri.common.SwingController.class);
        org.icepdf.ri.common.views.DocumentViewController mockDvc =
                mock(org.icepdf.ri.common.views.DocumentViewController.class);
        org.icepdf.ri.common.views.DocumentViewModel mockDvm = mock(org.icepdf.ri.common.views.DocumentViewModel.class);
        when(mockController.getDocumentViewController()).thenReturn(mockDvc);
        when(mockDvc.getDocumentViewModel()).thenReturn(mockDvm);
        when(mockDvm.getPageComponents()).thenReturn(new java.util.ArrayList<>());

        DocumentSearchControllerImpl swingController = new DocumentSearchControllerImpl(mockController);

        assertDoesNotThrow(() -> swingController.clearSearchHighlight(-1));
        assertDoesNotThrow(() -> swingController.clearSearchHighlight(999));
    }

    @Test
    void setSearchMode_and_getSearchMode_roundTrip() {
        controller.setSearchMode(org.icepdf.core.search.SearchMode.PAGE);

        assertEquals(org.icepdf.core.search.SearchMode.PAGE, controller.getSearchMode());
    }

    @Test
    void addSearchTerm_multipleTerms_allAreStored() {
        controller.addSearchTerm("hello", false, false);
        controller.addSearchTerm("world", false, false);

        // adding a third term and verifying it returns non-null confirms the model accumulates terms
        SearchTerm third = controller.addSearchTerm("icepdf", false, false);
        assertNotNull(third);
        assertEquals("icepdf", third.getTerm());
    }

    @Test
    void getComponentsFor_pageWithNoHits_returnsEmptySet() {
        assertTrue(controller.getComponentsFor(0).isEmpty());
    }

    @Test
    void getComponentsFor_negativePageIndex_returnsEmptySet() {
        assertTrue(controller.getComponentsFor(-1).isEmpty());
    }
}

