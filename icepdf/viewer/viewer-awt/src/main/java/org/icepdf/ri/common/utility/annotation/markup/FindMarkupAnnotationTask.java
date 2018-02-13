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
package org.icepdf.ri.common.utility.annotation.markup;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.AbstractTask;
import org.icepdf.ri.common.DragDropColorList;
import org.icepdf.ri.common.SwingWorker;
import org.icepdf.ri.common.utility.signatures.SigVerificationTask;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.PageComponentSelector;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindMarkupAnnotationTask extends AbstractTask<FindMarkupAnnotationTask> {

    private static final Logger logger =
            Logger.getLogger(SigVerificationTask.class.toString());

    protected static ArrayList<DragDropColorList.ColorLabel> colorLabels;

    // status summary labels
    protected MessageFormat loadingMessage;
    protected MessageFormat completeMessage;
    protected MessageFormat completeFilteredMessage;

    // sort/grouping labels
    protected MessageFormat pageLabelFormat;
    protected MessageFormat authorLabelFormat;
    protected MessageFormat dateLabelFormat;
    protected MessageFormat colorLabelFormat;


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
                                    Controller controller,
                                    ResourceBundle messageBundle) {
        super(controller, messageBundle, controller.getDocument().getNumberOfPages());
        this.controller = controller;
        this.markupAnnotationHandlerPanel = markupAnnotationHandlerPanel;

        loadingMessage = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.loadingAnnotations.label"));
        completeMessage = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.loadingComplete.label"));
        completeFilteredMessage = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.summaryFiltered.label"));

        pageLabelFormat = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.page.label"));
        authorLabelFormat = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.author.label"));
        dateLabelFormat = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.date.label"));
        colorLabelFormat = new MessageFormat(
                messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.color.label"));
    }

    @Override
    public FindMarkupAnnotationTask getTask() {
        return this;
    }

    /**
     * Start the task, start searching the document for the pattern.
     * Color
     * @param filterAuthor author filter column value
     * @param filterColor color filter column value
     * @param filterType annotation type filter column value
     * @param searchPattern search pattern
     * @param sortType  sort type
     * @param isRegex regex notation enabled.
     * @param isCaseSensitive case sensitivity
     */
    public void startTask(
            final Pattern searchPattern,
            final MarkupAnnotationPanel.SortColumn sortType,
            final MarkupAnnotationPanel.FilterSubTypeColumn filterType,
            final MarkupAnnotationPanel.FilterAuthorColumn filterAuthor,
            final Color filterColor,
            final boolean isRegex,
            final boolean isCaseSensitive) {
        final SwingWorker worker = new SwingWorker() {
            public Object construct() {
                current = 0;
                done = false;
                canceled = false;
                taskStatusMessage = null;
                return new FindMarkupAnnotationTask.ActualTask(
                        searchPattern, sortType, filterType, filterAuthor, filterColor, isRegex, isCaseSensitive);
            }
        };
        worker.setThreadPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    /**
     * The actual long running task.  This runs in a SwingWorker thread.
     */
    private class ActualTask {
        ActualTask(Pattern searchPattern,
                   MarkupAnnotationPanel.SortColumn sortType,
                   MarkupAnnotationPanel.FilterSubTypeColumn filterType,
                   MarkupAnnotationPanel.FilterAuthorColumn filterAuthor,
                   Color filterColor,
                   boolean isRegex,
                   boolean isCaseSensitive) {

            taskRunning = true;

            colorLabels = DragDropColorList.retrieveColorLabels();
            int totalAnnotations = 0;
            int filteredAnnotationCount = 0;
            try {
                current = 0;
                try {
                    Document currentDocument = controller.getDocument();
                    if (currentDocument != null) {
                        // iterate over markup annotations
                        Library library = currentDocument.getCatalog().getLibrary();
                        int pageCount = currentDocument.getPageTree().getNumberOfPages();
                        ArrayList<MarkupAnnotation> markupAnnotations = new ArrayList<>();
                        for (int i = 0; i < pageCount; i++) {
                            // break if needed
                            if (canceled || done) {
                                break;
                            }
                            // Update task information
                            current = i;
                            taskStatusMessage = loadingMessage.format(new Object[]{i + 1, pageCount});

                            String userName = System.getProperty("user.name");
                            Page page = currentDocument.getPageTree().getPage(i);
                            if (page != null) {
                                ArrayList<Reference> annotationReferences = page.getAnnotationReferences();
                                if (annotationReferences != null && annotationReferences.size() > 0) {
                                    for (Object annotationReference : annotationReferences) {
                                        Object annotation = library.getObject(annotationReference);
                                        if (annotation instanceof MarkupAnnotation) {
                                            MarkupAnnotation markupAnnotation = (MarkupAnnotation) annotation;
                                            totalAnnotations++;
                                            // apply any filters
                                            // author
                                            boolean filter = false;
                                            if (filterAuthor.equals(
                                                    MarkupAnnotationPanel.FilterAuthorColumn.AUTHOR_OTHER)) {
                                                if (markupAnnotation.getTitleText() == null ||
                                                        markupAnnotation.getTitleText().equalsIgnoreCase(userName)) {
                                                    filter = true;
                                                }
                                            } else if (filterAuthor.equals(
                                                    MarkupAnnotationPanel.FilterAuthorColumn.AUTHOR_CURRENT)) {
                                                if (markupAnnotation.getTitleText() == null ||
                                                        !markupAnnotation.getTitleText().equalsIgnoreCase(userName)) {
                                                    filter = true;
                                                }
                                            }
                                            // color
                                            if (filterColor != null) {
                                                if (markupAnnotation.getColor() == null ||
                                                        markupAnnotation.getColor().getRGB() != filterColor.getRGB()) {
                                                    filter = true;
                                                }
                                            }
                                            // filter by type
                                            if (!filterType.equals(MarkupAnnotationPanel.FilterSubTypeColumn.ALL)) {
                                                if (markupAnnotation.getSubType() != null) {
                                                    String subType = markupAnnotation.getSubType().toString();
                                                    if (!subType.equalsIgnoreCase(filterType.toString())) {
                                                        filter = true;
                                                    }
                                                } else {
                                                    filter = true;
                                                }
                                            }
                                            // app search regex
                                            if (isRegex && searchPattern != null) {
                                                Matcher matcher = searchPattern.matcher(
                                                        ((MarkupAnnotation) annotation).getContents());
                                                filter = !matcher.find();
                                            } else if (searchPattern != null) {
                                                String annotationText = ((MarkupAnnotation) annotation).getContents();
                                                if (isCaseSensitive && annotationText != null) {
                                                    filter = !annotationText.contains(searchPattern.pattern());
                                                } else if (annotationText != null) {
                                                    filter = !annotationText.toLowerCase().contains(
                                                            searchPattern.pattern().toLowerCase());
                                                }
                                            }
                                            // apply the filter flag
                                            if (!filter) {
                                                markupAnnotations.add(markupAnnotation);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // sort the annotations.
                        markupAnnotations.sort(new AnnotationComparator(sortType));
                        filteredAnnotationCount = markupAnnotations.size();
                        // build the tree
                        MarkupAnnotation previousMarkupAnnotation = null;
                        for (MarkupAnnotation markupAnnotation : markupAnnotations) {
                            if (!markupAnnotation.isInReplyTo()) {
                                // add group as needed
                                checkGroupLabelChange(sortType, previousMarkupAnnotation, markupAnnotation);
                                previousMarkupAnnotation = markupAnnotation;
                                // add the annotation node
                                SwingUtilities.invokeLater(() -> {
                                    // add the node
                                    markupAnnotationHandlerPanel.addAnnotation(markupAnnotation, searchPattern);
                                    // try repainting the container
                                    markupAnnotationHandlerPanel.repaint();
                                });
                                Thread.yield();
                            }
                        }
                    }
                    // update status message to show sort and filter results.
                    if (!filterAuthor.equals(MarkupAnnotationPanel.FilterAuthorColumn.ALL) ||
                            !filterType.equals(MarkupAnnotationPanel.FilterSubTypeColumn.ALL) ||
                            filterColor != null) {
                        taskStatusMessage = completeFilteredMessage.format(new Object[]{filteredAnnotationCount, totalAnnotations});
                    } else {
                        taskStatusMessage = completeMessage.format(new Object[]{totalAnnotations, totalAnnotations});
                    }
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

    protected void checkGroupLabelChange(MarkupAnnotationPanel.SortColumn sortColumn,
                                         MarkupAnnotation previousMarkupAnnotation,
                                         MarkupAnnotation markupAnnotation) {

        String pageLabel = null;
        if (MarkupAnnotationPanel.SortColumn.PAGE.equals(sortColumn)) {
            if (previousMarkupAnnotation == null ||
                    previousMarkupAnnotation.getPageIndex() != markupAnnotation.getPageIndex()) {
                if (markupAnnotation.getPageIndex() < 0) {
                    PageComponentSelector.AssignAnnotationPage(controller, markupAnnotation);
                }
                pageLabel = pageLabelFormat.format(new Object[]{markupAnnotation.getPageIndex() + 1});
            }
        } else if (MarkupAnnotationPanel.SortColumn.AUTHOR.equals(sortColumn)) {
            String markupTitle = markupAnnotation.getTitleText() != null ? markupAnnotation.getTitleText() : "";
            String previousTitle = previousMarkupAnnotation != null ?
                    previousMarkupAnnotation.getTitleText() != null ? previousMarkupAnnotation.getTitleText() : "" : "";
            if (previousMarkupAnnotation == null ||
                    !markupTitle.equals(previousTitle)) {
                pageLabel = authorLabelFormat.format(new Object[]{markupTitle});
            }
        } else if (MarkupAnnotationPanel.SortColumn.DATE.equals(sortColumn)) {
            PDate pDate1 = markupAnnotation.getCreationDate();
            if (previousMarkupAnnotation == null ||
                    !pDate1.equalsDay(previousMarkupAnnotation.getCreationDate())) {
                LocalDateTime creationDate = pDate1.asLocalDateTime();
                DateTimeFormatter formatter = DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault());
                pageLabel = dateLabelFormat.format(new Object[]{creationDate.format(formatter)});
            }
        } else if (MarkupAnnotationPanel.SortColumn.TYPE.equals(sortColumn)) {
            if (previousMarkupAnnotation == null ||
                    !previousMarkupAnnotation.getSubType().equals(markupAnnotation.getSubType())) {
                pageLabel = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.type.label");
            }
        } else if (MarkupAnnotationPanel.SortColumn.COLOR.equals(sortColumn)) {
            if (previousMarkupAnnotation == null ||
                    (previousMarkupAnnotation.getColor() != null &&
                            !previousMarkupAnnotation.getColor().equals(markupAnnotation.getColor()))) {
                pageLabel = colorLabelFormat.format(new Object[]{
                        markupAnnotation.getColor() != null ? findColor(markupAnnotation.getColor()) : ""});
            }
        }

        if (pageLabel != null) {
            final String tmpLabel = pageLabel;
            SwingUtilities.invokeLater(() -> {
                markupAnnotationHandlerPanel.addPageGroup(tmpLabel);
                // try repainting the container
                markupAnnotationHandlerPanel.repaint();
            });
        }

    }

    private String findColor(Color color) {
        if (colorLabels != null) {
            for (DragDropColorList.ColorLabel colorLabel : colorLabels) {
                if (color.equals(colorLabel.getColor())) {
                    return colorLabel.getLabel();
                }
            }
        }
        // see if we can't return a hex color
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }


    class AnnotationComparator implements Comparator<MarkupAnnotation> {

        private MarkupAnnotationPanel.SortColumn sortColumn;

        public AnnotationComparator(MarkupAnnotationPanel.SortColumn sortColumn) {
            this.sortColumn = sortColumn;
        }

        @Override
        public int compare(MarkupAnnotation o1, MarkupAnnotation o2) {
            if (MarkupAnnotationPanel.SortColumn.PAGE.equals(sortColumn)) {
                if (o1.getPageIndex() > o2.getPageIndex()) {
                    return 1;
                } else if (o1.getPageIndex() > o2.getPageIndex()) {
                    return -1;
                } else {
                    return 0;
                }
            } else if (MarkupAnnotationPanel.SortColumn.AUTHOR.equals(sortColumn)) {
                String author1 = o1.getTitleText();
                String author2 = o2.getTitleText();
                if (author1 != null && author2 != null)
                    return author1.compareToIgnoreCase(author2);
                else {
                    return 0;
                }
            } else if (MarkupAnnotationPanel.SortColumn.DATE.equals(sortColumn)) {
                PDate pDate1 = o1.getCreationDate();
                PDate pDate2 = o2.getCreationDate();
                LocalDateTime date1 = pDate1.asLocalDateTime();
                LocalDateTime date2 = pDate2.asLocalDateTime();
                if (date1 != null && date2 != null)
                    return date1.compareTo(date2);
                else {
                    return 0;
                }
            } else if (MarkupAnnotationPanel.SortColumn.TYPE.equals(sortColumn)) {
                String type1 = o1.getSubType().toString();
                String type2 = o2.getSubType().toString();
                return type1.compareToIgnoreCase(type2);
            } else if (MarkupAnnotationPanel.SortColumn.COLOR.equals(sortColumn)) {
                Color color1 = o1.getColor();
                Color color2 = o2.getColor();
                if (color1 != null && color2 != null) {
                    int rgb1 = color1.getRGB();
                    int rgb2 = color2.getRGB();
                    return Integer.compare(rgb2, rgb1);
                } else {
                    return 0;
                }
            }
            return 0;
        }
    }
}