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

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.Project;

/**
 * Project name
 */
public class ProjectTitledPane extends TitledPane {

    private Project project;

    private final TextField projectNameTextField;

    public ProjectTitledPane(String name) {
        super(name, null);
        setCollapsible(false);

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(8, 8, 8, 8));
        gridPane.setHgap(4);
        gridPane.setVgap(4);

        Insets labelInsets = new Insets(0, 10, 0, 0);
        Insets inputInsets = new Insets(0, 0, 0, 0);

        ColumnConstraints lableColumn = new ColumnConstraints();
        lableColumn.setPrefWidth(75);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setPercentWidth(75);
        gridPane.getColumnConstraints().add(lableColumn);

        // project name.
        Label projectNameLabel = new Label("Name:");
        projectNameTextField = new TextField();
        projectNameTextField.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue && projectNameTextField.getText().isEmpty()) {
                // check if the file already exists
                projectNameTextField.setText("Cannot be null.");
            } else if (!newValue) {
                // save the state.
                project.setName(projectNameTextField.getText().trim());
                ConfigSerializer.save(project);
            }
        });
        GridPane.setMargin(projectNameLabel, labelInsets);
        GridPane.setMargin(projectNameTextField, inputInsets);
        GridPane.setConstraints(projectNameLabel, 0, 0);
        GridPane.setConstraints(projectNameTextField, 1, 0);


        gridPane.getChildren().addAll(projectNameLabel, projectNameTextField);
        setContent(gridPane);
    }

    public void setProject(Project project) {
        this.project = project;
        if (project != null) {
            projectNameTextField.setText(project.getName().trim());
        } else {
            projectNameTextField.setText("");
        }
    }
}
