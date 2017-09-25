/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.utility.signatures.SigVerificationTask;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FindMarkupAnnotationTask extends AbstractTask {

    private static final Logger logger =
            Logger.getLogger(SigVerificationTask.class.toString());

    // append nodes for found fonts.
    private MarkupAnnotationHandlerPanel markupAnnotationHandlerPanel;


    /**
     * Creates a new instance of the search for markup annotations tasks.
     *
     * @param markupAnnotationHandlerPanel parent search panel that start this task via an action
     * @param controller                   root controller object
     * @param messageBundle                message bundle used for dialog text.
     */
    public FindMarkupAnnotationTask(MarkupAnnotationHandlerPanel markupAnnotationHandlerPanel,
                                    SwingController controller,
                                    ResourceBundle messageBundle) {
        super(controller, messageBundle, controller.getDocument().getNumberOfPages());
        this.controller = controller;
        this.markupAnnotationHandlerPanel = markupAnnotationHandlerPanel;
    }

    @Override
    public FindMarkupAnnotationTask getTask() {
        return this;
    }

    /**
     * Start the task, start searching the document for the pattern.
     */
    public void startTask() {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                taskStatusMessage = null;
                return new FindMarkupAnnotationTask.ActualTask();
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

            taskRunning = true;
            MessageFormat loadingMessage = new MessageFormat(
                    messageBundle.getString("viewer.utilityPane.markupAnnotation.view.loadingAnnotations.label"));
            MessageFormat pageLabelFormat = new MessageFormat(
                    messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.page.label"));
            try {
                current = 0;
                try {
                    Document currentDocument = controller.getDocument();
                    if (currentDocument != null) {
                        Library library = currentDocument.getCatalog().getLibrary();
                        int pageCount = currentDocument.getPageTree().getNumberOfPages();
                        for (int i = 0; i < pageCount; i++) {
                            // break if needed
                            if (canceled || done) {
                                break;
                            }
                            // Update task information
                            current = i;
                            taskStatusMessage = loadingMessage.format(new Object[]{i + 1, pageCount});
                            String pageLabel = pageLabelFormat.format(new Object[]{i + 1});

                            Page page = currentDocument.getPageTree().getPage(i);
                            if (page != null) {
                                ArrayList<Reference> annotationReferences = page.getAnnotationReferences();
                                if (annotationReferences != null && annotationReferences.size() > 0) {
//                                    // insert page node
                                    final String label = pageLabel;
                                    SwingUtilities.invokeLater(() -> {
                                        markupAnnotationHandlerPanel.addPageGroup(label);
                                        // try repainting the container
                                        markupAnnotationHandlerPanel.repaint();
                                    });
                                    Thread.yield();
                                    // add child nodes for each annotation.
                                    for (Object annotationReference : annotationReferences) {
                                        final Object annotation = library.getObject(annotationReference);
                                        // add the node to the signature panel tree but on the
                                        // awt thread.
                                        if (annotation instanceof MarkupAnnotation) {
                                            MarkupAnnotation markupAnnotation = (MarkupAnnotation) annotation;
                                            if (!markupAnnotation.isInReplyTo()) {
                                                SwingUtilities.invokeLater(() -> {
                                                    // add the node
                                                    markupAnnotationHandlerPanel.addAnnotation((Annotation) annotation);
                                                    // try repainting the container
                                                    markupAnnotationHandlerPanel.repaint();
                                                });
                                                Thread.yield();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // update the dialog and end the task
                    taskStatusMessage = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.loadingComplete.label");
                    done = true;
                } catch (Exception e) {
                    logger.log(Level.FINER, "Error loading annotations.", e);
                }
            } finally {
                taskRunning = false;
            }
            // repaint the view container
            SwingUtilities.invokeLater(() -> markupAnnotationHandlerPanel.validate());
        }
    }
}