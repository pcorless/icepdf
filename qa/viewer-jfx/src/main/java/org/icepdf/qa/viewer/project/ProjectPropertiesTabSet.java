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
    private final MetaDataTab metaDataTabSet;

    public ProjectPropertiesTabSet(Mediator mediator) {
        super();
        setSide(Side.TOP);

        projectTabSet = new ProjectTab("Project", mediator);
        resultsTabSet = new ResultsTab("Results", mediator);
        metaDataTabSet = new MetaDataTab("Metadata", mediator);

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
