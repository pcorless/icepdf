/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.icepdf.qa.viewer.common.PreferencesController;
import org.icepdf.qa.viewer.common.Viewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pcorl_000 on 2017-02-07.
 */
public class ResultsTab extends Tab {

    private final Slider filterSlider;

    private final ContextMenu openFileContextMenu;
    private final TableView<Result> resultsTable;
    private final ObservableList<Result> data;

    public ResultsTab(String title, Mediator mediator) {
        super(title);
        setClosable(false);

        // Threshold slider: show only results whose similarity is at or below
        // the slider value, i.e. drag right to widen the net and include
        // near-identical pages, drag left to surface only the worst regressions.
        filterSlider = new Slider(0, 100, PreferencesController.getImageCompareThreshold());
        filterSlider.setShowTickMarks(true);
        filterSlider.setShowTickLabels(true);
        filterSlider.setMajorTickUnit(25);
        filterSlider.setMinorTickCount(4);
        filterSlider.setPrefWidth(260);

        resultsTable = new TableView<>();
        resultsTable.setEditable(false);

        data = FXCollections.observableArrayList(new ArrayList<>());

        TableColumn<Result, String> fileNameColumn = new TableColumn<>("File");
        fileNameColumn.setSortType(TableColumn.SortType.ASCENDING);
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("documentFileName"));

        TableColumn<Result, String> captureNameColumn = new TableColumn<>("Capture ");
        captureNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileNameA"));

        // Ink-weighted similarity is the headline regression number.
        TableColumn<Result, Double> compareColumn = new TableColumn<>("Ink %");
        compareColumn.setCellValueFactory(new PropertyValueFactory<>("difference"));

        TableColumn<Result, Double> aeColumn = new TableColumn<>("AE %");
        aeColumn.setCellValueFactory(new PropertyValueFactory<>("aeSimilarity"));

        TableColumn<Result, Double> ssimColumn = new TableColumn<>("SSIM %");
        ssimColumn.setCellValueFactory(new PropertyValueFactory<>("structuralSimilarity"));

        resultsTable.getSortOrder().add(fileNameColumn);
        resultsTable.getColumns().addAll(fileNameColumn, captureNameColumn, compareColumn, aeColumn, ssimColumn);

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

        Label filterValueLabel = new Label();
        filterValueLabel.setMinWidth(60);
        Runnable applyFilter = () -> {
            double threshold = filterSlider.getValue();
            filterValueLabel.setText(String.format("≤ %.2f%%", threshold));
            filteredData.setPredicate(result -> result.getDifference() <= threshold);
        };
        filterSlider.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        // Persist the chosen threshold as the new default once the drag settles.
        filterSlider.valueChangingProperty().addListener((observable, wasChanging, changing) -> {
            if (!changing) {
                PreferencesController.saveImageCompareThreshold(filterSlider.getValue());
            }
        });
        applyFilter.run();

        SortedList<Result> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(resultsTable.comparatorProperty());
        resultsTable.setItems(sortedData);

        BorderPane borderPane = new BorderPane();
        ToolBar viewTools = new ToolBar();
        viewTools.getItems().addAll(new Label("Show ≤"), filterSlider, filterValueLabel);
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
