package org.icepdf.fx.ri.ui.sidepanel;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.logging.Logger;

/**
 * Panel displaying the document outline (bookmarks) in a tree structure.
 * Allows navigation to destinations by clicking on outline items.
 */
public class OutlinePanel extends VBox {

    private static final Logger logger = Logger.getLogger(OutlinePanel.class.getName());

    private final ViewerModel model;
    private final TreeView<OutlineItemWrapper> outlineTreeView;
    private final Label emptyLabel;

    public OutlinePanel(ViewerModel model) {
        this.model = model;
        this.outlineTreeView = new TreeView<>();
        this.emptyLabel = new Label("No outline available");

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Configure TreeView
        outlineTreeView.setShowRoot(false);
        outlineTreeView.setCellFactory(tv -> new OutlineTreeCell());
        // Note: TreeView doesn't have setPlaceholder like ListView

        // Handle selection
        outlineTreeView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && newValue.getValue() != null) {
                        navigateToOutlineItem(newValue.getValue());
                    }
                }
        );

        VBox.setVgrow(outlineTreeView, Priority.ALWAYS);
        getChildren().add(outlineTreeView);
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Load outline if document already exists
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }
    }

    private void handleDocumentChange(Document newDoc) {
        // Clear existing outline
        outlineTreeView.setRoot(null);

        if (newDoc != null) {
            // Get the document catalog's outlines
            org.icepdf.core.pobjects.Catalog catalog = newDoc.getCatalog();
            if (catalog != null) {
                org.icepdf.core.pobjects.Outlines outlines = catalog.getOutlines();
                if (outlines != null) {
                    OutlineItem rootOutlineItem = outlines.getRootOutlineItem();
                    if (rootOutlineItem != null && !rootOutlineItem.isEmpty()) {
                        TreeItem<OutlineItemWrapper> root = new TreeItem<>(
                                new OutlineItemWrapper(null, "Root")
                        );

                        // Add children of root
                        int subItemCount = rootOutlineItem.getSubItemCount();
                        for (int i = 0; i < subItemCount; i++) {
                            OutlineItem item = rootOutlineItem.getSubItem(i);
                            TreeItem<OutlineItemWrapper> treeItem = createTreeItem(item);
                            if (treeItem != null) {
                                root.getChildren().add(treeItem);
                            }
                        }

                        root.setExpanded(true);
                        outlineTreeView.setRoot(root);
                    }
                }
            }
        }
    }

    /**
     * Recursively creates tree items from outline items.
     */
    private TreeItem<OutlineItemWrapper> createTreeItem(OutlineItem outlineItem) {
        if (outlineItem == null) return null;

        String title = outlineItem.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = "(Untitled)";
        }

        OutlineItemWrapper wrapper = new OutlineItemWrapper(outlineItem, title);
        TreeItem<OutlineItemWrapper> treeItem = new TreeItem<>(wrapper);

        // Process children recursively
        int subItemCount = outlineItem.getSubItemCount();
        if (subItemCount > 0) {
            for (int i = 0; i < subItemCount; i++) {
                OutlineItem child = outlineItem.getSubItem(i);
                TreeItem<OutlineItemWrapper> childItem = createTreeItem(child);
                if (childItem != null) {
                    treeItem.getChildren().add(childItem);
                }
            }
        }

        return treeItem;
    }

    /**
     * Navigates to the destination specified by an outline item.
     */
    private void navigateToOutlineItem(OutlineItemWrapper wrapper) {
        if (wrapper == null || wrapper.getOutlineItem() == null) return;

        OutlineItem item = wrapper.getOutlineItem();

        try {
            // Try to get destination from action or directly
            org.icepdf.core.pobjects.Destination dest = item.getDest();

            if (dest != null) {
                Reference pageRef = dest.getPageReference();
                if (pageRef != null && model.document.get() != null) {
                    // Look up the page number from the reference
                    Document doc = model.document.get();
                    org.icepdf.core.pobjects.PageTree pageTree = doc.getPageTree();
                    int pageNumber = pageTree.getPageNumber(pageRef);

                    if (pageNumber >= 0) {
                        // Convert to 1-based page number
                        model.currentPage.set(pageNumber + 1);
                        logger.fine("Navigated to page " + (pageNumber + 1) + " from outline");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to navigate to outline destination: " + e.getMessage());
        }
    }

    /**
     * Wrapper class for outline items to display in TreeView.
     */
    private static class OutlineItemWrapper {
        private final OutlineItem outlineItem;
        private final String displayTitle;

        public OutlineItemWrapper(OutlineItem outlineItem, String displayTitle) {
            this.outlineItem = outlineItem;
            this.displayTitle = displayTitle;
        }

        public OutlineItem getOutlineItem() {
            return outlineItem;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    /**
     * Custom TreeCell for outline items with icons.
     */
    private class OutlineTreeCell extends TreeCell<OutlineItemWrapper> {

        @Override
        protected void updateItem(OutlineItemWrapper item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDisplayTitle());
                // Could add icons here in the future
                setGraphic(null);
            }
        }
    }
}

