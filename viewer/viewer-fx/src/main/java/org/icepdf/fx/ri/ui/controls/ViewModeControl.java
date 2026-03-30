package org.icepdf.fx.ri.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * View mode control with toggle buttons for different page layout modes.
 * Provides visual selection of single page, continuous, facing, and continuous facing modes.
 */
public class ViewModeControl extends HBox {

    private final ViewerModel model;

    private ToggleButton singlePageButton;
    private ToggleButton continuousButton;
    private ToggleButton facingButton;
    private ToggleButton continuousFacingButton;
    private ToggleGroup viewModeGroup;

    public ViewModeControl(ViewerModel model) {
        this.model = model;

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(5);
        setPadding(new Insets(5));

        buildControls();
        setupBindings();
        setupEventHandlers();
    }

    private void buildControls() {
        viewModeGroup = new ToggleGroup();

        // Single page button
        singlePageButton = new ToggleButton("☰");
        singlePageButton.setTooltip(new Tooltip("Single Page View"));
        singlePageButton.setToggleGroup(viewModeGroup);
        singlePageButton.setSelected(true);

        // Continuous scrolling button
        continuousButton = new ToggleButton("≡");
        continuousButton.setTooltip(new Tooltip("Continuous Scrolling View"));
        continuousButton.setToggleGroup(viewModeGroup);

        // Facing pages button
        facingButton = new ToggleButton("⚏");
        facingButton.setTooltip(new Tooltip("Facing Pages View"));
        facingButton.setToggleGroup(viewModeGroup);

        // Continuous facing button
        continuousFacingButton = new ToggleButton("⚏⚏");
        continuousFacingButton.setTooltip(new Tooltip("Continuous Facing Pages View"));
        continuousFacingButton.setToggleGroup(viewModeGroup);

        getChildren().addAll(
                singlePageButton,
                continuousButton,
                facingButton,
                continuousFacingButton
        );
    }

    private void setupBindings() {
        // Update buttons when view mode changes from elsewhere
        model.viewMode.addListener((obs, oldVal, newVal) -> {
            updateButtonSelection(newVal);
        });

        // Disable when no document
        singlePageButton.disableProperty().bind(model.document.isNull());
        continuousButton.disableProperty().bind(model.document.isNull());
        facingButton.disableProperty().bind(model.document.isNull());
        continuousFacingButton.disableProperty().bind(model.document.isNull());
    }

    private void setupEventHandlers() {
        singlePageButton.setOnAction(e -> {
            if (singlePageButton.isSelected()) {
                model.viewMode.set(ViewerModel.ViewMode.SINGLE_PAGE);
                model.statusMessage.set("Single Page view");
            }
        });

        continuousButton.setOnAction(e -> {
            if (continuousButton.isSelected()) {
                model.viewMode.set(ViewerModel.ViewMode.CONTINUOUS);
                model.statusMessage.set("Continuous view");
            }
        });

        facingButton.setOnAction(e -> {
            if (facingButton.isSelected()) {
                model.viewMode.set(ViewerModel.ViewMode.FACING_PAGES);
                model.statusMessage.set("Facing Pages view");
            }
        });

        continuousFacingButton.setOnAction(e -> {
            if (continuousFacingButton.isSelected()) {
                model.viewMode.set(ViewerModel.ViewMode.CONTINUOUS_FACING);
                model.statusMessage.set("Continuous Facing view");
            }
        });
    }

    /**
     * Updates button selection to match the current view mode.
     */
    private void updateButtonSelection(ViewerModel.ViewMode mode) {
        switch (mode) {
            case SINGLE_PAGE:
                singlePageButton.setSelected(true);
                break;
            case CONTINUOUS:
                continuousButton.setSelected(true);
                break;
            case FACING_PAGES:
                facingButton.setSelected(true);
                break;
            case CONTINUOUS_FACING:
                continuousFacingButton.setSelected(true);
                break;
        }
    }

    /**
     * Sets compact mode (smaller buttons).
     */
    public void setCompactMode(boolean compact) {
        if (compact) {
            singlePageButton.setPrefWidth(30);
            continuousButton.setPrefWidth(30);
            facingButton.setPrefWidth(30);
            continuousFacingButton.setPrefWidth(40);
        } else {
            singlePageButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            continuousButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            facingButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            continuousFacingButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
        }
    }

    /**
     * Sets whether to show text labels on buttons.
     */
    public void setShowLabels(boolean show) {
        if (show) {
            singlePageButton.setText("☰ Single");
            continuousButton.setText("≡ Continuous");
            facingButton.setText("⚏ Facing");
            continuousFacingButton.setText("⚏⚏ Cont. Facing");
        } else {
            singlePageButton.setText("☰");
            continuousButton.setText("≡");
            facingButton.setText("⚏");
            continuousFacingButton.setText("⚏⚏");
        }
    }
}

