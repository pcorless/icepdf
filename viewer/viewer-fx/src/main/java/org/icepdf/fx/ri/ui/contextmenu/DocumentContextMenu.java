package org.icepdf.fx.ri.ui.contextmenu;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.document.SearchCommand;
import org.icepdf.fx.ri.viewer.commands.view.*;
import org.icepdf.fx.ri.views.DocumentViewPane;

/**
 * Context menu for right-clicking on the document area.
 * Provides quick access to common document operations.
 */
public class DocumentContextMenu extends ContextMenu {

    private final ViewerModel model;
    private final Window window;
    private final DocumentViewPane documentViewPane;

    public DocumentContextMenu(ViewerModel model, Window window, DocumentViewPane documentViewPane) {
        this.model = model;
        this.window = window;
        this.documentViewPane = documentViewPane;

        buildMenu();
    }

    private void buildMenu() {
        // Copy text
        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(e -> copySelectedText());
        copy.disableProperty().bind(model.selectedText.isEmpty());

        // Select all
        MenuItem selectAll = new MenuItem("Select All");
        selectAll.setOnAction(e -> model.statusMessage.set("Select All not yet implemented"));
        selectAll.disableProperty().bind(model.document.isNull());

        // Search
        MenuItem search = new MenuItem("Search...");
        search.setOnAction(e -> new SearchCommand(model, window).execute());
        search.disableProperty().bind(model.document.isNull());

        // Zoom submenu
        Menu zoomMenu = new Menu("Zoom");
        zoomMenu.disableProperty().bind(model.document.isNull());

        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setOnAction(e -> new ZoomInCommand(documentViewPane, model).execute());

        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setOnAction(e -> new ZoomOutCommand(documentViewPane, model).execute());

        MenuItem actualSize = new MenuItem("Actual Size");
        actualSize.setOnAction(e -> new ActualSizeCommand(model).execute());

        MenuItem fitWidth = new MenuItem("Fit Width");
        fitWidth.setOnAction(e -> new FitWidthCommand(model).execute());

        MenuItem fitPage = new MenuItem("Fit Page");
        fitPage.setOnAction(e -> new FitPageCommand(model).execute());

        zoomMenu.getItems().addAll(zoomIn, zoomOut, new SeparatorMenuItem(), actualSize, fitWidth, fitPage);

        // Rotation submenu
        Menu rotationMenu = new Menu("Rotate");
        rotationMenu.disableProperty().bind(model.document.isNull());

        MenuItem rotateLeft = new MenuItem("Rotate Left (90°)");
        rotateLeft.setOnAction(e -> new RotateLeftCommand(model).execute());

        MenuItem rotateRight = new MenuItem("Rotate Right (90°)");
        rotateRight.setOnAction(e -> new RotateRightCommand(model).execute());

        rotationMenu.getItems().addAll(rotateLeft, rotateRight);

        // View mode submenu
        Menu viewModeMenu = new Menu("View Mode");
        viewModeMenu.disableProperty().bind(model.document.isNull());

        MenuItem singlePage = new MenuItem("Single Page");
        singlePage.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.SINGLE_PAGE);
            model.statusMessage.set("Single Page view");
        });

        MenuItem continuous = new MenuItem("Continuous");
        continuous.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.CONTINUOUS);
            model.statusMessage.set("Continuous view");
        });

        MenuItem facingPages = new MenuItem("Facing Pages");
        facingPages.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.FACING_PAGES);
            model.statusMessage.set("Facing Pages view");
        });

        MenuItem continuousFacing = new MenuItem("Continuous Facing");
        continuousFacing.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.CONTINUOUS_FACING);
            model.statusMessage.set("Continuous Facing view");
        });

        viewModeMenu.getItems().addAll(singlePage, continuous, facingPages, continuousFacing);

        // Add all items to context menu
        getItems().addAll(
                copy,
                selectAll,
                new SeparatorMenuItem(),
                search,
                new SeparatorMenuItem(),
                zoomMenu,
                rotationMenu,
                viewModeMenu
        );
    }

    /**
     * Copies selected text to clipboard.
     */
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
}

