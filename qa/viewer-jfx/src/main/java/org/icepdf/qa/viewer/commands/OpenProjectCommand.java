package org.icepdf.qa.viewer.commands;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.stage.FileChooser;
import org.icepdf.qa.config.ConfigSerializer;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.common.PreferencesController;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 */
public class OpenProjectCommand implements EventHandler<ActionEvent>, Command {

    private Mediator mediator;

    public OpenProjectCommand(Mediator mediator) {
        this.mediator = mediator;
    }

    @Override
    public void handle(ActionEvent event) {
        execute();
    }

    public void execute() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Project File");
        fileChooser.setInitialDirectory(new File(PreferencesController.getProjectDirectory()));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("QA Project Files", "*.json"));
        File selectedFile = fileChooser.showOpenDialog(mediator.getPrimaryStage());
        if (selectedFile != null) {
            Path projectPath = Paths.get(selectedFile.getAbsolutePath());
            Project project = ConfigSerializer.retrieveProject(projectPath);
            PreferencesController.saveLastUedProject(projectPath);
            mediator.loadProject(project);
        }
    }

}