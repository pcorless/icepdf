/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.fx.panels;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.view.PdfView;

import java.util.List;

/**
 * A document's layers (optional content groups, PDF 32000-1 8.11) as a tree of check boxes for a
 * {@link PdfView}: switching one on or off re-renders the view ({@link PdfView#refreshContent()}).
 * Members of a radio-button set switch each other off; labels group layers and have no box.
 */
public class LayersPanel extends BorderPane {

    private final PdfView view;
    private final TreeView<LayerNode> tree = new TreeView<>();
    private final Label empty = new Label("This document has no layers.");
    private final ReadOnlyBooleanWrapper hasLayers = new ReadOnlyBooleanWrapper(this, "hasLayers", false);

    public LayersPanel(PdfView view) {
        this.view = view;
        getStyleClass().add("layers-panel");
        tree.setShowRoot(false);
        tree.setCellFactory(t -> new LayerCell());
        empty.setWrapText(true);
        empty.setPadding(new Insets(8));
        view.documentProperty().addListener((obs, was, now) -> onDocument(now));
        onDocument(view.getDocument());
    }

    /** True when the current document has layers. */
    public final ReadOnlyBooleanProperty hasLayersProperty() {
        return hasLayers.getReadOnlyProperty();
    }

    public final boolean hasLayers() {
        return hasLayers.get();
    }

    private void onDocument(Document document) {
        List<LayerNode> layers = LayerNode.of(document);
        hasLayers.set(!layers.isEmpty());
        TreeItem<LayerNode> root = new TreeItem<>();
        add(root, layers);
        root.setExpanded(true);
        tree.setRoot(root);
        setCenter(layers.isEmpty() ? empty : tree);
    }

    private static void add(TreeItem<LayerNode> parent, List<LayerNode> nodes) {
        for (LayerNode node : nodes) {
            TreeItem<LayerNode> item = new TreeItem<>(node);
            item.setExpanded(true);
            add(item, node.children());
            parent.getChildren().add(item);
        }
    }

    private final class LayerCell extends TreeCell<LayerNode> {
        private final CheckBox box = new CheckBox();

        LayerCell() {
            box.setOnAction(e -> {
                LayerNode node = getItem();
                if (node == null) return;
                node.setVisible(box.isSelected());
                // a radio set may have switched others off: redraw their boxes.
                tree.refresh();
                view.refreshContent();
            });
        }

        @Override
        protected void updateItem(LayerNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (node.isLabel()) {
                setGraphic(null);
                setText(node.name());
                setStyle("-fx-font-weight: bold;");
            } else {
                box.setText(node.name());
                box.setSelected(node.isVisible());
                box.setTooltip(node.isRadio() ? new Tooltip("Only one layer of this set can be on.") : null);
                setText(null);
                setGraphic(box);
                setStyle(null);
            }
        }
    }
}
