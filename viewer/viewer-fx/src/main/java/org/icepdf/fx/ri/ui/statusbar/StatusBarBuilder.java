package org.icepdf.fx.ri.ui.statusbar;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Builder;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Builder for the status bar.
 * Displays current page, total pages, zoom level, status messages, and loading progress.
 */
public class StatusBarBuilder implements Builder<HBox> {

    private final ViewerModel model;

    public StatusBarBuilder(ViewerModel model) {
        this.model = model;
    }

    @Override
    public HBox build() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border; -fx-border-width:" +
                " 1 0 0 0;");

        // Status message label (left side, takes available space)
        Label statusLabel = new Label();
        statusLabel.textProperty().bind(model.statusMessage);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Page information
        Label pageInfo = new Label();
        pageInfo.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                        () -> {
                            if (model.document.get() == null) {
                                return "No document";
                            }
                            return String.format("Page %d of %d",
                                    model.currentPage.get(),
                                    model.totalPages.get());
                        },
                        model.document,
                        model.currentPage,
                        model.totalPages
                )
        );
        pageInfo.setMinWidth(120);

        // Zoom information
        Label zoomInfo = new Label();
        zoomInfo.textProperty().bind(
                javafx.beans.binding.Bindings.concat(
                        model.zoomLevel.multiply(100).asString("%.0f"),
                        "%"
                )
        );
        zoomInfo.setMinWidth(60);

        // Document title (optional, middle section)
        Label documentTitle = new Label();
        documentTitle.textProperty().bind(model.documentTitle);
        documentTitle.setStyle("-fx-font-weight: bold;");
        documentTitle.visibleProperty().bind(model.documentTitle.isNotEmpty());
        documentTitle.managedProperty().bind(documentTitle.visibleProperty());

        // Loading progress indicator
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(100);
        progressBar.progressProperty().bind(model.loadingProgress);
        progressBar.visibleProperty().bind(model.isLoading);
        progressBar.managedProperty().bind(progressBar.visibleProperty());

        // Assembly
        statusBar.getChildren().addAll(
                statusLabel,
                createSpacer(),
                documentTitle,
                createSeparator(),
                pageInfo,
                createSeparator(),
                zoomInfo,
                progressBar
        );

        return statusBar;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.SOMETIMES);
        return spacer;
    }

    private Separator createSeparator() {
        return new Separator(Orientation.VERTICAL);
    }
}

