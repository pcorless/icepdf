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
package org.icepdf.ri.common.utility.search;

import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class SimpleSearchHelper implements PropertyChangeListener {

    private Controller controller;
    private DocumentViewControllerImpl documentViewController;
    private DocumentSearchController searchController;
    private String pattern;
    private int currentPage;
    private int startPage;
    private int pageCount;

    private int wordHits;
    private int wordIndex;
    private int commentHits;
    private int commentIndex;

    private boolean commentsEnabled;
    private boolean wholeWord;
    private boolean caseSensitive;

    private SimpleSearchHelper(Builder builder) {
        controller = builder.controller;
        pattern = builder.pattern;
        wholeWord = builder.wholeWord;
        caseSensitive = builder.caseSensitive;
        commentsEnabled = builder.comments;

        currentPage = controller.getCurrentPageNumber();
        pageCount = controller.getDocument().getNumberOfPages();

        documentViewController = (DocumentViewControllerImpl) controller.getDocumentViewController();
        documentViewController.addPropertyChangeListener(this);

        searchController = controller.getDocumentSearchController();
        clearAll();
        startPage = currentPage;
        searchForward(currentPage);
    }

    public void dispose() {
        documentViewController.removePropertyChangeListener(this);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        Object newValue = evt.getNewValue();
        String propertyName = evt.getPropertyName();
        switch (propertyName) {
            case PropertyConstants.DOCUMENT_CURRENT_PAGE:
                currentPage = (int) newValue;
                System.out.println("current page " + currentPage);
                break;
        }
    }

    public void clearAll() {
        // reset high light states.
        searchController.clearAllSearchHighlight();
        controller.getDocumentViewController().getViewContainer().repaint();
        // reset internal word counters
        wordHits = wordIndex = commentHits = commentIndex = 0;
    }

    // should return a value,  +1 if something, 0 if no more are found,  zero would mean we show a popup not found
    public void nextResult() {
        if (isEndOfPage()) {
            currentPage++;
            if (currentPage >= pageCount) {
                currentPage = 0;
            }
            searchForward(currentPage);
        } else {
            if (!isLastWord()) {
                // highlight next word, clear previous
                wordIndex++;
            }
            if (!isLastComment()) {
                // go to the next comment, set focus and expand?
            }
        }
        searchController.nextSearchHit();
    }

    public void previousResult() {
        if (isStartOfPage()) {
            currentPage--;
            if (currentPage >= 0) {
                currentPage = pageCount - 1;
            }
            searchBackwards(currentPage);
        } else {
            if (!isFirstWord()) {
                // highlight next word, clear previous
                wordIndex--;
            }
            if (!isLastComment()) {
                // go to the next comment, set focus and expand?
            }
        }
        searchController.previousSearchHit();
    }

    private void searchForward(int pageIndex) {
        if (commentsEnabled) {
            // search comments and update indexes
        }
        // search text and update indexes
        wordIndex = 0;
        currentPage = pageIndex;
        wordHits = searchController.searchHighlightPage(pageIndex, pattern, caseSensitive, wholeWord);
        if (wordHits == 0) {
            int nextPage = currentPage + 1;
            if (nextPage >= pageCount) {
                nextPage = 0;
            }
            if (nextPage != startPage) {
                searchForward(nextPage);
            }
        } else {
            controller.getDocumentViewController().getViewContainer().repaint();
        }
    }

    private void searchBackwards(int pageIndex) {
        if (commentsEnabled) {
            // search comments and update indexes
        }
        // search text and update indexes
        currentPage = pageIndex;
        wordHits = searchController.searchHighlightPage(pageIndex, pattern, caseSensitive, wholeWord);
        wordIndex = wordHits;
        if (wordHits == 0) {
            int nextPage = currentPage - 1;
            if (nextPage < 0) {
                nextPage = pageCount - 1;
            }
            if (nextPage != startPage) {
                searchBackwards(nextPage);
            }
        } else {
            controller.getDocumentViewController().getViewContainer().repaint();
        }
    }

    private boolean isEndOfPage() {
        return isLastWord() && isLastComment();
    }

    private boolean isStartOfPage() {
        return wordIndex == 0 && commentIndex == 0;
    }

    private boolean isLastWord() {
        return wordIndex >= wordHits - 1;
    }

    private boolean isFirstWord() {
        return wordIndex <= 0;
    }

    private boolean isLastComment() {
        return commentIndex == commentHits;
    }

    public static class Builder {

        // required model setup
        private final Controller controller;
        private final String pattern;

        // optional search controls.
        private boolean wholeWord;
        private boolean caseSensitive;
        private boolean comments;

        public Builder(Controller controller, String pattern) {
            this.controller = controller;
            this.pattern = pattern;
        }

        public Builder setWholeWord(boolean wholeWord) {
            this.wholeWord = wholeWord;
            return this;
        }

        public Builder setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder setComments(boolean comments) {
            this.comments = comments;
            return this;
        }

        public SimpleSearchHelper build() {
            return new SimpleSearchHelper(this);
        }
    }
}
