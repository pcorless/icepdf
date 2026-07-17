package org.icepdf.fx.ri.ui.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Dialog for application preferences and settings.
 */
public class PreferencesDialog extends Dialog<ButtonType> {

    private final ViewerModel model;
    private TabPane tabPane;

    // General preferences
    private CheckBox useSingleWindowCheckBox;
    private CheckBox showToolbarCheckBox;
    private CheckBox showStatusBarCheckBox;
    private CheckBox showLeftPanelCheckBox;

    // Display preferences
    private Slider zoomIncrementSlider;
    private ComboBox<String> defaultViewModeCombo;
    private ComboBox<String> defaultFitModeCombo;

    // Rendering preferences
    private CheckBox antiAliasingCheckBox;
    private CheckBox textAntiAliasingCheckBox;
    private ComboBox<String> renderQualityCombo;

    // Memory preferences
    private Slider maxMemorySlider;
    private CheckBox enableCachingCheckBox;

    public PreferencesDialog(ViewerModel model, Window owner) {
        this.model = model;

        initOwner(owner);
        setTitle("Preferences");
        setResizable(true);

        // Create dialog content
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefSize(600, 500);

        // Add tabs
        tabPane.getTabs().addAll(
                createGeneralTab(),
                createDisplayTab(),
                createRenderingTab(),
                createMemoryTab()
        );

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL, ButtonType.APPLY);

        // Handle Apply button
        Button applyButton = (Button) getDialogPane().lookupButton(ButtonType.APPLY);
        applyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            applyPreferences();
            event.consume(); // Don't close dialog
        });

        // Handle OK button
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                applyPreferences();
            }
            return buttonType;
        });
    }

    /**
     * Creates the General preferences tab.
     */
    private Tab createGeneralTab() {
        Tab tab = new Tab("General");

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));

        // Window behavior
        Label windowLabel = new Label("Window Behavior");
        windowLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        useSingleWindowCheckBox = new CheckBox("Use single window for all documents");
        useSingleWindowCheckBox.setSelected(model.useSingleViewerStage.get());

        // UI visibility
        Label uiLabel = new Label("User Interface");
        uiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        showToolbarCheckBox = new CheckBox("Show toolbar");
        showToolbarCheckBox.setSelected(model.toolBarVisible.get());

        showStatusBarCheckBox = new CheckBox("Show status bar");
        showStatusBarCheckBox.setSelected(model.statusBarVisible.get());

        showLeftPanelCheckBox = new CheckBox("Show left panel");
        showLeftPanelCheckBox.setSelected(model.leftPanelVisible.get());

        vbox.getChildren().addAll(
                windowLabel,
                useSingleWindowCheckBox,
                new Separator(),
                uiLabel,
                showToolbarCheckBox,
                showStatusBarCheckBox,
                showLeftPanelCheckBox
        );

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Creates the Display preferences tab.
     */
    private Tab createDisplayTab() {
        Tab tab = new Tab("Display");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Zoom increment
        Label zoomLabel = new Label("Zoom Increment:");
        zoomIncrementSlider = new Slider(0.05, 0.5, model.zoomFactorIncrement.get());
        zoomIncrementSlider.setShowTickLabels(true);
        zoomIncrementSlider.setShowTickMarks(true);
        zoomIncrementSlider.setMajorTickUnit(0.1);
        zoomIncrementSlider.setMinorTickCount(1);
        zoomIncrementSlider.setBlockIncrement(0.05);

        Label zoomValueLabel = new Label(String.format("%.0f%%", zoomIncrementSlider.getValue() * 100));
        zoomIncrementSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomValueLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
        });

        grid.add(zoomLabel, 0, row);
        grid.add(zoomIncrementSlider, 1, row);
        grid.add(zoomValueLabel, 2, row);
        row++;

        // Default view mode
        Label viewModeLabel = new Label("Default View Mode:");
        defaultViewModeCombo = new ComboBox<>();
        defaultViewModeCombo.getItems().addAll(
                "Single Page",
                "Continuous",
                "Facing Pages",
                "Continuous Facing"
        );
        defaultViewModeCombo.setValue(getViewModeString(model.viewMode.get()));

        grid.add(viewModeLabel, 0, row);
        grid.add(defaultViewModeCombo, 1, row, 2, 1);
        row++;

        // Default fit mode
        Label fitModeLabel = new Label("Default Fit Mode:");
        defaultFitModeCombo = new ComboBox<>();
        defaultFitModeCombo.getItems().addAll(
                "None",
                "Fit Width",
                "Fit Height",
                "Fit Page",
                "Actual Size"
        );
        defaultFitModeCombo.setValue(getFitModeString(model.fitMode.get()));

        grid.add(fitModeLabel, 0, row);
        grid.add(defaultFitModeCombo, 1, row, 2, 1);

        tab.setContent(grid);
        return tab;
    }

    /**
     * Creates the Rendering preferences tab.
     */
    private Tab createRenderingTab() {
        Tab tab = new Tab("Rendering");

        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));

        // Anti-aliasing
        Label aaLabel = new Label("Anti-aliasing");
        aaLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        antiAliasingCheckBox = new CheckBox("Enable graphics anti-aliasing");
        antiAliasingCheckBox.setSelected(true);

        textAntiAliasingCheckBox = new CheckBox("Enable text anti-aliasing");
        textAntiAliasingCheckBox.setSelected(true);

        // Render quality
        Label qualityLabel = new Label("Render Quality");
        qualityLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        renderQualityCombo = new ComboBox<>();
        renderQualityCombo.getItems().addAll("Low", "Medium", "High");
        renderQualityCombo.setValue("Medium");

        vbox.getChildren().addAll(
                aaLabel,
                antiAliasingCheckBox,
                textAntiAliasingCheckBox,
                new Separator(),
                qualityLabel,
                renderQualityCombo
        );

        tab.setContent(vbox);
        return tab;
    }

    /**
     * Creates the Memory preferences tab.
     */
    private Tab createMemoryTab() {
        Tab tab = new Tab("Memory");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Max memory
        Label memoryLabel = new Label("Maximum Memory Usage:");
        maxMemorySlider = new Slider(128, 2048, 512);
        maxMemorySlider.setShowTickLabels(true);
        maxMemorySlider.setShowTickMarks(true);
        maxMemorySlider.setMajorTickUnit(256);
        maxMemorySlider.setMinorTickCount(4);
        maxMemorySlider.setBlockIncrement(128);

        Label memoryValueLabel = new Label(String.format("%.0f MB", maxMemorySlider.getValue()));
        maxMemorySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            memoryValueLabel.setText(String.format("%.0f MB", newVal.doubleValue()));
        });

        grid.add(memoryLabel, 0, row);
        grid.add(maxMemorySlider, 1, row);
        grid.add(memoryValueLabel, 2, row);
        row++;

        // Caching
        enableCachingCheckBox = new CheckBox("Enable page caching");
        enableCachingCheckBox.setSelected(true);

        grid.add(enableCachingCheckBox, 0, row, 3, 1);
        row++;

        // Current memory info
        Label infoLabel = new Label("Current Memory Usage:");
        infoLabel.setStyle("-fx-font-weight: bold;");

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        Label memoryInfo = new Label(String.format("%d MB / %d MB", usedMemory, maxMemory));

        grid.add(infoLabel, 0, row);
        grid.add(memoryInfo, 1, row, 2, 1);

        tab.setContent(grid);
        return tab;
    }

    /**
     * Applies the preferences to the model.
     */
    private void applyPreferences() {
        // General preferences
        model.useSingleViewerStage.set(useSingleWindowCheckBox.isSelected());
        model.toolBarVisible.set(showToolbarCheckBox.isSelected());
        model.statusBarVisible.set(showStatusBarCheckBox.isSelected());
        model.leftPanelVisible.set(showLeftPanelCheckBox.isSelected());

        // Display preferences
        model.zoomFactorIncrement.set((float) zoomIncrementSlider.getValue());
        model.viewMode.set(parseViewMode(defaultViewModeCombo.getValue()));
        model.fitMode.set(parseFitMode(defaultFitModeCombo.getValue()));

        // TODO: Save preferences to file/properties
    }

    /**
     * Converts ViewMode enum to string.
     */
    private String getViewModeString(ViewerModel.ViewMode mode) {
        switch (mode) {
            case SINGLE_PAGE:
                return "Single Page";
            case CONTINUOUS:
                return "Continuous";
            case FACING_PAGES:
                return "Facing Pages";
            case CONTINUOUS_FACING:
                return "Continuous Facing";
            default:
                return "Single Page";
        }
    }

    /**
     * Parses ViewMode from string.
     */
    private ViewerModel.ViewMode parseViewMode(String mode) {
        switch (mode) {
            case "Single Page":
                return ViewerModel.ViewMode.SINGLE_PAGE;
            case "Continuous":
                return ViewerModel.ViewMode.CONTINUOUS;
            case "Facing Pages":
                return ViewerModel.ViewMode.FACING_PAGES;
            case "Continuous Facing":
                return ViewerModel.ViewMode.CONTINUOUS_FACING;
            default:
                return ViewerModel.ViewMode.SINGLE_PAGE;
        }
    }

    /**
     * Converts FitMode enum to string.
     */
    private String getFitModeString(ViewerModel.FitMode mode) {
        switch (mode) {
            case NONE:
                return "None";
            case FIT_WIDTH:
                return "Fit Width";
            case FIT_HEIGHT:
                return "Fit Height";
            case FIT_PAGE:
                return "Fit Page";
            case ACTUAL_SIZE:
                return "Actual Size";
            default:
                return "None";
        }
    }

    /**
     * Parses FitMode from string.
     */
    private ViewerModel.FitMode parseFitMode(String mode) {
        switch (mode) {
            case "None":
                return ViewerModel.FitMode.NONE;
            case "Fit Width":
                return ViewerModel.FitMode.FIT_WIDTH;
            case "Fit Height":
                return ViewerModel.FitMode.FIT_HEIGHT;
            case "Fit Page":
                return ViewerModel.FitMode.FIT_PAGE;
            case "Actual Size":
                return ViewerModel.FitMode.ACTUAL_SIZE;
            default:
                return ViewerModel.FitMode.NONE;
        }
    }
}

