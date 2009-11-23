/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.search;

import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;

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
     * which can be queried using isSearchHighlightNeeded to effiecently make
     * sure that a pages text is highlighted even after a despose/init cycle.
     * If the text state is no longer preseent then the search should be executed
     * again.
     * <p/>
     * This method cleasr the serach results for the page before it searches. If
     * you wich to have cumligive search results then searches terms should
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
     * <p/>
     * This method represent the core search algorithm for this
     * DocumentSearchController implmentation.  This method can be overriden
     * if a different search algorithm or functinality is needed.
     *
     * @param pageIndex page index to search
     * @return number of hits found for this page.
     */
    int searchHighlightPage(int pageIndex);

    /**
     * Searches the page index given the search terms that have been added
     * with {@link #addSearchTerm(String, boolean, boolean)}.  If search
     * hits where detected then the Page's PageText is added to the cache.
     * <p/>
     * This class differences from {@link #searchHighlightPage(int)} in that
     * is returns a list of lineText fragements for each hit but the LinText
     * is padded by pre and post words that surround the hit in the page
     * context.
     * <p/>
     * This method represent the core search algorithm for this
     * DocumentSearchController implmentation.  This method can be overriden
     * if a different search algorithm or functinality is needed.
     *
     * @param pageIndex   page index to search
     * @param wordPadding word padding on either side of hit to give context
     *                    to found words in the returned LineText.  Values should be greater than
     *                    zero
     * @return number of hits found for this page.
     */
    List<LineText> searchHighlightPage(int pageIndex, int wordPadding);

    /**
     * Search page but only return words that are hits.  Highlighting is till
     * applied but this method can be used if other data needs to be extracted
     * from the found words.
     *
     * @param pageIndex page to search
     * @return list of words that match the term and search properites.
     */
    ArrayList<WordText> searchPage(int pageIndex);

    /**
     * Add the search term to the list of search terms.  The term is split
     * into words based on white space and punctuation. No checks are done
     * for duplication.
     * <p/>
     * A new search needs to be executed for this change to take place.
     *
     * @param term          single word or phrace to search for.
     * @param caseSensitive is search case sensitive.
     * @param wholeWord     is search whole word senstive.
     * @return searchTerm newly create search term.
     */
    SearchTerm addSearchTerm(String term, boolean caseSensitive,
                             boolean wholeWord);

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
     * @param pageIndex page indext to clear
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
