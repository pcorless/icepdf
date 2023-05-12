package org.icepdf.qa.viewer.project;

import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.ContentSet;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by pcorl_000 on 2017-02-13.
 */
public class NewContentSetDialog extends AbstractDialog<ContentSet> {

    private final TextField contentSetNameTextField;
    private final TextField contentSetPathTextFeild;
    private final Label errorLabel;
    private ContentSet contentSet;

    private final ButtonType buttonTypeOk;
    private boolean skipFileNameCheck = false;

    public NewContentSetDialog(Mediator mediator) {
        super(mediator);
        setTitle("Create New Capture Set");
        setHeaderText("Create a new QA content set.");

        // error field for presenting validation result.
        errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setVisible(false);

        Label captureSetNameLabel = new Label("Content set name: ");
        contentSetNameTextField = new TextField();
        contentSetNameTextField.focusedProperty();
        contentSetNameTextField.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue) {
                // check if the file already exists
                validateName();
            }
        });

        Label contentSetPathLabel = new Label("Content set path: ");
        contentSetPathTextFeild = new TextField();
        contentSetPathTextFeild.focusedProperty();
        contentSetPathTextFeild.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue) {
                // check if the file already exists
                validatePath();
            }
        });

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(event -> {
            DirectoryChooser fileChooser = new DirectoryChooser();
            fileChooser.setTitle("Content set path");
            fileChooser.setInitialDirectory(PreferencesController.getLastUsedContentSetPath().toFile());
            File selectedFile = fileChooser.showDialog(mediator.getPrimaryStage());
            if (selectedFile != null) {
                if (Files.isDirectory(Paths.get(selectedFile.getAbsolutePath()))) {
                    contentSetPathTextFeild.setText(selectedFile.getName());
                } else {
                    contentSetPathTextFeild.setText(selectedFile.getParent());
                }
            }
        });

        // setup create button mask and cancel.
        buttonTypeOk = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(buttonTypeOk, buttonTypeCancel);
        setResultConverter(buttonType -> {
            // validation passed, save the project file.
            if (buttonType == buttonTypeOk) {
                if (contentSet == null) {
                    contentSet = new ContentSet(contentSetNameTextField.getText(), contentSetPathTextFeild.getText());
                } else {
                    contentSet.setName(contentSetNameTextField.getText());
                    contentSet.setPath(contentSetPathTextFeild.getText());
                }
                // read and add files names to the content set.
                contentSet.refreshFiles();
                ConfigSerializer.save(contentSet);
                return contentSet;
            }
            return null;
        });
        // setup validation action, if validation fails we consume the actionEvent and keep the dialog open
        final Button createButton = (Button) getDialogPane().lookupButton(buttonTypeOk);
        createButton.addEventFilter(ActionEvent.ACTION, ae -> {
            if (!validateName()) {
                ae.consume();
            }
        });

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(8, 8, 8, 8));
        grid.setHgap(4);
        grid.setVgap(4);
        GridPane.setHalignment(browseButton, HPos.RIGHT);
        grid.add(captureSetNameLabel, 1, 1);
        grid.add(contentSetNameTextField, 2, 1);
        grid.add(contentSetPathLabel, 1, 2);
        grid.add(contentSetPathTextFeild, 2, 2);
        grid.add(browseButton, 2, 3);
        grid.add(errorLabel, 2, 4);
        getDialogPane().setContent(grid);
    }

    public void setContentSet(ContentSet contentSet) {
        this.contentSet = contentSet;
        final Button createButton = (Button) getDialogPane().lookupButton(buttonTypeOk);
        createButton.setText("Update");
        skipFileNameCheck = true;
        contentSetNameTextField.setText(contentSet.getName());
        contentSetPathTextFeild.setText(contentSet.getPath());
    }

    private boolean validateName() {
        // check for empty project name as we base the file name on this.
        if (contentSetNameTextField.getText().isEmpty()) {
            errorLabel.setText("Please select a field name. ");
            errorLabel.setVisible(true);
            return false;
        } // check if the project already exists.
        else if (!skipFileNameCheck) {
            if (ConfigSerializer.exists(
                    PreferencesController.getContentSetDirectory(),
                    contentSetNameTextField.getText())) {
                errorLabel.setText("File name already exists. ");
                errorLabel.setVisible(true);
                return false;
            }
            return true;
        } else {
            errorLabel.setVisible(false);
            return true;
        }
    }

    private boolean validatePath() {
        // check for empty project name as we base the file name on this.
        if (contentSetNameTextField.getText().isEmpty()) {
            errorLabel.setText("Please select a path. ");
            errorLabel.setVisible(true);
            return false;
        } else {
            errorLabel.setVisible(false);
            return true;
        }
    }
}