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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.fx.view.PdfView;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A strip of page thumbnails for a {@link PdfView}: the current page is selected and kept in view,
 * and clicking a thumbnail goes to its page.  Only the visible cells are rendered (the list is
 * virtual), in the background, at the screen's resolution and in the view's rotation; they are
 * drawn again when the view's content changes ({@link PdfView#refreshContent()}).
 */
public class ThumbnailPanel extends BorderPane {

    private final PdfView view;
    private final ListView<Integer> list = new ListView<>();
    private final ObservableList<Integer> pages = FXCollections.observableArrayList();
    private final PageThumbnails thumbnails = new PageThumbnails();
    private final DoubleProperty thumbnailWidth = new SimpleDoubleProperty(this, "thumbnailWidth", 120);
    // the document the list was filled for; the view's changes first, while the old items are still listed.
    private Document document;
    // true while the selection follows the view, so the listener doesn't navigate back.
    private boolean syncing;

    public ThumbnailPanel(PdfView view) {
        this.view = view;
        getStyleClass().add("thumbnail-panel");
        list.setItems(pages);
        list.setCellFactory(l -> new ThumbnailCell());
        list.setFocusTraversable(true);
        setCenter(list);
        setMinWidth(80);
        setPrefWidth(180);

        view.documentProperty().addListener((obs, was, now) -> onDocument(now));
        view.rotationProperty().addListener((obs, was, now) -> list.refresh());
        view.pageBoundaryProperty().addListener((obs, was, now) -> list.refresh());
        view.contentVersionProperty().addListener((obs, was, now) -> {
            thumbnails.invalidate();
            list.refresh();
        });
        thumbnailWidth.addListener((obs, was, now) -> list.refresh());
        view.currentPageIndexProperty().addListener((obs, was, now) -> follow(now.intValue()));
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
            if (!syncing && now != null && now != view.getCurrentPageIndex()) view.setCurrentPageIndex(now);
        });
        onDocument(view.getDocument());
    }

    /** Thumbnail width in logical pixels; 120 by default. */
    public final DoubleProperty thumbnailWidthProperty() {
        return thumbnailWidth;
    }

    /** Stops the background renderer; call when the panel is thrown away. */
    public void dispose() {
        thumbnails.shutdown();
    }

    private void onDocument(Document document) {
        this.document = document;
        thumbnails.setDocument(document);
        int count = document != null ? document.getNumberOfPages() : 0;
        pages.setAll(IntStream.range(0, count).boxed().collect(Collectors.toList()));
        if (count > 0) follow(view.getCurrentPageIndex());
    }

    /** Selects the view's current page and scrolls it into the strip if it isn't showing. */
    private void follow(int page) {
        if (document != view.getDocument() || page < 0 || page >= pages.size()) return;
        syncing = true;
        try {
            list.getSelectionModel().select(page);
        } finally {
            syncing = false;
        }
        if (!isShowing(page)) list.scrollTo(Math.max(0, page - 1));
    }

    private boolean isShowing(int page) {
        for (javafx.scene.Node node : list.lookupAll(".list-cell")) {
            if (node instanceof ThumbnailCell cell && !cell.isEmpty() && cell.getIndex() == page
                    && cell.isVisible() && cell.localToScene(cell.getBoundsInLocal()) != null) {
                javafx.geometry.Bounds inList = list.sceneToLocal(cell.localToScene(cell.getBoundsInLocal()));
                return inList.getMinY() >= 0 && inList.getMaxY() <= list.getHeight();
            }
        }
        return false;
    }

    private double outputScale() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        return window != null && window.getOutputScaleX() > 0 ? window.getOutputScaleX() : 1;
    }

    /** A page's thumbnail, in a frame sized from the page's dimensions while it renders. */
    private final class ThumbnailCell extends ListCell<Integer> {
        private final ImageView image = new ImageView();
        private final StackPane frame = new StackPane(image);
        private final Label number = new Label();
        private final VBox box = new VBox(4, frame, number);

        ThumbnailCell() {
            box.setAlignment(Pos.CENTER);
            frame.getStyleClass().add("thumbnail-frame");
            frame.setStyle("-fx-background-color: white; -fx-border-color: #b0b0b0; -fx-effect: "
                    + "dropshadow(gaussian, rgba(0,0,0,0.25), 4, 0, 1, 1);");
            frame.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
            image.setPreserveRatio(true);
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Integer page, boolean empty) {
            super.updateItem(page, empty);
            if (empty || page == null || document == null || page >= document.getNumberOfPages()) {
                setGraphic(null);
                setText(null);
                image.setImage(null);
                return;
            }
            float rotation = (float) view.getRotation();
            double width = thumbnailWidth.get();
            PDimension size = document.getPageDimension(page, rotation);
            double height = size.getWidth() > 0 ? width * size.getHeight() / size.getWidth() : width;
            frame.setPrefSize(width, height);
            image.setFitWidth(width);
            number.setText(String.valueOf(page + 1));
            int widthPx = (int) Math.round(width * outputScale());
            Image ready = thumbnails.get(page, widthPx, rotation, view.getPageBoundary(), done -> {
                if (getItem() != null && getItem().equals(page)) image.setImage(done);
            });
            image.setImage(ready);
            setGraphic(box);
            setText(null);
        }
    }
}
