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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
import org.icepdf.ri.common.SwingWorker;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class is a utility for extracting text from a PDF document.
 *
 * @since 1.1
 */
public class TextExtractionTask {

    private static final Logger logger =
            Logger.getLogger(TextExtractionTask.class.toString());

    // total length of task (total page count), used for progress bar
    private int lengthOfTask;

    // current progress, used for the progress bar
    private int current = 0;

    // message displayed on progress bar
    private String dialogMessage;

    // flags for threading
    private boolean done = false;
    private boolean canceled = false;

    // internationalization
    private ResourceBundle messageBundle;

    // PDF document pointer
    private Document document = null;

    // File used for text export
    private File file = null;

    /**
     * Create a new instance of the TextExtraction object.
     *
     * @param document document whose text will be extracted.
     * @param file     output file for extracted text.
     */
    public TextExtractionTask(Document document, File file, ResourceBundle messageBundle) {
        this.document = document;
        this.file = file;
        lengthOfTask = document.getNumberOfPages();
        this.messageBundle = messageBundle;
    }

    /**
     * Start the task,  created a new SwingWorker for the text extraction
     * process.
     */
    public void go() {
        final SwingWorker worker = new SwingWorker() {
            // reset all instance variables
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                dialogMessage = null;
                return new ActualTask();
            }
        };
        worker.setThreadPriority(Thread.MIN_PRIORITY);
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
            // Extraction of text from pdf procedure
            try {
                // create file output stream
                BufferedWriter fileOutputStream = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file),"UTF8"));
                // Print document information
                String pageNumber =
                        messageBundle.getString("viewer.exportText.fileStamp.msg");

                fileOutputStream.write(pageNumber);
                fileOutputStream.write(10); // line break

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    // break if needed
                    if (canceled || done) {
                        break;
                    }

                    // Update task information
                    current = i;

                    // Build Internationalized plural phrase.
                    MessageFormat messageForm =
                            new MessageFormat(messageBundle.getString(
                                    "viewer.exportText.fileStamp.progress.msg"));
                    double[] fileLimits = {0, 1, 2};
                    String[] fileStrings = {
                            messageBundle.getString(
                                    "viewer.exportText.fileStamp.progress.moreFile.msg"),
                            messageBundle.getString(
                                    "viewer.exportText.fileStamp.progress.oneFile.msg"),
                            messageBundle.getString(
                                    "viewer.exportText.fileStamp.progress.moreFile.msg"),
                    };
                    ChoiceFormat choiceForm = new ChoiceFormat(fileLimits,
                            fileStrings);
                    Format[] formats = {null, choiceForm, null};
                    messageForm.setFormats(formats);
                    Object[] messageArguments = {String.valueOf((current + 1)),
                            lengthOfTask, lengthOfTask};

                    dialogMessage = messageForm.format(messageArguments);

                    messageForm =
                            new MessageFormat(messageBundle.getString(
                                    "viewer.exportText.pageStamp.msg"));
                    messageArguments = new Object[]{String.valueOf((current + 1))};

                    pageNumber = messageForm.format(messageArguments);

                    fileOutputStream.write(pageNumber);
                    fileOutputStream.write(10); // line break

                    String pageText = document.getPageText(i).toString();

                    fileOutputStream.write(pageText);

                    Thread.yield();

                }

                done = true;
                current = 0;
                fileOutputStream.flush();
                fileOutputStream.close();
            }
            catch (Throwable e) {
                logger.log(Level.FINE, "Malformed URL Exception ", e);
            }
        }
    }
}
