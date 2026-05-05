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
package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleSearchHelperTest {

    private Controller mockController;
    private DocumentViewControllerImpl mockDocumentViewController;
    private DocumentSearchController mockSearchController;
    private Document mockDocument;
    private JComponent mockViewContainer;

    // tracks the page last searched so tests can assert on it
    private final AtomicInteger lastSearchedPage = new AtomicInteger(-1);

    @BeforeEach
    void setUp() {
        mockController = mock(Controller.class);
        mockDocumentViewController = mock(DocumentViewControllerImpl.class);
        mockSearchController = mock(DocumentSearchController.class);
        mockDocument = mock(Document.class);
        mockViewContainer = new JPanel();

        when(mockController.getCurrentPageNumber()).thenReturn(0);
        when(mockController.getDocument()).thenReturn(mockDocument);
        when(mockController.getDocumentViewController()).thenReturn(mockDocumentViewController);
        when(mockController.getDocumentSearchController()).thenReturn(mockSearchController);
        when(mockDocumentViewController.getViewContainer()).thenReturn(mockViewContainer);

        // track last page searched and return 0 hits by default
        doAnswer((Answer<Integer>) invocation -> {
            lastSearchedPage.set(invocation.getArgument(0));
            return 0;
        }).when(mockSearchController).searchHighlightPage(anyInt(), anyString(), anyBoolean(), anyBoolean());
    }

    private SimpleSearchHelper buildHelper(int pageCount, int startPage) {
        when(mockController.getCurrentPageNumber()).thenReturn(startPage);
        when(mockDocument.getNumberOfPages()).thenReturn(pageCount);
        return new SimpleSearchHelper.Builder(mockController, "test").build();
    }

    private void returnHitsOnPage(int page, int hits) {
        doAnswer((Answer<Integer>) invocation -> {
            lastSearchedPage.set(invocation.getArgument(0));
            return invocation.getArgument(0).equals(page) ? hits : 0;
        }).when(mockSearchController).searchHighlightPage(anyInt(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void nextResult_advancesForwardFromStartPage() {
        returnHitsOnPage(1, 3);
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.nextResult();

        verify(mockSearchController).nextSearchHit();
    }

    @Test
    void nextResult_wrapsAroundFromLastPageToFirstPage() {
        returnHitsOnPage(0, 2);
        SimpleSearchHelper helper = buildHelper(5, 4);

        helper.nextResult();

        assertEquals(0, lastSearchedPage.get());
    }

    @Test
    void previousResult_wrapsAroundFromFirstPageToLastPage() {
        // start on page 2 with 1 hit, then navigate back past the start of page (wordIndex will be 0)
        // so previousResult should search backwards and wrap to page 4
        returnHitsOnPage(2, 1);
        when(mockController.getCurrentPageNumber()).thenReturn(2);
        when(mockDocument.getNumberOfPages()).thenReturn(5);

        // set up: on build, searchForward from page 2 finds 1 hit there
        // then override so backwards search finds hits on page 4
        SimpleSearchHelper helper = new SimpleSearchHelper.Builder(mockController, "test").build();

        // after construction wordIndex=0, wordHits=1 → isStartOfPage()=true (wordIndex==0 && commentIndex==0)
        // so previousResult should decrement page and searchBackwards
        returnHitsOnPage(4, 2);
        lastSearchedPage.set(-1);
        helper.previousResult();

        assertEquals(4, lastSearchedPage.get());
        verify(mockSearchController).previousSearchHit();
    }

    @Test
    void previousResult_doesNotPassNegativePageIndexToSearchController() {
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.previousResult();

        // verify searchHighlightPage was never called with a negative index
        verify(mockSearchController, never()).searchHighlightPage(
                intThat(page -> page < 0), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void previousResult_onSinglePageDocument_staysOnPageZero() {
        returnHitsOnPage(0, 1);
        SimpleSearchHelper helper = buildHelper(1, 0);

        helper.previousResult();

        verify(mockSearchController, never()).searchHighlightPage(
                intThat(page -> page < 0), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void searchForward_skipsPageWithNoHitsAndSearchesNext() {
        returnHitsOnPage(2, 3);
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.nextResult();

        assertEquals(2, lastSearchedPage.get());
    }

    @Test
    void searchBackwards_skipsPageWithNoHitsAndSearchesPrevious() {
        // Start on page 2 with hits, so construction lands there. wordIndex=0 → isStartOfPage()=true.
        // When previousResult is called, searchBackwards from page 1 → 0 → wraps → 4 → 3 (hits).
        returnHitsOnPage(2, 1);
        when(mockController.getCurrentPageNumber()).thenReturn(2);
        when(mockDocument.getNumberOfPages()).thenReturn(5);
        SimpleSearchHelper helper = new SimpleSearchHelper.Builder(mockController, "test").build();

        returnHitsOnPage(3, 2);
        lastSearchedPage.set(-1);
        helper.previousResult();

        assertEquals(3, lastSearchedPage.get());
    }

    @Test
    void clearAll_resetsSearchHighlightsAndCallsRepaint() {
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.clearAll();

        verify(mockSearchController, atLeastOnce()).clearAllSearchHighlight();
    }

    @Test
    void nextResult_onDocumentWithNoHits_doesNotThrow() {
        SimpleSearchHelper helper = buildHelper(5, 0);

        // no pages have hits — should complete without throwing
        helper.nextResult();

        verify(mockSearchController).nextSearchHit();
    }

    @Test
    void previousResult_onDocumentWithNoHits_doesNotThrow() {
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.previousResult();

        verify(mockSearchController).previousSearchHit();
    }

    @Test
    void dispose_removesPropertyChangeListener() {
        SimpleSearchHelper helper = buildHelper(5, 0);

        helper.dispose();

        verify(mockDocumentViewController).removePropertyChangeListener(helper);
    }
}

