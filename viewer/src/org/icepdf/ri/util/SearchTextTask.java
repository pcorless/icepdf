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
package org.icepdf.ri.util;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.SearchPanel;
import org.icepdf.ri.common.SwingWorker;

import javax.swing.*;
import java.awt.*;
import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;

/**
 * This class is a utility for searching text in a PDF document.  This is only
 * a reference implementation; there is currently no support for regular
 * expression and other advanced search features.
 *
 * @since 1.1
 */
public class SearchTextTask {

    // total length of task (total page count), used for progress bar
    private int lengthOfTask;

    // current progress, used for the progress bar
    private int current = 0;

    // message displayed on progress bar
    private String dialogMessage;

    // flags for threading
    private boolean done = false;
    private boolean canceled = false;

    // keep track of total hits
    private int totalHitCount = 0;

    // PDF document pointer
    private Document document = null;

    // String to search for
    private String pattern = "";
    private boolean wholeWord;
    private boolean caseSensitive;
    private boolean r2L;

    // append nodes for found text.
    private SearchPanel searchPanel;

    // message bundle for internationalization
    private ResourceBundle messageBundle;

    private boolean currentlySearching;

    private Container viewContainer;

    /**
     * Creates a new instance of the SearchTextTask.
     *
     * @param document    document that will be searched
     * @param searchPanel GUI that shows search tools and results
     * @param pattern     string to search for in the document
     */
    public SearchTextTask(Document document,
                          SearchPanel searchPanel,
                          String pattern,
                          boolean wholeWord,
                          boolean caseSensitive,
                          boolean r2L,
                          ResourceBundle messageBundle,
                          Container viewContainer) {
        this.document = document;
        this.pattern = pattern;
        this.searchPanel = searchPanel;
        lengthOfTask = document.getNumberOfPages();
        this.messageBundle = messageBundle;
        this.viewContainer = viewContainer;
        this.wholeWord = wholeWord;
        this.caseSensitive = caseSensitive;
        this.r2L = r2L;
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
     * Find out how much work needs to be done.
     */
    public int getLengthOfTask() {
        return lengthOfTask;
    }

    /**
     * Find out how much has been done.
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
     */
    public String getMessage() {
        return dialogMessage;
    }

    /**
     * The actual long running task.  This runs in a SwingWorker thread.
     */
    class ActualTask {
        ActualTask() {

            // break on bad input, todo, extend cases.
            if ("".equals(pattern) || " ".equals(pattern)) {
                return;
            }

            try {
                currentlySearching = true;
                // Extraction of text from pdf procedure
                totalHitCount = 0;
                current = 0;

                // check criteria for case sensitivity.
                if (!caseSensitive){
                    pattern = pattern.toLowerCase();
                }

                // parse search term out into words, so we can match
                // them against WordText
                ArrayList<String> searchPhrase = phraseParser(pattern);

                // found word index to keep track of when we have found a hit
                int searchPhraseHitCount = 0;
                int searchPhraseFoundCount = searchPhrase.size();

                // list of found words for highlighting, as hits can span
                // lines and pages
                ArrayList<WordText> searchPhraseHits =
                        new ArrayList<WordText>(searchPhraseFoundCount);

                // iterate over each page in the document
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    // break if needed
                    if (canceled || done) {
                        setDialogMessage();
                        break;
                    }

                    // Update task information
                    current = i;

                    // Build Internationalized plural phrase.
                    MessageFormat messageForm =
                            new MessageFormat(messageBundle.getString(
                                    "viewer.utilityPane.search.searching1.msg"));
                    double[] fileLimits = {0, 1, 2};
                    String[] fileStrings = {
                            messageBundle.getString(
                                    "viewer.utilityPane.search.searching1.moreFile.msg"),
                            messageBundle.getString(
                                    "viewer.utilityPane.search.searching1.oneFile.msg"),
                            messageBundle.getString(
                                    "viewer.utilityPane.search.searching1.moreFile.msg"),
                    };
                    ChoiceFormat choiceForm = new ChoiceFormat(fileLimits,
                            fileStrings);
                    Format[] formats = {null, choiceForm, null};
                    messageForm.setFormats(formats);
                    Object[] messageArguments = {String.valueOf((current + 1)),
                            lengthOfTask, lengthOfTask};
                    dialogMessage = messageForm.format(messageArguments);

                    // hits per page count
                    int hitCount = 0;

                    // iterate of page sentences, words, glyphs
                    PageText pageText = document.getPageText(i);
                    // clear previous searches.
                    pageText.clearHighlighted();

                    ArrayList<LineText> pageLines = pageText.getPageLines();
                    for (LineText pageLine : pageLines) {

                        if (canceled || done) {
                            setDialogMessage();
                            break;
                        }
                        ArrayList<WordText> lineWords = pageLine.getWords();

                        // compare words against search terms.
                        String wordString;
                        for (WordText word : lineWords) {
                            // apply case sensitivity rule.
                            wordString = caseSensitive?word.toString():
                                    word.toString().toLowerCase();
                            // word matches, we have to match full word hits
                            if (wholeWord){
                                if (wordString.equals(
                                        searchPhrase.get(searchPhraseHitCount))) {
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
                                else{
                                    searchPhraseHits.clear();
                                    searchPhraseHitCount = 0;
                                }
                            }
                            // otherwise we look for an index of hits
                            else{
                                // found a potential hit, depends on the length
                                // of searchPhrase.
                                if (wordString.indexOf(
                                        searchPhrase.get(searchPhraseHitCount)) >= 0) {
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
                                else{
                                    searchPhraseHits.clear();
                                    searchPhraseHitCount = 0;
                                }

                            }

                            // check if we have found what we're looking for
                            if (searchPhraseHitCount == searchPhraseFoundCount){
                                // iterate of found, highlighting words
                                for (WordText wordHit : searchPhraseHits){
                                    wordHit.setHighlighted(true);
                                    wordHit.setHasHighlight(true);
                                }

                                // rest counts and start over again.
                                hitCount++;
                                searchPhraseHits.clear();
                                searchPhraseHitCount = 0;

                                // queue repaint for page
                                // repaint the view container
                                SwingUtilities.invokeLater(new Runnable() {
                                    public void run() {
                                        viewContainer.repaint();
                                    }
                                });
                            }

                        }
                    }
                    // update total hit count
                    totalHitCount += hitCount;
                    if (hitCount > 0) {

                        // Build Internationalized plural phrase.
                        messageForm =
                                new MessageFormat(messageBundle.getString(
                                        "viewer.utilityPane.search.result.msg"));
                        fileLimits = new double[]{0, 1, 2};
                        fileStrings = new String[]{
                                messageBundle.getString(
                                        "viewer.utilityPane.search.result.moreFile.msg"),
                                messageBundle.getString(
                                        "viewer.utilityPane.search.result.oneFile.msg"),
                                messageBundle.getString(
                                        "viewer.utilityPane.search.result.moreFile.msg"),
                        };
                        choiceForm = new ChoiceFormat(fileLimits,
                                fileStrings);
                        formats = new Format[]{null, choiceForm};
                        messageForm.setFormats(formats);
                        messageArguments = new Object[]{String.valueOf((current + 1)),
                                new Integer(hitCount), new Integer(hitCount)};

                        searchPanel.addFoundEntry(
                                messageForm.format(messageArguments),
                                current);
                    }
                    Thread.yield();
                }
                // update the dialog and end the task
                setDialogMessage();

                done = true;
            }
            finally {
                currentlySearching = false;
            }

            // repaint the view container
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    viewContainer.repaint();
                }
            });
        }
    }

    /**
     * Gets the message that should be displayed when the task has completed.
     */
    public String getFinalMessage() {
        setDialogMessage();
        return dialogMessage;
    }

    /**
     * Utility for breaking the pattern up into searchable words.  Breaks are
     * done on white spaces and punctuation. 
     * @param pattern pattern to search words for.
     * @return
     */
    private ArrayList<String> phraseParser(String pattern){
        // trim white space, not really useful.
        pattern = pattern.trim();

        ArrayList<String> words = new ArrayList<String>();
        char c;
        for (int start = 0, curs= 0, max = pattern.length(); curs < max; curs++){
            c = pattern.charAt(curs);
            if (WordText.isWhiteSpace(c) ||
                    WordText.isPunctuation(c)){
                // add word segment
                if (start!= curs){
                    words.add(pattern.substring(start, curs));
                }
                // add white space  as word too.
                words.add(pattern.substring(curs, curs+1));
                // start
                start = curs + 1 < max?curs+1:start;
            }
            else if (curs + 1 == max){
                words.add(pattern.substring(start, curs+1));
            }

        }
        return words;
    }

    /**
     * Utility method for setting the dialog message.
     */
    private void setDialogMessage() {

        // Build Internationalized plural phrase.
        MessageFormat messageForm =
                new MessageFormat(messageBundle.getString(
                        "viewer.utilityPane.search.progress.msg"));
        double[] pageLimits = {0, 1, 2};
        String[] pageStrings = {
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.morePage.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.onePage.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.morePage.msg"),
        };
        ChoiceFormat pageChoiceForm = new ChoiceFormat(pageLimits,
                pageStrings);
        String[] resultsStrings = {
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.moreMatch.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.oneMatch.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.progress.moreMatch.msg"),
        };
        ChoiceFormat resultsChoiceForm = new ChoiceFormat(pageLimits,
                resultsStrings);

        Format[] formats = {null, pageChoiceForm, resultsChoiceForm};
        messageForm.setFormats(formats);
        Object[] messageArguments = {String.valueOf((current + 1)),
                new Integer((current + 1)), new Integer(totalHitCount)};

        dialogMessage = messageForm.format(messageArguments);
    }
}
