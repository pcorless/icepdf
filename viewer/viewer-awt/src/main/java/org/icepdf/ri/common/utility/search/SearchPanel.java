/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DestinationResult;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AnnotationCellRender;
import org.icepdf.ri.common.utility.annotation.AnnotationTreeNode;
import org.icepdf.ri.common.utility.outline.OutlineItemTreeNode;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.PageComponentSelector;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.icepdf.ri.util.ViewerPropertiesManager.*;

/**
 * This class is the GUI component for the SearchTextTask.  This panel can be
 * added to a utility panel.  The GUI allows users to
 * type in a search string and displays a JList of all the pages that have results.
 * Each list item can be selected, and when selected, the viewer will show the
 * corresponding page.
 *
 * @since 1.1
 */
@SuppressWarnings("serial")
public class SearchPanel extends JPanel implements ActionListener, MutableDocument,
        TreeSelectionListener, DocumentListener, BaseSearchModel {

    private static final Logger logger =
            Logger.getLogger(SearchPanel.class.toString());

    private static final int maxPagesForLiveSearch;

    static {
        maxPagesForLiveSearch = Defs.intProperty(
                "org.icepdf.ri.common.utility.search.maxPages.liveSearch", 50);
    }

    // markup for search context.
    public static final String HTML_TAG_START = "<html>";
    public static final String HTML_TAG_END = "</html>";
    public static final String BOLD_TAG_START = "<b>";
    public static final String BOLD_TAG_END = "</b>";

    // layouts constraint
    private GridBagConstraints constraints;
    // input for a search pattern
    private JTextField searchTextField;
    // pointer to document which will be searched
    private final org.icepdf.ri.common.views.Controller controller;

    // list box to hold search results
    private JTree tree;
    private DefaultMutableTreeNode rootTreeNode;
    private DefaultMutableTreeNode textTreeNode;
    private DefaultMutableTreeNode formsTreeNode;
    private DefaultMutableTreeNode commentsTreeNode;
    private DefaultMutableTreeNode outlinesTreeNode;
    private DefaultMutableTreeNode destinationsTreeNode;
    private DefaultTreeModel treeModel;
    // search start button
    private JButton searchButton;
    // clear search
    private JButton clearSearchButton;
    private SearchFilterButton searchFilterButton;
    // page index of the last added node.
    private int lastTextNodePageIndex, lastCommentNodePageIndex;

    // show progress of search
    private JProgressBar progressBar;
    // task to complete in separate thread
    private SearchTextTask searchTextTask;

    // status label for search
    private JLabel findMessage;
    // message bundle for internationalization
    private final ResourceBundle messageBundle;
    private final MessageFormat searchResultMessageForm;

    /**
     * Create a new instance of SearchPanel.
     *
     * @param controller root Controller
     */
    public SearchPanel(SwingController controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        searchResultMessageForm = setupSearchResultMessageForm();
        setGui();
    }

    public String getSearchPhrase() {
        return searchTextField.getText();
    }

    public void setSearchPhrase(String searchPhrase) {
        clearSearch();
        searchTextField.setText(searchPhrase);
    }

    @Override
    public void refreshDocumentInstance() {
        // First have to stop any existing search
        stopSearch();
        // get the document from the controller.
        Document document = controller.getDocument();
        if (document != null && progressBar != null) {
            progressBar.setMaximum(document.getNumberOfPages());
        }
        if (searchTextField != null) {
            searchTextField.setText("");
        }
        if (searchButton != null) {
            searchButton.setText(messageBundle.getString(
                    "viewer.utilityPane.search.tab.title"));
        }
        if (rootTreeNode != null) {
            resetTree();
            // set title
            String docTitle = getDocumentTitle();
            rootTreeNode.setUserObject(docTitle);
            rootTreeNode.setAllowsChildren(true);
        }
        if (findMessage != null) {
            findMessage.setText("");
            findMessage.setVisible(false);
        }
        if (progressBar != null) {
            progressBar.setVisible(false);
        }
        // check length of document to see if we can keep all the page text memory and do live searches
        if (searchTextField != null && searchTextField.getDocument() != null) {
            searchTextField.getDocument().removeDocumentListener(this);
            if (document != null && document.getNumberOfPages() < maxPagesForLiveSearch) {
                searchTextField.getDocument().addDocumentListener(this);
            }
        }
    }

    @Override
    public void disposeDocument() {
        searchTextTask = null;
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        // build the supporting tree objects
        rootTreeNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootTreeNode);

        // root text node which all entry fall under,  pages or no pages.
        textTreeNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.search.tree.text.title"), true);
        formsTreeNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.search.tree.forms.title"), true);
        commentsTreeNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.search.tree.markup.title"), true);
        outlinesTreeNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.search.tree.outlines.title"), true);
        destinationsTreeNode = new DefaultMutableTreeNode(
                messageBundle.getString("viewer.utilityPane.search.tree.destinations.title"), true);

        // build and customize the JTree
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setExpandsSelectedPaths(true);
        tree.setShowsRootHandles(true);
        tree.setScrollsOnExpand(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(this);
        tree.addMouseListener(new AnnotationNodeSelectionListener());

        // set look and feel to match outline style
        tree.setCellRenderer(new AnnotationCellRender());

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(150, 75));

        // search input field
        searchTextField = new JTextField("", 15);
        searchTextField.addActionListener(this);

        // setup search progress bar
        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        findMessage = new JLabel(messageBundle.getString("viewer.utilityPane.search.searching.msg"));
        findMessage.setVisible(false);

        // setup search button
        searchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.search.searchButton.label"));
        searchButton.addActionListener(this);

        // clear search button
        clearSearchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.search.clearSearchButton.label"));
        clearSearchButton.addActionListener(this);

        // apply default preferences
        Preferences preferences = controller.getPropertiesManager().getPreferences();
        String iconSize = preferences.get(ViewerPropertiesManager.PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE);
        boolean isRegex = preferences.getBoolean(PROPERTY_SEARCH_PANEL_REGEX_ENABLED, true);
        boolean isWholeWord = preferences.getBoolean(PROPERTY_SEARCH_PANEL_WHOLE_WORDS_ENABLED, false);
        boolean isCaseSensitive = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CASE_SENSITIVE_ENABLED, false);
        boolean isCumulative = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CUMULATIVE_ENABLED, false);

        boolean isText = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_TEXT_ENABLED, true);
        boolean isComments = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_COMMENTS_ENABLED, false);
        boolean isDestinations = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_DEST_ENABLED, false);
        boolean isOutlines = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_OUTLINES_ENABLED, false);

        boolean isShowPages = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, true);

        // search options check boxes.
        // search option check boxes.
        searchFilterButton = new SearchFilterButton(this, controller, "viewer.utilityPane.markupAnnotation.toolbar.filter.filterButton.tooltip");
        searchFilterButton.getShowPagesCheckbox().addActionListener(this);

        // Build search GUI
        GridBagLayout layout = new GridBagLayout();
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 1, 1, 5);

        // content Panel
        JPanel searchPanel = new JPanel(layout);
        this.setLayout(new BorderLayout());
        this.add(searchPanel);

        // add the search input field
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, searchTextField, 0, 1, 1, 1);

        // add start/stop search button
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(1, 1, 1, 1);
        // add filter button and make sure height matches search button
        searchFilterButton.setPreferredSize(
                new Dimension(searchFilterButton.getPreferredSize().width, searchButton.getPreferredSize().height));
        addGB(searchPanel, searchFilterButton, 1, 1, 1, 1);
        addGB(searchPanel, searchButton, 2, 1, 1, 1);

        // add clear search button
        addGB(searchPanel, clearSearchButton, 2, 2, 1, 1);

        // Add Results label
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.fill = GridBagConstraints.NONE;

        // add the lit to scroll pane
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(searchPanel, scrollPane, 0, 4, 3, 1);

        // add find message
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.EAST;
        findMessage.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
        addGB(searchPanel, findMessage, 0, 5, 3, 1);

        // add progress
        constraints.insets = new Insets(5, 5, 1, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, progressBar, 0, 6, 3, 1);

        // finally add our nodes for the tree
        insertSectionNodes();
    }

    public void setVisible(boolean flag) {
        // try and get searchText focus
        super.setVisible(flag);
        if (this.isShowing()) {
            searchTextField.requestFocus(true);
        }
    }

    public void requestFocus() {
        super.requestFocus();
        // try and get searchText focus
        searchTextField.requestFocus();
    }

    // Listen for selected tree items
    public void valueChanged(TreeSelectionEvent e) {
        // jump to the page stored in the JTree
        if (tree.getLastSelectedPathComponent() != null) {
            DefaultMutableTreeNode selectedNode = ((DefaultMutableTreeNode)
                    tree.getLastSelectedPathComponent());
            if (selectedNode.getUserObject() instanceof FindEntry) {
                // get the find entry and navigate to the page.
                FindEntry tmp = (FindEntry) selectedNode.getUserObject();
                if (controller != null) {
                    try {
                        int pageIndex = tmp.getPageNumber();
                        WordText wordText = tmp.getWordText();
                        // navigate to the word.
                        controller.getDocumentSearchController().showWord(pageIndex, wordText);
                        // clear the current cursor and set it to this word
                        PageText pageText = controller.getDocument().getPageViewText(pageIndex);
                        pageText.clearHighlightedCursor();
                        // move the cursor so we can easily show hits on the page with f3
                        controller.getDocumentSearchController().setCurrentPage(pageIndex);
                        // find the word in the current pageText, object may be stale.
                        if (wordText != null) {
                            wordText = pageText.find(wordText);
                            if (wordText != null) {
                                wordText.setHasHighlightCursor(true);
                                wordText.setHighlightCursor(true);
                                // update the selection model.
                                DocumentSearchController searchController = controller.getDocumentSearchController();
                                searchController.setCurrentSearchHit(pageIndex, wordText);
                            }
                        }
                    } catch (InterruptedException e1) {
                        logger.finer("Page text retrieval interrupted.");
                    }
                }
            } else if (selectedNode instanceof AnnotationTreeNode) {
                AnnotationTreeNode formNode = (AnnotationTreeNode) selectedNode;
                Annotation annotation = formNode.getAnnotation();
                PageComponentSelector.SelectAnnotationComponent(controller, annotation, false);
            } else if (selectedNode instanceof OutlineItemTreeNode) {
                controller.followOutlineItem((OutlineItemTreeNode) selectedNode);
            } else if (selectedNode instanceof NameTreeNode) {
                controller.followDestinationItem((NameTreeNode) selectedNode);
            }
        }
    }

    /**
     * Adds a new node item to the treeModel.
     *
     * @param textResult     search result
     * @param searchTextTask search task conducting the gui build.
     */
    public void addFoundTextEntry(SearchTextTask.TextResult textResult, SearchTextTask searchTextTask) {
        String title = textResult.getNodeText();
        int pageNumber = textResult.getCurrentPage();
        List<LineText> textResults = textResult.getLineItems();
        // add the new results entry.
        if ((textResults != null) && (textResults.size() > 0)) {
            DefaultMutableTreeNode parentNode;
            // insert parent page number note.
            if (searchTextTask.isShowPages() && lastTextNodePageIndex != pageNumber) {
                parentNode = new DefaultMutableTreeNode(
                        new FindEntry(title, pageNumber, null), true);
                treeModel.insertNodeInto(parentNode, textTreeNode, textTreeNode.getChildCount());
            } else {
                parentNode = textTreeNode;
            }
            // add the hit entries.
            for (LineText currentText : textResults) {
                addObject(parentNode,
                        new DefaultMutableTreeNode(
                                new FindEntry(generateResultPreview(
                                        currentText.getWords()), pageNumber, currentText.getWords()),
                                false), false);
            }

            // expand the root node, we only do this once.
            if (lastTextNodePageIndex == -1) {
                tree.expandPath(new TreePath(textTreeNode.getPath()));
            }

            lastTextNodePageIndex = pageNumber;

        }
    }

    public void addFoundFormsEntry(SearchTextTask.FormsResult formsResult, SearchTextTask searchTextTask) {
        final String title = formsResult.getNodeText();
        final int pageNumber = formsResult.getCurrentPage();
        final List<TextWidgetAnnotation> formResults = formsResult.getWidgets();
        // add the new results entry.
        if ((formResults != null) && (!formResults.isEmpty())) {
            final DefaultMutableTreeNode parentNode;
            // insert parent page number note.
            if (searchTextTask.isShowPages() && lastTextNodePageIndex != pageNumber) {
                parentNode = new DefaultMutableTreeNode(
                        new FindEntry(title, pageNumber, null), true);
                treeModel.insertNodeInto(parentNode, formsTreeNode, formsTreeNode.getChildCount());
            } else {
                parentNode = formsTreeNode;
            }
            // add the hit entries.
            for (final TextWidgetAnnotation textWidgetAnnotation : formResults) {
                final AnnotationTreeNode annotationTreeNode = new AnnotationTreeNode(textWidgetAnnotation,
                        messageBundle, searchTextTask.getSearchPattern(), searchTextTask.isCaseSensitive());
                treeModel.insertNodeInto(annotationTreeNode, parentNode, parentNode.getChildCount());
            }

            // expand the root node, we only do this once.
            if (lastTextNodePageIndex == -1) {
                tree.expandPath(new TreePath(formsTreeNode.getPath()));
            }

            lastTextNodePageIndex = pageNumber;

        }
    }

    public void addFoundCommentEntry(SearchTextTask.CommentsResult comment, SearchTextTask searchTextTask) {
        if (comment != null) {
            int pageNumber = comment.getCurrentPage();
            DefaultMutableTreeNode parentNode;
            if (searchTextTask.isShowPages() && lastCommentNodePageIndex != pageNumber) {
                parentNode = new DefaultMutableTreeNode(
                        new FindEntry(comment.getNodeText(), pageNumber, null), true);
                treeModel.insertNodeInto(parentNode, commentsTreeNode, commentsTreeNode.getChildCount());
            } else {
                parentNode = commentsTreeNode;
            }
            // add the hits to the tree node.
            List<MarkupAnnotation> annotations = comment.getMarkupAnnotations();
            for (MarkupAnnotation annotation : annotations) {
                AnnotationTreeNode annotationTreeNode =
                        new AnnotationTreeNode(annotation, messageBundle, searchTextTask.getSearchPattern(),
                                searchTextTask.isCaseSensitive());
                treeModel.insertNodeInto(annotationTreeNode, parentNode, parentNode.getChildCount());
            }

            // expand the root node, we only do this once.
            if (lastCommentNodePageIndex == -1) {
                tree.expandPath(new TreePath(commentsTreeNode.getPath()));
            }
            lastCommentNodePageIndex = pageNumber;

        }
    }

    public void addFoundOutlineEntry(SearchTextTask.OutlineResult outlineResult, SearchTextTask searchTextTask) {
        List<OutlineItem> outlineItems = outlineResult.getOutlinesMatches();
        for (OutlineItem outlineItem : outlineItems) {
            OutlineItemTreeNode outlineItemTreeNode =
                    new OutlineItemTreeNode(outlineItem, searchTextTask.getSearchPattern(),
                            searchTextTask.isCaseSensitive());
            treeModel.insertNodeInto(outlineItemTreeNode, outlinesTreeNode, outlinesTreeNode.getChildCount());
        }
        tree.expandPath(new TreePath(outlinesTreeNode.getPath()));
    }

    public void addFoundDestinationEntry(SearchTextTask.DestinationsResult destinationResult,
                                         SearchTextTask searchTextTask) {
        List<DestinationResult> destinationResults = destinationResult.getDestinationsResult();
        for (DestinationResult nameNode : destinationResults) {
            NameTreeNode nameTreeNode =
                    new NameTreeNode(new LiteralStringObject(nameNode.getName()), nameNode.getValue(),
                            searchTextTask.getSearchPattern(), searchTextTask.isCaseSensitive());
            treeModel.insertNodeInto(nameTreeNode, destinationsTreeNode, destinationsTreeNode.getChildCount());
        }
        tree.expandPath(new TreePath(destinationsTreeNode.getPath()));
    }

    /**
     * Utility for adding a tree node.
     *
     * @param parent          parent to add the node too.
     * @param childNode       node to add.
     * @param shouldBeVisible true indicates an auto scroll to bottom of page
     */
    private void addObject(DefaultMutableTreeNode parent,
                           DefaultMutableTreeNode childNode,
                           boolean shouldBeVisible) {
        if (parent == null) {
            parent = rootTreeNode;
        }
        //It is key to invoke this on the TreeModel, and NOT DefaultMutableTreeNode
        treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
        //Make sure the user can see the lovely new node.
        if (shouldBeVisible) {
            tree.scrollPathToVisible(new TreePath(childNode.getPath()));
        }
    }

    /**
     * Generate the results preview label where the hit word is bolded using
     * html markup.
     *
     * @param allText text to unravel into a string.
     * @return styled html text.
     */
    private static String generateResultPreview(List<WordText> allText) {
        StringBuilder toReturn = new StringBuilder(HTML_TAG_START);
        for (WordText currentText : allText) {
            if (currentText.isHighlighted()) {
                toReturn.append(" ");
                toReturn.append(BOLD_TAG_START);
                toReturn.append(currentText.getText());
                toReturn.append(BOLD_TAG_END);
                toReturn.append(" ");
            } else {
                toReturn.append(currentText.getText());
            }
        }
        toReturn.append(HTML_TAG_END);
        return toReturn.toString();
    }

    /**
     * Reset the tree for a new document or a new search.
     */
    private void resetTree() {
        tree.setSelectionPath(null);
        // rest node count
        lastTextNodePageIndex = lastCommentNodePageIndex = -1;

        // clean up the children
        textTreeNode.removeAllChildren();
        formsTreeNode.removeAllChildren();
        commentsTreeNode.removeAllChildren();
        outlinesTreeNode.removeAllChildren();
        destinationsTreeNode.removeAllChildren();
        // re-insert the nodes as they may have changed
        rootTreeNode.removeAllChildren();
        insertSectionNodes();

        treeModel.nodeStructureChanged(rootTreeNode);
    }

    private void insertSectionNodes() {
        if (searchFilterButton.isText()) {
            rootTreeNode.insert(textTreeNode, rootTreeNode.getChildCount());
        }
        if (searchFilterButton.isForms()) {
            rootTreeNode.insert(formsTreeNode, rootTreeNode.getChildCount());
        }
        if (searchFilterButton.isComments()) {
            rootTreeNode.insert(commentsTreeNode, rootTreeNode.getChildCount());
        }
        if (searchFilterButton.isOutlines()) {
            rootTreeNode.insert(outlinesTreeNode, rootTreeNode.getChildCount());
        }
        if (searchFilterButton.isDestinations()) {
            rootTreeNode.insert(destinationsTreeNode, rootTreeNode.getChildCount());
        }
    }

    /**
     * Utility for getting the doucment title.
     *
     * @return document title, if non title then a simple search results
     * label is returned;
     */
    private String getDocumentTitle() {
        String documentTitle = null;
        Document document = controller.getDocument();
        if (document != null && document.getInfo() != null) {
            documentTitle = document.getInfo().getTitle();
        }

        if ((documentTitle == null) || (documentTitle.trim().length() == 0)) {
            return null;
        }

        return documentTitle;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        stopSearch();
        resetTree();
        startSearch();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        stopSearch();
        resetTree();
        startSearch();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        stopSearch();
        resetTree();
        startSearch();
    }

    @Override
    public void notifySearchFiltersChanged() {

    }

    private void stopSearch() {
        if (searchTextTask != null) {
            searchTextTask.cancel(true);
        }

        searchFilterButton.setEnabled(true);
    }

    private void startSearch() {

        // update gui components
        findMessage.setVisible(true);
        progressBar.setVisible(true);

        // clean the previous results and repaint the tree
        resetTree();

        // reset high light states.
        controller.getDocumentSearchController().clearAllSearchHighlight();
        controller.getDocumentViewController().getViewContainer().repaint();

        // do a quick check to make sure we have valida expression.
        if (searchFilterButton.isRegex()) {
            try {
                Pattern.compile(searchTextField.getText());
            } catch (PatternSyntaxException e) {
                // log and show the error in the status label.
                logger.warning("Error processing search pattern syntax");
                findMessage.setText(e.getMessage());
                return;
            }
        }

        // start a new search text task
        searchTextTask = searchFilterButton.getSearchTask(this, controller, searchTextField.getText());

        // set state of search button
        searchButton.setText(messageBundle.getString("viewer.utilityPane.search.stopButton.label"));
        searchFilterButton.setEnabled(false);

        // start the task and the timer
        searchTextTask.execute();
    }

    private void clearSearch() {
        stopSearch();
        // clear input
        searchTextField.setText("");
        // clear the tree.
        resetTree();
        // reset high light states.
        controller.getDocumentSearchController().clearAllSearchHighlight();
        controller.getDocumentViewController().getViewContainer().repaint();
    }

    private void showAllNodePages() {
        showNodePages(textTreeNode);
        showNodePages(formsTreeNode);
        showNodePages(commentsTreeNode);
    }

    private void showNodePages(DefaultMutableTreeNode node) {
        if (node != null && node.getChildCount() > 0) {
            DefaultMutableTreeNode currentChild; // the current node we're handling
            DefaultMutableTreeNode storedChildParent = null; // the newest page node we're adding to
            int newPageNumber; // page number of the current result node
            int storedPageNumber = -1; // the page number of the node we're adding to
            int storedResultCount = 0; // the count of results that are on the storedPageNumber
            Object[] messageArguments; // arguments used for formatting the labels

            // Loop through the results tree
            for (int i = 0; i < node.getChildCount(); i++) {
                currentChild = (DefaultMutableTreeNode) node.getChildAt(i);

                // Ensure we have a FindEntry object
                if (currentChild.getUserObject() instanceof FindEntry ||
                        currentChild instanceof AnnotationTreeNode) {
                    if (currentChild.getUserObject() instanceof FindEntry) {
                        newPageNumber = ((FindEntry) currentChild.getUserObject()).getPageNumber();
                    } else {//if (currentChild instanceof AnnotationTreeNode){
                        newPageNumber = ((AnnotationTreeNode) currentChild).getAnnotation().getPageIndex();
                    }

                    // Check if the page number for the current node matches the stored number
                    // If it does we will want to add the node to the existing page node,
                    //  otherwise we'll want to create a new page node and start adding to that
                    if (storedPageNumber == newPageNumber) {
                        storedResultCount++;

                        if (storedChildParent != null) {
                            // Remove the old parentless child from the tree
                            treeModel.removeNodeFromParent(currentChild);

                            // Add the child back to the new page node
                            storedChildParent.add(currentChild);
                            currentChild.setParent(storedChildParent);

                            // Reduce the loop count by one since we moved a node from the root to a page node
                            i--;
                        }
                    } else {
                        // Update the label of the page node, so that the result count is correct
                        if (storedChildParent != null) {
                            messageArguments = new Object[]{
                                    String.valueOf(storedPageNumber + 1),
                                    storedResultCount, storedResultCount};
                            storedChildParent.setUserObject(
                                    new FindEntry(searchResultMessageForm.format(messageArguments),
                                            storedPageNumber, null));
                        }

                        // Reset the stored variables
                        storedPageNumber = newPageNumber;
                        storedResultCount = 1;

                        treeModel.removeNodeFromParent(currentChild);

                        // Create a new page node and move the current leaf to it
                        messageArguments = new Object[]{
                                String.valueOf(storedPageNumber + 1),
                                storedResultCount, storedResultCount};
                        storedChildParent = new DefaultMutableTreeNode(
                                new FindEntry(searchResultMessageForm.format(messageArguments),
                                        storedPageNumber, null),
                                true);
                        storedChildParent.add(currentChild);
                        currentChild.setParent(storedChildParent);

                        // Put the new page node into the overall tree
                        treeModel.insertNodeInto(storedChildParent, node, i);
                    }
                }
            }
        }
    }

    private void hideAllNodePages() {
        hideNodePages(textTreeNode);
        hideNodePages(formsTreeNode);
        hideNodePages(commentsTreeNode);
    }

    private void hideNodePages(DefaultMutableTreeNode node) {
        if ((node != null) && (node.getChildCount() > 0)) {
            // Now add the children back into the tree, this time without parent nodes
            DefaultMutableTreeNode currentChild;
            int rootChildCount = node.getChildCount();
            // Loop through all children page nodes and explode the children out into leafs under the root node
            // Then we'll remove the parent nodes so we're just left with leafs under the root
            for (int i = 0; i < rootChildCount; i++) {
                // get the page label
                currentChild = (DefaultMutableTreeNode) node.getChildAt(0);
                if (currentChild.getChildCount() > 0) {
                    // Get any subchildren and reinsert them as plain leafs on the root
                    // We need to wrap the user object in a new mutable tree node to stop any conflicts with parent indexes
                    for (int j = 0, max = currentChild.getChildCount(); j < max; j++) {
                        treeModel.insertNodeInto(
                                (DefaultMutableTreeNode) currentChild.getChildAt(0),
                                node, node.getChildCount());
                    }
                }
                treeModel.removeNodeFromParent(currentChild);
            }
        }
    }

    /**
     * Two main actions are handle here, search and clear search.
     *
     * @param event awt action event.
     */
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        // Start/ stop a search
        if (source == searchTextField || source == searchButton) {
            startSearch();
        } else if (source == clearSearchButton) {
            clearSearch();
        } else if (source == searchFilterButton.getShowPagesCheckbox()) {
            if (event.getSource() != null) {
                if (searchFilterButton.isShowPages()) {
                    showAllNodePages();
                } else {
                    hideAllNodePages();
                }
            }
        }
    }

    /**
     * Gridbag constructor helper
     *
     * @param panel     parent adding component too.
     * @param component component to add to grid
     * @param x         row
     * @param y         col
     * @param rowSpan   rowspane of field
     * @param colSpan   colspane of field.
     */
    private void addGB(JPanel panel, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        panel.add(component, constraints);
    }

    /**
     * Uitility for createing the searchable dialog message format.
     *
     * @return reuseable message format.
     */
    public MessageFormat setupSearchResultMessageForm() {
        MessageFormat messageForm =
                new MessageFormat(messageBundle.getString(
                        "viewer.utilityPane.search.result.msg"));
        double[] pageLimits = {0, 1, 2};
        String[] resultsStrings = {
                messageBundle.getString(
                        "viewer.utilityPane.search.result.moreFile.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.result.oneFile.msg"),
                messageBundle.getString(
                        "viewer.utilityPane.search.result.moreFile.msg")
        };
        ChoiceFormat resultsChoiceForm = new ChoiceFormat(pageLimits,
                resultsStrings);

        Format[] formats = {null, resultsChoiceForm};
        messageForm.setFormats(formats);
        return messageForm;
    }

    /**
     * Uitility for createing the searching message format.
     *
     * @return reuseable message format.
     */
    public MessageFormat setupSearchingMessageForm() {
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

        return messageForm;
    }

    public MessageFormat setupSearchCompletionMessageForm() {
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
        return messageForm;
    }

    public void updateProgressControls(String message) {
        progressBar.setValue(searchTextTask.getCurrent());
        if (message != null) {
            findMessage.setText(message);
        }
        // update the text when the search is completed
        if (searchTextTask.isDone() || searchTextTask.isCancelled()) {
            // update search status
            findMessage.setText(message);
            // update buttons states.
            searchButton.setText(messageBundle.getString("viewer.utilityPane.search.searchButton.label"));
            //resetTree();
            searchFilterButton.setEnabled(true);

            // update progress bar then hide it.
            progressBar.setValue(progressBar.getMinimum());
            progressBar.setVisible(false);
        }
    }

    /**
     * An Entry objects represents the found pages
     */
    @SuppressWarnings("serial")
    static
    class FindEntry extends DefaultMutableTreeNode {

        // The text to be displayed on the screen for this item.
        final String title;

        // The destination to be displayed when this item is activated
        final int pageNumber;

        WordText wordText;

        /**
         * Creates a new instance of a FindEntry.
         *
         * @param title      display title
         * @param pageNumber page number where the hit(s) occured
         */
        FindEntry(String title, int pageNumber, List<WordText> currentText) {
            super();
            this.pageNumber = pageNumber;
            this.title = title;
            setUserObject(title);
            // store the search hit so we can use it's bounds for navigation and cursor painting
            if (currentText != null) {
                for (WordText currentWord : currentText) {
                    if (currentWord.isHighlighted()) {
                        wordText = currentWord;
                        break;
                    }
                }
            }
        }

        /**
         * Get the page number.
         *
         * @return page number
         */
        public int getPageNumber() {
            return pageNumber;
        }

        /**
         * Gets the associated worded marked as highlighted.  Not this object may not be the same as the object
         * currently being painted.
         *
         * @return word marked as search.
         */
        WordText getWordText() {
            return wordText;
        }
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class AnnotationNodeSelectionListener extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof AnnotationTreeNode) {
                    AnnotationTreeNode formNode = (AnnotationTreeNode) node;
                    // on double click, navigate to page and set focus of component.
                    Annotation annotation = formNode.getAnnotation();
                    AnnotationComponent comp =
                            PageComponentSelector.SelectAnnotationComponent(controller, annotation, false);
                    if (comp instanceof MarkupAnnotationComponent) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            // toggle the popup annotations visibility on double click
                            MarkupAnnotationComponent markupAnnotationComponent = (MarkupAnnotationComponent) comp;
                            if (e.getClickCount() == 2) {
                                markupAnnotationComponent.requestFocus();
                            }
                        }
                    }
                } else if (node instanceof OutlineItemTreeNode) {
                    // on a double click we'll set focus to the outline pane and navigate to the node in question
                    if (e.getClickCount() == 2) {
                        SwingController swingController = (SwingController) controller;
                        JTree outlineTree = swingController.getOutlineTree();
                        OutlineItemTreeNode outlineItemTreeNode = (OutlineItemTreeNode) outlineTree.getModel().getRoot();
                        for (int i = 0, max = outlineItemTreeNode.getChildCount(); i < max; i++) {
                            OutlineItemTreeNode outlineNode =
                                    findOutlineTreeNode(outlineItemTreeNode.getChildAt(i), (OutlineItemTreeNode) node);
                            if (outlineNode != null) {
                                swingController.showOutlinePanel(true);
                                TreePath outlinePath = new TreePath(outlineNode.getPath());
                                outlineTree.setSelectionPath(outlinePath);
                                outlineTree.scrollPathToVisible(outlinePath);
                                break;
                            }
                        }
                    }
                } else if (node instanceof NameTreeNode) {
                    // on a double click we'll set focus to the destination tab and expand the tree to the node in question
                    if (e.getClickCount() == 2) {
                        SwingController swingController = (SwingController) controller;
                        Names names = controller.getDocument().getCatalog().getNames();
                        if (names != null && names.getDestsNameTree() != null) {
                            NameTree nameTree = names.getDestsNameTree();
                            DefaultTreeModel destinationModel =
                                    new DefaultTreeModel(new NameTreeNode(nameTree.getRoot(), messageBundle));
                            // try to expand back to the same path
                            NameTreeNode nameTreeNode = (NameTreeNode) node;
                            // find and select a node with the same node.)
                            Enumeration<TreeNode> nodes = ((NameTreeNode) destinationModel.getRoot()).depthFirstEnumeration();
                            while (nodes.hasMoreElements()) {
                                NameTreeNode currentNode = (NameTreeNode) nodes.nextElement();
                                if (currentNode.getName() != null &&
                                        currentNode.getName().toString().compareToIgnoreCase(
                                                nameTreeNode.getName().toString()) == 0) {
                                    // expand the node
                                    swingController.showAnnotationDestinationPanel(new TreePath(currentNode.getPath()));
                                }
                            }
                        }
                    }
                }
            }
        }

        private OutlineItemTreeNode findOutlineTreeNode(TreeNode treeNode, OutlineItemTreeNode selectedNode) {
            OutlineItemTreeNode currentNode = (OutlineItemTreeNode) treeNode;
            OutlineItem selectedOutlineItem = selectedNode.getOutlineItem();
            if (selectedOutlineItem.equals(currentNode.getOutlineItem())) {
                return (OutlineItemTreeNode) treeNode;
            }
            for (int i = 0, max = currentNode.getChildCount(); i < max; i++) {
                OutlineItemTreeNode currentChildNode = (OutlineItemTreeNode) currentNode.getChildAt(i);
                if (selectedOutlineItem.equals(currentChildNode.getOutlineItem())) {
                    return currentChildNode;
                }
                if (currentChildNode.getChildCount() > 0) {
                    OutlineItemTreeNode found = findOutlineTreeNode(currentChildNode, selectedNode);
                    if (found != null) {
                        return found;
                    }
                }
            }

            return null;
        }
    }
}
