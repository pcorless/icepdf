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
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.common.search;

import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.ri.common.SwingController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Document search controller used to manage document searches.  This class
 * class takes care of many of the performance issues of doing searches on
 * larges documents and is also used by PageViewComponentImpl to highlight
 * search results.
 * <p/>
 * This implementation uses simple search algorithm that will work well for most
 * users. This class can be exteded and the method {@link #searchHighlightPage(int)}
 * can be overridden for custom search implmentations.
 *
 * @since 4.0
 */
public class DocumentSearchControllerImpl implements DocumentSearchController {

    private static final Logger logger =
            Logger.getLogger(DocumentSearchControllerImpl.class.toString());

    // search model contains caching and memory optimizations.
    protected DocumentSearchModelImpl searchModel;
    // parent controller used to get at RI controllers and models.
    protected SwingController viewerController;

    /**
     * Create a news instnace of search controller. A search model is created
     * for this instanc.e
     *
     * @param viewerController parent controller/mediator.
     */
    public DocumentSearchControllerImpl(SwingController viewerController) {
        this.viewerController = viewerController;
        searchModel = new DocumentSearchModelImpl();
    }

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
    public int searchHighlightPage(int pageIndex, String term,
                                   boolean caseSensitive, boolean wholeWord) {
        // clear previous search
        clearSearchHighlight(pageIndex);
        // add the search term
        addSearchTerm(term, caseSensitive, wholeWord);
        // start the search and return the hit count.
        return searchHighlightPage(pageIndex);
    }

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
    public int searchHighlightPage(int pageIndex) {

        // get search terms from model and search for each occurrence.
        Collection<SearchTerm> terms = searchModel.getSearchTerms();

        // search hit count
        int hitCount = 0;

        // get our our page text reference
        PageText pageText = viewerController.getDocument()
                .getPageText(pageIndex);

        // some pages just don't have any text. 
        if (pageText == null){
            return 0;
        }

        // we need to do the search for  each term.
        for (SearchTerm term : terms) {

            // found word index to keep track of when we have found a hit
            int searchPhraseHitCount = 0;
            int searchPhraseFoundCount = term.getTerms().size();
            // list of found words for highlighting, as hits can span
            // lines and pages
            ArrayList<WordText> searchPhraseHits =
                    new ArrayList<WordText>(searchPhraseFoundCount);

            // start iteration over words.
            ArrayList<LineText> pageLines = pageText.getPageLines();
            for (LineText pageLine : pageLines) {
                ArrayList<WordText> lineWords = pageLine.getWords();
                // compare words against search terms.
                String wordString;
                for (WordText word : lineWords) {
                    // apply case sensitivity rule.
                    wordString = term.isCaseSensitive() ? word.toString() :
                            word.toString().toLowerCase();
                    // word matches, we have to match full word hits
                    if (term.isWholeWord()) {
                        if (wordString.equals(
                                term.getTerms().get(searchPhraseHitCount))) {
                            // add word to potentials
                            searchPhraseHits.add(word);
                            searchPhraseHitCount++;
                        }
//                                else if (wordString.length() == 1 &&
//                                        WordText.isPunctuation(wordString.charAt(0))){
//                                    // ignore punctuation
//                                    searchPhraseHitCount++;
//                                }
                        // reset the counters.
                        else {
                            searchPhraseHits.clear();
                            searchPhraseHitCount = 0;
                        }
                    }
                    // otherwise we look for an index of hits
                    else {
                        // found a potential hit, depends on the length
                        // of searchPhrase.
                        if (wordString.indexOf(
                                term.getTerms().get(searchPhraseHitCount)) >= 0) {
                            // add word to potentials
                            searchPhraseHits.add(word);
                            searchPhraseHitCount++;
                        }
//                                else if (wordString.length() == 1 &&
//                                        WordText.isPunctuation(wordString.charAt(0))){
//                                    // ignore punctuation
//                                    searchPhraseHitCount++;
//                                }
                        // reset the counters.
                        else {
                            searchPhraseHits.clear();
                            searchPhraseHitCount = 0;
                        }

                    }
                    // check if we have found what we're looking for
                    if (searchPhraseHitCount == searchPhraseFoundCount) {
                        // iterate of found, highlighting words
                        for (WordText wordHit : searchPhraseHits) {
                            wordHit.setHighlighted(true);
                            wordHit.setHasHighlight(true);
                        }

                        // rest counts and start over again.
                        hitCount++;
                        searchPhraseHits.clear();
                        searchPhraseHitCount = 0;
                    }

                }
            }
        }

        // if we have a hit we'll add it to the model cache
        if (hitCount > 0) {
            searchModel.addPageSearchHit(pageIndex, pageText);
            if (logger.isLoggable(Level.FINE)){
                logger.fine("Found search hits on page " + pageIndex +
                        " hit count " + hitCount);
            }
        }

        return hitCount;
    }

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
     * @param pageIndex page index to search
     * @param wordPadding word padding on either side of hit to give context
     * to found woords in the returned LineText
     * @return list of contectual hits for the give page.  If no hits an empty
     * list is returned. 
     */                                                                          
    public ArrayList<LineText> searchHighlightPage(int pageIndex, int wordPadding){
        // get search terms from model and search for each occurrence.
        Collection<SearchTerm> terms = searchModel.getSearchTerms();

        // search hit list
        ArrayList<LineText>searchHits = new ArrayList<LineText>();

        // get our our page text reference
        PageText pageText = viewerController.getDocument()
                .getPageText(pageIndex);

        // some pages just don't have any text.
        if (pageText == null){
            return searchHits;
        }

        // we need to do the search for  each term.
        for (SearchTerm term : terms) {

            // found word index to keep track of when we have found a hit
            int searchPhraseHitCount = 0;
            int searchPhraseFoundCount = term.getTerms().size();
            // list of found words for highlighting, as hits can span
            // lines and pages
            ArrayList<WordText> searchPhraseHits =
                    new ArrayList<WordText>(searchPhraseFoundCount);

            // start iteration over words.
            ArrayList<LineText> pageLines = pageText.getPageLines();
            for (LineText pageLine : pageLines) {
                ArrayList<WordText> lineWords = pageLine.getWords();
                // compare words against search terms.
                String wordString;
                WordText word;
                for (int i= 0, max = lineWords.size(); i < max; i++) {
                    word = lineWords.get(i);

                    // apply case sensitivity rule.
                    wordString = term.isCaseSensitive() ? word.toString() :
                            word.toString().toLowerCase();
                    // word matches, we have to match full word hits
                    if (term.isWholeWord()) {
                        if (wordString.equals(
                                term.getTerms().get(searchPhraseHitCount))) {
                            // add word to potentials
                            searchPhraseHits.add(word);
                            searchPhraseHitCount++;
                        }
                        // reset the counters.
                        else {
                            searchPhraseHits.clear();
                            searchPhraseHitCount = 0;
                        }
                    }
                    // otherwise we look for an index of hits
                    else {
                        // found a potential hit, depends on the length
                        // of searchPhrase.
                        if (wordString.indexOf(
                                term.getTerms().get(searchPhraseHitCount)) >= 0) {
                            // add word to potentials
                            searchPhraseHits.add(word);
                            searchPhraseHitCount++;
                        }
                        // reset the counters.
                        else {
                            searchPhraseHits.clear();
                            searchPhraseHitCount = 0;
                        }

                    }
                    // check if we have found what we're looking for
                    if (searchPhraseHitCount == searchPhraseFoundCount) {

                        LineText lineText = new LineText();
                        int lineWordsSize = lineWords.size();
                        ArrayList<WordText> hitWords = lineText.getWords();
                        // add pre padding
                        int start = i - searchPhraseHitCount - wordPadding + 1;
                        start = start < 0? 0:start;
                        int end = i - searchPhraseHitCount + 1;
                        end = end < 0? 0:end;
                        for (int p = start; p < end; p++){
                            hitWords.add(lineWords.get(p));
                        }

                        // iterate of found, highlighting words
                        for (WordText wordHit : searchPhraseHits) {
                            wordHit.setHighlighted(true);
                            wordHit.setHasHighlight(true);
                        }
                        hitWords.addAll(searchPhraseHits);

                        // add word padding to front of line
                        start = i + 1;
                        start = start > lineWordsSize?lineWordsSize:start;
                        end = start + wordPadding;
                        end = end > lineWordsSize?lineWordsSize:end;
                        for (int p = start; p < end; p++){
                            hitWords.add(lineWords.get(p));
                        }

                        // add the hits to our list.
                        searchHits.add(lineText);

                        searchPhraseHits.clear();
                        searchPhraseHitCount = 0;
                    }

                }
            }
        }

        // if we have a hit we'll add it to the model cache
        if (searchHits.size() > 0) {
            searchModel.addPageSearchHit(pageIndex, pageText);
            if (logger.isLoggable(Level.FINE)){
                logger.fine("Found search hits on page " + pageIndex +
                        " hit count " + searchHits.size());
            }
        }

        return searchHits;
    }

    /**
     * Search page but only return words that are hits.  Highlighting is till
     * applied but this method can be used if other data needs to be extracted
     * from the found words.
     *
     * @param pageIndex page to search
     * @return list of words that match the term and search properites.
     */
    public ArrayList<WordText> searchPage(int pageIndex) {

        int hits = searchHighlightPage(pageIndex);
        if (hits > 0) {
            PageText searchText = searchModel.getPageTextHit(pageIndex);
            if (searchText != null) {
                ArrayList<WordText> words = new ArrayList<WordText>(hits);
                ArrayList<LineText> pageLines = searchText.getPageLines();
                for (LineText pageLine : pageLines) {
                    ArrayList<WordText> lineWords = pageLine.getWords();
                    for (WordText word : lineWords) {
                        if (word.isHighlighted()) {
                            words.add(word);
                        }
                    }
                }
                return words;
            }
        }
        return null;
    }

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
    public SearchTerm addSearchTerm(String term, boolean caseSensitive,
                                    boolean wholeWord) {
        // keep origional copy
        String origionalTerm = new String(term);

        // check criteria for case sensitivity.
        if (!caseSensitive) {
            term = term.toLowerCase();
        }
        // parse search term out into words, so we can match
        // them against WordText
        ArrayList<String> searchPhrase = searchPhraseParser(term);
        // finally add the search term to the list and return it for management
        SearchTerm searchTerm =
                new SearchTerm(origionalTerm, searchPhrase, caseSensitive, wholeWord);
        searchModel.addSearchTerm(searchTerm);
        return searchTerm;
    }

    /**
     * Removes the specified search term from the search. A new search needs
     * to be executed for this change to take place.
     *
     * @param searchTerm search term to remove.
     */
    public void removeSearchTerm(SearchTerm searchTerm) {
        searchModel.removeSearchTerm(searchTerm);
    }

    /**
     * Clear all searched items for specified page.
     *
     * @param pageIndex page indext to clear
     */
    public void clearSearchHighlight(int pageIndex) {
        // clear cache and terms list 
        searchModel.clearSearchResults(pageIndex);
    }

    /**
     * Clears all highlighted text states for this this document.  This optimized
     * to use the the SearchHighlightModel to only clear pages that still have
     * selected states.
     */
    public void clearAllSearchHighlight() {
        searchModel.clearSearchResults();
    }

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
    public boolean isSearchHighlightRefreshNeeded(int pageIndex, PageText pageText) {

        // check model to see if pages pagTex still has reference
        return searchModel.isPageTextMatch(pageIndex, pageText);
    }

    /**
     * Disposes controller clearing resources.
     */
    public void dispose() {
        searchModel.clearSearchResults();
    }

    /**
     * Utility for breaking the pattern up into searchable words.  Breaks are
     * done on white spaces and punctuation.
     *
     * @param phrase pattern to search words for.
     * @return list of words that make up phrase, words, spaces, punctuation.
     */
    protected ArrayList<String> searchPhraseParser(String phrase) {
        // trim white space, not really useful.
        phrase = phrase.trim();
        // found words. 
        ArrayList<String> words = new ArrayList<String>();
        char c;
        for (int start = 0, curs = 0, max = phrase.length(); curs < max; curs++) {
            c = phrase.charAt(curs);
            if (WordText.isWhiteSpace(c) ||
                    WordText.isPunctuation(c)) {
                // add word segment
                if (start != curs) {
                    words.add(phrase.substring(start, curs));
                }
                // add white space  as word too.
                words.add(phrase.substring(curs, curs + 1));
                // start
                start = curs + 1 < max ? curs + 1 : start;
            } else if (curs + 1 == max) {
                words.add(phrase.substring(start, curs + 1));
            }
        }
        return words;
    }
}
