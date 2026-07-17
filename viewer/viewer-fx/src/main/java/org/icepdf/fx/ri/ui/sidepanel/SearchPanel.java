package org.icepdf.fx.ri.ui.sidepanel;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.List;
import java.util.logging.Logger;

/**
 * Panel for searching text within the PDF document.
 * Provides search options and displays results in a list.
 */
public class SearchPanel extends VBox {

    private static final Logger logger = Logger.getLogger(SearchPanel.class.getName());

    private final ViewerModel model;
    private final TextField searchField;
    private final Button searchButton;
    private final Button clearButton;
    private final CheckBox caseSensitiveCheck;
    private final CheckBox wholeWordCheck;
    private final ListView<SearchResultItem> resultsListView;
    private final Label statusLabel;
    private final ProgressIndicator progressIndicator;

    private DocumentSearchController searchController;
    private Task<List<SearchResultItem>> currentSearchTask;

    public SearchPanel(ViewerModel model) {
        this.model = model;
        this.searchField = new TextField();
        this.searchButton = new Button("Search");
        this.clearButton = new Button("Clear");
        this.caseSensitiveCheck = new CheckBox("Case sensitive");
        this.wholeWordCheck = new CheckBox("Whole word");
        this.resultsListView = new ListView<>();
        this.statusLabel = new Label("");
        this.progressIndicator = new ProgressIndicator();

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(10));

        // Search input section
        VBox searchInputSection = new VBox(5);
        searchField.setPromptText("Enter search text...");
        searchField.setOnAction(e -> performSearch());

        HBox buttonBox = new HBox(5);
        searchButton.setDefaultButton(true);
        searchButton.setOnAction(e -> performSearch());
        clearButton.setOnAction(e -> clearSearch());
        buttonBox.getChildren().addAll(searchButton, clearButton);

        searchInputSection.getChildren().addAll(
                new Label("Search:"),
                searchField,
                buttonBox
        );

        // Search options section
        VBox optionsSection = new VBox(5);
        optionsSection.getChildren().addAll(
                new Label("Options:"),
                caseSensitiveCheck,
                wholeWordCheck
        );

        // Results section
        VBox resultsSection = new VBox(5);
        Label resultsLabel = new Label("Results:");

        resultsListView.setCellFactory(lv -> new SearchResultCell());
        resultsListView.setPlaceholder(new Label("No search results"));
        resultsListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        navigateToResult(newValue);
                    }
                }
        );

        VBox.setVgrow(resultsListView, Priority.ALWAYS);

        // Status section
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);
        statusBox.getChildren().addAll(progressIndicator, statusLabel);

        resultsSection.getChildren().addAll(resultsLabel, resultsListView, statusBox);

        // Add all sections to main panel
        getChildren().addAll(
                searchInputSection,
                new Separator(),
                optionsSection,
                new Separator(),
                resultsSection
        );
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Initialize if document already loaded
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }

        // Disable search when no document
        searchButton.disableProperty().bind(model.document.isNull());
        searchField.disableProperty().bind(model.document.isNull());
    }

    private void handleDocumentChange(Document newDoc) {
        clearSearch();

        // TODO: Implement DocumentSearchController for JavaFX
        // DocumentSearchController is an interface - need to port the implementation
        // from viewer-awt (DocumentSearchControllerImpl) or create a simpler version
        searchController = null;
    }

    private void performSearch() {
        String searchText = searchField.getText();
        if (searchText == null || searchText.trim().isEmpty()) {
            statusLabel.setText("Please enter search text");
            return;
        }

        // TODO: Implement search functionality
        // For now, just show a placeholder message
        statusLabel.setText("Search not yet implemented - pending DocumentSearchController port");
        logger.info("Search requested for: '" + searchText + "' (not yet implemented)");

        // Future implementation will:
        // 1. Port DocumentSearchControllerImpl from viewer-awt or create JavaFX-specific version
        // 2. Perform background search using Task
        // 3. Display results in resultsListView
        // 4. Navigate to results on click
        // 5. Highlight matches in document view
    }

    private void clearSearch() {
        searchField.clear();
        resultsListView.getItems().clear();
        statusLabel.setText("");

        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            currentSearchTask.cancel();
        }
    }

    private void navigateToResult(SearchResultItem result) {
        if (result != null) {
            // Navigate to the page containing the result
            model.currentPage.set(result.getPageNumber());

            // TODO: Highlight the search hit on the page
            // This will require integration with the page view
            logger.info("Navigated to search result on page " + result.getPageNumber());
        }
    }

    /**
     * Represents a single search result item.
     */
    private static class SearchResultItem {
        private final int pageNumber;
        private final String preview;
        private final org.icepdf.core.pobjects.graphics.text.WordText wordText;

        public SearchResultItem(int pageNumber, String preview,
                                org.icepdf.core.pobjects.graphics.text.WordText wordText) {
            this.pageNumber = pageNumber;
            this.preview = preview;
            this.wordText = wordText;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getPreview() {
            return preview;
        }

        public org.icepdf.core.pobjects.graphics.text.WordText getWordText() {
            return wordText;
        }
    }

    /**
     * Custom ListCell for displaying search results.
     */
    private class SearchResultCell extends ListCell<SearchResultItem> {

        @Override
        protected void updateItem(SearchResultItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(2);

                Label pageLabel = new Label("Page " + item.getPageNumber());
                pageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

                Label previewLabel = new Label(item.getPreview());
                previewLabel.setWrapText(true);
                previewLabel.setStyle("-fx-font-size: 10px;");

                content.getChildren().addAll(pageLabel, previewLabel);
                setGraphic(content);
            }
        }
    }
}

