package org.icepdf.ri.common.utility.annotation.destinations;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.Names;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.DragDropColorList;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.utility.signatures.SigVerificationTask;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FindDestinationsTask extends AbstractTask {

    private static final Logger logger =
            Logger.getLogger(SigVerificationTask.class.toString());

    protected static ArrayList<DragDropColorList.ColorLabel> colorLabels;

    // status summary labels
    protected MessageFormat loadingMessage;
    protected MessageFormat completeMessage;

    // append nodes for found fonts.
    private DestinationsHandlerPanel destinationsPanel;

    /**
     * Creates a new instance of the search for markup annotations tasks.
     *
     * @param destinationsPanel parent search panel that start this task via an action
     * @param controller        root controller object
     * @param messageBundle     message bundle used for dialog text.
     */
    public FindDestinationsTask(DestinationsHandlerPanel destinationsPanel,
                                SwingController controller,
                                ResourceBundle messageBundle) {
        super(controller, messageBundle, controller.getDocument().getNumberOfPages());
        this.controller = controller;
        this.destinationsPanel = destinationsPanel;

        loadingMessage = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.destinations.view.loading.label"));
        completeMessage = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.destinations.view.loadingComplete.label"));
    }

    @Override
    public FindDestinationsTask getTask() {
        return this;
    }

    /**
     * Start the task, start searching the document for the pattern.
     * Color
     */
    public void startTask() {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                taskStatusMessage = null;
                return new FindDestinationsTask.ActualTask();
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

            int totalAnnotations = 0;
            try {
                current = 0;
                try {
                    Document currentDocument = controller.getDocument();
                    if (currentDocument != null) {
                        // iterate over markup annotations
                        Library library = currentDocument.getCatalog().getLibrary();
                        Names names = currentDocument.getCatalog().getNames();
                        if (names != null && names.getDestsNameTree() != null) {
                            NameTree tree = names.getDestsNameTree();
                            taskStatusMessage = loadingMessage.format(new Object[]{0, 0});
                        }

                    }
                    done = true;
                    taskStatusMessage = completeMessage.format(new Object[]{0, 0});
                } catch (Exception e) {
                    logger.log(Level.FINER, "Error loading annotations.", e);
                }
            } finally {
                taskRunning = false;
            }
            // repaint the view container
            SwingUtilities.invokeLater(() -> destinationsPanel.validate());
        }
    }

}