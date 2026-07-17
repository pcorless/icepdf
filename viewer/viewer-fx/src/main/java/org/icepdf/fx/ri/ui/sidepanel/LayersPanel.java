package org.icepdf.fx.ri.ui.sidepanel;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OptionalContent;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.List;
import java.util.logging.Logger;

/**
 * Panel for managing optional content (layers) in the PDF document.
 * Allows toggling layer visibility.
 */
public class LayersPanel extends VBox {

    private static final Logger logger = Logger.getLogger(LayersPanel.class.getName());

    private final ViewerModel model;
    private final TreeView<LayerWrapper> layersTreeView;
    private final Label emptyLabel;

    public LayersPanel(ViewerModel model) {
        this.model = model;
        this.layersTreeView = new TreeView<>();
        this.emptyLabel = new Label("No layers available");

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Configure TreeView
        layersTreeView.setShowRoot(false);
        layersTreeView.setCellFactory(tv -> new LayerTreeCell());
        // Note: TreeView doesn't have setPlaceholder like ListView

        VBox.setVgrow(layersTreeView, Priority.ALWAYS);
        getChildren().add(layersTreeView);
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Load layers if document already exists
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }
    }

    private void handleDocumentChange(Document newDoc) {
        // Clear existing layers
        layersTreeView.setRoot(null);

        if (newDoc != null) {
            try {
                // Get optional content from catalog
                org.icepdf.core.pobjects.Catalog catalog = newDoc.getCatalog();
                if (catalog != null) {
                    OptionalContent optionalContent = catalog.getOptionalContent();
                    if (optionalContent != null && !optionalContent.isEmptyDefinition()) {
                        // Get the ordered list of layers
                        List<Object> order = optionalContent.getOrder();

                        if (order != null && !order.isEmpty()) {
                            TreeItem<LayerWrapper> root = new TreeItem<>(
                                    new LayerWrapper(null, "Layers", true)
                            );

                            // Build layer tree from order list
                            buildLayerTree(root, order, optionalContent);

                            root.setExpanded(true);
                            layersTreeView.setRoot(root);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading layers: " + e.getMessage());
            }
        }
    }

    /**
     * Recursively builds the layer tree from the order list.
     */
    @SuppressWarnings("unchecked")
    private void buildLayerTree(TreeItem<LayerWrapper> parent, List<Object> items,
                                OptionalContent optionalContent) {
        for (Object item : items) {
            if (item instanceof org.icepdf.core.pobjects.Reference) {
                // It's a reference to an OptionalContentGroup
                org.icepdf.core.pobjects.OptionalContentGroup ocg =
                        optionalContent.getOCGs((org.icepdf.core.pobjects.Reference) item);
                if (ocg != null) {
                    String name = ocg.getName();
                    boolean visible = optionalContent.isVisible(ocg);

                    TreeItem<LayerWrapper> layerItem = new TreeItem<>(
                            new LayerWrapper(ocg, name != null ? name : "(Unnamed)", visible)
                    );
                    parent.getChildren().add(layerItem);
                }
            } else if (item instanceof List) {
                // It's a nested list (possibly with a label as first element)
                List<?> nestedList = (List<?>) item;
                if (!nestedList.isEmpty()) {
                    Object first = nestedList.get(0);
                    if (first instanceof String || first instanceof org.icepdf.core.pobjects.StringObject) {
                        // First element is a label
                        String label = first.toString();
                        TreeItem<LayerWrapper> labelItem = new TreeItem<>(
                                new LayerWrapper(null, label, true)
                        );
                        parent.getChildren().add(labelItem);

                        // Process remaining items under this label
                        if (nestedList.size() > 1) {
                            buildLayerTree(labelItem, (List<Object>) nestedList.subList(1, nestedList.size()),
                                    optionalContent);
                        }
                    } else {
                        // No label, just nested items
                        buildLayerTree(parent, (List<Object>) nestedList, optionalContent);
                    }
                }
            }
        }
    }

    /**
     * Wrapper class for layer items.
     */
    private static class LayerWrapper {
        private final org.icepdf.core.pobjects.OptionalContentGroup layer;
        private final String name;
        private boolean visible;

        public LayerWrapper(org.icepdf.core.pobjects.OptionalContentGroup layer, String name, boolean visible) {
            this.layer = layer;
            this.name = name;
            this.visible = visible;
        }

        public org.icepdf.core.pobjects.OptionalContentGroup getLayer() {
            return layer;
        }

        public String getName() {
            return name;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
            if (layer != null) {
                layer.setVisible(visible);
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Custom TreeCell with checkboxes for layer visibility.
     */
    private class LayerTreeCell extends TreeCell<LayerWrapper> {

        private final CheckBox checkBox;

        public LayerTreeCell() {
            this.checkBox = new CheckBox();
            checkBox.setOnAction(e -> {
                LayerWrapper wrapper = getItem();
                if (wrapper != null) {
                    wrapper.setVisible(checkBox.isSelected());
                    // TODO: Trigger page refresh to show/hide layer
                    logger.info("Layer '" + wrapper.getName() + "' visibility: " + checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(LayerWrapper item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                checkBox.setText(item.getName());
                checkBox.setSelected(item.isVisible());
                setGraphic(checkBox);
            }
        }
    }
}

