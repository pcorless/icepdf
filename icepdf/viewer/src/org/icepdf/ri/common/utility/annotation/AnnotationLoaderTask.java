package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.utility.signatures.SigVerificationTask;
import org.icepdf.ri.util.AbstractTask;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AnnotationLoaderTask uses the SwingWorker Thread to traverse all pages in a document and any found annotation are
 * grouped by parent page and a callback is made back to AnnotationHandlerPanel to build the actual tree.  Tree
 * constructions is executed on the AWT thread.
 */
public class AnnotationLoaderTask extends AbstractTask<AnnotationLoaderTask> {

    private static final Logger logger =
            Logger.getLogger(SigVerificationTask.class.toString());

    // append nodes for found text.
    private AnnotationHandlerPanel annotationHandlerPanel;

    /**
     * Creates a new instance of the SigVerificationTask.
     *
     * @param annotationHandlerPanel parent signature panel that start this task via an action
     * @param controller             root controller object
     * @param messageBundle          message bundle used for dialog text.
     */
    public AnnotationLoaderTask(AnnotationHandlerPanel annotationHandlerPanel,
                                SwingController controller,
                                ResourceBundle messageBundle) {
        super(controller, messageBundle,
                controller.getDocument().getPageTree().getNumberOfPages());
        this.annotationHandlerPanel = annotationHandlerPanel;
    }

    @Override
    public AnnotationLoaderTask getTask() {
        return this;
    }

    public void startTask() {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                taskStatusMessage = null;
                return new LoadAllAnnotations();
            }
        };
        worker.setThreadPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    /**
     * Load all annotation in the specified document.
     */
    class LoadAllAnnotations {
        LoadAllAnnotations() {
            taskRunning = true;
            MessageFormat loadingMessage = new MessageFormat(
                    messageBundle.getString("viewer.utilityPane.annotation.tab.loadingAnnotations.label"));
            MessageFormat pageLabelFormat = new MessageFormat(
                    messageBundle.getString("viewer.utilityPane.annotation.tab.tree.page.label"));
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
                                    // insert page node
                                    final String label = pageLabel;
                                    SwingUtilities.invokeLater(new Runnable() {
                                        public void run() {
                                            annotationHandlerPanel.addPageGroup(label);
                                            // try repainting the container
                                            annotationHandlerPanel.repaint();
                                        }
                                    });
                                    Thread.yield();
                                    // add child nodes for each annotation.
                                    for (Object annotationReference : annotationReferences) {
                                        final Object annotation = library.getObject(annotationReference);
                                        // add the node to the signature panel tree but on the
                                        // awt thread.
                                        SwingUtilities.invokeLater(new Runnable() {
                                            public void run() {
                                                // add the node
                                                annotationHandlerPanel.addAnnotation(annotation);
                                                // try repainting the container
                                                annotationHandlerPanel.repaint();
                                            }
                                        });
                                        Thread.yield();
                                    }
                                }
                            }
                        }
                    }
                    // update the dialog and end the task
                    taskStatusMessage = messageBundle.getString("viewer.utilityPane.annotation.tab.loadingComplete.label");
                    done = true;
                } catch (Exception e) {
                    logger.log(Level.FINER, "Error loading annotations.", e);
                }
            } finally {
                taskRunning = false;
            }
            // repaint the view container
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    annotationHandlerPanel.validate();
                }
            });
        }
    }

}
