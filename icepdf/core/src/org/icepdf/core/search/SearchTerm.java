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
package org.icepdf.core.search;

import java.util.ArrayList;

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

    // case sensitive search
    private boolean caseSensitive;
    // whole word search.
    private boolean wholeWord;

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
        this.term = term;
        this.terms = terms;
        this.caseSensitive = caseSensitive;
        this.wholeWord = wholeWord;
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
