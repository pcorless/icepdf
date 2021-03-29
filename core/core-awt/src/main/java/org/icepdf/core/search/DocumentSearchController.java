/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
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
package org.icepdf.core.search;

import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Document search controller interface for content text searches
 *
 * @since 4.0
 */
public interface DocumentSearchController {
    /**
     * Searches the given page using the specified term and properties.  The
     * search model is updated to store the pages Page text as a weak reference
     * which can be queried using isSearchHighlightNeeded to efficiently make
     * sure that a pages text is highlighted even after a despose/init cycle.
     * If the text state is no longer present then the search should be executed
     * again.
     * <br>
     * This method clears the search results for the page before it searches. If
     * you wish to have cumulative search results then searches terms should
     * be added with {@link #addSearchTerm(String, boolean, boolean)} and the
     * method {@link #searchPage(int)} should be called after each term is
     * added or after all have been added.
     *
     * @param pageIndex     page to search
     * @param caseSensitive if true use case sensitive searches
     * @param wholeWord     if true use whole word searches
     * @param term          term to search for
     * @return number for hits for this page.
     */
    int searchHighlightPage(int pageIndex, String term,
                            boolean caseSensitive, boolean wholeWord);

    /**
     * Searches the page index given the search terms that have been added
     * with {@link #addSearchTerm(String, boolean, boolean)}.  If search
     * hits where detected then the Page's PageText is added to the cache.
     * <br>
     * This method represent the org.icepdf.core search algorithm for this
     * DocumentSearchController implementation.  This method can be overridden
     * if a different search algorithm or functionality is needed.
     *
     * @param pageIndex page index to search
     * @return number of hits found for this page.
     */
    int searchHighlightPage(int pageIndex);

    /**
     * Searches the page index given the search terms that have been added
     * with {@link #addSearchTerm(String, boolean, boolean)}.  If search
     * hits where detected then the Page's PageText is added to the cache.
     * <br>
     * This class differences from {@link #searchHighlightPage(int)} in that
     * is returns a list of lineText fragments for each hit but the LinText
     * is padded by pre and post words that surround the hit in the page
     * context.
     * <br>
     * This method represent the org.icepdf.core search algorithm for this
     * DocumentSearchController implementation.  This method can be overridden
     * if a different search algorithm or functionality is needed.
     *
     * @param pageIndex   page index to search
     * @param wordPadding word padding on either side of hit to give context
     *                    to found words in the returned LineText.  Values should be greater than
     *                    zero
     * @return number of hits found for this page.
     */
    List<LineText> searchHighlightPage(int pageIndex, int wordPadding);

    /**
     * Sets the search mode
     * @param searchMode The searchMode
     */
    void setSearchMode(SearchMode searchMode);

    /**
     * @return The current SearchMode
     */
    SearchMode getSearchMode();

    /**
     * Search page but only return words that are hits.  Highlighting is till
     * applied but this method can be used if other data needs to be extracted
     * from the found words.
     *
     * @param pageIndex page to search
     * @return list of words that match the term and search properties.
     */
    ArrayList<WordText> searchPage(int pageIndex);

    /**
     * Search page only return MarkupAnnotations that are hits.
     *
     * @param pageIndex page index to search
     * @return any markup annotations that match the search terms,  null if none are found.
     */
    ArrayList<MarkupAnnotation> searchComments(int pageIndex);

    /**
     * Search the document's destination objects for names that match the search terms.
     *
     * @return any destination label that match the search terms,  null if none are found.
     */
    ArrayList<DestinationResult> searchDestinations();

    /**
     * Search the document's outlines objects for names that match the search terms.
     *
     * @return any destination label that match the search terms,  null if none are found.
     */
    ArrayList<OutlineItem> searchOutlines();

    /**
     * Sets the search hit cursor to a particular page to aid in large page jumps when other
     * navigation controls are used to navigate around the document.
     *
     * @param page page number.
     */
    void setCurrentPage(int page);

    /**
     * Sets the specified word as the current search hit, so that next and previous will be relative to the the
     * word position.
     *
     * @param pageIndex parent page index.
     * @param wordText  word text to make current cursor.
     */
    void setCurrentSearchHit(int pageIndex, WordText wordText);

    /**
     * Gets the next word that is marked as hit.  The word's highlightCursor flag is also set
     * allowing it to be painted differently then regular highlight flagged text.  If the last
     * page and word hit of the document is reached then the first page will be displayed next.
     *
     * @return next search hit work if any, otherwise null.
     */
    WordText nextSearchHit();

    /**
     * Gets the previous word that is marked as hit.  The word's highlightCursor flag is also set
     * allowing it to be painted differently then regular highlight flagged text.  If the first
     * page and word hit of the document is reached then the last page will be displayed next.
     *
     * @return next search hit work if any, otherwise null.
     */
    WordText previousSearchHit();

    /**
     * Thew page view if applicable should navigate to the page and word specified.  There is no quarentee
     * the work will be highlighted.
     *
     * @param pageIndex page index to navigate to
     * @param word      word to show.
     */
    void showWord(int pageIndex, WordText word);

    /**
     * Add the search term to the list of search terms.  The term is split
     * into words based on white space and punctuation if the search mode is WORD. No checks are done
     * for duplication.
     * <br>
     * A new search needs to be executed for this change to take place.
     *
     * @param term          single word or phrase to search for.
     * @param caseSensitive is search case sensitive.
     * @param wholeWord     is search whole word sensitive.
     * @return searchTerm newly create search term.
     */
    SearchTerm addSearchTerm(String term, boolean caseSensitive,
                             boolean wholeWord);

    /**
     * Add the search term to the list of search terms.  The term is split
     * into words based on white space and punctuation if the search mode is WORD. No checks are done
     * for duplication.
     * <br>
     * A new search needs to be executed for this change to take place.
     *
     * @param term          single word or phrase to search for.
     * @param caseSensitive is search case sensitive.
     * @param wholeWord     is search whole word sensitive.
     * @param regex         enable regex processing on a word by word level.
     * @return searchTerm newly create search term.
     */
    SearchTerm addSearchTerm(String term, boolean caseSensitive,
                             boolean wholeWord, boolean regex);

    /**
     * Add the search term to the list of search terms.  The term is split
     * into words based on white space and punctuation. No checks are done
     * for duplication.
     * <br>
     * A new search needs to be executed for this change to take place.
     *
     * @param term           single word or phrase to search for.
     * @param caseSensitive  is search case sensitive.
     * @param wholeWord      is search whole word sensitive.
     * @param regex          enable regex processing on a word by word level.
     * @param highlightColor The color to highlight the found terms with
     * @return searchTerm newly create search term.
     */
    SearchTerm addSearchTerm(String term, boolean caseSensitive,
                             boolean wholeWord, boolean regex, Color highlightColor);

    /**
     * Removes the specified search term from the search. A new search needs
     * to be executed for this change to take place.
     *
     * @param searchTerm search term to remove.
     */
    void removeSearchTerm(SearchTerm searchTerm);

    /**
     * Clear all searched items for specified page.
     *
     * @param pageIndex page index to clear
     */
    void clearSearchHighlight(int pageIndex);

    /**
     * Clears all highlighted text states for this this document.  This optimized
     * to use the the SearchHighlightModel to only clear pages that still have
     * selected states.
     */
    void clearAllSearchHighlight();

    /**
     * Test to see if a search highlight is needed.  This is done by first
     * check if there is a hit for this page and if the PageText object is the
     * same as the one specified as a param.  If they are not the same PageText
     * object then we need to do refresh as the page was disposed and
     * reinitialized with new content.
     *
     * @param pageIndex page index to text for restuls.
     * @param pageText  current pageText object associated with the pageIndex.
     * @return true if refresh is needed, false otherwise.
     */
    boolean isSearchHighlightRefreshNeeded(int pageIndex, PageText pageText);

    /**
     * Disposes controller clearing resources.
     */
    void dispose();
}
