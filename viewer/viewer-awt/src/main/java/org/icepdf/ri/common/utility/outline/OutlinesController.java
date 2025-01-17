package org.icepdf.ri.common.utility.outline;

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.NamedDestinations;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.util.BareBonesBrowserLaunch;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;

public class OutlinesController implements TreeModelListener, TreeSelectionListener {

    private final SwingController viewerController;
    private final JTree outlinesTree;

    // likelyi going to set this to match the document permissions for encryption permissions
    private boolean editable = true;


    public OutlinesController(final SwingController viewerController, final JTree outlinesTree) {
        this.viewerController = viewerController;
        this.outlinesTree = outlinesTree;
        outlinesTree.addTreeSelectionListener(this);
    }

    // todo add editable tree node text

    @Override
    public void treeNodesChanged(TreeModelEvent treeModelEvent) {
        System.out.println("treeNodesChanged " + treeModelEvent.toString());
    }

    @Override
    public void treeNodesInserted(TreeModelEvent treeModelEvent) {
        System.out.println("treeNodesInserted " + treeModelEvent.toString());
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent treeModelEvent) {
        System.out.println("treeNodesRemoved " + treeModelEvent.toString());
    }

    @Override
    public void treeStructureChanged(TreeModelEvent treeModelEvent) {
        System.out.println("treeStructureChanged " + treeModelEvent.toString());
    }

    public void followOutlineItem(OutlineItemTreeNode node) {
        OutlineItem o = node.getOutlineItem();
        followOutlineItem(o);
    }

    /**
     * Disposes controller clearing resources.
     */
    public void dispose() {
        if (outlinesTree.getModel() != null) {
            outlinesTree.getModel().removeTreeModelListener(this);
        }
    }

    /**
     * When the user selects an OutlineItem from the Outlines (Bookmarks) JTree,
     * this displays the relevant target portion of the PDF Document
     *
     * @param outlineItem navigate to this outline item destination.
     */
    private void followOutlineItem(OutlineItem outlineItem) {
        if (outlineItem == null)
            return;
        int oldTool = viewerController.getDocumentViewToolMode();
        try {

            // set hour glass
            outlinesTree.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            viewerController.setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_WAIT);

            // capture the action if no destination is found and point to the
            // actions destination information
            Destination dest = outlineItem.getDest();
            if (outlineItem.getAction() != null) {
                Action action = outlineItem.getAction();
                if (action instanceof GoToAction) {
                    dest = ((GoToAction) action).getDestination();
                } else if (action instanceof URIAction) {
                    BareBonesBrowserLaunch.openURL(
                            ((URIAction) action).getURI());
                } else {
                    Library library = action.getLibrary();
                    DictionaryEntries entries = action.getEntries();
                    dest = new Destination(library, library.getObject(entries, Destination.D_KEY));
                }
            } else if (dest.getNamedDestination() != null) {
                // building the namedDestination tree can be very time-consuming, so we need
                // update the icons accordingly.
                NamedDestinations namedDestinations = viewerController.getDocument().getCatalog().getDestinations();
                if (namedDestinations != null) {
                    dest = namedDestinations.getDestination(dest.getNamedDestination());
                }
            }

            // Process the destination information
            if (dest == null)
                return;

            // let the document view controller resolve the destination
            viewerController.getDocumentViewController().setDestinationTarget(dest);
        } finally {
            // set the icon back to the pointer
            viewerController.setDisplayTool(oldTool);
            outlinesTree.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    public void valueChanged(TreeSelectionEvent e) {
        if (outlinesTree == null)
            return;
        TreePath treePath = outlinesTree.getSelectionPath();
        if (treePath == null)
            return;
        OutlineItemTreeNode node = (OutlineItemTreeNode) treePath.getLastPathComponent();
        followOutlineItem(node);

    }
}
