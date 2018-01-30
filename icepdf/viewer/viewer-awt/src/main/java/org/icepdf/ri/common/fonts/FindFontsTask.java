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
package org.icepdf.ri.common.fonts;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * This class is a utility for finding and reporting all font types in a document.  Each page in the document
 * is checked for valid font resources, if found the fonts are added to the calling FontDialog for addition to
 * a JTree of know document fonts.
 *
 *
 * @since 6.1.3
 */
public class FindFontsTask extends AbstractTask<FindFontsTask> {

    // canned internationalized messages.
    private MessageFormat searchingMessageForm;
    // append nodes for found fonts.
    private FontHandlerPanel fontHandlerPanel;

    private Container viewContainer;

    /**
     * Creates a new instance of the SearchTextTask.
     *
     * @param fontHandlerPanel    parent search panel that start this task via an action
     * @param controller    root controller object
     * @param messageBundle message bundle used for dialog text.
     */
    public FindFontsTask(FontHandlerPanel fontHandlerPanel,
                         Controller controller,
                         ResourceBundle messageBundle) {
        super(controller, messageBundle, controller.getDocument().getNumberOfPages());
        controller = controller;
        this.fontHandlerPanel = fontHandlerPanel;
        lengthOfTask = controller.getDocument().getNumberOfPages();
        this.viewContainer = controller.getDocumentViewController().getViewContainer();
        // setup searching format format.
        searchingMessageForm = new MessageFormat(messageBundle.getString("viewer.dialog.fonts.searching.label"));
    }

    @Override
    public FindFontsTask getTask() {
        return this;
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
                taskStatusMessage = null;
                return new FindFontsTask.ActualTask();
            }
        };
        worker.setThreadPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    /**
     * The actual long running task.  This runs in a SwingWorker thread.
     */
    private class ActualTask {
        ActualTask() {

            try {
                taskRunning = true;
                current = 0;
                // little cache of fonts by reference so we don't load a font more then once.
                HashMap<Reference, Font> fontCache = new HashMap<>();

                Document document = controller.getDocument();
                // iterate over each page in the document
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    // break if needed
                    if (canceled || done) {
                        taskStatusMessage = "";
                        break;
                    }
                    // Update task information
                    current = i;

                    // update search message in results pane.
                    int percent = (int) ((i / (float) lengthOfTask) * 100);
                    Object[] messageArguments = {String.valueOf(percent)};
                    taskStatusMessage = searchingMessageForm.format(messageArguments);

                    Library library = document.getCatalog().getLibrary();
                    Page page = document.getPageTree().getPage(i);
                    page.initPageResources();
                    Resources pageResources = page.getResources();
                    if (pageResources != null) {
                        HashMap pageFonts = pageResources.getFonts();
                        if (pageFonts != null && pageFonts.size() > 0) {
                            Set fontKeys = pageFonts.keySet();
                            for (Object fontObjectReference : fontKeys) {
                                Object fontObject = pageFonts.get(fontObjectReference);
                                if (fontObject instanceof Reference) {
                                    Reference fontReference = (Reference) fontObject;
                                    // check if we already have this font
                                    if (!fontCache.containsKey(fontReference)) {
                                        fontObject = library.getObject(fontReference);
                                        if (fontObject instanceof org.icepdf.core.pobjects.fonts.Font) {
                                            final Font font = (Font) fontObject;
                                            font.init();
                                            fontCache.put(fontReference, font);
                                            SwingUtilities.invokeLater(() -> {
                                                // add the node
                                                fontHandlerPanel.addFoundEntry(font);
                                                // try repainting the container
                                                fontHandlerPanel.expandAllNodes();
                                                viewContainer.repaint();
                                            });
                                        }
                                    }
                                }
                            }

                        }
                    }
                    Thread.yield();
                }
                // update the dialog and end the task
                taskStatusMessage = "";

                done = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                taskRunning = false;
            }

            // repaint the view container
            SwingUtilities.invokeLater(() -> viewContainer.validate());
        }
    }
}
