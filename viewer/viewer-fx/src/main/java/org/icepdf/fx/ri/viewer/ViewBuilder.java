package org.icepdf.fx.ri.viewer;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Builder;
import org.icepdf.fx.ri.ui.menubar.MenuBarBuilder;
import org.icepdf.fx.ri.ui.sidepanel.SidePanelContainer;
import org.icepdf.fx.ri.ui.statusbar.StatusBarBuilder;
import org.icepdf.fx.ri.ui.toolbar.ToolBarBuilder;
import org.icepdf.fx.ri.views.DocumentViewPane;

import java.util.function.Consumer;

public class ViewBuilder implements Builder<Region> {

    private final ViewerModel model;
    private final Interactor interactor;
    private final Window window;

    private Consumer<Runnable> openDocumentActionHandler;

    DocumentViewPane documentViewPane;

    public ViewBuilder(ViewerModel model, Interactor interactor, Window window) {
        this.model = model;
        this.interactor = interactor;
        this.window = window;
    }

    @Override
    public Region build() {
        BorderPane root = new BorderPane();

        // Create document view pane (needed by builders)
        documentViewPane = new DocumentViewPane(model);

        // Create builders
        MenuBarBuilder menuBarBuilder = new MenuBarBuilder(model, interactor, window, documentViewPane);
        ToolBarBuilder toolBarBuilder = new ToolBarBuilder(model, interactor, window, documentViewPane);
        StatusBarBuilder statusBarBuilder = new StatusBarBuilder(model);
        SidePanelContainer sidePanelContainer = new SidePanelContainer(model);

        // Top: Menu + Toolbar
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(
                menuBarBuilder.build(),
                toolBarBuilder.build()
        );
        topContainer.visibleProperty().bind(model.menuBarVisible.or(model.toolBarVisible));
        topContainer.managedProperty().bind(topContainer.visibleProperty());
        root.setTop(topContainer);

        // Center: SplitPane with side panel and document view
        SplitPane centerPane = new SplitPane();
        Region sidePanel = sidePanelContainer.build();

        // Bind side panel visibility
        sidePanel.visibleProperty().bind(model.leftPanelVisible);
        sidePanel.managedProperty().bind(sidePanel.visibleProperty());

        centerPane.getItems().addAll(sidePanel, documentViewPane);
        centerPane.setDividerPositions(0.2); // 20% for side panel, 80% for document

        root.setCenter(centerPane);

        // Bottom: Status bar
        Region statusBar = statusBarBuilder.build();
        statusBar.visibleProperty().bind(model.statusBarVisible);
        statusBar.managedProperty().bind(statusBar.visibleProperty());
        root.setBottom(statusBar);

        return root;
    }

    public DocumentViewPane getDocumentViewPane() {
        return documentViewPane;
    }
}
