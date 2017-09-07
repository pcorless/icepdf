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
package org.icepdf.ri.common.properties;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.font.ClearFontCacheWorker;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Panel display of document font properties.   The panel can be used as a fragment in any user interface.
 *
 * @since 6.3
 */
public class FontPanel extends JPanel implements ActionListener {

    // refresh rate of gui elements
    private static final int TIMER_REFRESH = 20;

    // pointer to document which will be searched
    private Document document;

    // list box to hold search results
    private JTree tree;
    private DefaultMutableTreeNode rootTreeNode;
    private DefaultTreeModel treeModel;
    // font look up start on creation, but ok button will kill the the process and close the dialog.
    private JButton okButton;
    // clear and rescan system for fonts and rewrite file.
    private JButton resetFontCacheButton;

    // task to complete in separate thread
    private FindFontsTask findFontsTask;

    // status label for font search
    private JLabel findMessage = new JLabel();

    // time class to manage gui updates
    private Timer timer;

    // flag indicating if search is under way.
    private boolean isFindignFonts;

    // message bundle for internationalization
    private ResourceBundle messageBundle;
    private MessageFormat typeMessageForm;
    private MessageFormat encodingMessageForm;
    private MessageFormat actualTypeMessageForm;
    private MessageFormat actualFontMessageForm;

    // layouts constraint
    private GridBagConstraints constraints;

    public FontPanel(Document doc, SwingController controller, ResourceBundle messageBundle) {

        this.document = doc;
        this.messageBundle = messageBundle;

        // First have to stop any existing font searches,  this shouldn't happen...
        if (timer != null)
            timer.stop();
        if (findFontsTask != null) {
            findFontsTask.stop();
            while (findFontsTask.isCurrentlySearching()) {
                try {
                    Thread.sleep(50L);
                } catch (Exception e) {
                    // intentional
                }
            }
        }
        document = doc;
        if (rootTreeNode != null) {
            resetTree();
            // set title
            String docTitle = getDocumentTitle();
            rootTreeNode.setUserObject(docTitle);
            rootTreeNode.setAllowsChildren(true);
            tree.setRootVisible((docTitle != null));
        }
        // setup the new worker task.
        if (findMessage != null) {
            findMessage.setText("");
        }

        setGui();

        // start the task and the timer
        findFontsTask = new FindFontsTask(this, controller, messageBundle);
        findFontsTask.go();
        timer.start();
        isFindignFonts = true;
    }

    /**
     * Construct the GUI layout.
     */
    private void setGui() {

        typeMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.type.label"));
        encodingMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.encoding.label"));
        actualTypeMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.substitution.type.label"));
        actualFontMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.substitution.path.label"));

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

        // set look and feel to match outline style, consider revising with font type icons.
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setOpenIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setClosedIcon(new ImageIcon(Images.get("page.gif")));
        renderer.setLeafIcon(new ImageIcon(Images.get("page.gif")));
        tree.setCellRenderer(renderer);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(150, 75));

        // setup refresh timer for the font scan progress.
        timer = new Timer(TIMER_REFRESH, new TimerListener());

        // content Panel
        JPanel documentFontPanel = new JPanel(new GridBagLayout());
        documentFontPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.dialog.fonts.border.label"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        setLayout(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 15, 1, 15);

        // add the lit to scroll pane
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(10, 15, 10, 15);
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        addGB(documentFontPanel, scrollPane, 0, 1, 2, 1);

        // add find message
        constraints.insets = new Insets(2, 10, 2, 10);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        findMessage.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
        addGB(documentFontPanel, findMessage, 0, 2, 2, 1);

        resetFontCacheButton = new JButton(messageBundle.getString("viewer.dialog.fonts.resetCache.label"));
        resetFontCacheButton.setToolTipText(messageBundle.getString("viewer.dialog.fonts.resetCache.tip"));
        resetFontCacheButton.addActionListener(this);
        constraints.insets = new Insets(2, 10, 2, 10);
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        addGB(documentFontPanel, resetFontCacheButton, 0, 3, 1, 1);

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(5, 5, 5, 5);
        addGB(this, documentFontPanel, 0, 0, 1, 1);
    }

    /**
     * Adds a new node item to the treeModel.
     *
     * @param font font used to build node properties.
     */
    public void addFoundEntry(Font font) {
        DefaultMutableTreeNode fontNode = new DefaultMutableTreeNode(font.getBaseFont(), true);
        // add type sub node for type
        insertNode(font.getSubType(), typeMessageForm, fontNode);
        // add encoding.
        insertNode(font.getEncoding(), encodingMessageForm, fontNode);
        // add font substitution info.
        if (font.isFontSubstitution() && font.getFont() != null) {
            insertNode(font.getFont().getName(), actualTypeMessageForm, fontNode);
            insertNode(font.getFont().getSource(), actualFontMessageForm, fontNode);
        }
        addObject(rootTreeNode, fontNode);

        // expand the root node, we only do this once.
        tree.expandPath(new TreePath(rootTreeNode));
    }

    /**
     * Utility to aid in the creation of a new font properties node.
     *
     * @param label         label for node.
     * @param messageFormat message formatter
     * @param parent        parent node.
     */
    private void insertNode(Object label, MessageFormat messageFormat, DefaultMutableTreeNode parent) {
        if (label != null) {
            Object[] messageArguments = {label.toString()};
            label = messageFormat.format(messageArguments);
            DefaultMutableTreeNode encodingNode = new DefaultMutableTreeNode(label, true);
            addObject(parent, encodingNode);
        }
    }

    /**
     * Utility for adding a tree node.
     *
     * @param parent    parent to add the node too.
     * @param childNode node to add.
     */
    private void addObject(DefaultMutableTreeNode parent,
                           DefaultMutableTreeNode childNode) {
        if (parent == null) {
            parent = rootTreeNode;
        }
        //It is key to invoke this on the TreeModel, and NOT DefaultMutableTreeNode
        treeModel.insertNodeInto(childNode, parent,
                parent.getChildCount());
    }

    // quick and dirty expand all.
    protected void expandAllNodes() {
        int rowCount = tree.getRowCount();
        int i = 0;
        while (i < rowCount) {
            tree.expandRow(i);
            i += 1;
            rowCount = tree.getRowCount();
        }
    }

    /**
     * Reset the tree, insuring it's empty
     */
    protected void resetTree() {
        tree.setSelectionPath(null);
        rootTreeNode.removeAllChildren();
        treeModel.nodeStructureChanged(rootTreeNode);
    }

    /**
     * Utility for getting the document title.
     *
     * @return document title, if non title then a simple search results
     * label is returned;
     */
    private String getDocumentTitle() {
        String documentTitle = null;
        if (document != null && document.getInfo() != null) {
            documentTitle = document.getInfo().getTitle();
        }
        if ((documentTitle == null) || (documentTitle.trim().length() == 0)) {
            return null;
        }
        return documentTitle;
    }

    /**
     * Two main actions are handle here, search and clear search.
     *
     * @param event awt action event.
     */
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == resetFontCacheButton) {
            // reset the font properties cache.
            resetFontCacheButton.setEnabled(false);
            org.icepdf.ri.common.SwingWorker worker = new ClearFontCacheWorker(resetFontCacheButton);
            worker.start();
        }
    }

    protected void closeWindowOperations() {
        // clean up the timer and worker thread.
        if (timer != null && timer.isRunning()) timer.stop();
        if (findFontsTask != null && findFontsTask.isCurrentlySearching()) findFontsTask.stop();
    }

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
            String s = findFontsTask.getMessage();
            if (s != null) {
                findMessage.setText(s);
            }
            // update the text when the search is completed
            if (findFontsTask.isDone() || !isFindignFonts) {
                // update search status, blank it.
                findMessage.setText("");
                timer.stop();
                findFontsTask.stop();
            }
        }
    }

    /**
     * An Entry objects represents the found pages
     */

    @SuppressWarnings("serial")
    class FontEntry extends DefaultMutableTreeNode {

        // The text to be displayed on the screen for this item.
        String title;

        /**
         * Creates a new instance of a FindEntry.
         *
         * @param title      display title
         * @param pageNumber page number where the hit(s) occured
         */
        FontEntry(String title, int pageNumber) {
            super();
            this.title = title;
            setUserObject(title);
        }

    }

}
