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
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.Outlines;
import org.icepdf.fx.view.PdfView;

/**
 * A document's bookmarks (its outline, PDF 32000-1 12.3.3) as a tree for a {@link PdfView}.
 * Children are read when an item is first expanded, and items open as the document asks (a positive
 * {@code /Count}).  Choosing an item performs it: its destination or GoTo action navigates the view,
 * any other action goes to the view's {@link PdfView#onAnnotationActionProperty() action handler}.
 */
public class OutlinePanel extends BorderPane {

    private final PdfView view;
    private final TreeView<OutlineItem> tree = new TreeView<>();
    private final Label empty = new Label("This document has no bookmarks.");
    private final ReadOnlyBooleanWrapper hasOutline = new ReadOnlyBooleanWrapper(this, "hasOutline", false);

    private static final Name COUNT_KEY = new Name("Count");

    public OutlinePanel(PdfView view) {
        this.view = view;
        getStyleClass().add("outline-panel");
        tree.setShowRoot(false);
        tree.setCellFactory(t -> {
            TreeCell<OutlineItem> cell = new TreeCell<>() {
                @Override
                protected void updateItem(OutlineItem item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : title(item));
                }
            };
            // a click performs the item - except on the disclosure arrow, which only opens or closes it.
            cell.setOnMouseClicked(e -> {
                if (cell.isEmpty() || cell.getItem() == null) return;
                javafx.scene.Node arrow = cell.getDisclosureNode();
                for (javafx.scene.Node n = e.getPickResult().getIntersectedNode(); n != null && n != cell; n = n.getParent()) {
                    if (n == arrow) return;
                }
                perform(cell.getItem());
            });
            return cell;
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                TreeItem<OutlineItem> selected = tree.getSelectionModel().getSelectedItem();
                if (selected != null) perform(selected.getValue());
            }
        });
        empty.setWrapText(true);
        view.documentProperty().addListener((obs, was, now) -> onDocument(now));
        onDocument(view.getDocument());
    }

    /** True when the current document has bookmarks; an application can hide the panel otherwise. */
    public final ReadOnlyBooleanProperty hasOutlineProperty() {
        return hasOutline.getReadOnlyProperty();
    }

    public final boolean hasOutline() {
        return hasOutline.get();
    }

    /** Expands or collapses every bookmark (expanding reads the whole outline). */
    public void setAllExpanded(boolean expanded) {
        if (tree.getRoot() != null) setExpanded(tree.getRoot().getChildren(), expanded);
    }

    private static void setExpanded(ObservableList<TreeItem<OutlineItem>> items, boolean expanded) {
        for (TreeItem<OutlineItem> item : items) {
            if (!item.isLeaf()) {
                item.setExpanded(expanded);
                setExpanded(item.getChildren(), expanded);
            }
        }
    }

    private void onDocument(Document document) {
        Outlines outlines = document != null ? document.getCatalog().getOutlines() : null;
        OutlineItem root = outlines != null ? outlines.getRootOutlineItem() : null;
        boolean any = root != null && root.getSubItemCount() > 0;
        hasOutline.set(any);
        tree.setRoot(any ? new OutlineTreeItem(root) : null);
        if (any) tree.getRoot().setExpanded(true);
        setCenter(any ? tree : empty);
    }

    private void perform(OutlineItem item) {
        if (item == null) return;
        if (item.getDest() != null) {
            view.navigateTo(item.getDest());
        } else if (item.getAction() != null) {
            view.performAction(item.getAction());
        }
    }

    private static String title(OutlineItem item) {
        String title = item.getTitle();
        return title != null ? title.replaceAll("[\\r\\n\\t]+", " ").trim() : "";
    }

    /** A bookmark whose children are read the first time they are asked for. */
    private static final class OutlineTreeItem extends TreeItem<OutlineItem> {
        private boolean childrenLoaded;

        OutlineTreeItem(OutlineItem item) {
            super(item);
            // a positive /Count means the item is open (PDF 32000-1 table 153).
            Object count = item.getLibrary().getObject(item.getEntries(), COUNT_KEY);
            if (count instanceof Number && ((Number) count).intValue() > 0 && item.getSubItemCount() > 0) {
                setExpanded(true);
            }
        }

        @Override
        public boolean isLeaf() {
            return getValue().getSubItemCount() == 0;
        }

        @Override
        public ObservableList<TreeItem<OutlineItem>> getChildren() {
            if (!childrenLoaded) {
                childrenLoaded = true;
                OutlineItem item = getValue();
                for (int i = 0, n = item.getSubItemCount(); i < n; i++) {
                    OutlineItem child = item.getSubItem(i);
                    if (child != null) super.getChildren().add(new OutlineTreeItem(child));
                }
            }
            return super.getChildren();
        }
    }
}
