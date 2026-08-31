package org.icepdf.fx.ri.ui.sidepanel;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.util.Builder;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Container for all side panels in the JavaFX viewer.
 * Hosts panels in a TabPane that can be placed on the left or right side of the main view.
 *
 * <p>Panels included:</p>
 * <ul>
 *   <li>Thumbnails - Page thumbnails for quick navigation</li>
 *   <li>Outline - Document bookmarks/outline tree</li>
 *   <li>Search - Text search interface</li>
 *   <li>Layers - Optional content layers</li>
 *   <li>Attachments - Embedded file attachments</li>
 *   <li>Annotations - Annotation summary and navigation</li>
 *   <li>Signatures - Digital signature information</li>
 * </ul>
 */
public class SidePanelContainer implements Builder<Region> {

    private final ViewerModel model;
    private TabPane tabPane;

    public SidePanelContainer(ViewerModel model) {
        this.model = model;
    }

    @Override
    public Region build() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setMinWidth(200);
        tabPane.setPrefWidth(250);

        // Create all side panel tabs
        tabPane.getTabs().addAll(
                createTab("Thumbnails", new ThumbnailsPanel(model)),
                createTab("Outline", new OutlinePanel(model)),
                createTab("Search", new SearchPanel(model)),
                createTab("Layers", new LayersPanel(model)),
                createTab("Attachments", new AttachmentsPanel(model)),
                createTab("Annotations", new AnnotationsPanel(model)),
                createTab("Signatures", new SignaturesPanel(model))
        );

        // Bind selected tab to model for persistence
        tabPane.getSelectionModel().selectedIndexProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        model.selectedSidePanelIndex.set(newValue.intValue());
                    }
                });

        // Restore previously selected tab
        if (model.selectedSidePanelIndex.get() >= 0 &&
                model.selectedSidePanelIndex.get() < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(model.selectedSidePanelIndex.get());
        }

        return tabPane;
    }

    /**
     * Creates a tab with the specified title and content panel.
     */
    private Tab createTab(String title, Region content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        return tab;
    }

    /**
     * Gets the TabPane component for direct manipulation if needed.
     */
    public TabPane getTabPane() {
        return tabPane;
    }
}

