package org.icepdf.qa.viewer.common;

import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.icepdf.qa.config.*;
import org.icepdf.qa.tests.AbstractTestTask;
import org.icepdf.qa.tests.TestFactory;
import org.icepdf.qa.viewer.comparitors.ComparatorPane;
import org.icepdf.qa.viewer.project.*;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Mediator/controller does most of lifting for the application.
 */
public class Mediator {

    public static final String TITLE_TEXT = "ICEpdf QA";
    public static final String STATUS_TEXT = "Status: ";

    private final Stage primaryStage;

    // menu items we need to disable.
    private MenuItem newCaptureSet;
    private MenuItem closeProjectMenuItem;
    private MenuItem copyFilePathMenuItem;
    private MenuItem restCaptureSetAMenuItem;
    private MenuItem restCaptureSetBMenuItem;
    private MenuItem highlightMenuItem;
    private MenuItem nextBlendingModeMenuItem;

    // toolbar controls.
    private Button newProjectButton;
    private Button runButton;
    private Button stopButton;
    private Label captureSetALabel;
    private ChoiceBox<CaptureSet> captureSetAChoiceBox;
    private Button newCaptureSetAButton;
    private Label captureSetBLabel;
    private ChoiceBox<CaptureSet> captureSetBChoiceBox;
    private Button newCaptureSetBButton;

    // Project controls.
    private SplitPane projectViewSplitPane;
    private ToolBar statusToolBar;
    private Label statusLabel;

    // status bar
    private ProgressBar progressBar;

    // main layout panels.
    private BorderPane projectBorderPane;
    private ProjectPropertiesTabSet projectTabSet;
    private ProjectCompareView projectCompareView;
    private ComparatorPane comparatorPane;
    private ProjectUtilityPane projectUtilityPane;

    // project structure
    private Project currentProject;
    private AbstractTestTask currentTestTask;

    public Mediator(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Load or create project dialog.
     */
    public void loadOrCreateProject() {
        // check if have stored the last opned project.
        Path lastUsedProjectPath = PreferencesController.getLastUsedProjectPath();
        if (lastUsedProjectPath != null) {
            Project project = ConfigSerializer.retrieveProject(lastUsedProjectPath);
            loadProject(project);
            PreferencesController.saveLastUedProject(lastUsedProjectPath);
        } else {
            showNewProjectDialog();
        }
    }

    /**
     * Executor for starting a test on the currently opened project.
     */
    public void createTestInstanceAndRun() {
        // clear the console
        if (projectUtilityPane != null)
            projectUtilityPane.clearConsole();
        if (comparatorPane != null)
            comparatorPane.openResult(null);
        // get the test and try to run
        currentTestTask = TestFactory.getInstance().createTestInstance(this);
        progressBar.progressProperty().bind(currentTestTask.progressProperty());
        new Thread(currentTestTask).start();
    }

    /**
     * Called from AbstractTestTask to update the UI table view of results objects.
     *
     * @param results test result.
     */
    public void addProjectResults(List<Result> results) {
        //  pass results off to project tabs.
        projectTabSet.setProjectResults(results);
    }

    /**
     * Called just before a test starts to clear the results table.
     */
    public void resetProjectResults() {
        projectTabSet.clearProjectResults();
    }

    public void clearCaptureSetResultsA() {
        if (currentProject != null && currentProject.getCaptureSetA() != null) {
            clearCaptureSetResults(currentProject.getCaptureSetA());
            currentProject.getCaptureSetA().setComplete(false);
            // reset class loader so we pick up on any changed jars.
            currentProject.getCaptureSetA().setClassLoader(null);
            ConfigSerializer.save(currentProject);
        }
    }

    public void clearCaptureSetResultsB() {
        if (currentProject != null && currentProject.getCaptureSetB() != null) {
            clearCaptureSetResults(currentProject.getCaptureSetB());
            currentProject.getCaptureSetB().setComplete(false);
            // reset class loader so we pick up on any changed jars.
            currentProject.getCaptureSetB().setClassLoader(null);
            ConfigSerializer.save(currentProject);
        }
    }

    /**
     * Clears/removes all results files associated with the proje
     *
     * @param captureSet
     */
    private void clearCaptureSetResults(CaptureSet captureSet) {
        Path resultsFolder = Paths.get(PreferencesController.getResultsPathDirectory(),
                captureSet.getCaptureSetPath().getFileName().toString());
        if (Files.isDirectory(resultsFolder)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultsFolder)) {
                for (Path entry : stream) {
                    Files.delete(entry);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void cancelTestInstance() {
        if (currentTestTask != null && currentTestTask.isRunning()) {
            currentTestTask.cancel(true);
            statusLabel.setText(STATUS_TEXT + currentTestTask.getMessage());
        }
    }

    /**
     * Load the current project and populate the UI with project data.
     *
     * @param project
     */
    public void loadProject(Project project) {
        if (currentProject != null) {
            closeCurrentProject();
        }
        // setup the new project.
        currentProject = project;
        primaryStage.setTitle(TITLE_TEXT + " - " + project.getName());

        setOpenProjectState();

        // read all capture sets that are defined.
        refreshCaptureSetChoices();

        // update the tabset
        projectTabSet.setProject(currentProject);

        // try and create a comparitor view
        projectCompareView.setProject(project);

        // clear the comparitor.
        if (comparatorPane != null)
            comparatorPane.openResult(null);
    }

    public void closeCurrentProject() {

        primaryStage.setTitle(TITLE_TEXT);

        // disable the view.
        setClosedProjectState();
    }

    /**
     * Toggles the diff view filter for the current comparitor view.
     */
    public void toggleDiffFilter() {
        comparatorPane.toggleDiffFilter();
    }

    /**
     * Changes the diff filter to the next available one.
     */
    public void nextDiffFilter() {
        comparatorPane.nextDiffFilter();
    }

    /**
     * Open a result in the comparitor view,  enable menu items.
     *
     * @param result result to open.
     */
    public void openResult(Result result) {
        if (result != null) {
            highlightMenuItem.setDisable(false);
            nextBlendingModeMenuItem.setDisable(false);
        } else {
            highlightMenuItem.setDisable(true);
            nextBlendingModeMenuItem.setDisable(true);
        }
        comparatorPane.openResult(result);
    }

    /**
     * Action call when a users selects a new capture set form the toolbar choice box.
     */
    public void captureSetASelection() {
        CaptureSet captureSet = captureSetAChoiceBox.getSelectionModel().getSelectedItem();
        if (captureSet != null) {
            currentProject.setCaptureSetAConfigFile(captureSet.getCaptureSetPath().getFileName().toString());
            refreshCaptureSets();
            projectTabSet.setProject(currentProject);
            ConfigSerializer.save(currentProject);
        }
    }

    /**
     * Action call when a users selects a new capture set form the toolbar choice box.
     */
    public void captureSetBSelection() {
        CaptureSet captureSet = captureSetBChoiceBox.getSelectionModel().getSelectedItem();
        if (captureSet != null) {
            currentProject.setCaptureSetBConfigFile(captureSet.getCaptureSetPath().getFileName().toString());
            refreshCaptureSets();
            projectTabSet.setProject(currentProject);
            ConfigSerializer.save(currentProject);
        }
    }

    /**
     * Utility to show new project dialog
     */
    public void showNewProjectDialog() {
        NewProjectDialog dialog = new NewProjectDialog(this);
        Optional<Project> result = dialog.showAndWait();
        // load the project data and enable the ui
        result.ifPresent(this::loadProject);
    }

    /**
     * Utility to show new captures set dialog.
     *
     * @param captureSetChoiceBox reference to choice box to update with new capture set.
     */
    public void showNewCaptureSetDialog(ChoiceBox captureSetChoiceBox) {
        NewCaptureSetDialog dialog = new NewCaptureSetDialog(this);
        Optional<CaptureSet> result = dialog.showAndWait();
        // load the project data and enable the ui
        result.ifPresent(captureSet -> updateProjectCaptureSet(captureSetChoiceBox, captureSet));
    }

    /**
     * Utility for creating new content set.  Called form file menu.
     */
    public void showNewContentSetDialog() {
        NewContentSetDialog dialog = new NewContentSetDialog(this);
        Optional<ContentSet> result = dialog.showAndWait();
        /* load the project data and enable the ui */
        result.ifPresent(this::addContent);
    }

    public void addContent(ContentSet contentSet) {
        // currently black as we only call this from the menu item execution.
    }

    /**
     * Utility to load refresh a projects content sets file association and updates the choice boxes.
     */
    public void refreshCaptureSetChoices() {
        CaptureSet captureSetA = null;
        if (currentProject.getCaptureSetAConfigFile() != null) {
            captureSetA = ConfigSerializer.retrieveCaptureSet(currentProject.getCaptureSetAConfigFile());
            currentProject.setCaptureSetA(captureSetA);
        }
        CaptureSet captureSetB = null;
        if (currentProject.getCaptureSetBConfigFile() != null) {
            captureSetB = ConfigSerializer.retrieveCaptureSet(currentProject.getCaptureSetBConfigFile());
            currentProject.setCaptureSetB(captureSetB);
        }
        refreshCaptureSetChoiceBox(captureSetAChoiceBox, captureSetA);
        refreshCaptureSetChoiceBox(captureSetBChoiceBox, captureSetB);
    }

    /**
     * Retrieves the associated captures sets and assigns them to the project by object reference.
     */
    public void refreshCaptureSets() {
        if (currentProject.getCaptureSetAConfigFile() != null) {
            CaptureSet captureSetA = ConfigSerializer.retrieveCaptureSet(currentProject.getCaptureSetAConfigFile());
            currentProject.setCaptureSetA(captureSetA);
        }
        if (currentProject.getCaptureSetBConfigFile() != null) {
            CaptureSet captureSetB = ConfigSerializer.retrieveCaptureSet(currentProject.getCaptureSetBConfigFile());
            currentProject.setCaptureSetB(captureSetB);
        }
    }

    protected void refreshCaptureSetChoiceBox(ChoiceBox<CaptureSet> captureSetChoiceBox, CaptureSet selectedCaptureSet) {
        List<CaptureSet> captureSets = ConfigSerializer.retrieveAllCaptureSets();
        if (captureSetChoiceBox == null) {
            return;
        }
        if (captureSets != null) {
            for (CaptureSet captureSet : captureSets) {
                captureSetChoiceBox.getItems().add(captureSet);
            }
            if (selectedCaptureSet != null) {
                captureSetChoiceBox.setValue(selectedCaptureSet);
            }
        }
    }

    public void updateProjectCaptureSet(ChoiceBox captureSetChoiceBox, CaptureSet captureSet) {
        if (captureSetChoiceBox != null) {
            if (captureSetChoiceBox.equals(captureSetAChoiceBox)) {
                if (captureSet.getCaptureSetPath() != null) {
                    currentProject.setCaptureSetAConfigFile(captureSet.getCaptureSetPath().getFileName().toString());
                }
            } else {
                if (captureSet.getCaptureSetPath() != null) {
                    currentProject.setCaptureSetBConfigFile(captureSet.getCaptureSetPath().getFileName().toString());
                }
            }
            projectTabSet.setProject(currentProject);
            ConfigSerializer.save(currentProject);
        }
        refreshCaptureSetChoiceBox(captureSetChoiceBox, captureSet);
        refreshCaptureSetChoices();
    }

    public void setStartTestTaskGuiState() {
        projectUtilityPane.clearConsole();
        runButton.setDisable(true);
        stopButton.setDisable(false);
        // disable the project controls
        projectTabSet.setDisableProjectTab(true);
        // disable the toolbar
        newProjectButton.setDisable(true);
        captureSetALabel.setDisable(true);
        captureSetBLabel.setDisable(true);
        captureSetAChoiceBox.setDisable(true);
        captureSetBChoiceBox.setDisable(true);
        newCaptureSetAButton.setDisable(true);
        newCaptureSetBButton.setDisable(true);

        progressBar.setVisible(true);
        statusLabel.setText(STATUS_TEXT);

    }

    public void setStopTestTaskGuiState() {
        runButton.setDisable(false);
        stopButton.setDisable(true);
        // disable the project controls
        projectTabSet.setDisableProjectTab(false);
        // disable the toolbar
        newProjectButton.setDisable(false);
        captureSetALabel.setDisable(false);
        captureSetBLabel.setDisable(false);
        captureSetAChoiceBox.setDisable(false);
        captureSetBChoiceBox.setDisable(false);
        newCaptureSetAButton.setDisable(false);
        newCaptureSetBButton.setDisable(false);
        progressBar.setVisible(false);

        statusLabel.setText(STATUS_TEXT + currentTestTask.getMessage());
    }

    public void setOpenProjectState() {
        newProjectButton.setDisable(false);

        captureSetALabel.setDisable(false);
        captureSetAChoiceBox.setDisable(false);
        newCaptureSetAButton.setDisable(false);

        captureSetBLabel.setDisable(false);
        captureSetBChoiceBox.setDisable(false);
        newCaptureSetBButton.setDisable(false);

        runButton.setDisable(false);
        stopButton.setDisable(true);

        projectViewSplitPane.setDisable(false);
        statusToolBar.setDisable(false);

        // open project state.
        newCaptureSet.setDisable(false);
        closeProjectMenuItem.setDisable(false);
        highlightMenuItem.setDisable(true);
        nextBlendingModeMenuItem.setDisable(true);

        restCaptureSetAMenuItem.setDisable(false);
        restCaptureSetBMenuItem.setDisable(false);
    }

    public void setClosedProjectState() {
        newProjectButton.setDisable(false);

        captureSetALabel.setDisable(false);
        captureSetAChoiceBox.setDisable(false);
        newCaptureSetAButton.setDisable(false);

        captureSetBLabel.setDisable(false);
        captureSetBChoiceBox.setDisable(false);
        newCaptureSetBButton.setDisable(false);

        runButton.setDisable(false);
        stopButton.setDisable(false);

        projectViewSplitPane.setDisable(false);
        statusToolBar.setDisable(false);

        // close project state.
        newCaptureSet.setDisable(true);
        closeProjectMenuItem.setDisable(true);
        copyFilePathMenuItem.setDisable(true);
        highlightMenuItem.setDisable(true);
        nextBlendingModeMenuItem.setDisable(true);
        restCaptureSetAMenuItem.setDisable(true);
        restCaptureSetBMenuItem.setDisable(true);
    }

    public Project getCurrentProject() {
        return currentProject;
    }

    public void setNewCaptureSet(MenuItem newCaptureSet) {
        this.newCaptureSet = newCaptureSet;
    }

    public void setCloseProjectMenuItem(MenuItem closeProjectMenuItem) {
        this.closeProjectMenuItem = closeProjectMenuItem;
    }

    public void setCopyFilePathMenuItem(MenuItem copyFilePathMenuItem) {
        this.copyFilePathMenuItem = copyFilePathMenuItem;
    }

    public void setResetCaptureAMenuItem(MenuItem restCaptureSetAMenuItem) {
        this.restCaptureSetAMenuItem = restCaptureSetAMenuItem;
    }

    public void setResetCaptureBMenuItem(MenuItem restCaptureSetBMenuItem) {
        this.restCaptureSetBMenuItem = restCaptureSetBMenuItem;
    }

    public void setHighlightMenuItem(MenuItem highlightMenuItem) {
        this.highlightMenuItem = highlightMenuItem;
    }

    public void setNextBlendingModeMenuItem(MenuItem nextBlendingModeMenuItem) {
        this.nextBlendingModeMenuItem = nextBlendingModeMenuItem;
    }

    public void setNewProjectButton(Button newProjectButton) {
        this.newProjectButton = newProjectButton;
    }

    public void setRunButton(Button runButton) {
        this.runButton = runButton;
    }

    public void setStopButton(Button stopButton) {
        this.stopButton = stopButton;
    }

    public void setCaptureSetALabel(Label captureSetALabel) {
        this.captureSetALabel = captureSetALabel;
    }

    public void setCaptureSetAChoiceBox(ChoiceBox<CaptureSet> captureSetAChoiceBox) {
        this.captureSetAChoiceBox = captureSetAChoiceBox;
    }

    public void setNewCaptureSetAButton(Button newCaptureSetAButton) {
        this.newCaptureSetAButton = newCaptureSetAButton;
    }

    public void setCaptureSetBLabel(Label captureSetBLabel) {
        this.captureSetBLabel = captureSetBLabel;
    }

    public void setCaptureSetBChoiceBox(ChoiceBox<CaptureSet> captureSetBChoiceBox) {
        this.captureSetBChoiceBox = captureSetBChoiceBox;
    }

    public void setNewCaptureSetBButton(Button newCaptureSetBButton) {
        this.newCaptureSetBButton = newCaptureSetBButton;
    }

    public void setProjectViewSplitPane(SplitPane projectViewSplitPane) {
        this.projectViewSplitPane = projectViewSplitPane;
    }

    public void setStatusToolBar(ToolBar statusToolBar) {
        this.statusToolBar = statusToolBar;
    }

    public void setStatusLabel(Label statusLabel) {
        this.statusLabel = statusLabel;
    }

    public void setProjectBorderPane(BorderPane projectBorderPane) {
        this.projectBorderPane = projectBorderPane;
    }

    public void setProjectTabSet(ProjectPropertiesTabSet projectTabSet) {
        this.projectTabSet = projectTabSet;
    }

    public void setProjectCompareView(ProjectCompareView projectCompareView) {
        this.projectCompareView = projectCompareView;
    }

    public void setComparatorPane(ComparatorPane comparatorPane) {
        this.comparatorPane = comparatorPane;
    }

    public void setProjectUtilityPane(ProjectUtilityPane projectUtilityPane) {
        this.projectUtilityPane = projectUtilityPane;
    }

    public void setProgressBar(ProgressBar progressBar) {
        this.progressBar = progressBar;
    }
}
