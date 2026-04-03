package org.icepdf.fx.ri.ui.sidepanel;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Panel displaying page thumbnails for quick navigation.
 * Features lazy loading, caching, and click-to-navigate functionality.
 */
public class ThumbnailsPanel extends VBox {

    private static final Logger logger = Logger.getLogger(ThumbnailsPanel.class.getName());

    private static final int THUMBNAIL_WIDTH = 120;
    private static final int THUMBNAIL_HEIGHT = 160;
    private static final float THUMBNAIL_SCALE = 0.15f; // 15% of actual page size

    private final ViewerModel model;
    private final ListView<Integer> thumbnailListView;
    private final Map<Integer, Image> thumbnailCache;
    private final Label emptyLabel;

    public ThumbnailsPanel(ViewerModel model) {
        this.model = model;
        this.thumbnailCache = new HashMap<>();
        this.thumbnailListView = new ListView<>();
        this.emptyLabel = new Label("No document loaded");

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Configure ListView
        thumbnailListView.setCellFactory(lv -> new ThumbnailCell());
        thumbnailListView.setPlaceholder(emptyLabel);

        // Select page on click
        thumbnailListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && newValue > 0) {
                        model.currentPage.set(newValue);
                    }
                }
        );

        VBox.setVgrow(thumbnailListView, Priority.ALWAYS);
        getChildren().add(thumbnailListView);
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(oldDoc, newDoc);
        });

        // Highlight current page
        model.currentPage.addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.intValue() > 0) {
                // Select the current page in the list without triggering navigation
                Platform.runLater(() -> {
                    thumbnailListView.getSelectionModel().select(newValue.intValue());
                    thumbnailListView.scrollTo(newValue.intValue() - 1);
                });
            }
        });
    }

    private void handleDocumentChange(Document oldDoc, Document newDoc) {
        // Clear cache and list
        thumbnailCache.clear();
        thumbnailListView.getItems().clear();

        if (newDoc != null) {
            int pageCount = newDoc.getNumberOfPages();
            // Populate list with page numbers (1-based)
            for (int i = 1; i <= pageCount; i++) {
                thumbnailListView.getItems().add(i);
            }
        }
    }

    /**
     * Custom ListCell for displaying page thumbnails.
     */
    private class ThumbnailCell extends ListCell<Integer> {

        private final VBox container;
        private final ImageView imageView;
        private final Label pageLabel;
        private final ProgressIndicator loadingIndicator;

        public ThumbnailCell() {
            container = new VBox(5);
            container.setAlignment(Pos.CENTER);
            container.setPadding(new Insets(5));

            imageView = new ImageView();
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(THUMBNAIL_WIDTH);
            imageView.setFitHeight(THUMBNAIL_HEIGHT);
            imageView.setSmooth(true);

            pageLabel = new Label();
            pageLabel.setStyle("-fx-font-weight: bold;");

            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setMaxSize(40, 40);

            container.getChildren().addAll(imageView, pageLabel);

            // Style for selected cell
            selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    container.setStyle("-fx-background-color: -fx-accent; -fx-background-radius: 5;");
                    pageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                } else {
                    container.setStyle("-fx-background-color: transparent;");
                    pageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");
                }
            });
        }

        @Override
        protected void updateItem(Integer pageNum, boolean empty) {
            super.updateItem(pageNum, empty);

            if (empty || pageNum == null) {
                setGraphic(null);
            } else {
                pageLabel.setText("Page " + pageNum);

                // Check cache first
                Image cachedImage = thumbnailCache.get(pageNum);
                if (cachedImage != null) {
                    imageView.setImage(cachedImage);
                    container.getChildren().setAll(imageView, pageLabel);
                } else {
                    // Show loading indicator
                    container.getChildren().setAll(loadingIndicator, pageLabel);
                    loadThumbnailAsync(pageNum);
                }

                setGraphic(container);
            }
        }

        /**
         * Loads a thumbnail asynchronously to avoid blocking the UI thread.
         */
        private void loadThumbnailAsync(int pageNum) {
            Document doc = model.document.get();
            if (doc == null) return;

            Task<Image> loadTask = new Task<>() {
                @Override
                protected Image call() throws Exception {
                    try {
                        // Get the page (0-based index)
                        Page page = doc.getPageTree().getPage(pageNum - 1);
                        if (page == null) return null;

                        // Initialize the page if needed
                        page.init();

                        // Calculate dimensions based on page size
                        PDimension pageSize = page.getSize(Page.BOUNDARY_CROPBOX, 0f, 1.0f);
                        float pageWidth = (float) pageSize.getWidth();
                        float pageHeight = (float) pageSize.getHeight();

                        // Scale to thumbnail size while maintaining aspect ratio
                        float scale = Math.min(
                                THUMBNAIL_WIDTH / pageWidth,
                                THUMBNAIL_HEIGHT / pageHeight
                        );

                        int thumbWidth = (int) (pageWidth * scale);
                        int thumbHeight = (int) (pageHeight * scale);

                        // Render the page to a BufferedImage
                        BufferedImage bufferedImage = new BufferedImage(
                                thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB
                        );
                        java.awt.Graphics2D g2d = bufferedImage.createGraphics();

                        // Set rendering hints for better quality
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                                java.awt.RenderingHints.VALUE_RENDER_QUALITY);

                        // Paint the page
                        page.paint(g2d, GraphicsRenderingHints.PRINT,
                                Page.BOUNDARY_CROPBOX, 0, scale);

                        g2d.dispose();

                        // Convert BufferedImage to JavaFX Image
                        Image fxImage = convertToFxImage(bufferedImage);

                        return fxImage;
                    } catch (Exception e) {
                        logger.warning("Failed to load thumbnail for page " + pageNum + ": " + e.getMessage());
                        return null;
                    }
                }
            };

            loadTask.setOnSucceeded(event -> {
                Image image = loadTask.getValue();
                if (image != null) {
                    thumbnailCache.put(pageNum, image);
                    imageView.setImage(image);
                    container.getChildren().setAll(imageView, pageLabel);
                } else {
                    // Show error placeholder
                    Label errorLabel = new Label("Failed to load");
                    errorLabel.setStyle("-fx-text-fill: red;");
                    container.getChildren().setAll(errorLabel, pageLabel);
                }
            });

            loadTask.setOnFailed(event -> {
                logger.warning("Thumbnail load task failed: " + loadTask.getException());
                Label errorLabel = new Label("Error");
                errorLabel.setStyle("-fx-text-fill: red;");
                container.getChildren().setAll(errorLabel, pageLabel);
            });

            // Run in background thread
            new Thread(loadTask).start();
        }

        /**
         * Converts AWT BufferedImage to JavaFX Image.
         */
        private Image convertToFxImage(BufferedImage bufferedImage) {
            javafx.scene.image.WritableImage fxImage = new javafx.scene.image.WritableImage(
                    bufferedImage.getWidth(), bufferedImage.getHeight()
            );
            javafx.scene.image.PixelWriter pixelWriter = fxImage.getPixelWriter();

            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                for (int x = 0; x < bufferedImage.getWidth(); x++) {
                    int rgb = bufferedImage.getRGB(x, y);
                    pixelWriter.setArgb(x, y, rgb);
                }
            }

            return fxImage;
        }
    }
}

