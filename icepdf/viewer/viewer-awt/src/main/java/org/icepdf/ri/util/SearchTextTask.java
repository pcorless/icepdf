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
package org.icepdf.ri.util;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.utility.search.SearchPanel;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.List;

/**
 * This class is a utility for searching text in a PDF document.  This is only
 * a reference implementation; there is currently no support for regular
 * expression and other advanced search features.
 *
 * @since 1.1
 */
public class SearchTextTask {

    public static final int WORD_PADDING = 6;
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
    // flags for threading
    private boolean done = false;
    private boolean canceled = false;
    // keep track of total hits
    private int totalHitCount = 0;
    // String to search for and parameters from gui
    private String pattern = "";
    private boolean wholeWord;
    private boolean caseSensitive;
    private boolean cumulative;
    private boolean showPages;
    private boolean regex;
    private boolean searchComments;
    private boolean r2L;
    private boolean comments;
    private boolean outlines;
    private boolean destinations;

    // parent swing controller
    private Controller controller;

    // append nodes for found text.
    private SearchPanel searchPanel;

    private boolean currentlySearching;

    private Container viewContainer;

    /**
     * Creates a new instance of the SearchTextTask.
     *
     * @param builder searchTextTask builder
     */
    private SearchTextTask(Builder builder) {
        controller = builder.controller;
        pattern = builder.pattern;

        wholeWord = builder.wholeWord;
        caseSensitive = builder.caseSensitive;
        cumulative = builder.cumulative;
        regex = builder.regex;
        showPages = builder.showPages;
        r2L = builder.r2L;
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
     * Start the task, start searching the document for the pattern.
     */
    public void go() {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                dialogMessage = null;
                return new ActualTask();
            }
        };
        worker.setThreadPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    /**
     * Number pages that search task has to iterate over.
     *
     * @return returns max number of pages in document being search.
     */
    public int getLengthOfTask() {
        return lengthOfTask;
    }

    /**
     * Gets the page that is currently being searched by this task.
     *
     * @return current page being processed.
     */
    public int getCurrent() {
        return current;
    }

    /**
     * Stop the task.
     */
    public void stop() {
        canceled = true;
        dialogMessage = null;
    }

    /**
     * Find out if the task has completed.
     *
     * @return true if task is done, false otherwise.
     */
    public boolean isDone() {
        return done;
    }

    public boolean isCurrentlySearching() {
        return currentlySearching;
    }

    /**
     * Returns the most recent dialog message, or null
     * if there is no current dialog message.
     *
     * @return current message dialog text.
     */
    public String getMessage() {
        return dialogMessage;
    }

    /**
     * The actual long running task.  This runs in a SwingWorker thread.
     */
    class ActualTask {
        ActualTask() {

            // break on bad input
            if ("".equals(pattern) || " ".equals(pattern)) {
                done = true;
                return;
            }

            try {
                currentlySearching = true;
                // Extraction of text from pdf procedure
                totalHitCount = 0;
                current = 0;

                // get instance of the search controller
                DocumentSearchController searchController =
                        controller.getDocumentSearchController();
                if (!cumulative) {
                    searchController.clearAllSearchHighlight();
                }
                searchController.addSearchTerm(pattern,
                        caseSensitive, wholeWord, regex);

                Document document = controller.getDocument();
                // iterate over each page in the document
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    // break if needed
                    if (canceled || done) {
                        setDialogMessage();
                        break;
                    }
                    // Update task information
                    current = i;

                    // update search message in search pane.
                    Object[] messageArguments = {String.valueOf((current + 1)),
                            lengthOfTask, lengthOfTask};
                    if (searchingMessageForm != null) {
                        dialogMessage = searchingMessageForm.format(messageArguments);
                    }

                    // hits per page count
                    final List<LineText> lineItems = searchController.searchHighlightPage(current, WORD_PADDING);
                    int hitCount = lineItems.size();

                    // update total hit count
                    totalHitCount += hitCount;
                    if (hitCount > 0) {
                        // update search dialog
                        messageArguments = new Object[]{
                                String.valueOf((current + 1)),
                                hitCount, hitCount};
                        final String nodeText =
                                searchResultMessageForm != null ? searchResultMessageForm.format(messageArguments) : "";
                        final int currentPage = i;
                        // add the node to the search panel tree but on the
                        // awt thread.
                        if (searchPanel != null) {
                            SwingUtilities.invokeLater(() -> {
                                // add the node
                                searchPanel.addFoundTextEntry(
                                        nodeText,
                                        currentPage,
                                        lineItems,
                                        showPages);
                                // try repainting the container
                                viewContainer.repaint();
                            });
                        }
                    }
                    Thread.yield();
                }
                // update the dialog and end the task
                setDialogMessage();

                done = true;
            } finally {
                currentlySearching = false;
            }

            // repaint the view container
            SwingUtilities.invokeLater(() -> viewContainer.validate());
        }
    }

    /**
     * Gets the message that should be displayed when the task has completed.
     *
     * @return search completed or stopped final message.
     */
    public String getFinalMessage() {
        setDialogMessage();
        return dialogMessage;
    }

    /**
     * Utility method for setting the dialog message.
     */
    private void setDialogMessage() {

        // Build Internationalized plural phrase.

        Object[] messageArguments = {String.valueOf((current + 1)),
                (current + 1), totalHitCount};
        if (searchResultMessageForm != null) {
            dialogMessage = searchCompletionMessageForm.format(messageArguments);
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
