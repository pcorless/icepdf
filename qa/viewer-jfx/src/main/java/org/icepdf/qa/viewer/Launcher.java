package org.icepdf.qa.viewer;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.icepdf.qa.config.CaptureSet;
import org.icepdf.qa.viewer.commands.*;
import org.icepdf.qa.viewer.common.Mediator;
import org.icepdf.qa.viewer.project.ProjectCompareView;
import org.icepdf.qa.viewer.project.ProjectPropertiesTabSet;
import org.icepdf.qa.viewer.utilities.ImageLoader;

/**
 * QA Launcher for testing ICEpdf library.  The test harness was written using pure JavaFX and was designed ot
 * aid developers in creating content sets and capture sets as needed for various version of the library.  The
 * test harness allows for results to be viewed in real time and config to be as easy as possible to setup
 * and verify.
 * <p>
 * The launcher is project based and a project contains a capture set A and B.  Each capture set can have
 * one or more content sets.  A content set specifies a grouping of a common files that are located in a directory.
 * A capture set defines captures for set of content sets,  generally captures are thought as of image captures but
 * they can be of any type.  The capture set also defines the classpath used to for the captures and the number of
 * pages captured.
 */
public class Launcher extends Application {

    private Mediator mediator;

    private Stage primaryStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
//        setUserAgentStylesheet(STYLESHEET_MODENA);

        // our main primary stage.
        this.primaryStage = primaryStage;

        // core mediator to try and keeps various UI elements from knowing too much about each other.
        mediator = new Mediator(primaryStage);

        // build Menu Bar
        BorderPane rootBorderPane = new BorderPane();
        rootBorderPane.setTop(buildMenuBar());

        // build the main project content area
        Pane projectViewContent = createProjectViewContent();
        mediator.setClosedProjectState();
        rootBorderPane.setCenter(projectViewContent);

        Scene scene = new Scene(rootBorderPane, 1024, 768);
        // build out the icon state.
        primaryStage.getIcons().addAll(
                ImageLoader.loadImage("icepdf-app-icon-32x32.png"),
                ImageLoader.loadImage("icepdf-app-icon-64x64.png"));
        primaryStage.setTitle(Mediator.TITLE_TEXT);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            mediator.cancelTestInstance();
        });
        primaryStage.show();

        // check for last opened project
        mediator.loadOrCreateProject();
    }

    private BorderPane createProjectViewContent() {
        BorderPane projectBorderPane = new BorderPane();
        projectBorderPane.setTop(buildProjectToolBar());
        projectBorderPane.setCenter(buildViewerPane());
        projectBorderPane.setBottom(buildStatusToolBar());
        mediator.setProjectBorderPane(projectBorderPane);
        return projectBorderPane;
    }

    private Parent buildProjectToolBar() {
        ToolBar projectToolbar = new ToolBar();

        // new project
        Button newProjectButton = new Button("New Project");
        newProjectButton.setStyle(String.format("-fx-base: %s;", "indigo"));
        newProjectButton.setOnAction(new CreateProjectCommand(mediator));
        mediator.setNewProjectButton(newProjectButton);

        // Capture Set one
        Label captureSetALabel = new Label("Capture Set A:");
        mediator.setCaptureSetALabel(captureSetALabel);
        ChoiceBox<CaptureSet> captureSetAChoiceBox = new ChoiceBox<>();
        captureSetAChoiceBox.setOnAction((event) -> mediator.captureSetASelection());
        mediator.setCaptureSetAChoiceBox(captureSetAChoiceBox);
        Button newCaptureSetAButton = new Button("New");
        newCaptureSetAButton.setOnAction(event -> mediator.showNewCaptureSetDialog(captureSetAChoiceBox));
        mediator.setNewCaptureSetAButton(newCaptureSetAButton);

        // Capture Set two
        Label captureSetBLabel = new Label("Capture Set B:");
        mediator.setCaptureSetBLabel(captureSetBLabel);
        ChoiceBox<CaptureSet> captureSetBChoiceBox = new ChoiceBox<>();
        captureSetBChoiceBox.setOnAction((event) -> mediator.captureSetBSelection());
        mediator.setCaptureSetBChoiceBox(captureSetBChoiceBox);
        Button newCaptureSetBButton = new Button("New");
        newCaptureSetBButton.setOnAction(event -> mediator.showNewCaptureSetDialog(captureSetBChoiceBox));
        mediator.setNewCaptureSetBButton(newCaptureSetBButton);

        // testAndAnalyze control buttons.
        Button runButton = new Button("Run");
        runButton.setStyle(String.format("-fx-base: %s;", "green"));
        runButton.setOnAction(event -> mediator.createTestInstanceAndRun());
        mediator.setRunButton(runButton);

        Button stopButton = new Button("Stop");
        stopButton.setStyle(String.format("-fx-base: %s;", "red"));
        stopButton.setOnAction(event -> mediator.cancelTestInstance());
        mediator.setStopButton(stopButton);

        projectToolbar.getItems().addAll(
                newProjectButton,
                new Separator(),
                captureSetALabel, captureSetAChoiceBox, newCaptureSetAButton,
                captureSetBLabel, captureSetBChoiceBox, newCaptureSetBButton,
                new Separator()
        );
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        projectToolbar.getItems().addAll(spacer, runButton, stopButton);

        return new VBox(20, projectToolbar);
    }

    /**
     * Bottom frame of ui, shows progress of running test.
     *
     * @return toolbar containing status label and progress bar.
     */
    private ToolBar buildStatusToolBar() {
        ToolBar statusToolBar = new ToolBar();
        mediator.setStatusToolBar(statusToolBar);
        Label statusLabel = new Label(Mediator.STATUS_TEXT);
        mediator.setStatusLabel(statusLabel);
        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        mediator.setProgressBar(progressBar);
        HBox hBox = new HBox();
        hBox.getChildren().addAll(statusLabel, progressBar);
        statusToolBar.getItems().addAll(hBox);
        return statusToolBar;
    }

    private Parent buildViewerPane() {
        // tab set of various properties of the project.
        ProjectPropertiesTabSet projectTabSet = new ProjectPropertiesTabSet(mediator);
        mediator.setProjectTabSet(projectTabSet);

        // main compare view, contains diff implementation and console log.
        ProjectCompareView projectCompareView = new ProjectCompareView(mediator);
        mediator.setProjectCompareView(projectCompareView);

        // setup main split pain for project view.
        SplitPane projectViewSplitPane = new SplitPane();
        projectViewSplitPane.getItems().addAll(projectTabSet, projectCompareView);
        projectViewSplitPane.setDividerPosition(0, 0.35);
        SplitPane.setResizableWithParent(projectTabSet, false);
        mediator.setProjectViewSplitPane(projectViewSplitPane);
        return projectViewSplitPane;
    }

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();
        // file
        Menu fileMenuItem = new Menu("File");
        MenuItem newProjectMenuItem = new MenuItem("New Project");
        newProjectMenuItem.setOnAction(new CreateProjectCommand(mediator));
        fileMenuItem.getItems().add(newProjectMenuItem);
        MenuItem newCaptureSet = new MenuItem("New Capture Set");
        newCaptureSet.setOnAction(event -> mediator.showNewCaptureSetDialog(null));
        mediator.setNewCaptureSet(newProjectMenuItem);
        fileMenuItem.getItems().add(newCaptureSet);
        MenuItem newContentSetMenuItem = new MenuItem("New Content Set");
        newContentSetMenuItem.setOnAction(event -> mediator.showNewContentSetDialog());
        fileMenuItem.getItems().add(newContentSetMenuItem);
        SeparatorMenuItem newSeparator = new SeparatorMenuItem();
        fileMenuItem.getItems().add(newSeparator);
        MenuItem openProjectMenuItem = new MenuItem("Open Project");
        openProjectMenuItem.setOnAction(new OpenProjectCommand(mediator));
        fileMenuItem.getItems().add(openProjectMenuItem);
        MenuItem closeProjectMenuItem = new MenuItem("Close Project");
        closeProjectMenuItem.setOnAction(event -> mediator.closeCurrentProject());
        mediator.setCloseProjectMenuItem(closeProjectMenuItem);
        fileMenuItem.getItems().add(closeProjectMenuItem);
        SeparatorMenuItem openSeparator = new SeparatorMenuItem();
        fileMenuItem.getItems().add(openSeparator);
        MenuItem exitMenuItem = new MenuItem("Exit");
        exitMenuItem.setOnAction(new ExitCommand(primaryStage));
        fileMenuItem.getItems().add(exitMenuItem);
        // edit.
        Menu editMenuItem = new Menu("Edit");
        MenuItem copyFilePathMenuItem = new MenuItem("Copy File Path");
        mediator.setCopyFilePathMenuItem(copyFilePathMenuItem);
        MenuItem resetCaptureAMenuItem = new MenuItem("Reset Capture A");
        resetCaptureAMenuItem.setOnAction(event -> mediator.clearCaptureSetResultsA());
        mediator.setResetCaptureAMenuItem(resetCaptureAMenuItem);
        MenuItem resetCaptureBMenuItem = new MenuItem("Reset Capture B");
        resetCaptureBMenuItem.setOnAction(event -> mediator.clearCaptureSetResultsB());
        mediator.setResetCaptureBMenuItem(resetCaptureBMenuItem);
        editMenuItem.getItems().addAll(resetCaptureAMenuItem, resetCaptureBMenuItem);
        // view.
        Menu viewMenuItem = new Menu("View");
        MenuItem fullScreenMenuItem = new MenuItem("Full Screen");
        fullScreenMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        MenuItem highlightMenuItem = new MenuItem("Toggle Blending Mode");
        highlightMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));
        highlightMenuItem.setOnAction(new ToggleDiffFilterCommand(mediator));
        mediator.setHighlightMenuItem(highlightMenuItem);
        MenuItem nextBlendingModeMenuItem = new MenuItem("Next Blending Mode");
        nextBlendingModeMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        nextBlendingModeMenuItem.setOnAction(new NextDiffFilterCommand(mediator));
        mediator.setNextBlendingModeMenuItem(nextBlendingModeMenuItem);
        viewMenuItem.getItems().addAll(fullScreenMenuItem, new SeparatorMenuItem(), highlightMenuItem, nextBlendingModeMenuItem);
        // help
        Menu helpMenuItem = new Menu("Help");
        MenuItem aboutMenuItem = new MenuItem("About QA Launcher");
        helpMenuItem.getItems().add(aboutMenuItem);
        // put the menu together.
        menuBar.getMenus().addAll(fileMenuItem, editMenuItem, viewMenuItem, helpMenuItem);
        return menuBar;
    }
}
