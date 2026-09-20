package org.icepdf.fx.ri.ui.menubar;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;
import javafx.util.Builder;
import org.icepdf.fx.ri.viewer.Interactor;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.document.DocumentPropertiesCommand;
import org.icepdf.fx.ri.viewer.commands.document.OpenFileCommand;
import org.icepdf.fx.ri.viewer.commands.document.PrintDocumentCommand;
import org.icepdf.fx.ri.viewer.commands.document.SearchCommand;
import org.icepdf.fx.ri.viewer.commands.view.ZoomInCommand;
import org.icepdf.fx.ri.viewer.commands.view.ZoomOutCommand;
import org.icepdf.fx.ri.viewer.commands.window.AboutCommand;
import org.icepdf.fx.ri.viewer.commands.window.PreferencesCommand;
import org.icepdf.fx.ri.views.DocumentViewPane;

/**
 * Builder for the main menu bar.
 * Creates File, Edit, View, Document, Window, and Help menus.
 */
public class MenuBarBuilder implements Builder<MenuBar> {

    private final ViewerModel model;
    private final Interactor interactor;
    private final Window window;
    private final DocumentViewPane documentViewPane;

    public MenuBarBuilder(ViewerModel model, Interactor interactor, Window window, DocumentViewPane documentViewPane) {
        this.model = model;
        this.interactor = interactor;
        this.window = window;
        this.documentViewPane = documentViewPane;
    }

    @Override
    public MenuBar build() {
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(
                buildFileMenu(),
                buildEditMenu(),
                buildViewMenu(),
                buildDocumentMenu(),
                buildWindowMenu(),
                buildHelpMenu()
        );
        return menuBar;
    }

    private Menu buildFileMenu() {
        // Open
        MenuItem open = new MenuItem("Open...");
        open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        open.setOnAction(e -> new OpenFileCommand(window, model).execute());

        // Close
        MenuItem close = new MenuItem("Close");
        close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        close.disableProperty().bind(model.document.isNull());
        close.setOnAction(e -> closeDocument());

        // Save (placeholder)
        MenuItem save = new MenuItem("Save...");
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        save.disableProperty().bind(model.document.isNull());
        save.setOnAction(e -> model.statusMessage.set("Save not yet implemented"));

        // Print
        MenuItem print = new MenuItem("Print...");
        print.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        print.disableProperty().bind(model.document.isNull());
        print.setOnAction(e -> new PrintDocumentCommand(window, model).execute());

        // Recent Files submenu (placeholder)
        Menu recentFiles = new Menu("Recent Files");
        recentFiles.getItems().add(new MenuItem("(No recent files)"));

        // Exit
        MenuItem exit = new MenuItem("Exit");
        exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
        exit.setOnAction(e -> Platform.exit());

        return new Menu("File", null,
                open,
                close,
                new SeparatorMenuItem(),
                save,
                print,
                new SeparatorMenuItem(),
                recentFiles,
                new SeparatorMenuItem(),
                exit
        );
    }

    private Menu buildEditMenu() {
        // Copy
        MenuItem copy = new MenuItem("Copy");
        copy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copy.disableProperty().bind(model.selectedText.isEmpty());
        copy.setOnAction(e -> copySelectedText());

        // Select All
        MenuItem selectAll = new MenuItem("Select All");
        selectAll.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selectAll.disableProperty().bind(model.document.isNull());
        selectAll.setOnAction(e -> model.statusMessage.set("Select All not yet implemented"));

        // Search
        MenuItem search = new MenuItem("Search...");
        search.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        search.disableProperty().bind(model.document.isNull());
        search.setOnAction(e -> new SearchCommand(model, window).execute());

        // Preferences
        MenuItem preferences = new MenuItem("Preferences...");
        preferences.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
        preferences.setOnAction(e -> new PreferencesCommand(model, window).execute());

        return new Menu("Edit", null,
                copy,
                selectAll,
                new SeparatorMenuItem(),
                search,
                new SeparatorMenuItem(),
                preferences
        );
    }

    private Menu buildViewMenu() {
        // Zoom In
        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN));
        zoomIn.disableProperty().bind(model.document.isNull());
        zoomIn.setOnAction(e -> new ZoomInCommand(documentViewPane, model).execute());

        // Zoom Out
        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOut.disableProperty().bind(model.document.isNull());
        zoomOut.setOnAction(e -> new ZoomOutCommand(documentViewPane, model).execute());

        // Actual Size
        MenuItem actualSize = new MenuItem("Actual Size");
        actualSize.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        actualSize.disableProperty().bind(model.document.isNull());
        actualSize.setOnAction(e -> setActualSize());

        // Fit Width
        MenuItem fitWidth = new MenuItem("Fit Width");
        fitWidth.disableProperty().bind(model.document.isNull());
        fitWidth.setOnAction(e -> {
            model.fitMode.set(ViewerModel.FitMode.FIT_WIDTH);
            model.statusMessage.set("Fit Width not yet implemented");
        });

        // Fit Page
        MenuItem fitPage = new MenuItem("Fit Page");
        fitPage.disableProperty().bind(model.document.isNull());
        fitPage.setOnAction(e -> {
            model.fitMode.set(ViewerModel.FitMode.FIT_PAGE);
            model.statusMessage.set("Fit Page not yet implemented");
        });

        // Rotation submenu
        Menu rotation = new Menu("Rotate");
        MenuItem rotateLeft = new MenuItem("Rotate Left");
        rotateLeft.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN));
        rotateLeft.disableProperty().bind(model.document.isNull());
        rotateLeft.setOnAction(e -> rotateLeft());

        MenuItem rotateRight = new MenuItem("Rotate Right");
        rotateRight.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN));
        rotateRight.disableProperty().bind(model.document.isNull());
        rotateRight.setOnAction(e -> rotateRight());

        rotation.getItems().addAll(rotateLeft, rotateRight);

        // Panels submenu
        Menu panels = new Menu("Panels");
        CheckMenuItem showLeftPanel = new CheckMenuItem("Show Left Panel");
        showLeftPanel.selectedProperty().bindBidirectional(model.leftPanelVisible);

        CheckMenuItem showStatusBar = new CheckMenuItem("Show Status Bar");
        showStatusBar.selectedProperty().bindBidirectional(model.statusBarVisible);

        CheckMenuItem showToolBar = new CheckMenuItem("Show Tool Bar");
        showToolBar.selectedProperty().bindBidirectional(model.toolBarVisible);

        panels.getItems().addAll(showLeftPanel, showStatusBar, showToolBar);

        // Full Screen
        MenuItem fullScreen = new MenuItem("Full Screen");
        fullScreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
        fullScreen.setOnAction(e -> toggleFullScreen());

        return new Menu("View", null,
                zoomIn,
                zoomOut,
                actualSize,
                new SeparatorMenuItem(),
                fitWidth,
                fitPage,
                new SeparatorMenuItem(),
                rotation,
                new SeparatorMenuItem(),
                panels,
                new SeparatorMenuItem(),
                fullScreen
        );
    }

    private Menu buildDocumentMenu() {
        // Document Properties
        MenuItem properties = new MenuItem("Properties...");
        properties.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));
        properties.disableProperty().bind(model.document.isNull());
        properties.setOnAction(e -> new DocumentPropertiesCommand(window, model).execute());

        // Document Information
        MenuItem information = new MenuItem("Information...");
        information.disableProperty().bind(model.document.isNull());
        information.setOnAction(e -> model.statusMessage.set("Document Information not yet implemented"));

        // Fonts
        MenuItem fonts = new MenuItem("Fonts...");
        fonts.disableProperty().bind(model.document.isNull());
        fonts.setOnAction(e -> model.statusMessage.set("Fonts dialog not yet implemented"));

        // Security/Permissions
        MenuItem security = new MenuItem("Security...");
        security.disableProperty().bind(model.document.isNull());
        security.setOnAction(e -> model.statusMessage.set("Security dialog not yet implemented"));

        return new Menu("Document", null,
                properties,
                information,
                new SeparatorMenuItem(),
                fonts,
                security
        );
    }

    private Menu buildWindowMenu() {
        // New Window
        MenuItem newWindow = new MenuItem("New Window");
        newWindow.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newWindow.setOnAction(e -> model.statusMessage.set("New Window not yet implemented"));

        // Minimize
        MenuItem minimize = new MenuItem("Minimize");
        minimize.setAccelerator(new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN));
        minimize.setOnAction(e -> minimizeWindow());

        return new Menu("Window", null,
                newWindow,
                minimize
        );
    }

    private Menu buildHelpMenu() {
        // Documentation
        MenuItem documentation = new MenuItem("Documentation...");
        documentation.setOnAction(e -> model.statusMessage.set("Documentation not yet implemented"));

        // About
        MenuItem about = new MenuItem("About...");
        about.setOnAction(e -> new AboutCommand(window).execute());

        return new Menu("Help", null,
                documentation,
                new SeparatorMenuItem(),
                about
        );
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

    private void copySelectedText() {
        String text = model.selectedText.get();
        if (text != null && !text.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            model.statusMessage.set("Text copied to clipboard");
        }
    }

    private void setActualSize() {
        model.zoomLevel.set(1.0);
        model.fitMode.set(ViewerModel.FitMode.ACTUAL_SIZE);
        model.statusMessage.set("Actual Size (100%)");
    }

    private void rotateLeft() {
        double current = model.rotationAngle.get();
        model.rotationAngle.set((current - 90) % 360);
        model.statusMessage.set("Rotated left");
    }

    private void rotateRight() {
        double current = model.rotationAngle.get();
        model.rotationAngle.set((current + 90) % 360);
        model.statusMessage.set("Rotated right");
    }

    private void toggleFullScreen() {
        if (window instanceof javafx.stage.Stage) {
            javafx.stage.Stage stage = (javafx.stage.Stage) window;
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    private void minimizeWindow() {
        if (window instanceof javafx.stage.Stage) {
            javafx.stage.Stage stage = (javafx.stage.Stage) window;
            stage.setIconified(true);
        }
    }
}

