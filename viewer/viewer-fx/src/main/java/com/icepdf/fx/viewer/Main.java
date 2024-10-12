package com.icepdf.fx.viewer;

import com.icepdf.core.util.PropertiesManager;
import com.icepdf.fx.util.ImageLoader;
import com.icepdf.fx.util.SettingsLoaderTask;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The main class is responsible for displaying the splash screen and doing any settings loading on a separate thread.
 * When the viewer ri is launched for the first time the font manager will read the systems fonts which can take
 * quite a bit of time at which a indeterminate progress bar is used to show that the scan is taking place.
 *
 * @since 6.5
 */
public class Main extends Application {

    private static final Logger logger = Logger.getLogger(Main.class.toString());

    private static final int SPLASH_WIDTH = 640;
    private static final int SPLASH_HEIGHT = 400;
    private static final String ARGS_FILE_PARAMTER = "-loadfile";
    private static final String ARGS_URL_PARAMTER = "-loadurl";
    private static final String JNLP_FILE_PARAMTER = "loadfile";
    private static final String JNLP_URL_PARAMTER = "Loadurl";

    private static ResourceBundle messageBundle;

    private Pane splashPane;
    private ProgressBar loadProgress;
    private Label progressText;
    private Stage initStage;

    public static void main(String[] args) throws Exception {
        launch(args);
    }

    @Override
    public void init() {
        // load message bundle
        messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);

        splashPane = new StackPane();
        splashPane.getStylesheets().add(
                getClass().getResource(PropertiesManager.DEFAULT_SPLASH_CSS).toExternalForm());
        ImageView splashImageView = new ImageView();
        splashImageView.setId("splash-image");
        loadProgress = new ProgressBar();
        loadProgress.setId("progress-bar");
        progressText = new Label("Will find friends for peanuts . . .");
        progressText.getStyleClass().add("progress-label");

        BorderPane splashContentPane = new BorderPane();
        splashPane.getChildren().addAll(splashImageView, splashContentPane);

        // padding is used to move the progress bar to desired location
        Pane progressPane = new VBox();
        progressPane.getStyleClass().add("progress-pane");
        progressPane.getChildren().addAll(progressText, loadProgress);
        splashContentPane.setCenter(progressPane);

        // licence values.
        Pane licencePane = new VBox();
        licencePane.getStyleClass().add("license-pane");
        Label copyrightLabel = new Label(messageBundle.getString("icepdf.fx.viewer.Main.copyRight.label"));
        copyrightLabel.getStyleClass().add("copyright-label");
        licencePane.getChildren().addAll(copyrightLabel);
        splashContentPane.setBottom(licencePane);
        splashPane.getStyleClass().add("splash-layout");
        splashPane.setEffect(new DropShadow());
    }

    @Override
    public void start(final Stage initStage) throws Exception {
        this.initStage = initStage;

        initStage.getIcons().addAll(
                ImageLoader.loadImage("app_icon_128x128.png"),
                ImageLoader.loadImage("app_icon_64x64.png"),
                ImageLoader.loadImage("app_icon_48x48.png"),
                ImageLoader.loadImage("app_icon_32x32.png"),
                ImageLoader.loadImage("app_icon_16x16.png"));
        SettingsLoaderTask fontReaderTask = new SettingsLoaderTask();
        showSplash(initStage, fontReaderTask,
                this::showMainStage);
        new Thread(fontReaderTask).start();
    }

    private void showMainStage() {

        Launcher launcher = Launcher.getInstance();
        // check for -loadfile or -loadurl.
        Map<String, String> jnlpArgs = getParameters().getNamed();
        List<String> args = getParameters().getUnnamed();
        if (jnlpArgs.size() > 0) {
            processJNLPArguments(launcher, jnlpArgs);
        } else if (args.size() > 0) {
            processArguments(launcher, args);
        } else {
            launcher.newViewerWindow();
        }
    }

    private void showSplash(
            final Stage initStage,
            Task<?> task,
            InitCompletionHandler initCompletionHandler) {
        progressText.textProperty().bind(task.messageProperty());
        loadProgress.progressProperty().bind(task.progressProperty());
        task.stateProperty().addListener((observableValue, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                loadProgress.progressProperty().unbind();
                loadProgress.setProgress(1);
                initStage.toFront();
                FadeTransition fadeSplash = new FadeTransition(Duration.seconds(1.2), splashPane);
                fadeSplash.setFromValue(1.0);
                fadeSplash.setToValue(0.0);
                fadeSplash.setOnFinished(actionEvent -> initStage.hide());
                fadeSplash.play();
                // setup callback to start the main application state
                initCompletionHandler.complete();
            }
        });
        // center the splash screen on the primary monitory
        Scene splashScene = new Scene(splashPane, Color.TRANSPARENT);
        initStage.initStyle(StageStyle.TRANSPARENT);
//        initStage.setAlwaysOnTop(true);
        initStage.setScene(splashScene);
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        initStage.setX(bounds.getMinX() + bounds.getWidth() / 2 - SPLASH_WIDTH / 2);
        initStage.setY(bounds.getMinY() + bounds.getHeight() / 2 - SPLASH_HEIGHT / 2);
        initStage.initStyle(StageStyle.TRANSPARENT);
        initStage.show();
    }

    public interface InitCompletionHandler {
        void complete();
    }

    private void processJNLPArguments(Launcher launcher, Map<String, String> jnlpArgs) {
        String file = null;
        String url = null;
        if (jnlpArgs.containsKey(JNLP_FILE_PARAMTER)) {
            file = jnlpArgs.get(JNLP_FILE_PARAMTER).trim();
        } else if (jnlpArgs.containsKey(JNLP_URL_PARAMTER)) {
            url = jnlpArgs.get(JNLP_URL_PARAMTER).trim();
        }
        // load default empty viewer.
        if (file == null && url == null) {
            launcher.newViewerWindow();
        }
        if (file != null) {
            checkAndLaunchFile(launcher, file);
        }
        if (url != null) {
            checkAndLaunchUrl(launcher, url);
        }
    }

    private void processArguments(Launcher launcher, List<String> args) {
        if (args.size() == 2) {
            String command = args.get(0);
            String uri = args.get(1);
            if (ARGS_FILE_PARAMTER.equals(command)) {
                checkAndLaunchFile(launcher, uri);
            } else if (ARGS_URL_PARAMTER.equals(command)) {
                checkAndLaunchUrl(launcher, uri);
            }
        } else {
            launcher.newViewerWindow();
        }
    }

    private void checkAndLaunchFile(Launcher launcher, String file) {
        try {
            Path filePath = Paths.get(file);
            boolean exists = Files.exists(filePath);
            if (exists) {
                launcher.newViewerWindow(filePath);
            } else {
                throw new InvalidPathException(file, "Not found. ");
            }
        } catch (InvalidPathException e) {
            logger.warning("Could not load file path: " + file);
            this.initStage = launcher.newViewerWindow();
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadError.title"));
            alert.setHeaderText(messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.header"));
            MessageFormat formatter = new MessageFormat(
                    messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.content"));
            messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.content");
            alert.setContentText(formatter.format(new Object[]{file}));
            alert.initOwner(initStage);
            alert.showAndWait();
        }
    }

    private void checkAndLaunchUrl(Launcher launcher, String url) {
        try {
            URL fileUrl = new URL(url);
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection con = (HttpURLConnection) fileUrl.openConnection();
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("HEAD");
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                launcher.newViewerWindow(fileUrl);
            } else {
                throw new IllegalStateException();
            }
            con.disconnect();
        } catch (Exception e) {
            logger.warning("Could not load url: " + url);
            this.initStage = launcher.newViewerWindow();
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadError.title"));
            alert.setHeaderText(messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.header"));
            MessageFormat formatter = new MessageFormat(
                    messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.content"));
            messageBundle.getString("icepdf.fx.viewer.Main.dialog.loadUriError.content");
            alert.setContentText(formatter.format(new Object[]{url}));
            alert.initOwner(initStage);
            alert.showAndWait();
        }
    }
}