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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.image.PixelFormat;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What a PDF portfolio shows instead of its cover sheet (the "your viewer can't open this" page):
 * a tile per embedded file, with the first page of each embedded PDF as its thumbnail.  Double-click
 * a tile to open it.
 */
public final class CollectionView extends ScrollPane {

    private static final Logger logger = Logger.getLogger(CollectionView.class.getName());
    private static final int THUMBNAIL_WIDTH = 160;

    private final TilePane tiles = new TilePane(16, 16);
    private final ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "icepdf-fx-collection");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private Consumer<Attachment> onOpen;
    private int version;

    public CollectionView() {
        getStyleClass().add("collection-view");
        tiles.setPadding(new Insets(16));
        tiles.setPrefTileWidth(THUMBNAIL_WIDTH + 24);
        tiles.setAlignment(Pos.TOP_LEFT);
        setContent(tiles);
        setFitToWidth(true);
    }

    /** True when the document is a portfolio with at least one embedded PDF, as the Swing viewer decides. */
    public static boolean isCollection(Document document) {
        if (document == null) return false;
        Catalog catalog = document.getCatalog();
        if (catalog == null || catalog.getCollection() == null) return false;
        return Attachment.of(document).stream().anyMatch(Attachment::isPdf);
    }

    /** Called with the attachment a tile was double-clicked on. */
    public void setOnOpen(Consumer<Attachment> onOpen) {
        this.onOpen = onOpen;
    }

    public void setDocument(Document document) {
        version++;
        tiles.getChildren().clear();
        if (document == null) return;
        int current = version;
        for (Attachment attachment : Attachment.of(document)) {
            ImageView picture = new ImageView();
            picture.setFitWidth(THUMBNAIL_WIDTH);
            picture.setPreserveRatio(true);
            StackPane frame = new StackPane(picture);
            frame.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_WIDTH * 1.3);
            frame.setStyle("-fx-background-color: white; -fx-border-color: #00000030;");
            Label name = new Label(attachment.name());
            name.setWrapText(true);
            name.setMaxWidth(THUMBNAIL_WIDTH);
            name.setAlignment(Pos.CENTER);
            VBox tile = new VBox(6, frame, name);
            tile.setAlignment(Pos.TOP_CENTER);
            tile.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && onOpen != null) onOpen.accept(attachment);
            });
            tiles.getChildren().add(tile);
            if (attachment.isPdf()) {
                pool.submit(() -> {
                    Image image = thumbnail(attachment);
                    if (image != null) Platform.runLater(() -> {
                        if (current == version) picture.setImage(image);
                    });
                });
            }
        }
    }

    public void dispose() {
        version++;
        pool.shutdownNow();
    }

    /** Renders the first page of an embedded PDF; null when it can't be read. */
    private static Image thumbnail(Attachment attachment) {
        Document embedded = new Document();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            attachment.writeTo(bytes);
            embedded.setInputStream(new ByteArrayInputStream(bytes.toByteArray()), attachment.name());
            if (embedded.getNumberOfPages() < 1) return null;
            Page page = embedded.getPageTree().getPage(0);
            page.init();
            var size = page.getSize(Page.BOUNDARY_CROPBOX, 0f, 1f);
            float zoom = (float) (THUMBNAIL_WIDTH * 2 / Math.max(1, size.getWidth()));
            int width = Math.max(1, (int) Math.ceil(size.getWidth() * zoom));
            int height = Math.max(1, (int) Math.ceil(size.getHeight() * zoom));
            BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = buffer.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, zoom, true, false);
            } finally {
                g.dispose();
            }
            int[] pixels = ((java.awt.image.DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
            WritableImage image = new WritableImage(width, height);
            image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), pixels, 0, width);
            return image;
        } catch (Exception e) {
            logger.log(Level.FINE, "No thumbnail for " + attachment.name(), e);
            return null;
        } finally {
            embedded.dispose();
        }
    }
}
