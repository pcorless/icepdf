package org.icepdf.qa.viewer.project;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.config.Result;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.Viewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pcorl_000 on 2017-02-07.
 */
public class ResultsTab extends Tab {

    private final TextField filterTextField;

    private final ContextMenu openFileContextMenu;
    private final TableView<Result> resultsTable;
    private final ObservableList<Result> data;

    public ResultsTab(String title, Mediator mediator) {
        super(title);
        setClosable(false);

        filterTextField = new TextField("99.99");

        resultsTable = new TableView<>();
        resultsTable.setEditable(false);

        data = FXCollections.observableArrayList(new ArrayList<>());

        TableColumn<Result, String> fileNameColumn = new TableColumn<>("File");
        fileNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("documentFileName"));

        TableColumn<Result, String> captureNameColumn = new TableColumn<>("Capture ");
        captureNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileNameA"));

        TableColumn<Result, Double> compareColumn = new TableColumn<>("Compare");
        compareColumn.setCellValueFactory(new PropertyValueFactory<>("difference"));

        resultsTable.getSortOrder().add(fileNameColumn);
        resultsTable.getColumns().addAll(fileNameColumn, captureNameColumn, compareColumn);

        openFileContextMenu = new ContextMenu();
        MenuItem openClassPathA = new MenuItem("Open With classpath A");
        openClassPathA.setOnAction(e -> {
            Result result = resultsTable.getSelectionModel().getSelectedItem();
            if (result != null) {
                Viewer.launchViewer(result, mediator.getCurrentProject().getCaptureSetA());
            }

        });
        MenuItem openClassPathB = new MenuItem("Open With classpath B");
        openClassPathB.setOnAction(e -> {
            Result result = resultsTable.getSelectionModel().getSelectedItem();
            if (result != null) {
                Viewer.launchViewer(result, mediator.getCurrentProject().getCaptureSetB());
            }
        });
        openFileContextMenu.getItems().addAll(openClassPathA, openClassPathB);

        // add row listener
        resultsTable.setRowFactory(tv -> {
            TableRow<Result> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    mediator.openResult(row.getItem());
                }
                if (event.getButton() == MouseButton.SECONDARY) {
                    openFileContextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });

        resultsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldSelection, newSelection) -> mediator.openResult(newSelection));

        FilteredList<Result> filteredData = new FilteredList<>(data, p -> true);

        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> filteredData.setPredicate(result -> {
            // If filter text is empty, display all persons.
            if (newValue == null || newValue.isEmpty()) {
                return true;
            }

            return result.getDifference() <= Double.parseDouble(newValue);// Does not match.
        }));
        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d+(?:\\.\\d+)?")) {
                filterTextField.setText(newValue.replaceAll("[^\\d.]", ""));
            }
        });

        filteredData.setPredicate(result -> {
            // If filter text is empty, display all persons.
            return result.getDifference() < Double.parseDouble(filterTextField.getText());
        });

        SortedList<Result> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(resultsTable.comparatorProperty());
        resultsTable.setItems(sortedData);

        BorderPane borderPane = new BorderPane();
        ToolBar viewTools = new ToolBar();
        viewTools.getItems().addAll(new Label("Filter"), filterTextField);
        borderPane.setTop(new VBox(20, viewTools));
        borderPane.setCenter(resultsTable);
        this.setContent(borderPane);
    }

    public void clearResults() {
        data.clear();
    }

    public void setProject(Project project) {
        clearResults();
        // load the results set.
        if (project.getResults() != null) {
            setResults(project.getResults());
        }
    }

    public void setResults(List<Result> results) {
        data.addAll(results);
    }
}
