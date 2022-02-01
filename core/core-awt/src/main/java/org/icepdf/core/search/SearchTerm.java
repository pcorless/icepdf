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

import org.icepdf.core.pobjects.Page;

import java.awt.*;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Text searchs are used by the search controller to search for text in a
 * document.  A search can have one or more search terms which are all searched
 * for in a given search.
 *
 * @since 4.0
 */
public class SearchTerm {

    //original term before it was cut up into terms.
    private String term;

    // number of string in search term, one or more strings that make
    // up a phrase. words, white space and punctuation
    private ArrayList<String> terms;
    private final Color highlightColor;

    // case sensitive search
    private boolean caseSensitive;
    // whole word search.
    private boolean wholeWord;
    // allow for regex compare
    private boolean regex;
    private Pattern searchPattern;

    /**
     * Creates a new search term.
     *
     * @param term          full string represented by terms.
     * @param terms         terms that make ups serach
     * @param caseSensitive true to specify a case sensitive search
     * @param wholeWord     true to specify a whole word only search.
     */
    public SearchTerm(String term, ArrayList<String> terms,
                      boolean caseSensitive, boolean wholeWord) {
        this(term, terms, caseSensitive, wholeWord, false);
    }

    public SearchTerm(String term, ArrayList<String> terms, boolean caseSensitive, boolean wholeWord, boolean regex) {
        this(term, terms, caseSensitive, wholeWord, regex, Page.highlightColor);
    }

    public SearchTerm(String term, ArrayList<String> terms,
                      boolean caseSensitive, boolean wholeWord, boolean regex, Color highlightColor) {
        this.term = term;
        this.terms = terms;
        this.caseSensitive = caseSensitive;
        this.wholeWord = wholeWord;
        this.regex = regex;
        this.highlightColor = highlightColor;
    }

    /**
     * Gets individual strings that make up the search term,
     *
     * @return list of strings that contain searchable words.
     */
    public ArrayList<String> getTerms() {
        return terms;
    }

    /**
     * Get origional search term.
     *
     * @return term, word or phrase used to search.
     */
    public String getTerm() {
        return term;
    }

    /**
     * Specifies if the search term should be treated as case sensitive.
     *
     * @return true if cases senstive search, otherwise false.
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Specifies if the search term should be treated as whole word hits only.
     *
     * @return true if whole word search, otherwise false.
     */
    public boolean isWholeWord() {
        return wholeWord;
    }

    public boolean isRegex() {
        return regex;
    }

    public Pattern getRegexPattern() {
        if (searchPattern != null) return searchPattern;
        if (regex && term != null && !term.isEmpty()) {
            searchPattern = Pattern.compile(term, !caseSensitive ? Pattern.CASE_INSENSITIVE : 0);
        }
        return searchPattern;
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof SearchTerm) {
            SearchTerm test = (SearchTerm) object;
            return test.isCaseSensitive() == caseSensitive &&
                    test.isWholeWord() == wholeWord &&
                    test.getTerm().equals(term);
        } else {
            return false;
        }
    }
}
