package org.icepdf.fx.ri.ui.layout;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.logging.Logger;

/**
 * Manages responsive layout behavior for the application.
 * Handles window resize, minimum sizes, and adaptive UI based on window size.
 */
public class ResponsiveLayoutManager {

    private static final Logger logger = Logger.getLogger(ResponsiveLayoutManager.class.getName());

    // Minimum window sizes
    public static final double MIN_WIDTH = 800;
    public static final double MIN_HEIGHT = 600;

    // Breakpoints for responsive behavior
    public static final double COMPACT_BREAKPOINT = 1024;
    public static final double SMALL_BREAKPOINT = 1280;
    public static final double MEDIUM_BREAKPOINT = 1600;

    private final Stage stage;
    private final ViewerModel model;

    // Responsive state properties
    private final BooleanProperty compactMode = new SimpleBooleanProperty(false);
    private final BooleanProperty smallMode = new SimpleBooleanProperty(false);
    private final BooleanProperty mediumMode = new SimpleBooleanProperty(false);
    private final BooleanProperty largeMode = new SimpleBooleanProperty(true);

    public ResponsiveLayoutManager(Stage stage, ViewerModel model) {
        this.stage = stage;
        this.model = model;

        setupWindowConstraints();
        setupResponsiveListeners();
    }

    /**
     * Sets up minimum window size constraints.
     */
    private void setupWindowConstraints() {
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        logger.info("Window constraints set: " + MIN_WIDTH + "x" + MIN_HEIGHT);
    }

    /**
     * Sets up listeners for responsive behavior.
     */
    private void setupResponsiveListeners() {
        // Listen to width changes
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateResponsiveMode(newVal.doubleValue(), stage.getHeight());
        });

        // Listen to height changes
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            updateResponsiveMode(stage.getWidth(), newVal.doubleValue());
        });

        // Initial update
        updateResponsiveMode(stage.getWidth(), stage.getHeight());
    }

    /**
     * Updates the responsive mode based on window size.
     */
    private void updateResponsiveMode(double width, double height) {
        // Determine mode based on width
        if (width < COMPACT_BREAKPOINT) {
            setMode(LayoutMode.COMPACT);
        } else if (width < SMALL_BREAKPOINT) {
            setMode(LayoutMode.SMALL);
        } else if (width < MEDIUM_BREAKPOINT) {
            setMode(LayoutMode.MEDIUM);
        } else {
            setMode(LayoutMode.LARGE);
        }
    }

    /**
     * Sets the layout mode and updates properties.
     */
    private void setMode(LayoutMode mode) {
        compactMode.set(mode == LayoutMode.COMPACT);
        smallMode.set(mode == LayoutMode.SMALL);
        mediumMode.set(mode == LayoutMode.MEDIUM);
        largeMode.set(mode == LayoutMode.LARGE);

        // Update model-based behaviors
        switch (mode) {
            case COMPACT:
                // In compact mode, hide left panel by default
                if (model.leftPanelVisible.get()) {
                    logger.fine("Compact mode: Consider hiding left panel");
                }
                break;
            case SMALL:
                // Small mode - normal operation
                break;
            case MEDIUM:
                // Medium mode - comfortable viewing
                break;
            case LARGE:
                // Large mode - all features visible
                break;
        }

        logger.fine("Layout mode updated: " + mode);
    }

    /**
     * Centers the stage on the screen.
     */
    public void centerOnScreen() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX((screenBounds.getWidth() - stage.getWidth()) / 2);
        stage.setY((screenBounds.getHeight() - stage.getHeight()) / 2);
    }

    /**
     * Maximizes the stage while respecting taskbar/dock.
     */
    public void maximizeStage() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX(screenBounds.getMinX());
        stage.setY(screenBounds.getMinY());
        stage.setWidth(screenBounds.getWidth());
        stage.setHeight(screenBounds.getHeight());
    }

    /**
     * Restores the stage to a comfortable default size.
     */
    public void restoreDefaultSize() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1280, screenBounds.getWidth() * 0.8);
        double height = Math.min(900, screenBounds.getHeight() * 0.8);

        stage.setWidth(width);
        stage.setHeight(height);
        centerOnScreen();
    }

    /**
     * Checks if the window should use compact mode.
     */
    public boolean isCompactMode() {
        return compactMode.get();
    }

    public BooleanProperty compactModeProperty() {
        return compactMode;
    }

    /**
     * Checks if the window should use small mode.
     */
    public boolean isSmallMode() {
        return smallMode.get();
    }

    public BooleanProperty smallModeProperty() {
        return smallMode;
    }

    /**
     * Checks if the window should use medium mode.
     */
    public boolean isMediumMode() {
        return mediumMode.get();
    }

    public BooleanProperty mediumModeProperty() {
        return mediumMode;
    }

    /**
     * Checks if the window should use large mode.
     */
    public boolean isLargeMode() {
        return largeMode.get();
    }

    public BooleanProperty largeModeProperty() {
        return largeMode;
    }

    /**
     * Gets the current window width.
     */
    public double getWindowWidth() {
        return stage.getWidth();
    }

    /**
     * Gets the current window height.
     */
    public double getWindowHeight() {
        return stage.getHeight();
    }

    /**
     * Layout mode enumeration.
     */
    public enum LayoutMode {
        COMPACT,    // < 1024px
        SMALL,      // < 1280px
        MEDIUM,     // < 1600px
        LARGE       // >= 1600px
    }
}

