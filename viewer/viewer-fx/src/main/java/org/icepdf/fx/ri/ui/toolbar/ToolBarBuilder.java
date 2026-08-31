package org.icepdf.fx.ri.ui.toolbar;

import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Builder;
import org.icepdf.fx.ri.ui.common.NavigationCommands;
import org.icepdf.fx.ri.viewer.Interactor;
import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.document.OpenFileCommand;
import org.icepdf.fx.ri.viewer.commands.view.ZoomInCommand;
import org.icepdf.fx.ri.viewer.commands.view.ZoomOutCommand;
import org.icepdf.fx.ri.views.DocumentViewPane;

/**
 * Builder for the main toolbar.
 * Creates toolbar with file operations, navigation, zoom, rotation, and view mode controls.
 */
public class ToolBarBuilder implements Builder<ToolBar> {

    private final ViewerModel model;
    private final Interactor interactor;
    private final Window window;
    private final DocumentViewPane documentViewPane;

    public ToolBarBuilder(ViewerModel model, Interactor interactor, Window window, DocumentViewPane documentViewPane) {
        this.model = model;
        this.interactor = interactor;
        this.window = window;
        this.documentViewPane = documentViewPane;
    }

    @Override
    public ToolBar build() {
        ToolBar toolBar = new ToolBar();

        toolBar.getItems().addAll(
                createFileTools()
        );
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(
                createNavigationTools()
        );
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(
                createZoomTools()
        );
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(
                createRotationTools()
        );
        toolBar.getItems().add(new Separator());
        toolBar.getItems().addAll(
                createViewModeTools()
        );

        return toolBar;
    }

    private Button[] createFileTools() {
        Button open = createButton("Open", "Open Document");
        open.setOnAction(e -> new OpenFileCommand(window, model).execute());

        Button print = createButton("Print", "Print Document");
        print.disableProperty().bind(model.document.isNull());
        print.setOnAction(e -> model.statusMessage.set("Print not yet implemented"));

        return new Button[]{open, print};
    }

    private Region[] createNavigationTools() {
        Button first = createButton("|◀", "First Page");
        first.disableProperty().bind(model.document.isNull()
                .or(model.currentPage.isEqualTo(1)));
        first.setOnAction(e -> NavigationCommands.firstPage(model));

        Button previous = createButton("◀", "Previous Page");
        previous.disableProperty().bind(model.document.isNull()
                .or(model.currentPage.isEqualTo(1)));
        previous.setOnAction(e -> NavigationCommands.previousPage(model));

        // Page number field
        TextField pageField = new TextField();
        pageField.setPrefColumnCount(4);
        pageField.setPromptText("Page");
        pageField.textProperty().bind(model.currentPage.asString());
        pageField.setTooltip(new Tooltip("Current Page"));
        pageField.disableProperty().bind(model.document.isNull());

        // Total pages label
        Label totalLabel = new Label();
        totalLabel.textProperty().bind(javafx.beans.binding.Bindings.concat(" / ", model.totalPages.asString()));

        Button next = createButton("▶", "Next Page");
        next.disableProperty().bind(model.document.isNull()
                .or(model.currentPage.greaterThanOrEqualTo(model.totalPages)));
        next.setOnAction(e -> NavigationCommands.nextPage(model));

        Button last = createButton("▶|", "Last Page");
        last.disableProperty().bind(model.document.isNull()
                .or(model.currentPage.greaterThanOrEqualTo(model.totalPages)));
        last.setOnAction(e -> NavigationCommands.lastPage(model));

        return new Region[]{first, previous, pageField, totalLabel, next, last};
    }

    private Region[] createZoomTools() {
        Button zoomOut = createButton("−", "Zoom Out");
        zoomOut.disableProperty().bind(model.document.isNull());
        zoomOut.setOnAction(e -> new ZoomOutCommand(documentViewPane, model).execute());

        Button zoomIn = createButton("+", "Zoom In");
        zoomIn.disableProperty().bind(model.document.isNull());
        zoomIn.setOnAction(e -> new ZoomInCommand(documentViewPane, model).execute());

        // Zoom level display
        Label zoomLabel = new Label();
        zoomLabel.textProperty().bind(
                javafx.beans.binding.Bindings.concat(
                        model.zoomLevel.multiply(100).asString("%.0f"),
                        "%"
                )
        );
        zoomLabel.setTooltip(new Tooltip("Current Zoom Level"));

        // Fit width button
        Button fitWidth = createButton("⬌", "Fit Width");
        fitWidth.disableProperty().bind(model.document.isNull());
        fitWidth.setOnAction(e -> {
            model.fitMode.set(ViewerModel.FitMode.FIT_WIDTH);
            model.statusMessage.set("Fit Width not yet implemented");
        });

        // Fit page button
        Button fitPage = createButton("⬚", "Fit Page");
        fitPage.disableProperty().bind(model.document.isNull());
        fitPage.setOnAction(e -> {
            model.fitMode.set(ViewerModel.FitMode.FIT_PAGE);
            model.statusMessage.set("Fit Page not yet implemented");
        });

        return new Region[]{zoomOut, zoomLabel, zoomIn, fitWidth, fitPage};
    }

    private Button[] createRotationTools() {
        Button rotateLeft = createButton("↶", "Rotate Left (90°)");
        rotateLeft.disableProperty().bind(model.document.isNull());
        rotateLeft.setOnAction(e -> {
            double current = model.rotationAngle.get();
            model.rotationAngle.set((current - 90 + 360) % 360);
            model.statusMessage.set("Rotated left");
        });

        Button rotateRight = createButton("↷", "Rotate Right (90°)");
        rotateRight.disableProperty().bind(model.document.isNull());
        rotateRight.setOnAction(e -> {
            double current = model.rotationAngle.get();
            model.rotationAngle.set((current + 90) % 360);
            model.statusMessage.set("Rotated right");
        });

        return new Button[]{rotateLeft, rotateRight};
    }

    private ToggleButton[] createViewModeTools() {
        ToggleGroup viewModeGroup = new ToggleGroup();

        ToggleButton singlePage = createToggleButton("☰", "Single Page", viewModeGroup);
        singlePage.setSelected(true);
        singlePage.disableProperty().bind(model.document.isNull());
        singlePage.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.SINGLE_PAGE);
            model.statusMessage.set("Single Page view");
        });

        ToggleButton continuous = createToggleButton("≡", "Continuous", viewModeGroup);
        continuous.disableProperty().bind(model.document.isNull());
        continuous.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.CONTINUOUS);
            model.statusMessage.set("Continuous view");
        });

        ToggleButton facing = createToggleButton("⚏", "Facing Pages", viewModeGroup);
        facing.disableProperty().bind(model.document.isNull());
        facing.setOnAction(e -> {
            model.viewMode.set(ViewerModel.ViewMode.FACING_PAGES);
            model.statusMessage.set("Facing Pages view");
        });

        return new ToggleButton[]{singlePage, continuous, facing};
    }

    // Helper methods

    private Button createButton(String text, String tooltip) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private ToggleButton createToggleButton(String text, String tooltip, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setToggleGroup(group);
        return button;
    }
}

