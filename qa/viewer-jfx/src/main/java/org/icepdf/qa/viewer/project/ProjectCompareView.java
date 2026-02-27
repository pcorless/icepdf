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

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.comparitors.ComparatorPane;
import org.icepdf.qa.viewer.comparitors.ComparatorsViewFactory;

import java.io.PrintStream;

/**
 * Main project compare view.  Top contains the comparator and the button shows the console area.
 */
public class ProjectCompareView extends SplitPane {

    private TextArea consoleTextArea;
    private PrintStream printStream;
    private final Mediator mediator;

    public ProjectCompareView(Mediator mediator) {
        super();
        this.mediator = mediator;
        setOrientation(Orientation.VERTICAL);

        // get the correct view comparator for he project.
        mediator.getCurrentProject();

        ProjectUtilityPane utilityPane = new ProjectUtilityPane(mediator);
        mediator.setProjectUtilityPane(utilityPane);
        SplitPane.setResizableWithParent(utilityPane, false);

        getItems().addAll(new HBox(), utilityPane);
    }

    /**
     * Load the project and load the correct comparator view.
     *
     */
    public void setProject(Project currentProject) {
        if (currentProject.getCaptureSetA() != null && currentProject.getCaptureSetB() != null &&
                currentProject.getCaptureSetA().getType() == currentProject.getCaptureSetB().getType()) {
            ComparatorPane viewComparator = ComparatorsViewFactory.buildComparatorView(
                    currentProject.getCaptureSetA().getType(), mediator);
            mediator.setComparatorPane(viewComparator);
            getItems().remove(0);
            getItems().add(0, viewComparator);
        }
    }

}