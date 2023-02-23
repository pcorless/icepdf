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
package org.icepdf.ri.common.search;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DestinationResult;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.search.SearchMode;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.search.SearchHitComponent;
import org.icepdf.ri.common.utility.search.SearchHitComponentFactory;
import org.icepdf.ri.common.utility.search.SearchHitComponentFactoryImpl;
import org.icepdf.ri.common.views.PageViewComponentImpl;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Document search controller used to manage document searches.  This class
 * class takes care of many of the performance issues of doing searches on
 * larges documents and is also used by PageViewComponentImpl to highlight
 * search results.
 * <br>
 * This implementation uses simple search algorithm that will work well for most
 * users. This class can be extended and the method {@link #searchHighlightPage(int)}
 * can be overridden for custom search implementations.
 * <br>
 * The DocumentSearchControllerImpl can be constructed to be used with the
 * Viewer RI source code via the constructor that takes a Controller as
 * a parameter.  The second variation is ended for a headless environment where
 * Swing is not needed, the constructor for this instance takes a Document
 * as a parameter.
 *
 * @since 4.0
 */
public class DocumentSearchControllerImpl implements DocumentSearchController {

    private static final Logger logger =
            Logger.getLogger(DocumentSearchControllerImpl.class.toString());

    // search model contains caching and memory optimizations.
    private final DocumentSearchModelImpl searchModel;
    // parent controller used to get at RI controllers and models.
    private SwingController viewerController;
    // assigned document for headless searching.
    protected Document document;
    public static final SearchHitComponentFactory componentFactory = new SearchHitComponentFactoryImpl();

    //Search mode is per-word or whole page
    private SearchMode searchMode = SearchMode.WORD;

    //Page index to SearchHitComponents
    private final Map<Integer, Set<SearchHitComponent>> pageToComponents = new HashMap<>();

    /**
     * Create a news instance of search controller. A search model is created
     * for this instance.
     *
     * @param viewerController parent controller/mediator.
     */
    public DocumentSearchControllerImpl(SwingController viewerController) {
        this.viewerController = viewerController;
        searchModel = new DocumentSearchModelImpl();
    }

    /**
     * Create a news instance of search controller intended to be used in a
     * headless environment.  A search model is created for this instance.
     *
     * @param document document to search.
     */
    public DocumentSearchControllerImpl(Document document) {
        searchModel = new DocumentSearchModelImpl();
        this.document = document;
    }

    /**
     * Searches the given page using the specified term and properties.  The
     * search model is updated to store the pages Page text as a weak reference
     * which can be queried using isSearchHighlightNeeded to efficiently make
     * sure that a pages text is highlighted even after a dispose/init cycle.
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
     * <br>
     * This method represent the org.icepdf.core search algorithm for this
     * DocumentSearchController implementation. This method can be overriden
     * if a different search algorithm or functionality is needed.
     *
     * @param pageIndex page index to search
     * @return number of hits found for this page.
     */
    public int searchHighlightPage(int pageIndex) {

        List<LineText> hits = searchHighlightPage(pageIndex, 0);
        if (hits != null) {
            return hits.size();
        } else {
            return 0;
        }
    }

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
     * DocumentSearchController implementation. This method can be overriden
     * if a different search algorithm or functionality is needed.
     *
     * @param pageIndex   page index to search
     * @param wordPadding word padding on either side of hit to give context
     *                    to found words in the returned LineText
     * @return list of contextual hits for the give page. If no hits an empty
     * list is returned.
     */
    public List<LineText> searchHighlightPage(int pageIndex, int wordPadding) {
        switch (searchMode) {
            case WORD:
                return searchHighlightWordPage(pageIndex, wordPadding);
            case PAGE:
                return searchHighlightWholePage(pageIndex);
            default:
                return Collections.emptyList();
        }
    }

    private List<LineText> searchHighlightWordPage(int pageIndex, int wordPadding) {
        // search hit list
        List<LineText> searchHits = new ArrayList<>();

        // get our our page text reference
        PageText pageText = getPageText(pageIndex);

        // some pages just don't have any text.
        if (pageText == null) {
            return searchHits;
        }

        // get search terms from model and search for each occurrence.
        List<SearchTerm> terms = searchModel.getSearchTerms();
        // we need to do the search for  each term.
        SearchTerm term;
        for (SearchTerm searchTerm : terms) {
            term = searchTerm;

            // found word index to keep track of when we have found a hit
            int searchPhraseHitCount = 0;
            int searchPhraseFoundCount = term.getTerms().size();

            // start iteration over words.
            List<LineText> pageLines = pageText.getPageLines();
            if (pageLines != null) {
                for (LineText pageLine : pageLines) {
                    List<WordText> lineWords = pageLine.getWords();
                    // compare words against search terms.
                    String wordString;
                    WordText word;
                    for (int i = 0, max = lineWords.size(); i < max; i++) {
                        word = lineWords.get(i);

                        // apply case sensitivity rule.
                        wordString = term.isCaseSensitive() ? word.toString() :
                                word.toString().toLowerCase();

                        // skip if it's white space, so we don't worry about it in the search
                        if (wordString.length() == 1) {
                            char c = wordString.charAt(0);
                            if (WordText.isWhiteSpace(c)) {
                                continue;
                            }
                        }

                        // word matches, we have to match full word hits
                        if (term.isWholeWord()) {
                            final List<String> termList = term.getTerms();
                            if (termList != null && termList.size() > searchPhraseHitCount) {
                                final String hit = termList.get(searchPhraseHitCount);
                                if (wordString.equals(hit)) {
                                    // add word to potentials
                                    searchPhraseHitCount++;
                                }
                                // reset the counters.
                                else {
                                    searchPhraseHitCount = 0;
                                }
                            } else {
                                searchPhraseHitCount = 0;
                            }
                        } else if (term.isRegex()) {
                            Pattern pattern = term.getRegexPattern();
                            Matcher matcher = pattern.matcher(wordString);
                            if (matcher.find()) {
                                // add word to potentials
                                if (!word.isWhiteSpace()) {
                                    searchPhraseHitCount++;
                                }
                            }
                        }
                        // otherwise we look for an index of hits
                        else {
                            // found a potential hit, depends on the length
                            // of searchPhrase.
                            final List<String> termList = term.getTerms();
                            if (termList != null && termList.size() > searchPhraseHitCount) {
                                final String hit = term.getTerms().get(searchPhraseHitCount);
                                if (hit != null && wordString.contains(hit)) {
                                    // add word to potentials
                                    searchPhraseHitCount++;
                                }
                                // reset the counters.
                                else {
                                    searchPhraseHitCount = 0;
                                }
                            } else {
                                searchPhraseHitCount = 0;
                            }
                        }
                        // check if we have found what we're looking for
                        if (searchPhraseHitCount > 0 && searchPhraseHitCount == searchPhraseFoundCount) {
                            LineText lineText = new LineText();
                            int lineWordsSize = lineWords.size();
                            List<WordText> hitWords = lineText.getWords();
                            // add pre padding
                            int spaces = searchPhraseHitCount - 1;
                            spaces = spaces < 0 ? 0 : spaces;
                            int start = i - searchPhraseHitCount - spaces - wordPadding + 1;
                            start = start < 0 ? 0 : start;
                            int end = i - searchPhraseHitCount - spaces;
                            end = end < 0 ? 0 : end;
                            // add post padding indexes.
                            int start2 = i + 1;
                            start2 = start2 > lineWordsSize ? lineWordsSize : start2;
                            int end2 = start2 + wordPadding;
                            end2 = end2 > lineWordsSize ? lineWordsSize : end2;

                            for (int p = start; p < end; p++) {
                                hitWords.add(lineWords.get(p));
                            }

                            // highlight the found words.
                            WordText wordHit;
                            for (int w = (end == 0) ? 0 : end + 1; w < start2; w++) {
                                wordHit = lineWords.get(w);
                                wordHit.setHighlighted(true);
                                wordHit.setHasHighlight(true);
                                wordHit.setHighlightColor(term.getHighlightColor());
                                hitWords.add(wordHit);
                                addComponent(pageIndex, wordHit.getText(), wordHit.getBounds());
                            }

                            for (int p = start2; p < end2; p++) {
                                hitWords.add(lineWords.get(p));
                            }

                            // add the hits to our list.
                            searchHits.add(lineText);
                            searchPhraseHitCount = 0;
                        }

                    }
                }
            }
        }

        // if we have a hit we'll add it to the model cache
        if (searchHits.size() > 0) {
            searchModel.addPageSearchHit(pageIndex, pageText, searchHits.size());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Found search hits on page " + pageIndex + " hit count " + searchHits.size());
            }
        }
        return searchHits;
    }

    @Override
    public void setSearchMode(SearchMode searchMode) {
        this.searchMode = searchMode;
    }

    @Override
    public SearchMode getSearchMode() {
        return searchMode;
    }

    private List<LineText> searchHighlightWholePage(final int pageIndex) {
        // search hit list
        final List<LineText> searchHits = new ArrayList<>();

        // get our our page text reference
        final PageText pageText = getPageText(pageIndex);

        // some pages just don't have any text.
        if (pageText == null) {
            return searchHits;
        }

        //Prepare whole text
        final List<SearchTerm> terms = searchModel.getSearchTerms();
        final Map<Integer, WordText> idxToWordText = new HashMap<>();
        final List<Integer> wordOffsets = new ArrayList<>();
        final Map<WordText, LineText> wordTextToLineText = new HashMap<>();
        SearchTerm term;
        final StringBuilder textBuilder = new StringBuilder();
        for (final LineText line : pageText.getPageLines()) {
            String lastWordText = null;
            if (line != null && line.getWords() != null) {
                for (final WordText word : line.getWords()) {
                    final String wordText = word.getText();
                    //Remove multi-spaces
                    if (!wordText.equals(" ") || (lastWordText != null && !lastWordText.equals(" "))) {
                        wordOffsets.add(textBuilder.length());
                        idxToWordText.put(wordOffsets.size() - 1, word);
                        textBuilder.append(wordText);
                        wordOffsets.add(textBuilder.length());
                        idxToWordText.put(wordOffsets.size() - 1, word);
                        lastWordText = wordText;
                        wordTextToLineText.put(word, line);
                    }
                }
            }
            textBuilder.append("\n");
        }
        final String text = textBuilder.toString();
        final Map<Color, List<SearchHit>> colorToHits = new HashMap<>();
        for (final SearchTerm searchTerm : terms) {
            term = searchTerm;

            String searchString = text;
            //If not regex, replace line feed by spaces
            if (!term.isRegex()) {
                searchString = searchString.replace("\n", " ");
            }
            //Always use regex to search
            final Pattern pattern = term.isRegex() ? term.getRegexPattern() : Pattern.compile(Pattern.quote(term.getTerm()),
                    term.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
            final Matcher matcher = pattern.matcher(searchString);
            final List<SearchHit> hits = new ArrayList<>();
            while (matcher.find()) {
                // add word to potentials
                final int start = matcher.start();
                final int end = matcher.end();
                final String hitText = text.substring(start, end);
                hits.add(new SearchHit(start, end, hitText));
            }
            final List<SearchHit> existingHits = colorToHits.getOrDefault(term.getHighlightColor(), new ArrayList<>());
            existingHits.addAll(hits);
            colorToHits.put(term.getHighlightColor(), existingHits);
        }
        // check if we have found what we're looking for
        for (final Color color : colorToHits.keySet()) {
            for (final SearchHit searchHit : colorToHits.get(color)) {
                final int start = searchHit.getStartOffset();
                final int end = searchHit.getEndOffset();
                //Get relevant WordTexts
                int startIdx = Collections.binarySearch(wordOffsets, start);
                if (startIdx < 0) {
                    startIdx = -(startIdx + 1);
                } else {
                    //Take word start
                    if (startIdx + 1 < wordOffsets.size() && wordOffsets.get(startIdx + 1) == start) {
                        startIdx += 1;
                    }
                }
                int endIdx = Collections.binarySearch(wordOffsets, end);
                if (endIdx < 0) {
                    endIdx = Math.min(-(endIdx + 1), idxToWordText.size() - 1);
                }
                final LineText lineText = new LineText();
                //Add start line context
                final WordText firstWord = idxToWordText.get(startIdx);
                final LineText firstLineText = wordTextToLineText.get(firstWord);
                if (firstLineText != null && firstLineText.getWords() != null) {
                    for (final WordText wt : firstLineText.getWords()) {
                        if (wt != firstWord) {
                            lineText.getWords().add(wt);
                        } else {
                            break;
                        }
                    }
                }
                WordText previous = null;
                for (int i = startIdx; i <= endIdx; ++i) {
                    final WordText wt = idxToWordText.get(i);
                    if (wt != previous) {
                        wt.setHighlighted(true);
                        wt.setHasHighlight(true);
                        wt.setHighlightColor(color);
                        lineText.getWords().add(wt);
                        addComponent(pageIndex, searchHit.getText(), wt.getBounds());
                    }
                    previous = wt;
                }
                //Add end line context
                final WordText lastWord = idxToWordText.get(endIdx);
                final LineText lastLineText = wordTextToLineText.get(lastWord);
                if (lastLineText != null && lastLineText.getWords() != null) {
                    boolean take = false;
                    for (final WordText wt : lastLineText.getWords()) {
                        if (take) {
                            lineText.getWords().add(wt);
                        } else if (wt == lastWord) {
                            take = true;
                        }
                    }
                }
                searchHits.add(lineText);
            }
        }


        // if we have a hit we'll add it to the model cache
        if (!searchHits.isEmpty()) {
            searchModel.addPageSearchHit(pageIndex, pageText, searchHits.size());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Found search hits on page " + pageIndex + " hit count " + searchHits.size());
            }
        }
        return searchHits;
    }

    /**
     * Search page but only return words that are hits. Highlighting is still
     * applied but this method can be used if other data needs to be extracted
     * from the found words.
     *
     * @param pageIndex page to search
     * @return list of words that match the term and search properties.
     */
    public ArrayList<WordText> searchPage(int pageIndex) {

        int hits = searchHighlightPage(pageIndex);
        if (hits > 0) {
            PageText searchText = searchModel.getPageTextHit(pageIndex);
            if (searchText != null) {
                ArrayList<WordText> words = new ArrayList<>(hits);
                ArrayList<LineText> pageLines = searchText.getPageLines();
                if (pageLines != null) {
                    for (LineText pageLine : pageLines) {
                        java.util.List<WordText> lineWords = pageLine.getWords();
                        if (lineWords != null) {
                            for (WordText word : lineWords) {
                                if (word.isHighlighted()) {
                                    words.add(word);
                                }
                            }
                        }
                    }
                }
                return words;
            }
        }
        return null;
    }

    @Override
    public List<TextWidgetAnnotation> searchForms(final int pageIndex) {
        if (document == null) document = viewerController.getDocument();
        final Page page = document.getPageTree().getPage(pageIndex);
        return page.getAnnotations() == null ? Collections.emptyList() : page.getAnnotations().stream()
                .filter(TextWidgetAnnotation.class::isInstance).map(TextWidgetAnnotation.class::cast)
                .filter(this::textWidgetAnnotationMatches).collect(Collectors.toList());

    }

    private boolean textWidgetAnnotationMatches(final TextWidgetAnnotation annotation) {
        final String contents = (String) annotation.getFieldDictionary().getFieldValue();
        if (contents != null) {
            final List<SearchTerm> searchTerms = searchModel.getSearchTerms();
            boolean found = false;
            for (int i = 0; i < searchTerms.size() && !found; i++) {
                final SearchTerm searchTerm = searchTerms.get(i);
                final Pattern pattern = searchTerm.getRegexPattern();
                final String term = searchTerm.getTerm();
                if (searchTerm.isRegex() && pattern != null) {
                    final Matcher matcher = pattern.matcher(contents);
                    found = matcher.find();
                } else if (term != null) {
                    if (searchTerm.isCaseSensitive()) {
                        found = contents.contains(term);
                    } else {
                        found = contents.toLowerCase().contains(term.toLowerCase());
                    }
                }
            }
            return found;
        } else {
            return false;
        }
    }

    @Override
    public ArrayList<MarkupAnnotation> searchComments(int pageIndex) {
        if (document == null) document = viewerController.getDocument();
        Page page = document.getPageTree().getPage(pageIndex);
        Library library = document.getCatalog().getLibrary();
        List<Reference> annotationReferences = page.getAnnotationReferences();
        ArrayList<MarkupAnnotation> foundAnnotations = new ArrayList<>();
        if (annotationReferences != null && annotationReferences.size() > 0) {
            // get search terms from model and search for each occurrence.
            Collection<SearchTerm> terms = searchModel.getSearchTerms();
            // we need to do the search for  each term.
            if (terms.size() > 0) {
                SearchTerm term = ((ArrayList<SearchTerm>) terms).get(0);
                Pattern searchPattern = term.getRegexPattern();
                String searchTerm = term.getTerm();
                for (Object annotationReference : annotationReferences) {
                    Object annotation = library.getObject(annotationReference);
                    if (annotation instanceof MarkupAnnotation) {
                        boolean found = false;
                        // app search regex
                        if (term.isRegex() && searchPattern != null) {
                            Matcher matcher = searchPattern.matcher(
                                    ((MarkupAnnotation) annotation).getContents());
                            found = matcher.find();
                        } else if (searchTerm != null) {
                            String annotationText = ((MarkupAnnotation) annotation).getContents();
                            if (term.isCaseSensitive() && annotationText != null) {
                                found = annotationText.contains(searchTerm);
                            } else if (annotationText != null) {
                                found = annotationText.toLowerCase().contains(searchTerm.toLowerCase());
                            }
                        }
                        if (found) {
                            foundAnnotations.add((MarkupAnnotation) annotation);
                        }
                    }
                }
            }
        }
        return foundAnnotations;
    }

    @Override
    public ArrayList<DestinationResult> searchDestinations() {
        if (document == null) document = viewerController.getDocument();
        ArrayList<DestinationResult> foundNames = new ArrayList<>();
        Names names = document.getCatalog().getNames();
        if (searchModel.getSearchTerms().size() > 0 &&
                names != null && names.getDestsNameTree() != null) {
            NameTree nameTree = names.getDestsNameTree();
            if (nameTree != null) {
                Collection<SearchTerm> terms = searchModel.getSearchTerms();
                SearchTerm term = ((ArrayList<SearchTerm>) terms).get(0);
                Pattern searchPattern = term.getRegexPattern();
                String searchTerm = term.getTerm();
                if (searchPattern == null) {
                    searchPattern = Pattern.compile(term.isCaseSensitive() ? searchTerm : searchTerm.toLowerCase());
                }
                recursiveNameSearch(searchPattern, term.isCaseSensitive(), foundNames, nameTree.getRoot());
            }
        }
        return foundNames;
    }

    private void recursiveNameSearch(Pattern searchPattern, boolean isCaseSensitive,
                                     ArrayList<DestinationResult> foundNameNodes, NameNode nameNode) {
        List kids = nameNode.getKidsReferences();
        if (kids != null) {
            int count = nameNode.getKidsReferences().size();
            for (int i = 0; i < count; i++) {
                NameNode child = nameNode.getNode(i);
                if (child.hasLimits()) {
                    recursiveNameSearch(searchPattern, isCaseSensitive, foundNameNodes, child);
                }
            }
        } else {
            // interate over the names.
            if (nameNode.getNamesAndValues() != null) {
                List namesAndValues = nameNode.getNamesAndValues();
                for (int i = 0, max = namesAndValues.size() - 1; i < max; i += 2) {
                    String name = ((StringObject) namesAndValues.get(i)).getLiteralString();
                    if (name != null && !name.isEmpty()) {
                        String matcherString = name;
                        if (!isCaseSensitive) {
                            matcherString = name.toLowerCase();
                        }
                        Matcher matcher = searchPattern.matcher(matcherString);
                        if (matcher.find()) {
                            foundNameNodes.add(new DestinationResult(name, namesAndValues.get(i + 1)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public ArrayList<OutlineItem> searchOutlines() {
        if (document == null) document = viewerController.getDocument();
        ArrayList<OutlineItem> foundOutlines = new ArrayList<>();
        Outlines outlines = document.getCatalog().getOutlines();
        if (outlines != null && searchModel.getSearchTerms().size() > 0) {
            Collection<SearchTerm> terms = searchModel.getSearchTerms();
            SearchTerm term = ((ArrayList<SearchTerm>) terms).get(0);
            Pattern searchPattern = term.getRegexPattern();
            String searchTerm = term.getTerm();
            if (searchPattern == null) {
                searchPattern = Pattern.compile(term.isCaseSensitive() ? searchTerm : searchTerm.toLowerCase());
            }
            recursiveOutlineSearch(searchPattern, term.isCaseSensitive(), foundOutlines, outlines.getRootOutlineItem());
        }
        return foundOutlines;
    }

    private void recursiveOutlineSearch(Pattern searchPattern, boolean isCaseSensitive,
                                        ArrayList<OutlineItem> foundOutlines, OutlineItem item) {
        int count = item.getSubItemCount();
        for (int i = 0; i < count; i++) {
            OutlineItem child = item.getSubItem(i);
            if (child.getSubItemCount() > 0) {
                recursiveOutlineSearch(searchPattern, isCaseSensitive, foundOutlines, child);
            } else {
                // search the item title for a match.
                String outlineTitle = child.getTitle();
                if (outlineTitle != null && !outlineTitle.isEmpty()) {
                    Matcher matcher = searchPattern.matcher(isCaseSensitive ? outlineTitle : outlineTitle.toLowerCase());
                    if (matcher.find()) {
                        foundOutlines.add(child);
                    }
                }
            }
        }
    }

    @Override
    public void setCurrentSearchHit(int pageIndex, WordText wordText) {

        PageText pageText = searchModel.getPageTextHit(pageIndex);
        if (pageText != null) {
            WordText word;
            ArrayList<LineText> pageLines = pageText.getPageLines();
            for (int k = 0, maxk = pageLines.size(); k < maxk; k++) {
                LineText lineText = pageLines.get(k);
                List<WordText> words = lineText.getWords();
                for (int j = 0, maxj = words.size(); j < maxj; j++) {
                    word = words.get(j);
                    if (word.equals(wordText)) {
                        word.setHighlightCursor(true);
                        searchModel.setSearchPageCursor(pageIndex);
                        searchModel.setSearchLineCursor(k);
                        searchModel.setSearchWordCursor(j);
                    }
                }
            }
        }
    }

    @Override
    public WordText nextSearchHit() {
        if (searchModel.getPageSearchHitsSize() == 0) return null;
        int searchPageCursor = searchModel.getSearchPageCursor();
        int searchLineCursor = searchModel.getSearchLineCursor();
        int searchWordCursor = searchModel.getSearchWordCursor();
        int pageCount = viewerController.getDocument().getNumberOfPages();

        if (searchPageCursor < pageCount) {
            WordText word;
            // move to the next hit, start at -1 after a search clear
            searchWordCursor++;
            for (int i = searchPageCursor; i < pageCount; i++) {
                if (searchModel.isPageSearchHit(i)) {
                    if (searchModel.getPageTextHit(i) == null) {
                        searchHighlightPage(i);
                    }
                    PageText pageText = searchModel.getPageTextHit(i);
                    if (pageText != null) {
                        pageText.clearHighlightedCursor();
                        ArrayList<LineText> pageLines = pageText.getPageLines();
                        for (int k = searchLineCursor, maxk = pageLines.size(); k < maxk; k++) {
                            LineText lineText = pageLines.get(k);
                            List<WordText> words = lineText.getWords();
                            for (int j = searchWordCursor, maxj = words.size(); j < maxj; j++) {
                                word = words.get(j);
                                if (word.isHighlighted()) {
                                    // highlight the rest of the words
                                    for (; j < maxj; j++) {
                                        if (!words.get(j).isHighlighted()) {
                                            break;
                                        }
                                        words.get(j).setHighlightCursor(true);
                                    }
                                    searchModel.setSearchPageCursor(i);
                                    searchModel.setSearchLineCursor(k);
                                    searchModel.setSearchWordCursor(j);
                                    showWord(i, word);
                                    return word;
                                }
                            }
                            searchWordCursor = 0;
                        }
                        searchLineCursor = 0;
                    }
                }
            }
            // if no more highlights left we go to the firs page.
            searchModel.setSearchPageCursor(0);
            searchModel.setSearchLineCursor(0);
            searchModel.setSearchWordCursor(-1);
            return nextSearchHit();
        }
        return null;
    }

    /**
     * Navigate tot he page that the current word is on.
     *
     * @param pageIndex page number to navigate to
     * @param word      word that has been marked as a cursor.
     */
    public void showWord(int pageIndex, WordText word) {
        viewerController.showPage(pageIndex);
        // navigate to the location
        Rectangle2D.Double bounds = word.getBounds();

        viewerController.getDocumentViewController().setDestinationTarget(
                new Destination(viewerController.getDocument().getPageTree().getPage(pageIndex),
                        (int) bounds.x, (int) (bounds.y + bounds.height + 100)));
    }

    @Override
    public WordText previousSearchHit() {
        if (searchModel.getPageSearchHitsSize() == 0) return null;
        int searchPageCursor = searchModel.getSearchPageCursor();
        int searchLineCursor = searchModel.getSearchLineCursor();
        int searchWordCursor = searchModel.getSearchWordCursor();
        int pageCount = viewerController.getDocument().getNumberOfPages();

        if (searchPageCursor < pageCount) {
            WordText word;
            // move to the next hit, start at -1 after a search clear
            searchWordCursor--;
            for (int i = searchPageCursor; i >= 0; i--) {
                if (searchModel.isPageSearchHit(i)) {
                    if (searchModel.getPageTextHit(i) == null) {
                        searchHighlightPage(i);
                    }
                    PageText pageText = searchModel.getPageTextHit(i);
                    if (pageText != null) {
                        pageText.clearHighlightedCursor();
                        ArrayList<LineText> pageLines = pageText.getPageLines();
                        if (pageLines.size() > 0) {
                            if (searchWordCursor < 0) searchLineCursor--;
                            if (searchLineCursor < 0) searchLineCursor = pageLines.size() - 1;
                            for (int k = searchLineCursor; k >= 0; k--) {
                                LineText lineText = pageLines.get(k);
                                List<WordText> words = lineText.getWords();
                                if (searchWordCursor < 0) searchWordCursor = words.size() - 1;
                                for (int j = searchWordCursor; j >= 0; j--) {
                                    word = words.get(j);
                                    if (word.isHighlighted()) {
                                        // highlight the rest of the words
                                        for (; j >= 0; j--) {
                                            if (!words.get(j).isHighlighted()) {
                                                break;
                                            }
                                            words.get(j).setHighlightCursor(true);
                                        }
                                        searchModel.setSearchPageCursor(i);
                                        searchModel.setSearchLineCursor(k);
                                        searchModel.setSearchWordCursor(j);
                                        showWord(i, word);
                                        return word;
                                    }
                                }
                                searchWordCursor = -1;
                            }
                        }
                        searchLineCursor = -1;
                    }
                }
            }
            // if no more highlights left we go to the last page.
            searchModel.setSearchPageCursor(pageCount - 1);
            searchModel.setSearchLineCursor(-1);
            searchModel.setSearchWordCursor(-1);
            return previousSearchHit();
        }
        return null;
    }

    @Override
    public void setCurrentPage(int page) {
        searchModel.setSearchPageCursor(page);
        searchModel.setSearchLineCursor(0);
        searchModel.setSearchWordCursor(-1);
    }

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
    public SearchTerm addSearchTerm(String term, boolean caseSensitive,
                                    boolean wholeWord) {
        return addSearchTerm(term, caseSensitive, wholeWord, false);
    }

    @Override
    public SearchTerm addSearchTerm(String term, boolean caseSensitive, boolean wholeWord, boolean regex) {
        return addSearchTerm(term, caseSensitive, wholeWord, regex, Page.highlightColor);
    }

    @Override
    public SearchTerm addSearchTerm(String term, boolean caseSensitive, boolean wholeWord, boolean regex, Color highlightColor) {
        // keep original copy
        String originalTerm = String.valueOf(term);

        // check criteria for case sensitivity.
        if (!caseSensitive) {
            term = term.toLowerCase();
        }
        // parse search term out into words, so we can match
        // them against WordText
        ArrayList<String> searchPhrase = searchMode == SearchMode.PAGE ? new ArrayList<>(Collections.singletonList(term)) : searchPhraseParser(term);
        // finally add the search term to the list and return it for management
        SearchTerm searchTerm =
                new SearchTerm(originalTerm, searchPhrase, caseSensitive, wholeWord, regex, highlightColor);
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
        PageViewComponentImpl pvc = (PageViewComponentImpl) viewerController.getDocumentViewController().getDocumentViewModel()
                .getPageComponents().get(pageIndex);
        pvc.clearSearchHighlights();
    }

    /**
     * Clears all highlighted text states for this this document.  This optimized
     * to use the the SearchHighlightModel to only clear pages that still have
     * selected states.
     */
    public void clearAllSearchHighlight() {
        searchModel.clearSearchResults();
        pageToComponents.forEach((key, shc) -> {
            PageViewComponentImpl pvc = (PageViewComponentImpl) viewerController.getDocumentViewController().getDocumentViewModel()
                    .getPageComponents().get(key);
            pvc.clearSearchHighlights();
        });
        pageToComponents.clear();
    }

    /**
     * Test to see if a search highlight is needed.  This is done by first
     * check if there is a hit for this page and if the PageText object is the
     * same as the one specified as a param.  If they are not the same PageText
     * object then we need to do refresh as the page was disposed and
     * reinitialized with new content.
     *
     * @param pageIndex page index to text for results.
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
     * Gets teh page text for the given page index.
     *
     * @param pageIndex page index of page to extract text.
     * @return page's page text,  can be null.
     */
    protected PageText getPageText(int pageIndex) {
        PageText pageText = null;
        if (document == null) document = viewerController.getDocument();
        try {
            if (viewerController != null) {
                // get access to currently open document instance.
                pageText = viewerController.getDocument().getPageText(pageIndex);
            } else if (document != null) {
                pageText = document.getPageText(pageIndex);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("PageText extraction thread was interrupted.");
        }
        return pageText;
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
        ArrayList<String> words = new ArrayList<>();
        char c;
        char cPrev = 0;
        boolean isPunctuation;
        for (int start = 0, curs = 0, max = phrase.length(); curs < max; curs++) {
            c = phrase.charAt(curs);
            isPunctuation = WordText.isPunctuation(c);
            if (WordText.isWhiteSpace(c) || (isPunctuation && !WordText.isDigit(cPrev))) {
                // add word segment
                if (start != curs) {
                    words.add(phrase.substring(start, curs));
                }
                // skip white space.
                if (isPunctuation) {
                    words.add(phrase.substring(curs, curs + 1));
                }
                // start
                start = curs + 1 < max ? curs + 1 : start;
            } else if (curs + 1 == max) {
                words.add(phrase.substring(start, curs + 1));
            }
            cPrev = c;
        }
        return words;
    }

    /**
     * Returns the components created by the search for a given page
     *
     * @param pageIndex The index of the page
     * @return The set of components
     */
    public Set<SearchHitComponent> getComponentsFor(int pageIndex) {
        return pageToComponents.getOrDefault(pageIndex, new HashSet<>());
    }

    /**
     * Adds a component with the given arguments.
     *
     * @param pageIndex The page to add the component to
     * @param text      The text of the component
     * @param bounds    The bounds of the component
     */
    protected void addComponent(final int pageIndex, final String text, final Rectangle2D.Double bounds) {
        final Set<SearchHitComponent> components = pageToComponents.getOrDefault(pageIndex, new HashSet<>());
        final SearchHitComponent component = componentFactory.createComponent(text, bounds, document.getPageTree().getPage(pageIndex), viewerController);
        if (!components.contains(component)) {
            if (component != null) {
                components.add(component);
            }
            pageToComponents.put(pageIndex, components);
        }
    }
}
