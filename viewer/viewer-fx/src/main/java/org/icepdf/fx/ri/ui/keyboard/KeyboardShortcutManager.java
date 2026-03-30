package org.icepdf.fx.ri.ui.keyboard;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.document.OpenFileCommand;
import org.icepdf.fx.ri.viewer.commands.document.SearchCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.FirstPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.LastPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.NextPageCommand;
import org.icepdf.fx.ri.viewer.commands.navigation.PreviousPageCommand;
import org.icepdf.fx.ri.viewer.commands.view.*;
import org.icepdf.fx.ri.views.DocumentViewPane;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages global keyboard shortcuts for the application.
 * Provides centralized keyboard shortcut handling with configurable bindings.
 */
public class KeyboardShortcutManager {

    private static final Logger logger = Logger.getLogger(KeyboardShortcutManager.class.getName());

    private final ViewerModel model;
    private final Window window;
    private final DocumentViewPane documentViewPane;
    private final Map<KeyCodeCombination, Runnable> shortcuts;

    public KeyboardShortcutManager(ViewerModel model, Window window, DocumentViewPane documentViewPane) {
        this.model = model;
        this.window = window;
        this.documentViewPane = documentViewPane;
        this.shortcuts = new HashMap<>();

        registerDefaultShortcuts();
    }

    /**
     * Registers default keyboard shortcuts.
     */
    private void registerDefaultShortcuts() {
        // File operations
        registerShortcut(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                () -> new OpenFileCommand(window, model).execute(),
                "Open File");

        registerShortcut(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                () -> closeDocument(),
                "Close Document");

        registerShortcut(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN),
                () -> javafx.application.Platform.exit(),
                "Exit Application");

        // Search
        registerShortcut(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> new SearchCommand(model, window).execute(),
                "Search");

        // Zoom operations
        registerShortcut(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN),
                () -> new ZoomInCommand(documentViewPane, model).execute(),
                "Zoom In");

        registerShortcut(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN),
                () -> new ZoomInCommand(documentViewPane, model).execute(),
                "Zoom In (alternate)");

        registerShortcut(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                () -> new ZoomOutCommand(documentViewPane, model).execute(),
                "Zoom Out");

        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN),
                () -> new ActualSizeCommand(model).execute(),
                "Actual Size");

        // Navigation operations
        registerShortcut(new KeyCodeCombination(KeyCode.HOME),
                () -> new FirstPageCommand(model).execute(),
                "First Page");

        registerShortcut(new KeyCodeCombination(KeyCode.END),
                () -> new LastPageCommand(model).execute(),
                "Last Page");

        registerShortcut(new KeyCodeCombination(KeyCode.PAGE_UP),
                () -> new PreviousPageCommand(model).execute(),
                "Previous Page");

        registerShortcut(new KeyCodeCombination(KeyCode.PAGE_DOWN),
                () -> new NextPageCommand(model).execute(),
                "Next Page");

        // Arrow keys for navigation (when no document focused)
        registerShortcut(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
                () -> new PreviousPageCommand(model).execute(),
                "Previous Page (Alt+Left)");

        registerShortcut(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
                () -> new NextPageCommand(model).execute(),
                "Next Page (Alt+Right)");

        // Rotation
        registerShortcut(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                () -> new RotateLeftCommand(model).execute(),
                "Rotate Left");

        registerShortcut(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                () -> new RotateRightCommand(model).execute(),
                "Rotate Right");

        // Fit modes
        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN),
                () -> new FitWidthCommand(model).execute(),
                "Fit Width");

        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN),
                () -> new FitPageCommand(model).execute(),
                "Fit Page");

        // View modes
        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN),
                () -> setViewMode(ViewerModel.ViewMode.SINGLE_PAGE),
                "Single Page View");

        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN),
                () -> setViewMode(ViewerModel.ViewMode.CONTINUOUS),
                "Continuous View");

        registerShortcut(new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN),
                () -> setViewMode(ViewerModel.ViewMode.FACING_PAGES),
                "Facing Pages View");

        // Full screen
        registerShortcut(new KeyCodeCombination(KeyCode.F11),
                () -> toggleFullScreen(),
                "Toggle Full Screen");
    }

    /**
     * Registers a keyboard shortcut.
     */
    public void registerShortcut(KeyCodeCombination combination, Runnable action, String description) {
        shortcuts.put(combination, action);
        logger.fine("Registered shortcut: " + combination + " - " + description);
    }

    /**
     * Unregisters a keyboard shortcut.
     */
    public void unregisterShortcut(KeyCodeCombination combination) {
        shortcuts.remove(combination);
    }

    /**
     * Attaches the keyboard shortcut handler to a scene.
     */
    public void attachToScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    }

    /**
     * Handles key press events.
     */
    private void handleKeyPress(KeyEvent event) {
        for (Map.Entry<KeyCodeCombination, Runnable> entry : shortcuts.entrySet()) {
            if (entry.getKey().match(event)) {
                entry.getValue().run();
                event.consume();
                return;
            }
        }
    }

    /**
     * Gets all registered shortcuts.
     */
    public Map<KeyCodeCombination, Runnable> getShortcuts() {
        return new HashMap<>(shortcuts);
    }

    // Helper methods

    private void closeDocument() {
        if (model.document.get() != null) {
            model.document.get().dispose();
            model.document.set(null);
            model.filePath.set(null);
            model.documentTitle.set("");
            model.currentPage.set(1);
            model.totalPages.set(0);
            model.statusMessage.set("Document closed");
        }
    }

    private void setViewMode(ViewerModel.ViewMode mode) {
        if (model.document.get() != null) {
            model.viewMode.set(mode);
            model.statusMessage.set(mode.toString() + " view");
        }
    }

    private void toggleFullScreen() {
        if (window instanceof javafx.stage.Stage) {
            javafx.stage.Stage stage = (javafx.stage.Stage) window;
            stage.setFullScreen(!stage.isFullScreen());
        }
    }
}

