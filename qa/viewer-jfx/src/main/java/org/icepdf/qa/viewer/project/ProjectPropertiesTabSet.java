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

import javafx.geometry.Side;
import javafx.scene.control.TabPane;
import org.icepdf.qa.config.Project;
import org.icepdf.qa.config.Result;
import org.icepdf.qa.viewer.common.Mediator;

import java.util.List;

/**
 * Main tab set for project data view  We show project summary data, results and secondary data.
 */
public class ProjectPropertiesTabSet extends TabPane {

    private final ProjectTab projectTabSet;
    private final ResultsTab resultsTabSet;

    public ProjectPropertiesTabSet(Mediator mediator) {
        super();
        setSide(Side.TOP);

        projectTabSet = new ProjectTab("Project", mediator);
        resultsTabSet = new ResultsTab("Results", mediator);
        MetaDataTab metaDataTabSet = new MetaDataTab("Metadata", mediator);

        getTabs().addAll(projectTabSet, resultsTabSet, metaDataTabSet);
    }

    public void setProject(Project project) {
        projectTabSet.setProject(project);
        resultsTabSet.setProject(project);
//        metaDataTabSet.setProject()project
    }

    public void setProjectResults(List<Result> results) {
        resultsTabSet.setResults(results);
    }

    public void clearProjectResults() {
        resultsTabSet.clearResults();
    }


    public void setDisableProjectTab(boolean disable) {
        projectTabSet.setDisable(disable);
    }

}
