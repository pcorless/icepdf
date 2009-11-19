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
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.ri.common;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.SearchTextTask;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ResourceBundle;

/**
 * This class is the GUI component for the SearchTextTask.  This panel can be
 * added to a utility panel.  The GUI allows users to
 * type in a search string and displays a JList of all the pages that have results.
 * Each list item can be selected, and when selected, the viewer will show the
 * corresponding page.
 *
 * @since 1.1
 */
public class SearchPanel extends JPanel implements ActionListener,
        TreeSelectionListener {

    // markup for search context.
    private static final String HTML_TAG_START = "<html>";
    private static final String HTML_TAG_END = "</html>";
    private static final String BOLD_TAG_START = "<b>";
    private static final String BOLD_TAG_END = "</b>";

    // layouts constraint
    private GridBagConstraints constraints;

    // input for a search pattern
    private JTextField searchTextField;

    // pointer to document which will be searched
    private Document document;

    private SwingController controller;

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

    // search option check boxes.
    private JCheckBox caseSensitiveCheckbox;
    private JCheckBox wholeWordCheckbox;
    private JCheckBox cumulativeCheckbox;

    // page index of the last added node.
    private int lastNodePageIndex;

    // show progress of search
    protected JProgressBar progressBar;

    // task to complete in separate thread
    protected SearchTextTask searchTextTask;

    // status label for search
    protected JLabel findMessage;

    // time class to manage gui updates
    protected Timer timer;

    // refresh rate of gui elements
    private static final int ONE_SECOND = 1000;

    // flag indicating if search is under way.
    private boolean isSearching = false;

    // message bundle for internationalization
    ResourceBundle messageBundle;

    /**
     * Create a new instance of SearchPanel.
     *
     * @param controller root SwingController
     */
    public SearchPanel(SwingController controller) {
        super(true);
        setFocusable(true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();
        setGui();
        setDocument(controller.getDocument());
    }

    public void setDocument(Document doc) {
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

        document = doc;
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
            rootTreeNode.setUserObject(getDocumentTitle());
            rootTreeNode.setAllowsChildren(true);
        }
        if (findMessage != null) {
            findMessage.setText("");
            findMessage.setVisible(false);
        }
        if (progressBar != null) {
            progressBar.setVisible(false);
        }
        isSearching = false;
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        /**
         * Setup GUI objects
         */

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
        scrollPane.setPreferredSize(new Dimension(150, 250));

        // search Label
        JLabel searchLabel = new JLabel(messageBundle.getString(
                "viewer.utilityPane.search.searchText.label"));

        // search input field
        searchTextField = new JTextField("", 15);
        searchTextField.addActionListener(this);

        // setup search progress bar
        progressBar = new JProgressBar(0, 1);
        progressBar.setValue(0);
        progressBar.setVisible(false);
        findMessage = new JLabel(
                messageBundle.getString(
                        "viewer.utilityPane.search.searching.msg"));
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

        // search options check boxes.
        wholeWordCheckbox = new JCheckBox(messageBundle.getString(
                "viewer.utilityPane.search.wholeWordCheckbox.label"));
        caseSensitiveCheckbox = new JCheckBox(messageBundle.getString(
                "viewer.utilityPane.search.caseSenstiveCheckbox.label"));
        cumulativeCheckbox = new JCheckBox(messageBundle.getString(
                "viewer.utilityPane.search.cumlitiveCheckbox.label"));

        /**
         * Build search GUI
         */

        GridBagLayout layout = new GridBagLayout();
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 5, 1, 5);

        // content Panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setLayout(layout);
        this.setLayout(new BorderLayout());
        this.add(searchPanel, BorderLayout.NORTH);

        // add the search label
        addGB(searchPanel, searchLabel, 0, 0, 2, 1);

        // add the search input field
        constraints.insets = new Insets(1, 1, 1, 5);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, searchTextField, 0, 1, 2, 1);

        // add start/stop search button
        constraints.insets = new Insets(1, 1, 1, 5);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.EAST;
        addGB(searchPanel, searchButton, 0, 2, 1, 1);

        // add clear search button
        constraints.insets = new Insets(1, 1, 1, 5);
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.REMAINDER;
        addGB(searchPanel, clearSearchButton, 1, 2, 1, 1);

        // add case sensitive button
        constraints.insets = new Insets(5, 1, 1, 5);
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, caseSensitiveCheckbox, 0, 3, 2, 1);

        // add whole word checkbox
        constraints.insets = new Insets(1, 1, 1, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, wholeWordCheckbox, 0, 4, 2, 1);

        // add cumulative checkbox
        constraints.insets = new Insets(1, 1, 1, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, cumulativeCheckbox, 0, 5, 2, 1);

        // Add Results label
        constraints.insets = new Insets(10, 5, 1, 5);
        constraints.fill = GridBagConstraints.NONE;
        addGB(searchPanel, new JLabel(messageBundle.getString(
                "viewer.utilityPane.search.results.label")),
                0, 6, 2, 1);

        // add the lit to scroll pane
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(1, 5, 1, 5);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(searchPanel, scrollPane, 0, 7, 2, 1);

        // add find message
        constraints.insets = new Insets(1, 5, 1, 5);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        findMessage.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
        addGB(searchPanel, findMessage, 0, 8, 2, 1);

        // add progress
        constraints.insets = new Insets(5, 5, 1, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addGB(searchPanel, progressBar, 0, 9, 2, 1);

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

    public void dispose() {
        document = null;
        controller = null;
        searchTextTask = null;
        timer = null;
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
                    int oldTool = controller.getDocumentViewToolMode();
                    try {
                        controller.setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_WAIT);
                        controller.showPage(tmp.getPageNumber());
                    }
                    finally {
                        controller.setDisplayTool(oldTool);
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
     */
    public void addFoundEntry(String title, int pageNumber,
                              List<LineText> textResults) {
        // add the new results entry.
        if ((textResults != null) && (textResults.size() > 0)) {
            DefaultMutableTreeNode parentNode;
            // insert parent page number note. 
            if (lastNodePageIndex != pageNumber) {
                parentNode = new DefaultMutableTreeNode(
                        new FindEntry(title, pageNumber), true);
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
                                        currentText.getWords()), pageNumber),
                                false), false);
            }

            // expand the root node, we only do this once.
            if (lastNodePageIndex == -1) {
                tree.expandPath(new TreePath(rootTreeNode));
            }

            lastNodePageIndex = pageNumber;

        }
    }

    /**
     * Utility for adding a tree node.
     * @param parent parent to add the node too.
     * @param childNode node to add.
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
     * @param allText text to unravel into a string.
     * @return styled html text.
     */
    private static String generateResultPreview(List<WordText> allText) {
        StringBuffer toReturn = new StringBuffer(HTML_TAG_START);
        for (WordText currentText : allText) {
            if (currentText.isHighlighted()) {
                toReturn.append(BOLD_TAG_START);
                toReturn.append(currentText.getText());
                toReturn.append(BOLD_TAG_END);
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
    protected void resetTree() {
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
     *         label is returned;
     */
    private String getDocumentTitle() {
        String documentTitle = null;
        if (document != null && document.getInfo() != null) {
            documentTitle = document.getInfo().getTitle();
        }
        if (documentTitle == null) {
            documentTitle = messageBundle.getString(
                    "viewer.utilityPane.search.results.label");
        }

        return documentTitle;
    }

    /**
     * Two main actions are handle here, search and clear search.
     *
     * @param event awt action event.
     */
    public void actionPerformed(ActionEvent event) {

        Object source = event.getSource();
        // Start/ stop a search
        if (searchTextField.getText().length() > 0 &&
                (source == searchTextField || source == searchButton)) {

            if (!timer.isRunning()) {
                // update gui components
                findMessage.setVisible(true);
                progressBar.setVisible(true);

                // clean the previous results and repaint the tree
                resetTree();

                // start a new search text task
                searchTextTask = new SearchTextTask(this,
                        controller,
                        searchTextField.getText(),
                        wholeWordCheckbox.isSelected(),
                        caseSensitiveCheckbox.isSelected(),
                        cumulativeCheckbox.isSelected(),
                        false,
                        messageBundle);
                isSearching = true;

                // set state of search button
                searchButton.setText(messageBundle.getString(
                        "viewer.utilityPane.search.stopButton.label"));
                clearSearchButton.setEnabled(false);
                caseSensitiveCheckbox.setEnabled(false);
                wholeWordCheckbox.setEnabled(false);
                cumulativeCheckbox.setEnabled(false);

                // start the task and the timer
                searchTextTask.go();
                timer.start();
            } else {
                isSearching = false;
                clearSearchButton.setEnabled(true);
                caseSensitiveCheckbox.setEnabled(true);
                wholeWordCheckbox.setEnabled(true);
                cumulativeCheckbox.setEnabled(true);
            }
        } else if (source == clearSearchButton) {
            // clear input
            searchTextField.setText("");

            // clear the tree.
            resetTree();

            // reset high light states.
            controller.getDocumentSearchController().clearAllSearchHighlight();
            controller.getDocumentViewController().getViewContainer().repaint();
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

                // update progress bar then hide it.
                progressBar.setValue(progressBar.getMinimum());
                progressBar.setVisible(false);
            }
        }
    }

    /**
     * An Entry objects represents the found pages
     */
    class FindEntry extends DefaultMutableTreeNode {

        // The text to be displayed on the screen for this item.
        String title;

        // The destination to be displayed when this item is activated
        int pageNumber;

        /**
         * Creates a new instance of a FindEntry.
         *
         * @param title of found entry
         */
        FindEntry(String title) {
            super();
            this.pageNumber = 0;
            this.title = title;
            setUserObject(title);
        }

        /**
         * Creates a new instance of a FindEntry.
         *
         * @param title      display title
         * @param pageNumber page number where the hit(s) occured
         */
        FindEntry(String title, int pageNumber) {
            super();
            this.pageNumber = pageNumber;
            this.title = title;
            setUserObject(title);
        }

        /**
         * Get the page number.
         *
         * @return page number
         */
        public int getPageNumber() {
            return pageNumber;
        }
    }
}
