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

import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.viewer.common.Mediator;

/**
 * Created by pcorl_000 on 2017-02-07.
 */
public class ProjectTab extends Tab {

    protected final Mediator mediator;

    private final ProjectTitledPane projectPane;
    private final CaptureSetPropertyPane captureSetAPane;
    private final CaptureSetPropertyPane captureSetBPane;


    public ProjectTab(String title, Mediator mediator) {
        super(title);
        setClosable(false);
        this.mediator = mediator;

        // build out the project sub panes.
        projectPane = new ProjectTitledPane("Project");
        captureSetAPane = new CaptureSetPropertyPane("Capture Set A", mediator);
        captureSetBPane = new CaptureSetPropertyPane("Project Set B", mediator);

        VBox projectDetails = new VBox();
        projectDetails.getChildren().addAll(projectPane, captureSetAPane, captureSetBPane);
        setContent(projectDetails);
    }

    public void setProject(Project project) {
        projectPane.setProject(project);
        captureSetAPane.setProject(project, project.getCaptureSetA());
        captureSetBPane.setProject(project, project.getCaptureSetB());
    }
}
