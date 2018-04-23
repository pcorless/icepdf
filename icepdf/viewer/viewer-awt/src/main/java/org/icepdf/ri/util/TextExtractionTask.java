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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is a utility for extracting text from a PDF document.
 *
 * @since 1.1
 */
public class TextExtractionTask extends SwingWorker<Void, StringBuilder> {

    private static final Logger logger =
            Logger.getLogger(TextExtractionTask.class.toString());

    // total length of task (total page count), used for progress bar
    private int lengthOfTask;

    // current progress, used for the progress bar
    private int current;

    // message displayed on progress bar
    private MessageFormat messageDialogFormat;
    private MessageFormat messageTextFormat;
    private String dialogMessage;

    // internationalization
    private ResourceBundle messageBundle;

    // PDF document pointer
    private Document document;

    // File used for text export
    private File file;

    private ProgressMonitor progressMonitor;

    private static final double[] fileLimits = {0, 1, 2};


    /**
     * Create a new instance of the TextExtraction object.
     *
     * @param document      document whose text will be extracted.
     * @param file          output file for extracted text.
     * @param progressMonitor progressMonitor to update with extraction progress
     * @param messageBundle main message bundle for i18n
     */
    public TextExtractionTask(Document document, File file, ProgressMonitor progressMonitor, ResourceBundle messageBundle) {
        this.document = document;
        this.file = file;
        lengthOfTask = document.getNumberOfPages();
        this.progressMonitor = progressMonitor;
        this.messageBundle = messageBundle;
        // build out dialog messages
        messageDialogFormat = new MessageFormat(messageBundle.getString(
                "viewer.exportText.fileStamp.progress.msg"));
        String[] fileStrings = {
                messageBundle.getString("viewer.exportText.fileStamp.progress.moreFile.msg"),
                messageBundle.getString("viewer.exportText.fileStamp.progress.oneFile.msg"),
                messageBundle.getString("viewer.exportText.fileStamp.progress.moreFile.msg")};
        ChoiceFormat choiceForm = new ChoiceFormat(fileLimits, fileStrings);
        Format[] formats = {null, choiceForm, null};
        messageDialogFormat.setFormats(formats);
        // build out text file messages
        messageTextFormat = new MessageFormat(messageBundle.getString("viewer.exportText.pageStamp.msg"));
    }

    @Override
    protected void done() {
        progressMonitor.close();
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    protected void process(List<StringBuilder> chunks) {
        // Update progressMonitor progress
        progressMonitor.setProgress(current);
        if (dialogMessage != null) {
            progressMonitor.setNote(dialogMessage);
        }
    }

    @Override
    protected Void doInBackground() {
        // Extraction of text from pdf procedure
        try {
            // create file output stream
            BufferedWriter fileOutputStream = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
            // Print document information
            String pageNumber = messageBundle.getString("viewer.exportText.fileStamp.msg");

            fileOutputStream.write(pageNumber);
            fileOutputStream.write(10); // line break

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                // break if needed
                if (isCancelled()) {
                    break;
                }
                // Update task information
                current = i;
                Object[] messageArguments = {String.valueOf((current + 1)), lengthOfTask, lengthOfTask};
                dialogMessage = messageDialogFormat.format(messageArguments);

                messageArguments = new Object[]{String.valueOf((current + 1))};
                pageNumber = messageTextFormat.format(messageArguments);

                fileOutputStream.write(pageNumber);
                fileOutputStream.write(10); // line break

                Page page = document.getPageTree().getPage(i);
                List<LineText> pageLines;
                if (page.isInitiated()) {
                    // get a pages already initialized text.
                    pageLines = document.getPageViewText(i).getPageLines();
                } else {
                    // grap the text the fastest way possible.
                    pageLines = document.getPageText(i).getPageLines();
                }
                StringBuilder extractedText = null;
                for (LineText lineText : pageLines) {
                    extractedText = new StringBuilder();
                    for (WordText wordText : lineText.getWords()) {
                        extractedText.append(wordText.getText());
                    }
                    extractedText.append('\n');
                    fileOutputStream.write(extractedText.toString());
                }
                publish(extractedText);
            }
            current = 0;
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (Throwable e) {
            logger.log(Level.FINE, "Malformed URL Exception ", e);
        }
        return null;
    }
}
