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
package org.icepdf.ri.common.utility.search;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.Defs;
import org.icepdf.ri.common.DropDownButton;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;
import org.icepdf.ri.util.SearchTextTask;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.icepdf.ri.util.PropertiesManager.*;

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
        TreeSelectionListener, DocumentListener {

    private static final Logger logger =
            Logger.getLogger(SearchPanel.class.toString());

    private static int maxPagesForLiveSearch;

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
    private org.icepdf.ri.common.views.Controller controller;
    private Preferences preferences;

    // tree view of the groups and panels
    //private ResultsTree resultsTree;

    // list box to hold search results
    private JTree tree;
    private DefaultMutableTreeNode rootTreeNode;
    private DefaultTreeModel treeModel;
    // search start button
    private JButton searchButton;
    // clear search
    private JButton clearSearchButton;
    private JCheckBoxMenuItem caseSensitiveCheckbox;
    private JCheckBoxMenuItem wholeWordCheckbox;
    private JCheckBoxMenuItem regexCheckbox;
    private JCheckBoxMenuItem cumulativeCheckbox;
    private JCheckBoxMenuItem showPagesCheckbox;
    private JCheckBoxMenuItem commentsCheckbox;
    private JCheckBoxMenuItem outlinesCheckbox;
    private JCheckBoxMenuItem destinationsCheckbox;
    // page index of the last added node.
    private int lastNodePageIndex;

    // show progress of search
    private JProgressBar progressBar;

    // task to complete in separate thread
    private SearchTextTask searchTextTask;

    // status label for search
    private JLabel findMessage;

    // time class to manage gui updates
    protected Timer timer;

    // refresh rate of gui elements
    private static final int ONE_SECOND = 1000;

    // flag indicating if search is under way.
    private boolean isSearching;

    // message bundle for internationalization
    private ResourceBundle messageBundle;
    private MessageFormat searchResultMessageForm;

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


    @Override
    public void refreshDocumentInstance() {
        // First have to stop any existing search
        if (timer != null)
            timer.stop();
        if (searchTextTask != null) {
            searchTextTask.stop();
            while (searchTextTask.isCurrentlySearching()) {
                try {
                    Thread.sleep(50L);
                } catch (Exception e) {
                    // intentional
                }
            }
        }
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
            tree.setRootVisible((docTitle != null));
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
        isSearching = false;
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

        // build and customize the JTree
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setExpandsSelectedPaths(true);
        tree.setShowsRootHandles(true);
        tree.setScrollsOnExpand(true);
        tree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(this);

        // set look and feel to match outline style
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setOpenIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setClosedIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setLeafIcon(new ImageIcon(Images.get("page.gif")));
        tree.setCellRenderer(renderer);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(150, 75));

        // search Label
//        JLabel searchLabel = new JLabel(messageBundle.getString(
//                "viewer.utilityPane.search.searchText.label"));

        // search input field
        searchTextField = new JTextField("", 15);
        searchTextField.addActionListener(this);

        // setup search progress bar
        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        findMessage = new JLabel(messageBundle.getString("viewer.utilityPane.search.searching.msg"));
        findMessage.setVisible(false);
        timer = new Timer(ONE_SECOND, new TimerListener());

        // setup search button
        searchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.search.searchButton.label"));
        searchButton.addActionListener(this);

        // clear search button
        clearSearchButton = new JButton(messageBundle.getString(
                "viewer.utilityPane.search.clearSearchButton.label"));
        clearSearchButton.addActionListener(this);

        // apply default preferences
        preferences = controller.getPropertiesManager().getPreferences();
        String iconSize = preferences.get(PropertiesManager.PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE);
        boolean isRegex = preferences.getBoolean(PROPERTY_SEARCH_PANEL_REGEX_ENABLED, true);
        boolean isWholeWord = preferences.getBoolean(PROPERTY_SEARCH_PANEL_WHOLE_WORDS_ENABLED, false);
        boolean isCaseSensitive = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CASE_SENSITIVE_ENABLED, false);
        boolean isCumulative = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CUMULATIVE_ENABLED, false);

        boolean isComments = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_COMMENTS_ENABLED, false);
        boolean isDestinations = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_DEST_ENABLED, false);
        boolean isOutlines = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_OUTLINES_ENABLED, false);

        boolean isShowPages = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, true);

        // search options check boxes.
        // search option check boxes.
        DropDownButton filterDropDownButton = new DropDownButton(controller, "",
                messageBundle.getString("viewer.utilityPane.markupAnnotation.toolbar.filter.filterButton.tooltip"),
                "filter", iconSize, SwingViewBuilder.buildButtonFont());
        wholeWordCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.wholeWordCheckbox.label"), isWholeWord);
        wholeWordCheckbox.addActionListener(this);
        regexCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.regexCheckbox.label"), isRegex);
        regexCheckbox.addActionListener(this);
        caseSensitiveCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.caseSenstiveCheckbox.label"), isCaseSensitive);
        caseSensitiveCheckbox.addActionListener(this);
        if (isRegex || isWholeWord) {
            regexCheckbox.setEnabled(isRegex);
            wholeWordCheckbox.setEnabled(isWholeWord);
        }
        cumulativeCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.cumlitiveCheckbox.label"), isCumulative);
        cumulativeCheckbox.addActionListener(this);
        commentsCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.comments.label"), isComments);
        commentsCheckbox.addActionListener(this);
        destinationsCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.destinations.label"), isDestinations);
        destinationsCheckbox.addActionListener(this);
        outlinesCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.outlines.label"), isOutlines);
        outlinesCheckbox.addActionListener(this);
        showPagesCheckbox = new JCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.showPagesCheckbox.label"), isShowPages);
        showPagesCheckbox.addActionListener(this);
        filterDropDownButton.add(regexCheckbox);
        filterDropDownButton.add(wholeWordCheckbox);
        filterDropDownButton.add(caseSensitiveCheckbox);
        filterDropDownButton.add(cumulativeCheckbox);
        filterDropDownButton.addSeparator();
        filterDropDownButton.add(commentsCheckbox);
        filterDropDownButton.add(destinationsCheckbox);
        filterDropDownButton.add(outlinesCheckbox);
        filterDropDownButton.addSeparator();
        filterDropDownButton.add(showPagesCheckbox);

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

        // add the search label
//        addGB(searchPanel, searchLabel, 0, 0, 3, 1);

        // add the search input field
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, searchTextField, 0, 1, 2, 1);

        // add start/stop search button
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(1, 1, 1, 1);
        addGB(searchPanel, searchButton, 2, 1, 1, 1);
        // add filter button.
        constraints.weightx = 0;
        addGB(searchPanel, filterDropDownButton, 0, 2, 1, 1);
        // add clear search button
        addGB(searchPanel, clearSearchButton, 2, 2, 1, 1);

        // Add Results label
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.fill = GridBagConstraints.NONE;
//        addGB(searchPanel, new JLabel(messageBundle.getString("viewer.utilityPane.search.results.label")),
//                0, 3, 3, 1);

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
                    } catch (Throwable e1) {
                        logger.finer("Page text retrieval interrupted.");
                    }
                }
            }
        }
    }

    /**
     * Adds a new node item to the treeModel.
     *
     * @param title       display title of tree item
     * @param pageNumber  page number where the hit(s) occured
     * @param textResults list of LineText items that match
     * @param showPages   boolean to display or hide the page node
     */
    public void addFoundTextEntry(String title, int pageNumber,
                                  List<LineText> textResults,
                                  boolean showPages) {
        // add the new results entry.
        if ((textResults != null) && (textResults.size() > 0)) {
            DefaultMutableTreeNode parentNode;
            // insert parent page number note.
            if (showPages && lastNodePageIndex != pageNumber) {
                parentNode = new DefaultMutableTreeNode(
                        new FindEntry(title, pageNumber, null), true);
                treeModel.insertNodeInto(parentNode, rootTreeNode,
                        rootTreeNode.getChildCount());
            } else {
                parentNode = rootTreeNode;
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
            if (lastNodePageIndex == -1) {
                tree.expandPath(new TreePath(rootTreeNode));
            }

            lastNodePageIndex = pageNumber;

        }
    }

    public void addFoundCommentEntry() {
    }

    public void addFoundOutlineEntry() {
    }

    public void addFoundDestinationEntry() {
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
        treeModel.insertNodeInto(childNode, parent,
                parent.getChildCount());
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
        rootTreeNode.removeAllChildren();
        treeModel.nodeStructureChanged(rootTreeNode);
        // rest node count
        lastNodePageIndex = -1;
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
        isSearching = false;
        if (searchTextTask != null) {
            searchTextTask.stop();
            timer.stop();
            resetTree();
        }
        startStopSearch();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        isSearching = false;
        if (searchTextTask != null) {
            searchTextTask.stop();
            timer.stop();
            resetTree();
        }
        startStopSearch();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        isSearching = false;
        if (searchTextTask != null) {
            searchTextTask.stop();
            timer.stop();
            resetTree();
        }
        startStopSearch();
    }

    private void startStopSearch() {
        if (!timer.isRunning()) {
            // update gui components
            findMessage.setVisible(true);
            progressBar.setVisible(true);

            // clean the previous results and repaint the tree
            resetTree();

            // reset high light states.
            controller.getDocumentSearchController().clearAllSearchHighlight();
            controller.getDocumentViewController().getViewContainer().repaint();

            // do a quick check to make sure we have valida expression.
            if (regexCheckbox.isSelected()) {
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
            SearchTextTask.Builder builder = new SearchTextTask.Builder(controller, searchTextField.getText());
            searchTextTask = builder.setSearchPanel(this)
                    .setCaseSensitive(caseSensitiveCheckbox.isSelected())
                    .setWholeWord(wholeWordCheckbox.isSelected())
                    .setCumulative(cumulativeCheckbox.isSelected())
                    .setShowPages(showPagesCheckbox.isSelected())
                    .setRegex(regexCheckbox.isSelected())
                    .setDestinations(destinationsCheckbox.isSelected())
                    .setOutlines(outlinesCheckbox.isSelected())
                    .setComments(commentsCheckbox.isSelected()).build();

            isSearching = true;

            // set state of search button
            searchButton.setText(messageBundle.getString(
                    "viewer.utilityPane.search.stopButton.label"));
            clearSearchButton.setEnabled(false);
            caseSensitiveCheckbox.setEnabled(false);
            wholeWordCheckbox.setEnabled(false);
            cumulativeCheckbox.setEnabled(false);
            showPagesCheckbox.setEnabled(false);

            // start the task and the timer
            searchTextTask.go();
            timer.start();
        } else {
            isSearching = false;
            clearSearchButton.setEnabled(true);
            caseSensitiveCheckbox.setEnabled(true);
            wholeWordCheckbox.setEnabled(true);
            cumulativeCheckbox.setEnabled(true);
            showPagesCheckbox.setEnabled(true);
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
            startStopSearch();
        } else if (source == clearSearchButton) {
            // clear input
            searchTextField.setText("");

            // clear the tree.
            resetTree();

            // reset high light states.
            controller.getDocumentSearchController().clearAllSearchHighlight();
            controller.getDocumentViewController().getViewContainer().repaint();
        } else if (source == regexCheckbox) {
            wholeWordCheckbox.setEnabled(!regexCheckbox.isSelected());
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_REGEX_ENABLED, regexCheckbox.isSelected());
        } else if (source == wholeWordCheckbox) {
            regexCheckbox.setEnabled(!wholeWordCheckbox.isSelected());
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_WHOLE_WORDS_ENABLED, wholeWordCheckbox.isSelected());
        } else if (source == cumulativeCheckbox) {
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_CUMULATIVE_ENABLED, cumulativeCheckbox.isSelected());
        } else if (source == caseSensitiveCheckbox) {
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_CASE_SENSITIVE_ENABLED, caseSensitiveCheckbox.isSelected());
        } else if (source == showPagesCheckbox) {
            if (event.getSource() != null) {
                preferences.putBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, showPagesCheckbox.isSelected());
                // Determine if the user just selected or deselected the Show Pages checkbox
                // If selected we'll want to combine all the leaf results into page nodes containing a series of results
                // Otherwise we'll want to explode the parent/node page folders into basic leafs showing the results
                if (showPagesCheckbox.isSelected()) {
                    if ((rootTreeNode != null) && (rootTreeNode.getChildCount() > 0)) {
                        DefaultMutableTreeNode currentChild; // the current node we're handling
                        DefaultMutableTreeNode storedChildParent = null; // the newest page node we're adding to
                        int newPageNumber; // page number of the current result node
                        int storedPageNumber = -1; // the page number of the node we're adding to
                        int storedResultCount = 0; // the count of results that are on the storedPageNumber
                        Object[] messageArguments; // arguments used for formatting the labels

                        // Loop through the results tree
                        for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                            currentChild = (DefaultMutableTreeNode) rootTreeNode.getChildAt(i);

                            // Ensure we have a FindEntry object
                            if (currentChild.getUserObject() instanceof FindEntry) {
                                newPageNumber = ((FindEntry) currentChild.getUserObject()).getPageNumber();

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
                                    treeModel.insertNodeInto(storedChildParent, rootTreeNode, i);
                                }
                            }
                        }
                    }
                } else {
                    if ((rootTreeNode != null) && (rootTreeNode.getChildCount() > 0)) {
                        // Now add the children back into the tree, this time without parent nodes
                        DefaultMutableTreeNode currentChild;
                        int rootChildCount = rootTreeNode.getChildCount();

                        // Loop through all children page nodes and explode the children out into leafs under the root node
                        // Then we'll remove the parent nodes so we're just left with leafs under the root
                        for (int i = 0; i < rootChildCount; i++) {
                            currentChild = (DefaultMutableTreeNode) rootTreeNode.getChildAt(0);

                            if (currentChild.getChildCount() > 0) {
                                // Get any subchildren and reinsert them as plain leafs on the root
                                // We need to wrap the user object in a new mutable tree node to stop any conflicts with parent indexes
                                for (int j = 0; j < currentChild.getChildCount(); j++) {
                                    treeModel.insertNodeInto(
                                            new DefaultMutableTreeNode(
                                                    ((DefaultMutableTreeNode) currentChild.getChildAt(j)).getUserObject(),
                                                    false),
                                            rootTreeNode, rootTreeNode.getChildCount());
                                }
                            }
                            treeModel.removeNodeFromParent(currentChild);
                        }
                    }
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

    /**
     * The actionPerformed method in this class
     * is called each time the Timer "goes off".
     */
    class TimerListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            progressBar.setValue(searchTextTask.getCurrent());
            String s = searchTextTask.getMessage();
            if (s != null) {
                findMessage.setText(s);
            }
            // update the text when the search is completed
            if (searchTextTask.isDone() || !isSearching) {
                // update search status
                findMessage.setText(searchTextTask.getFinalMessage());
                timer.stop();
                searchTextTask.stop();
                // update buttons states. 
                searchButton.setText(messageBundle.getString("viewer.utilityPane.search.searchButton.label"));
                clearSearchButton.setEnabled(true);
                caseSensitiveCheckbox.setEnabled(true);
                wholeWordCheckbox.setEnabled(true);
                cumulativeCheckbox.setEnabled(true);
                showPagesCheckbox.setEnabled(true);

                // update progress bar then hide it.
                progressBar.setValue(progressBar.getMinimum());
                progressBar.setVisible(false);
            }
        }
    }

    /**
     * An Entry objects represents the found pages
     */
    @SuppressWarnings("serial")
    class FindEntry extends DefaultMutableTreeNode {

        // The text to be displayed on the screen for this item.
        String title;

        // The destination to be displayed when this item is activated
        int pageNumber;

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
}
