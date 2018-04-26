/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.search.DestinationResult;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class is a utility for searching text in a PDF document.  This is only
 * a reference implementation; there is currently no support for regular
 * expression and other advanced search features.
 *
 * @since 1.1
 */
public class SearchTextTask extends SwingWorker<Void, SearchTextTask.SearchResult> {

    private static final int WORD_PADDING = 6;
    // total length of task (total page count), used for progress bar
    private int lengthOfTask;
    // current progress, used for the progress bar
    private int current;
    // message displayed on progress bar
    private String dialogMessage;
    // canned internationalized messages.
    private MessageFormat searchingMessageForm;
    private MessageFormat searchResultMessageForm;
    private MessageFormat searchCompletionMessageForm;
    // keep track of total hits
    private int totalHitCount;
    // String to search for and parameters from gui
    private String pattern = "";
    private Pattern searchPattern;
    private boolean wholeWord;
    private boolean caseSensitive;
    private boolean cumulative;
    private boolean showPages;
    private boolean regex;
    private boolean r2L;
    private boolean text;
    private boolean comments;
    private boolean outlines;
    private boolean destinations;

    // parent swing controller
    private Controller controller;
    // append nodes for found text.
    private SearchPanel searchPanel;
    private Container viewContainer;

    /**
     * Creates a new instance of the SearchTextTask.
     *
     * @param builder searchTextTask builder
     */
    private SearchTextTask(Builder builder) {
        controller = builder.controller;
        pattern = builder.pattern;
        if (pattern != null && !pattern.isEmpty()) {
            searchPattern = Pattern.compile(isCaseSensitive() ? pattern : pattern.toLowerCase());
        }

        wholeWord = builder.wholeWord;
        caseSensitive = builder.caseSensitive;
        cumulative = builder.cumulative;
        regex = builder.regex;
        showPages = builder.showPages;
        r2L = builder.r2L;
        text = builder.text;
        comments = builder.comments;
        outlines = builder.outlines;
        destinations = builder.destinations;

        this.viewContainer = controller.getDocumentViewController().getViewContainer();
        lengthOfTask = controller.getDocument().getNumberOfPages();

        // setup searching format format.
        this.searchPanel = builder.searchPanel;
        if (searchPanel != null) {
            searchingMessageForm = searchPanel.setupSearchingMessageForm();
            searchResultMessageForm = searchPanel.setupSearchResultMessageForm();
            searchCompletionMessageForm = searchPanel.setupSearchCompletionMessageForm();
        }
    }

    /**
     * Gets the page that is currently being searched by this task.
     *
     * @return current page being processed.
     */
    public int getCurrent() {
        return current;
    }

    @Override
    protected Void doInBackground() {
        // break on bad input
        if ("".equals(pattern) || " ".equals(pattern)) {
            return null;
        }

        // Extraction of text from pdf procedure
        totalHitCount = 0;
        current = 0;

        // get instance of the search controller
        DocumentSearchController searchController = controller.getDocumentSearchController();
        if (!cumulative) {
            searchController.clearAllSearchHighlight();
        }
        searchController.addSearchTerm(pattern, caseSensitive, wholeWord, regex);

        Document document = controller.getDocument();
        // iterate over each page in the document
        if (text || comments) {
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                // break if needed
                if (isCancelled()) {
                    setDialogMessage();
                    break;
                }
                // Update task information
                current = i;

                // update search message in search pane.
                Object[] messageArguments = {String.valueOf((current + 1)), lengthOfTask, lengthOfTask};
                if (searchingMessageForm != null) {
                    dialogMessage = searchingMessageForm.format(messageArguments);
                }

                // one page is initialized we will also search for annotation if selected.
                if (text) {
                    final List<LineText> matchLineItems = searchController.searchHighlightPage(current, WORD_PADDING);
                    int hitCount = matchLineItems.size();
                    // update total hit count
                    totalHitCount += hitCount;
                    if (isCancelled()) {
                        break;
                    }
                    if (hitCount > 0) {
                        // update search dialog
                        messageArguments = new Object[]{String.valueOf((current + 1)), hitCount, hitCount};
                        String nodeText =
                                searchResultMessageForm != null ? searchResultMessageForm.format(messageArguments) : "";
                        // add the node to the search panel tree
                        if (searchPanel != null) {
                            publish(new TextResult(matchLineItems, nodeText, i));
                        }
                    } else {
                        publish(new SearchResult());
                    }
                }
                // search comments,  page is already initialized, so we'll take advantage of that.
                if (comments) {
                    ArrayList<MarkupAnnotation> matchMarkupAnnotations = searchController.searchComments(current);
                    if (matchMarkupAnnotations != null && matchMarkupAnnotations.size() > 0) {
                        int hitCount = matchMarkupAnnotations.size();
                        messageArguments = new Object[]{String.valueOf((current + 1)), hitCount, hitCount};
                        final String nodeText =
                                searchResultMessageForm != null ? searchResultMessageForm.format(messageArguments) : "";
                        publish(new CommentsResult(matchMarkupAnnotations, nodeText, current));
                    }
                }
            }
        }
        // outlines and destination are outside the page tree so we search for them separately
        if (outlines) {
            if (isCancelled()) {
                return null;
            }
            ArrayList<OutlineItem> outlinesMatches = searchController.searchOutlines();
            if (outlinesMatches != null && outlinesMatches.size() > 0) {
                publish(new OutlineResult(outlinesMatches));
            }
        }
        if (destinations) {
            if (isCancelled()) {
                return null;
            }
            ArrayList<DestinationResult> destinationMatches = searchController.searchDestinations();
            if (destinationMatches != null && destinationMatches.size() > 0) {
                publish(new DestinationsResult(destinationMatches));
            }
        }
        // update the dialog and end the task
        setDialogMessage();
        return null;
    }

    @Override
    protected void process(List<SearchResult> chunks) {

        for (SearchResult searchResult : chunks) {
            if (isCancelled()) {
                break;
            }
            if (searchResult instanceof CommentsResult) {
                CommentsResult comment = (CommentsResult) searchResult;
                searchPanel.addFoundCommentEntry(comment, this);
            } else if (searchResult instanceof TextResult) {
                TextResult textResult = (TextResult) searchResult;
                searchPanel.addFoundTextEntry(textResult, this);
            } else if (searchResult instanceof OutlineResult) {
                OutlineResult outlineResult = (OutlineResult) searchResult;
                searchPanel.addFoundOutlineEntry(outlineResult, this);
            } else if (searchResult instanceof DestinationsResult) {
                DestinationsResult destinationsResult = (DestinationsResult) searchResult;
                searchPanel.addFoundDestinationEntry(destinationsResult, this);
            }
        }
        viewContainer.repaint();
        // update the dialog messages.
        searchPanel.updateProgressControls(dialogMessage);
    }

    @Override
    protected void done() {
        searchPanel.updateProgressControls(dialogMessage);
        viewContainer.validate();
    }

    /**
     * Utility method for setting the dialog message.
     */
    private void setDialogMessage() {
        // Build Internationalized plural phrase.
        Object[] messageArguments = {String.valueOf((current + 1)), (current + 1), totalHitCount};
        if (searchResultMessageForm != null) {
            dialogMessage = searchCompletionMessageForm.format(messageArguments);
        }
    }

    public String getPattern() {
        return pattern;
    }

    public Pattern getSearchPattern() {
        return searchPattern;
    }

    public boolean isWholeWord() {
        return wholeWord;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isCumulative() {
        return cumulative;
    }

    public boolean isShowPages() {
        return showPages;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isText() {
        return text;
    }

    public boolean isComments() {
        return comments;
    }

    public boolean isOutlines() {
        return outlines;
    }

    public boolean isDestinations() {
        return destinations;
    }

    class SearchResult {
        public String nodeText;
    }

    class TextResult extends SearchResult {
        List<LineText> lineItems;
        int currentPage;

        TextResult(List<LineText> lineItems, String nodeText, int currentPage) {
            this.lineItems = lineItems;
            this.nodeText = nodeText;
            this.currentPage = currentPage;
        }
    }

    class CommentsResult extends SearchResult {
        ArrayList<MarkupAnnotation> markupAnnotations;
        int currentPage;

        CommentsResult(ArrayList<MarkupAnnotation> markupAnnotations, String nodeText, int currentPage) {
            this.markupAnnotations = markupAnnotations;
            this.nodeText = nodeText;
            this.currentPage = currentPage;
        }
    }

    class OutlineResult extends SearchResult {
        ArrayList<OutlineItem> outlinesMatches;

        OutlineResult(ArrayList<OutlineItem> outlinesMatches) {
            this.outlinesMatches = outlinesMatches;
        }
    }

    class DestinationsResult extends SearchResult {
        ArrayList<DestinationResult> destinationsResult;

        DestinationsResult(ArrayList<DestinationResult> destinationsResult) {
            this.destinationsResult = destinationsResult;
        }
    }

    public static class Builder {

        // required model setup
        private final Controller controller;
        private final String pattern;

        // parent search panel
        private SearchPanel searchPanel;

        // optional search controls.
        private boolean wholeWord;
        private boolean caseSensitive;
        private boolean cumulative;
        private boolean showPages;
        private boolean r2L;
        private boolean regex;
        private boolean text;
        private boolean comments;
        private boolean outlines;
        private boolean destinations;

        public Builder(Controller controller, String pattern) {
            this.controller = controller;
            this.pattern = pattern;
        }

        public Builder setSearchPanel(SearchPanel searchPanel) {
            this.searchPanel = searchPanel;
            return this;
        }

        public Builder setWholeWord(boolean wholeWord) {
            this.wholeWord = wholeWord;
            return this;
        }

        public Builder setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder setCumulative(boolean cumulative) {
            this.cumulative = cumulative;
            return this;
        }

        public Builder setShowPages(boolean showPages) {
            this.showPages = showPages;
            return this;
        }

        public Builder setR2L(boolean r2L) {
            this.r2L = r2L;
            return this;
        }

        public Builder setRegex(boolean regex) {
            this.regex = regex;
            return this;
        }

        public Builder setText(boolean text) {
            this.text = text;
            return this;
        }

        public Builder setComments(boolean comments) {
            this.comments = comments;
            return this;
        }

        public Builder setOutlines(boolean outlines) {
            this.outlines = outlines;
            return this;
        }

        public Builder setDestinations(boolean destinations) {
            this.destinations = destinations;
            return this;
        }

        public SearchTextTask build() {
            return new SearchTextTask(this);
        }
    }
}
