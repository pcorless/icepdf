package org.icepdf.ri.common.fonts;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AbstractWorkerPanel;
import org.icepdf.ri.util.AbstractTask;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.text.MessageFormat;

/**
 * FontHandlerPanel handles the building of the document's font property tree.
 */
@SuppressWarnings("serial")
public class FontHandlerPanel extends AbstractWorkerPanel {

    private MessageFormat typeMessageForm;
    private MessageFormat encodingMessageForm;
    private MessageFormat actualTypeMessageForm;
    private MessageFormat actualFontMessageForm;

    // task to complete in separate thread
    private AbstractTask<FindFontsTask> findFontTask;

    public FontHandlerPanel(SwingController controller) {
        super(controller);
        typeMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.type.label"));
        encodingMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.encoding.label"));
        actualTypeMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.substitution.type.label"));
        actualFontMessageForm =
                new MessageFormat(messageBundle.getString("viewer.dialog.fonts.info.substitution.path.label"));
    }

    public void buildUI() {
        super.buildUI();
        // setup validation progress bar and status label
        buildProgressBar();
    }

    @Override
    public void setDocument(Document document) {
        super.setDocument(document);

        // update root node's title with document's title.
        rootNodeLabel = getDocumentTitle();
        cellRenderer = new FontCellRender();

        // setup the new worker task.
        if (progressLabel != null) {
            progressLabel.setText("");
        }

        // construct the annotation tree of nodes.
        buildUI();

        // start the task and the timer
        findFontTask = new FindFontsTask(this, controller, messageBundle);
        workerTask = findFontTask;
        findFontTask.getTask().go();
        timer.start();
    }

    @Override
    protected void buildWorkerTaskUI() {

    }

    @Override
    public void dispose() {
        super.dispose();
        if (findFontTask != null && findFontTask.isCurrentlyRunning()) findFontTask.stop();
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

    /**
     * Utility for getting the document title.
     *
     * @return document title, if non title then a simple search results
     * label is returned;
     */
    private String getDocumentTitle() {
        String documentTitle = null;
        if (currentDocument != null && currentDocument.getInfo() != null) {
            documentTitle = currentDocument.getInfo().getTitle();
        }
        if ((documentTitle == null) || (documentTitle.trim().length() == 0)) {
            return null;
        }
        return documentTitle;
    }
}
