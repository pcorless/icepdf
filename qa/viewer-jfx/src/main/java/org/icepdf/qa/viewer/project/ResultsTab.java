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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pcorl_000 on 2017-02-07.
 */
public class ResultsTab extends Tab {

    private TextField filterTextField;

    private ContextMenu openFileContextMenu;
    private TableView<Result> resultsTable;
    private ObservableList<Result> data;

    public ResultsTab(String title, Mediator mediator) {
        super(title);
        setClosable(false);

        filterTextField = new TextField("99.99");

        resultsTable = new TableView<>();
        resultsTable.setEditable(false);

        data = FXCollections.observableArrayList(new ArrayList<Result>());

        TableColumn<Result, String> fileNameColumn = new TableColumn<>("File");
        fileNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<Result, String>("documentFileName"));

        TableColumn<Result, String> captureNameColumn = new TableColumn<>("Capture ");
        captureNameColumn.setCellValueFactory(new PropertyValueFactory<Result, String>("fileNameA"));

        TableColumn<Result, Double> compareColumn = new TableColumn<>("Compare");
        compareColumn.setCellValueFactory(new PropertyValueFactory<>("difference"));

        resultsTable.getSortOrder().add(fileNameColumn);
        resultsTable.getColumns().addAll(fileNameColumn, captureNameColumn, compareColumn);

        openFileContextMenu = new ContextMenu();
        MenuItem openClassPathA = new MenuItem("Open With classpath A");
        openClassPathA.setOnAction(e -> {
            Result result = resultsTable.getSelectionModel().getSelectedItem();
            if (result != null) {
                try {
                    Viewer.launchViewer(result, mediator.getCurrentProject().getCaptureSetA());
                } catch (InvocationTargetException e1) {
                    e1.printStackTrace();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }

        });
        MenuItem openClassPathB = new MenuItem("Open With classpath B");
        openClassPathB.setOnAction(e -> {
            Result result = resultsTable.getSelectionModel().getSelectedItem();
            if (result != null) {
                try {
                    Viewer.launchViewer(result, mediator.getCurrentProject().getCaptureSetB());
                } catch (InvocationTargetException e1) {
                    e1.printStackTrace();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
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

        resultsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldSelection, newSelection) -> {
            mediator.openResult(newSelection);
        });

        FilteredList<Result> filteredData = new FilteredList<>(data, p -> true);

        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(result -> {
                // If filter text is empty, display all persons.
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                if (result.getDifference() <= Double.parseDouble(newValue)) {
                    return true;
                }
                return false; // Does not match.
            });
        });
        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d+(?:\\.\\d+)?")) {
                filterTextField.setText(newValue.replaceAll("[^\\d.]", ""));
            }
        });

        filteredData.setPredicate(result -> {
            // If filter text is empty, display all persons.
            if (result.getDifference() < Double.parseDouble(filterTextField.getText())) {
                return true;
            }
            return false;
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
