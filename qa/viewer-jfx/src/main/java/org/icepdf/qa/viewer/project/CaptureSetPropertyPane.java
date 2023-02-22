package org.icepdf.qa.viewer.project;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import org.icepdf.qa.config.CaptureSet;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.ContentSet;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * private String name;
 * <p>
 * private Type type;
 * <p>
 * private String version;
 * private String classPath;
 * private int capturePageCount;
 * <p>
 * private String jdkVersion;
 * private String systemProperties;
 * <p>
 * private List<String> contentSets;
 * <p>
 * private String relativePath;
 */
public class CaptureSetPropertyPane extends TitledPane implements EventHandler<ActionEvent> {

    private final Mediator mediator;
    private Project currentProject;
    private CaptureSet captureSet;

    private final TextField captureCountTextField;
    private final TextField captureSetNameTextField;
    private final ChoiceBox<CaptureSet.Type> captureSetTypes;
    private final TextField classPathTextField;
    private final ListView<String> contentList;

    private final Button addButton;
    private Button editButton;
    private Button removeButton;

    public CaptureSetPropertyPane(String name, Mediator mediator) {
        super(name, null);
        setCollapsible(false);
        this.mediator = mediator;

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
        gridPane.getColumnConstraints().add(lableColumn);//2*50 percent
//        gridPane.getColumnConstraints().add(fieldColumn);

        // content name.
        Label projectNameLabel = new Label("Name:");
        captureSetNameTextField = new TextField();
        captureSetNameTextField.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue && captureSetNameTextField.getText().isEmpty()) {
                // check if the file already exists
                captureSetNameTextField.setText("Cannot be null.");
            } else if (!newValue) {
                // save the state.
                if (captureSet != null) {
                    captureSet.setName(captureSetNameTextField.getText().trim());
                    ConfigSerializer.save(captureSet);
                }
            }
        });
        GridPane.setMargin(projectNameLabel, labelInsets);
        GridPane.setMargin(captureSetNameTextField, inputInsets);
        GridPane.setConstraints(projectNameLabel, 0, 0, 3, 1);
        GridPane.setConstraints(captureSetNameTextField, 1, 0, 3, 1);

        // type.
        Label captureTypeLabel = new Label("Type:");
        captureSetTypes = new ChoiceBox<>();
        for (CaptureSet.Type type : CaptureSet.Type.values()) {
            captureSetTypes.getItems().add(type);
        }
        captureSetTypes.setDisable(true);
        GridPane.setMargin(captureSetTypes, inputInsets);
        GridPane.setConstraints(captureTypeLabel, 0, 1, 3, 1);
        GridPane.setConstraints(captureSetTypes, 1, 1, 3, 1);

        // page capture count

        Label captureCountTypeLabel = new Label("Capture Count:");
        captureCountTextField = new TextField();
        captureCountTextField.focusedProperty().addListener((arg0, oldValue, newValue) -> {
            // focus lost.
            if (!newValue && captureCountTextField.getText().isEmpty()) {
                // check if the file already exists
                captureCountTextField.setText("Cannot be null.");
            } else if (!newValue) {
                // save the state.
                captureSet.setCapturePageCount(Integer.parseInt(captureCountTextField.getText().trim()));
                captureSet.setComplete(false);
                ConfigSerializer.save(captureSet);
            }
        });
        captureCountTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                captureCountTextField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
        GridPane.setMargin(captureCountTextField, inputInsets);
        GridPane.setConstraints(captureCountTypeLabel, 0, 2, 3, 1);
        GridPane.setConstraints(captureCountTextField, 1, 2, 3, 1);

        // classpath.
        Label classPathLabel = new Label("ClassPath:");
        classPathTextField = new TextField();
        GridPane.setMargin(classPathLabel, labelInsets);
        GridPane.setMargin(classPathTextField, inputInsets);
        GridPane.setConstraints(classPathLabel, 0, 3, 3, 1);
        GridPane.setConstraints(classPathTextField, 1, 3, 3, 1);
        // browse button
        Button browseButton = new Button("Browse");
        browseButton.setOnAction(event -> {
            DirectoryChooser fileChooser = new DirectoryChooser();
            fileChooser.setTitle("Version class path");
            fileChooser.setInitialDirectory(Paths.get(PreferencesController.getProductClassPathDirectory()).toFile());
            File selectedFile = fileChooser.showDialog(mediator.getPrimaryStage());
            if (selectedFile != null) {
                classPathTextField.setText(selectedFile.getPath());
                captureSet.setClassLoader(null);
                // save state.
//                String baseDir = PreferencesController.getProductClassPathDirectory();
//                String classPath = selectedFile.getPath().replace(baseDir, "");
                captureSet.setClassPath(selectedFile.getPath());
                captureSet.setComplete(false);
                ConfigSerializer.save(captureSet);
            }
        });
        GridPane.setMargin(browseButton, inputInsets);
        GridPane.setHalignment(browseButton, HPos.RIGHT);
        GridPane.setConstraints(browseButton, 3, 4, 1, 1);

        // content sets
        Label contentSetLabel = new Label("Test Set:");
        contentList = new ListView<>();
        contentList.setMaxHeight(75);
        contentList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        contentList.getSelectionModel().selectedIndexProperty().addListener(observable -> {
            List<String> selectedItems = contentList.getSelectionModel().getSelectedItems();
            if (selectedItems.size() > 0) {
                removeButton.setDisable(false);
            } else {
                removeButton.setDisable(true);
            }
            if (selectedItems.size() == 1) {
                editButton.setDisable(false);
            } else {
                editButton.setDisable(true);
            }
        });
        GridPane.setMargin(contentSetLabel, labelInsets);
        GridPane.setMargin(contentList, inputInsets);
        GridPane.setConstraints(contentSetLabel, 0, 5, 3, 1);
        GridPane.setValignment(contentSetLabel, VPos.TOP);
        GridPane.setConstraints(contentList, 1, 5, 3, 1);

        // add testAndAnalyze set controls, add, remove, edit
        addButton = new Button("Add");
        addButton.setStyle(String.format("-fx-base: %s;", "indigo"));
        addButton.setOnAction(this);
        editButton = new Button("Edit");
        editButton.setStyle(String.format("-fx-base: %s;", "green"));
        editButton.setDisable(true);
        editButton.setOnAction(this);
        removeButton = new Button("Remove");
        removeButton.setStyle(String.format("-fx-base: %s;", "red"));
        removeButton.setDisable(true);
        removeButton.setOnAction(this);
        GridPane.setHalignment(addButton, HPos.RIGHT);
        GridPane.setHalignment(editButton, HPos.RIGHT);
        GridPane.setHalignment(removeButton, HPos.RIGHT);
        GridPane.setConstraints(addButton, 1, 6, 1, 1);
        GridPane.setConstraints(editButton, 2, 6, 1, 1);
        GridPane.setConstraints(removeButton, 3, 6, 1, 1);

        gridPane.getChildren().addAll(
                projectNameLabel, captureSetNameTextField,
                captureTypeLabel, captureSetTypes,
                captureCountTypeLabel, captureCountTextField,
                classPathLabel, classPathTextField, browseButton,
                contentSetLabel, contentList,
                addButton, editButton, removeButton
        );
        setContent(gridPane);
    }

    public void setProject(Project project, CaptureSet captureSet) {
        currentProject = project;
        this.captureSet = captureSet;
        // data injections
        if (captureSet != null) {
            captureSetNameTextField.setText(captureSet.getName());
            captureSetTypes.getSelectionModel().select(captureSet.getType());
            classPathTextField.setText(captureSet.getClassPath());
            captureCountTextField.setText(String.valueOf(captureSet.getCapturePageCount()));
            // build out the content set's uses in this capture.
            ObservableList<String> stringList = FXCollections.observableArrayList();
            List<String> contentSetNames = captureSet.getContentSets();
            // check if each is still a valid file
            List<ContentSet> contentSets = ConfigSerializer.retrieveAllContentSets();
            if (contentSets != null) {
                for (ContentSet contentSet : contentSets) {
                    for (String name : contentSetNames) {
                        if (contentSet.getName().equals(name)) {
                            stringList.add(name);
                        }
                    }
                }
            }
            contentList.getItems().clear();
            contentList.getItems().addAll(stringList);
        }
    }

    @Override
    public void handle(ActionEvent event) {
        Object source = event.getSource();
        if (source.equals(addButton)) {
            ContentSetSelectorDialog dialog = new ContentSetSelectorDialog(mediator, contentList.getItems());
            Optional<ObservableList<ContentSet>> result = dialog.showAndWait();
            // load the project data and enable the ui
            result.ifPresent(contentSet -> addContent(contentSet));
        } else if (source.equals(removeButton)) {
            List<String> selectedItems = contentList.getSelectionModel().getSelectedItems();
            contentList.getItems().removeAll(selectedItems);
            // save teh new state.
            captureSet.getContentSets().clear();
            captureSet.getContentSets().addAll(contentList.getSelectionModel().getSelectedItems());
            captureSet.setComplete(false);
            ConfigSerializer.save(captureSet);
        } else if (source.equals(editButton)) {
            String selectedName = contentList.getSelectionModel().getSelectedItem();
            List<ContentSet> contentSets = ConfigSerializer.retrieveAllContentSets();
            ContentSet selectedContentSet = null;
            if (contentSets != null) {
                for (ContentSet contentSet : contentSets) {
                    if (contentSet.getName().equals(selectedName)) {
                        selectedContentSet = contentSet;
                        break;
                    }
                }
            }
            if (selectedContentSet != null) {
                NewContentSetDialog dialog = new NewContentSetDialog(mediator);
                dialog.setContentSet(selectedContentSet);
                String oldName = selectedContentSet.getName();
                Optional<ContentSet> result = dialog.showAndWait();
                // load the project data and enable the ui
                result.ifPresent(contentSet -> editContent(contentSet, oldName));
            }
        }
    }

    public void editContent(ContentSet selectedContentSet, String oldName) {

        // we only need to save the capture set if the name has changed.
        if (!selectedContentSet.getName().equals(oldName)) {
            List<String> contentSetFileNames = new ArrayList<>();
            List<String> contentSets = captureSet.getContentSets();
            for (String contentSet : contentSets) {
                if (contentSet.equals(oldName)) {
                    contentSetFileNames.add(selectedContentSet.getName());
                } else {
                    contentSetFileNames.add(contentSet);
                }
            }

            captureSet.setContentSets(contentSetFileNames);
            ConfigSerializer.save(captureSet);

            // update the list to reflect the change.
            contentList.getItems().clear();
            contentList.getItems().addAll(contentSetFileNames);
        }
        // mark the set as changed,  we just assume.
        captureSet.setComplete(false);
    }

    public void addContent(ObservableList<ContentSet> selectedContentSets) {
        List<String> contentSetFileNames = new ArrayList<>();
        for (ContentSet contentSet : selectedContentSets) {
            contentSetFileNames.add(contentSet.getName());
        }
        captureSet.setContentSets(contentSetFileNames);
        captureSet.setComplete(false);
        ConfigSerializer.save(captureSet);

        // update the list to reflect the change.
        contentList.getItems().addAll(contentSetFileNames);
    }


}