package org.icepdf.fx.ri.viewer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Builder;
import org.icepdf.fx.ri.viewer.commands.OpenFileCommand;
import org.icepdf.fx.ri.viewer.commands.ZoomInCommand;
import org.icepdf.fx.ri.views.DocumentViewPane;

import java.util.function.Consumer;

public class ViewBuilder implements Builder<Region> {

    private final ViewerModel model;

    private Consumer<Runnable> openDocumentActionHandler;

    DocumentViewPane documentViewPane = new DocumentViewPane(null);

    public ViewBuilder(ViewerModel model) {
        this.model = model;
    }

    @Override
    public Region build() {
        BorderPane borderPane = new BorderPane();
        borderPane.setTop(createToolbar());
        borderPane.setCenter(documentViewPane);
        return borderPane;
    }

    private ToolBar createToolbar() {
        Button openDocument = new Button("Open Document");
        openDocument.setOnAction(event -> {
            Node source = (Node) event.getSource();
            Window stage = source.getScene().getWindow();
            new OpenFileCommand(stage, model).execute();
        });

        Button zoomOut = new Button("Zoom Out");
        zoomOut.setOnAction(event -> new ZoomInCommand(documentViewPane, model).execute());
        zoomOut.disableProperty().bind(model.toolbarDisabled);

        Button zoomIn = new Button("Zoom In");
        zoomIn.setOnAction(event -> new ZoomInCommand(documentViewPane, model).execute());
        zoomIn.disableProperty().bind(model.toolbarDisabled);

        ToolBar toolbar = new ToolBar();
        toolbar.getItems().addAll(openDocument, zoomOut, zoomIn);
        return toolbar;
    }
}
