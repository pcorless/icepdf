package org.icepdf.fx.ri.ui.dialogs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Advanced search dialog with search options and results display.
 */
public class SearchDialog extends Stage {

    private final ViewerModel model;

    // Search controls
    private TextField searchField;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox wholeWordCheckBox;
    private CheckBox regexCheckBox;
    private CheckBox highlightAllCheckBox;

    // Results
    private ListView<SearchResult> resultsListView;
    private ObservableList<SearchResult> searchResults;
    private Label statusLabel;
    private ProgressBar progressBar;

    // Navigation
    private Button findNextButton;
    private Button findPreviousButton;
    private Button findAllButton;
    private Button clearButton;

    public SearchDialog(ViewerModel model, Window owner) {
        this.model = model;
        this.searchResults = FXCollections.observableArrayList();

        initOwner(owner);
        initModality(Modality.NONE); // Non-modal dialog
        setTitle("Search");
        setResizable(true);

        // Create UI
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setTop(createSearchPanel());
        root.setCenter(createResultsPanel());
        root.setBottom(createStatusPanel());

        setScene(new javafx.scene.Scene(root, 500, 600));
    }

    /**
     * Creates the search input and options panel.
     */
    private VBox createSearchPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        // Search field
        Label searchLabel = new Label("Find what:");
        searchField = new TextField();
        searchField.setPromptText("Enter search text...");
        searchField.setPrefWidth(400);

        // Search on Enter
        searchField.setOnAction(e -> findNext());

        // Search options
        Label optionsLabel = new Label("Options:");
        optionsLabel.setStyle("-fx-font-weight: bold;");

        caseSensitiveCheckBox = new CheckBox("Case sensitive");
        wholeWordCheckBox = new CheckBox("Whole words only");
        regexCheckBox = new CheckBox("Regular expression");
        highlightAllCheckBox = new CheckBox("Highlight all matches");
        highlightAllCheckBox.setSelected(true);

        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(10);
        optionsGrid.setVgap(5);
        optionsGrid.add(caseSensitiveCheckBox, 0, 0);
        optionsGrid.add(wholeWordCheckBox, 1, 0);
        optionsGrid.add(regexCheckBox, 0, 1);
        optionsGrid.add(highlightAllCheckBox, 1, 1);

        // Buttons
        HBox buttonBox = new HBox(10);

        findNextButton = new Button("Find Next");
        findNextButton.setDefaultButton(true);
        findNextButton.setOnAction(e -> findNext());

        findPreviousButton = new Button("Find Previous");
        findPreviousButton.setOnAction(e -> findPrevious());

        findAllButton = new Button("Find All");
        findAllButton.setOnAction(e -> findAll());

        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clear());

        buttonBox.getChildren().addAll(findNextButton, findPreviousButton, findAllButton, clearButton);

        panel.getChildren().addAll(
                searchLabel,
                searchField,
                new Separator(),
                optionsLabel,
                optionsGrid,
                new Separator(),
                buttonBox
        );

        return panel;
    }

    /**
     * Creates the search results panel.
     */
    private VBox createResultsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label resultsLabel = new Label("Results:");
        resultsLabel.setStyle("-fx-font-weight: bold;");

        resultsListView = new ListView<>(searchResults);
        resultsListView.setPrefHeight(400);
        resultsListView.setCellFactory(lv -> new SearchResultCell());

        // Navigate to result on click
        resultsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                SearchResult selected = resultsListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    navigateToResult(selected);
                }
            }
        });

        panel.getChildren().addAll(resultsLabel, resultsListView);
        return panel;
    }

    /**
     * Creates the status panel at the bottom.
     */
    private VBox createStatusPanel() {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 10px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);

        panel.getChildren().addAll(statusLabel, progressBar);
        return panel;
    }

    /**
     * Finds the next occurrence of the search term.
     */
    private void findNext() {
        String searchText = searchField.getText();
        if (searchText == null || searchText.trim().isEmpty()) {
            showAlert("Please enter search text.");
            return;
        }

        // TODO: Implement search in document
        statusLabel.setText("Searching for next occurrence...");

        // Placeholder implementation
        showAlert("Find Next not yet implemented.\nSearch text: " + searchText);
    }

    /**
     * Finds the previous occurrence of the search term.
     */
    private void findPrevious() {
        String searchText = searchField.getText();
        if (searchText == null || searchText.trim().isEmpty()) {
            showAlert("Please enter search text.");
            return;
        }

        // TODO: Implement search in document
        statusLabel.setText("Searching for previous occurrence...");

        // Placeholder implementation
        showAlert("Find Previous not yet implemented.\nSearch text: " + searchText);
    }

    /**
     * Finds all occurrences of the search term.
     */
    private void findAll() {
        String searchText = searchField.getText();
        if (searchText == null || searchText.trim().isEmpty()) {
            showAlert("Please enter search text.");
            return;
        }

        // Clear previous results
        searchResults.clear();

        // TODO: Implement search in document
        statusLabel.setText("Searching entire document...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // Indeterminate

        // Placeholder - simulate search results
        javafx.concurrent.Task<Void> searchTask = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Simulate search delay
                Thread.sleep(500);

                // Add dummy results
                javafx.application.Platform.runLater(() -> {
                    searchResults.add(new SearchResult(1, "Line 1", searchText, 10));
                    searchResults.add(new SearchResult(1, "Line 2", searchText, 50));
                    searchResults.add(new SearchResult(2, "Line 1", searchText, 20));

                    statusLabel.setText("Found " + searchResults.size() + " matches");
                    progressBar.setVisible(false);
                });

                return null;
            }
        };

        new Thread(searchTask).start();
    }

    /**
     * Clears search results and resets the search.
     */
    private void clear() {
        searchField.clear();
        searchResults.clear();
        statusLabel.setText("Ready");
        progressBar.setVisible(false);

        // TODO: Clear highlighting in document
    }

    /**
     * Navigates to the selected search result.
     */
    private void navigateToResult(SearchResult result) {
        // TODO: Navigate to page and highlight result
        statusLabel.setText("Navigating to page " + result.getPageNumber() + "...");
        model.currentPage.set(result.getPageNumber());
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(this);
        alert.showAndWait();
    }

    /**
     * Custom cell for displaying search results.
     */
    private static class SearchResultCell extends ListCell<SearchResult> {
        @Override
        protected void updateItem(SearchResult item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(2);

                Label pageLabel = new Label("Page " + item.getPageNumber());
                pageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");

                Label contextLabel = new Label(item.getContext());
                contextLabel.setStyle("-fx-font-size: 11px;");
                contextLabel.setWrapText(true);

                vbox.getChildren().addAll(pageLabel, contextLabel);
                setGraphic(vbox);
            }
        }
    }

    /**
     * Represents a search result.
     */
    public static class SearchResult {
        private final int pageNumber;
        private final String context;
        private final String matchText;
        private final int position;

        public SearchResult(int pageNumber, String context, String matchText, int position) {
            this.pageNumber = pageNumber;
            this.context = context;
            this.matchText = matchText;
            this.position = position;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getContext() {
            return context;
        }

        public String getMatchText() {
            return matchText;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return "Page " + pageNumber + ": " + context;
        }
    }
}

