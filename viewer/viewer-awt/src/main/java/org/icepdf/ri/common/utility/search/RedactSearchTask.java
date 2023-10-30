package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.RedactionAnnotationComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * RedactSearchTask creates redaction annotation based on text search results highlight bounds.  The created
 * annotations can be previewed in the viewer.  This very IMPORTANT,  content is not removed until the document
 * is exported.
 *
 * @since 7.2.0
 */
public class RedactSearchTask extends SwingWorker<Void, RedactSearchTask.RedactResult> {

    private final int lengthOfTask;
    // current progress, used for the progress bar
    private int current;
    // message displayed on progress bar
    private String dialogMessage;

    private int redactionCount;

    // parent swing controller
    private final Controller controller;

    private final Container viewContainer;

    private final BaseRedactModel redactModel;

    private MessageFormat redactingMessageForm;
    private MessageFormat redactCompletionMessageForm;

    public static class RedactResult {
    }

    public RedactSearchTask(Controller controller, BaseRedactModel redactModel) {
        this.controller = controller;
        this.viewContainer = controller.getDocumentViewController().getViewContainer();
        lengthOfTask = controller.getDocument().getNumberOfPages();

        // setup redact output formats.
        this.redactModel = redactModel;
        if (redactModel != null) {
            redactingMessageForm = redactModel.setupRedactingMessageForm();
            redactCompletionMessageForm = redactModel.setupRedactCompletionMessageForm();
        }
    }

    protected Void doInBackground() {
        // shared search controller for shared results
        DocumentViewController documentViewController = controller.getDocumentViewController();
        DocumentSearchController searchController = controller.getDocumentSearchController();
        Document document = controller.getDocument();
        int totalPages = document.getNumberOfPages();
        redactionCount = 0;
        ArrayList<WordText> foundWords;
        List<AbstractPageViewComponent> pageComponents = controller.getDocumentViewController()
                .getDocumentViewModel().getPageComponents();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            // break if needed
            if (isCancelled()) {
                setDialogMessage();
                break;
            }
            current = pageIndex;
            // update search message in search pane.
            Object[] messageArguments = {String.valueOf((current + 1)), lengthOfTask, lengthOfTask};
            if (redactingMessageForm != null) {
                dialogMessage = redactingMessageForm.format(messageArguments);
            }
            // get the search results for this page
            foundWords = searchController.searchPage(pageIndex);
            if (foundWords != null) {
                AbstractPageViewComponent pageViewComponent = pageComponents.get(pageIndex);
                for (WordText wordText : foundWords) {
                    redactionCount++;
                    publish(new RedactResult());
                    Rectangle tBbox = wordText.getBounds().getBounds();

                    RedactionAnnotation redactionAnnotation = (RedactionAnnotation)
                            AnnotationFactory.buildAnnotation(
                                    document.getPageTree().getLibrary(),
                                    Annotation.SUBTYPE_REDACT,
                                    tBbox);

                    ArrayList<Shape> markupBounds = new ArrayList<>();
                    markupBounds.add(tBbox);
                    redactionAnnotation.setColor(Color.BLACK);
                    redactionAnnotation.setMarkupBounds(markupBounds);
                    redactionAnnotation.setMarkupPath(new GeneralPath(tBbox));
                    redactionAnnotation.setBBox(tBbox);
                    redactionAnnotation.resetAppearanceStream(new AffineTransform());

                    if (pageViewComponent.getPage().isInitiated()) {
                        RedactionAnnotationComponent annotationComponent = (RedactionAnnotationComponent)
                                AnnotationComponentFactory.buildAnnotationComponent(
                                        redactionAnnotation,
                                        documentViewController,
                                        pageViewComponent);
                        documentViewController.addNewAnnotation(annotationComponent);
                        tBbox = annotationComponent.convertToPageSpace(tBbox).getBounds();
                        annotationComponent.setBounds(tBbox);
                        pageViewComponent.repaint();
                    } else {
                        pageViewComponent.getPage().addAnnotation(redactionAnnotation, true);
                    }
                }
            }
        }
        // update the dialog and end the task
        setDialogMessage();
        return null;
    }

    private void setDialogMessage() {
        Object[] messageArguments = {String.valueOf((current + 1)), (current + 1), redactionCount};
        if (redactCompletionMessageForm != null) {
            dialogMessage = redactCompletionMessageForm.format(messageArguments);
        }
    }

    protected void done() {
        if (redactModel != null) {
            redactModel.updateProgressControls(dialogMessage);
        }
        viewContainer.validate();
    }

    protected void process(List<RedactSearchTask.RedactResult> chunks) {
        if (redactModel != null) {
            redactModel.updateProgressControls(dialogMessage);
        }
        viewContainer.repaint();
    }
}
