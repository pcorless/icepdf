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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.search.DestinationResult;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.search.SearchMode;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class is a utility for searching text in a PDF document.  This is only
 * a reference implementation.
 *
 * @since 1.1
 */
public class SearchTextTask extends SwingWorker<Void, SearchTextTask.SearchResult> {

    private static final int WORD_PADDING = 6;
    // total length of task (total page count), used for progress bar
    private final int lengthOfTask;
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
    private String pattern;
    private Pattern searchPattern;
    private final boolean wholeWord;
    private final boolean caseSensitive;
    private final boolean cumulative;
    private final boolean showPages;
    private final boolean regex;
    private final boolean text;
    private final boolean forms;
    private final boolean comments;
    private final boolean outlines;
    private final boolean destinations;
    private final SearchMode searchMode;
    // parent swing controller
    private final Controller controller;
    // append nodes for found text.
    private final BaseSearchModel searchModel;
    private final Container viewContainer;

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
        searchMode = builder.wholePage ? SearchMode.PAGE : SearchMode.WORD;
        wholeWord = builder.wholeWord;
        caseSensitive = builder.caseSensitive;
        cumulative = builder.cumulative;
        regex = builder.regex;
        showPages = builder.showPages;
        boolean r2L = builder.r2L;
        text = builder.text;
        forms = builder.forms;
        comments = builder.comments;
        outlines = builder.outlines;
        destinations = builder.destinations;

        this.viewContainer = controller.getDocumentViewController().getViewContainer();
        lengthOfTask = controller.getDocument().getNumberOfPages();

        // setup searching format format.
        this.searchModel = builder.searchModel;
        if (searchModel != null) {
            searchingMessageForm = searchModel.setupSearchingMessageForm();
            searchResultMessageForm = searchModel.setupSearchResultMessageForm();
            searchCompletionMessageForm = searchModel.setupSearchCompletionMessageForm();
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
        searchController.setSearchMode(searchMode);
        searchController.addSearchTerm(pattern, caseSensitive, wholeWord, regex);

        Document document = controller.getDocument();
        // iterate over each page in the document
        if (text || comments || forms) {
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
                        if (searchModel != null) {
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
                if (forms) {
                    final List<TextWidgetAnnotation> annotations = searchController.searchForms(current);
                    if (!annotations.isEmpty()) {
                        final int hitCount = annotations.size();
                        messageArguments = new Object[]{String.valueOf((current + 1)), hitCount, hitCount};
                        final String nodeText =
                                searchResultMessageForm != null ? searchResultMessageForm.format(messageArguments) : "";
                        publish(new FormsResult(annotations, nodeText, current));
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

        if (searchModel != null) {
            for (SearchResult searchResult : chunks) {
                if (isCancelled()) {
                    break;
                }
                if (searchResult instanceof CommentsResult) {
                    CommentsResult comment = (CommentsResult) searchResult;
                    searchModel.addFoundCommentEntry(comment, this);
                } else if (searchResult instanceof TextResult) {
                    TextResult textResult = (TextResult) searchResult;
                    searchModel.addFoundTextEntry(textResult, this);
                } else if (searchResult instanceof FormsResult) {
                    FormsResult formsResult = (FormsResult) searchResult;
                    searchModel.addFoundFormsEntry(formsResult, this);
                } else if (searchResult instanceof OutlineResult) {
                    OutlineResult outlineResult = (OutlineResult) searchResult;
                    searchModel.addFoundOutlineEntry(outlineResult, this);
                } else if (searchResult instanceof DestinationsResult) {
                    DestinationsResult destinationsResult = (DestinationsResult) searchResult;
                    searchModel.addFoundDestinationEntry(destinationsResult, this);
                }
            }
            // update the dialog messages.
            searchModel.updateProgressControls(dialogMessage);
        }
        viewContainer.repaint();
    }

    @Override
    protected void done() {
        if (searchModel != null) searchModel.updateProgressControls(dialogMessage);
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

    public boolean isForms() {
        return forms;
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

    public static class SearchResult {
        private final String nodeText;

        public SearchResult() {
            this.nodeText = null;
        }

        public SearchResult(String nodeText) {
            this.nodeText = nodeText;
        }

        public String getNodeText() {
            return nodeText;
        }
    }

    public static class TextResult extends SearchResult {
        private final List<LineText> lineItems;
        private final int currentPage;

        public TextResult(List<LineText> lineItems, String nodeText, int currentPage) {
            super(nodeText);
            this.lineItems = lineItems;
            this.currentPage = currentPage;
        }

        public List<LineText> getLineItems() {
            return lineItems;
        }

        public int getCurrentPage() {
            return currentPage;
        }
    }

    public static class FormsResult extends SearchResult {
        private final List<TextWidgetAnnotation> widgets;
        private final int currentPage;

        public FormsResult(List<TextWidgetAnnotation> widgets, String nodeText, int currentPage) {
            super(nodeText);
            this.widgets = widgets;
            this.currentPage = currentPage;
        }

        public List<TextWidgetAnnotation> getWidgets() {
            return widgets;
        }

        public int getCurrentPage() {
            return currentPage;
        }
    }

    public static class CommentsResult extends SearchResult {
        private final List<MarkupAnnotation> markupAnnotations;
        private final int currentPage;

        public CommentsResult(List<MarkupAnnotation> markupAnnotations, String nodeText, int currentPage) {
            super(nodeText);
            this.markupAnnotations = markupAnnotations;
            this.currentPage = currentPage;
        }

        public List<MarkupAnnotation> getMarkupAnnotations() {
            return markupAnnotations;
        }

        public int getCurrentPage() {
            return currentPage;
        }
    }

    public static class OutlineResult extends SearchResult {
        private final List<OutlineItem> outlinesMatches;

        public OutlineResult(List<OutlineItem> outlinesMatches) {
            super(null);
            this.outlinesMatches = outlinesMatches;
        }

        public List<OutlineItem> getOutlinesMatches() {
            return outlinesMatches;
        }
    }

    public static class DestinationsResult extends SearchResult {
        private final List<DestinationResult> destinationsResult;

        public DestinationsResult(List<DestinationResult> destinationsResult) {
            super(null);
            this.destinationsResult = destinationsResult;
        }

        public List<DestinationResult> getDestinationsResult() {
            return destinationsResult;
        }
    }


    public static class Builder {

        // required model setup
        private final Controller controller;
        private final String pattern;

        // parent search panel
        private BaseSearchModel searchModel;

        // optional search controls.
        private boolean wholePage;
        private boolean wholeWord;
        private boolean caseSensitive;
        private boolean cumulative;
        private boolean showPages;
        private boolean r2L;
        private boolean regex;
        private boolean text;
        private boolean forms;
        private boolean comments;
        private boolean outlines;
        private boolean destinations;

        public Builder(Controller controller, String pattern) {
            this.controller = controller;
            this.pattern = pattern;
        }

        public Builder setSearchModel(BaseSearchModel searchModel) {
            this.searchModel = searchModel;
            return this;
        }

        public Builder setWholePage(boolean wholePage) {
            this.wholePage = wholePage;
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

        public Builder setForms(boolean forms) {
            this.forms = forms;
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
