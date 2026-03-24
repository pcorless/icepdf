package org.icepdf.fx.ri.viewer;

import javafx.beans.property.*;
import org.icepdf.core.pobjects.Document;

public class ViewerModel {

    // Window management
    public final BooleanProperty useSingleViewerStage = new SimpleBooleanProperty(false);

    // Document properties
    public final ObjectProperty<Document> document;
    public final StringProperty filePath;
    public final StringProperty documentTitle = new SimpleStringProperty("");
    public final LongProperty documentSizeBytes = new SimpleLongProperty(0);

    // Page navigation properties
    public final IntegerProperty currentPage = new SimpleIntegerProperty(1);
    public final IntegerProperty totalPages = new SimpleIntegerProperty(0);

    // View properties
    public final DoubleProperty zoomLevel = new SimpleDoubleProperty(1.0);
    public final DoubleProperty rotationAngle = new SimpleDoubleProperty(0.0);
    public final ObjectProperty<ViewMode> viewMode = new SimpleObjectProperty<>(ViewMode.SINGLE_PAGE);
    public final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FitMode.NONE);

    // zoom factor increment (for zoom in/out commands)
    public final FloatProperty zoomFactorIncrement = new SimpleFloatProperty(0.1f);

    // UI state properties
    public final BooleanProperty leftPanelVisible = new SimpleBooleanProperty(true);
    public final BooleanProperty rightPanelVisible = new SimpleBooleanProperty(false);
    public final BooleanProperty statusBarVisible = new SimpleBooleanProperty(true);
    public final BooleanProperty toolBarVisible = new SimpleBooleanProperty(true);
    public final BooleanProperty menuBarVisible = new SimpleBooleanProperty(true);
    public final IntegerProperty selectedSidePanelIndex = new SimpleIntegerProperty(0);

    // toolbar disabled state (legacy - kept for compatibility)
    public final BooleanProperty toolbarDisabled;

    // Operation properties
    public final StringProperty statusMessage = new SimpleStringProperty("");
    public final DoubleProperty loadingProgress = new SimpleDoubleProperty(0.0);
    public final BooleanProperty isLoading = new SimpleBooleanProperty(false);

    // Selection properties
    public final StringProperty selectedText = new SimpleStringProperty("");

    // View mode enums
    public enum ViewMode {
        SINGLE_PAGE,
        CONTINUOUS,
        FACING_PAGES,
        CONTINUOUS_FACING
    }

    public enum FitMode {
        NONE,
        FIT_WIDTH,
        FIT_HEIGHT,
        FIT_PAGE,
        ACTUAL_SIZE
    }

    public ViewerModel() {
        document = new SimpleObjectProperty<>(null);
        filePath = new SimpleStringProperty(null);
        toolbarDisabled = new SimpleBooleanProperty(true);

        // Update toolbar disabled when document changes
        document.addListener((observable, oldValue, newValue) -> {
            // todo, not needed can just check if document is null
            toolbarDisabled.set(newValue == null);
            if (newValue != null) {
                totalPages.set(newValue.getNumberOfPages());
                currentPage.set(1);
            } else {
                totalPages.set(0);
                currentPage.set(1);
            }
        });
    }
}
