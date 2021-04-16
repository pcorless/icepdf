package org.icepdf.qa.viewer.project;

import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.nio.file.Path;

/**
 * Create new Project dialog.
 */
public class NewProjectDialog extends AbstractDialog<Project> {

    private TextField projectName;
    private Label errorLabel;

    public NewProjectDialog(Mediator mediator) {
        super(mediator);

        setTitle("Create New QA Project");
        setHeaderText("Create a new QA project");

        // error field for presenting validation result.
        errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setVisible(false);

        Label projectLabel = new Label("Project name: ");
        projectName = new TextField();
        projectName.focusedProperty();
        projectName.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue) {
                // check if the file already exists
                validate();
            }
        });

        // setup create button mask and canel.
        ButtonType buttonTypeOk = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(buttonTypeOk, buttonTypeCancel);
        setResultConverter(buttonType -> {
            // validation passed, save the project file.
            if (buttonType == buttonTypeOk) {
                Project project = new Project(projectName.getText());
                Path projectPath = ConfigSerializer.save(project);
                project.setProjectPath(projectPath);
                PreferencesController.saveLastUedProject(projectPath);
                return project;
            }
            return null;
        });
        // setup validation action, if validation fails we consume the actionEvent and keep the dialog open
        final Button createButton = (Button) getDialogPane().lookupButton(buttonTypeOk);
        createButton.addEventFilter(ActionEvent.ACTION, ae -> {
            if (!validate()) {
                ae.consume();
            }
        });

        GridPane grid = new GridPane();
        grid.add(projectLabel, 1, 1);
        grid.add(projectName, 2, 1);
        grid.add(errorLabel, 2, 2);
        getDialogPane().setContent(grid);
    }


    private boolean validate() {
        // check for empty project name as we base the file name on this.
        if (projectName.getText().isEmpty()) {
            errorLabel.setText("Please select a field name. ");
            errorLabel.setVisible(true);
            return false;
        } // check if the project already exists.
        else if (ConfigSerializer.exists(
                PreferencesController.getProjectDirectory(),
                projectName.getText())) {
            errorLabel.setText("File name already exists. ");
            errorLabel.setVisible(true);
            return false;
        } else {
            errorLabel.setVisible(false);
            return true;
        }
    }
}
