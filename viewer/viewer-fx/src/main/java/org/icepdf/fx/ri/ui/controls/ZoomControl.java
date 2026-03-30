package org.icepdf.fx.ri.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.icepdf.fx.ri.ui.icons.IconManager;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.view.*;
import org.icepdf.fx.ri.views.DocumentViewPane;

/**
 * Zoom control with preset zoom levels, custom input, and fit buttons.
 * Provides comprehensive zoom management.
 */
public class ZoomControl extends HBox {

    private final ViewerModel model;
    private final DocumentViewPane documentViewPane;
    private final IconManager iconManager;

    private Button zoomOutButton;
    private ComboBox<String> zoomComboBox;
    private Button zoomInButton;
    private Button fitWidthButton;
    private Button fitPageButton;
    private Button actualSizeButton;

    // Preset zoom levels (percentages)
    private static final String[] ZOOM_PRESETS = {
            "25%", "50%", "75%", "100%", "125%", "150%", "200%", "300%", "400%"
    };

    public ZoomControl(ViewerModel model, DocumentViewPane documentViewPane) {
        this.model = model;
        this.documentViewPane = documentViewPane;
        this.iconManager = IconManager.getInstance();

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(5);
        setPadding(new Insets(5));

        buildControls();
        setupBindings();
        setupEventHandlers();
    }

    private void buildControls() {
        // Zoom out button
        zoomOutButton = new Button();
        zoomOutButton.setGraphic(iconManager.getZoomOutIcon());
        zoomOutButton.setTooltip(new Tooltip("Zoom Out (Ctrl+-)"));

        // Zoom combo box with presets
        zoomComboBox = new ComboBox<>();
        zoomComboBox.getItems().addAll(ZOOM_PRESETS);
        zoomComboBox.setEditable(true);
        zoomComboBox.setPrefWidth(100);
        zoomComboBox.setPromptText("Zoom");
        zoomComboBox.setTooltip(new Tooltip("Zoom Level"));

        // Zoom in button
        zoomInButton = new Button();
        zoomInButton.setGraphic(iconManager.getZoomInIcon());
        zoomInButton.setTooltip(new Tooltip("Zoom In (Ctrl++)"));

        // Fit width button
        fitWidthButton = new Button("⬌");
        fitWidthButton.setTooltip(new Tooltip("Fit Width"));

        // Fit page button
        fitPageButton = new Button("⬚");
        fitPageButton.setTooltip(new Tooltip("Fit Page"));

        // Actual size button
        actualSizeButton = new Button("1:1");
        actualSizeButton.setTooltip(new Tooltip("Actual Size (Ctrl+0)"));

        getChildren().addAll(
                zoomOutButton,
                zoomComboBox,
                zoomInButton,
                new Label("|"), // Separator
                fitWidthButton,
                fitPageButton,
                actualSizeButton
        );
    }

    private void setupBindings() {
        // Update combo box when zoom level changes
        model.zoomLevel.addListener((obs, oldVal, newVal) -> {
            if (!zoomComboBox.isFocused()) {
                updateZoomDisplay(newVal.doubleValue());
            }
        });

        // Disable controls when no document
        zoomOutButton.disableProperty().bind(model.document.isNull());
        zoomInButton.disableProperty().bind(model.document.isNull());
        zoomComboBox.disableProperty().bind(model.document.isNull());
        fitWidthButton.disableProperty().bind(model.document.isNull());
        fitPageButton.disableProperty().bind(model.document.isNull());
        actualSizeButton.disableProperty().bind(model.document.isNull());
    }

    private void setupEventHandlers() {
        // Zoom buttons
        zoomOutButton.setOnAction(e -> new ZoomOutCommand(documentViewPane, model).execute());
        zoomInButton.setOnAction(e -> new ZoomInCommand(documentViewPane, model).execute());

        // Fit buttons
        fitWidthButton.setOnAction(e -> new FitWidthCommand(model).execute());
        fitPageButton.setOnAction(e -> new FitPageCommand(model).execute());
        actualSizeButton.setOnAction(e -> new ActualSizeCommand(model).execute());

        // Combo box - apply zoom on selection or Enter
        zoomComboBox.setOnAction(e -> applyZoomFromComboBox());

        // Validate input - only allow numbers and %
        zoomComboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty() && !newVal.matches("\\d*%?")) {
                zoomComboBox.getEditor().setText(oldVal);
            }
        });
    }

    /**
     * Updates the zoom display in the combo box.
     */
    private void updateZoomDisplay(double zoomLevel) {
        int percentage = (int) Math.round(zoomLevel * 100);
        String display = percentage + "%";

        // Only update if different to avoid infinite loops
        if (!zoomComboBox.getValue().equals(display)) {
            zoomComboBox.setValue(display);
        }
    }

    /**
     * Applies the zoom level from the combo box value.
     */
    private void applyZoomFromComboBox() {
        String value = zoomComboBox.getValue();
        if (value == null || value.trim().isEmpty()) {
            updateZoomDisplay(model.zoomLevel.get());
            return;
        }

        try {
            // Remove % if present
            value = value.replace("%", "").trim();
            int percentage = Integer.parseInt(value);

            // Validate range (10% to 1000%)
            if (percentage < 10) {
                percentage = 10;
                model.statusMessage.set("Minimum zoom is 10%");
            } else if (percentage > 1000) {
                percentage = 1000;
                model.statusMessage.set("Maximum zoom is 1000%");
            }

            double zoomLevel = percentage / 100.0;
            model.zoomLevel.set(zoomLevel);
            model.fitMode.set(ViewerModel.FitMode.NONE);
            model.statusMessage.set("Zoom: " + percentage + "%");

        } catch (NumberFormatException e) {
            model.statusMessage.set("Invalid zoom value: " + value);
            updateZoomDisplay(model.zoomLevel.get());
        }

        // Clear focus
        zoomComboBox.getParent().requestFocus();
    }

    /**
     * Sets compact mode (smaller buttons, no labels).
     */
    public void setCompactMode(boolean compact) {
        if (compact) {
            zoomOutButton.setText("−");
            zoomOutButton.setGraphic(null);
            zoomInButton.setText("+");
            zoomInButton.setGraphic(null);
            zoomComboBox.setPrefWidth(80);
        } else {
            zoomOutButton.setText("");
            zoomOutButton.setGraphic(iconManager.getZoomOutIcon());
            zoomInButton.setText("");
            zoomInButton.setGraphic(iconManager.getZoomInIcon());
            zoomComboBox.setPrefWidth(100);
        }
    }

    /**
     * Adds a custom zoom preset to the combo box.
     */
    public void addZoomPreset(String preset) {
        if (!zoomComboBox.getItems().contains(preset)) {
            zoomComboBox.getItems().add(preset);
        }
    }
}

