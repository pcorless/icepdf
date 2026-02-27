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

    private final Mediator mediator;

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