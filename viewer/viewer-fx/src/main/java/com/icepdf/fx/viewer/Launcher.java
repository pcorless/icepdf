package com.icepdf.fx.viewer;

import com.icepdf.core.util.PropertiesManager;
import com.icepdf.fx.scene.control.DocumentView;
import com.icepdf.fx.util.ImageLoader;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Uses the build class to assemble full viewer RI.
 */
public class Launcher {

    private static Preferences prefs = Preferences.userNodeForPackage(Launcher.class);
    private static String VIEWER_WINDOW_WIDTH = "last_used_width";
    private static String VIEWER_WINDOW_HEIGHT = "last_used_width";
    private static String VIEWER_WINDOW_X = "last_used_X";
    private static String VIEWER_WINDOW_Y = "last_used_Y";

    private static Launcher launcher;

//    private PropertiesManager properties;

//    private ArrayList<Controller> controllers;

//    private long newWindowInvocationCounter = 0;

//    private ResourceBundle messageBundle = null;

    private Launcher() {
    }

    public static Launcher getInstance() {
        if (launcher == null) {
            launcher = new Launcher();
        }
        return launcher;
    }

    public Stage newViewerWindow() {
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);

        Stage mainStage = new Stage(StageStyle.DECORATED);
        mainStage.setTitle(messageBundle.getString("icepdf.fx.viewer.ViewerPane.default.title"));
        mainStage.getIcons().addAll(
                ImageLoader.loadImage("app_icon_128x128.png"),
                ImageLoader.loadImage("app_icon_64x64.png"),
                ImageLoader.loadImage("app_icon_48x48.png"),
                ImageLoader.loadImage("app_icon_32x32.png"),
                ImageLoader.loadImage("app_icon_16x16.png"));


        BorderPane borderPane = new BorderPane();

        // zoom controls.
        Slider zoomSlider = new Slider(0.05, 8, 1.0);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.setMajorTickUnit(0.05);
        zoomSlider.setBlockIncrement(0.05);
        zoomSlider.setMinorTickCount(0);
        zoomSlider.setShowTickLabels(false);
        zoomSlider.setShowTickMarks(false);
        Button zoomInButton = new Button("-");
        zoomInButton.setOnAction(event -> zoomSlider.decrement());
        Button zoomOutButton = new Button("+");
        zoomOutButton.setOnAction(event -> zoomSlider.increment());

        // rotation controls
        Slider rotationSlider = new Slider(-180, 180, 0);
        rotationSlider.setSnapToTicks(true);
        rotationSlider.setMajorTickUnit(15);
        rotationSlider.setBlockIncrement(15);
        rotationSlider.setMinorTickCount(0);
        rotationSlider.setShowTickLabels(false);
        rotationSlider.setShowTickMarks(false);
        Button rotateLeftButton = new Button("-");
        rotateLeftButton.setOnAction(event -> rotationSlider.decrement());
        Button rotateRightButton = new Button("+");
        rotateRightButton.setOnAction(event -> rotationSlider.increment());

        // view mode controls

        Document document = new Document();

        try {
//            document.setFile("E:\\pdf-qa\\metrics\\content-parser\\ottawa_cycling_map.pdf");
//            document.setFile("E:\\pdf-qa\\metrics\\content-parser\\map.pdf");
//            document.setFile("E:\\pdf-qa\\metrics\\content-parser\\SF_923200345630.pdf");
//            document.setFile("E:\\pdf-qa\\PDF32000_2008.pdf");
//            document.setFile("d:\\pdf-qa\\fonts\\cid\\R&D-05-Carbon.pdf");
            document.setFile("d:\\pdf-qa\\metrics\\full-monty\\fcom.pdf");
//            document.setFile("E:\\pdf-qa\\metrics\\full-monty\\ACEASPA_LIBGIO_2013_00001_53923_000001_0703467_V3.pdf");
        } catch (PDFException e) {
            e.printStackTrace();
        } catch (PDFSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // main view model
        DocumentView documentView = new DocumentView(document);

        // setup scale binding for touch and wheel mouse interaction
        documentView.scaleProperty().bindBidirectional(zoomSlider.valueProperty());
        documentView.scaleIncrementValueProperty().bind(zoomSlider.blockIncrementProperty());
        documentView.scaleMaxValueProperty().bind(zoomSlider.minProperty());
        documentView.scaleMaxValueProperty().bind(zoomSlider.maxProperty());

        // rotation slider binding.
        documentView.rotationProperty().bind(rotationSlider.valueProperty());
        borderPane.setCenter(documentView);

        // setup view mode controls.

        // but the toolbar together
        ToolBar toolbar = new ToolBar();
        toolbar.getItems().addAll(
                new Label("Scale"), zoomInButton, zoomSlider, zoomOutButton,
                new Separator(),
                new Label("Rotation"), rotateLeftButton, rotationSlider, rotateRightButton);
        borderPane.setTop(toolbar);

        mainStage.setScene(new Scene(borderPane));
        calculateStageLocation(mainStage);
        mainStage.setOnCloseRequest(event -> {
            prefs.putDouble(VIEWER_WINDOW_X, mainStage.getX());
            prefs.putDouble(VIEWER_WINDOW_Y, mainStage.getY());
            prefs.putDouble(VIEWER_WINDOW_WIDTH, mainStage.getWidth());
            prefs.putDouble(VIEWER_WINDOW_HEIGHT, mainStage.getHeight());
        });
        mainStage.show();
        mainStage.toFront();

        return mainStage;
    }

    public Stage newViewerWindow(Path path) {
        return newViewerWindow();
    }

    public Stage newViewerWindow(URL url) {
        return newViewerWindow();
    }

    public void disposeViewerWindow(Object controller, Scene scene) {

    }

    public void minimiseAllViewerWindows() {

    }

//    public void bringAllViewerWindowsToFront(Object frontMost){
//
//    }
//
//    public void bringWindowToFront(int index);

//    public List getWindowDocumentOriginList(Controller giveIndex);

    public void quit(Object controller, Scene viewer) {

    }

    private void calculateStageLocation(Stage stage) {
        final Rectangle2D bounds = Screen.getPrimary().getBounds();

        double width = prefs.getDouble(VIEWER_WINDOW_WIDTH, 800);
        double height = prefs.getDouble(VIEWER_WINDOW_HEIGHT, 600);

        // default center for width on primary screen.
        double x = bounds.getMinX() + bounds.getWidth() / 2 - width / 2;
        double y = bounds.getMinY() + bounds.getHeight() / 2 - height / 2;

        double previousX = prefs.getDouble(VIEWER_WINDOW_X, x);
        double previousY = prefs.getDouble(VIEWER_WINDOW_Y, y);

        // quick check to make sure the viewer will be visable in at least one screen, if not we default to primary
        ObservableList<Screen> screens = Screen.getScreensForRectangle(previousX, previousY, width, height);
        if (screens.size() == 0) {
            previousX = x;
            previousY = y;
        }

        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(previousX);
        stage.setY(previousY);
    }

}
