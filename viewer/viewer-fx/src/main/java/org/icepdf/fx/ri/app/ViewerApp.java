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
package org.icepdf.fx.ri.app;

import org.icepdf.fx.i18n.Messages;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.icepdf.fx.ri.FileHistory;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.document.DocumentPane;
import org.icepdf.fx.ri.Theme;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.ViewMode;
import org.icepdf.fx.fonts.FontSettings;
import org.icepdf.fx.ri.prefs.FontDirectories;
import org.icepdf.fx.ri.prefs.ImagingSettings;
import org.icepdf.fx.ri.prefs.PerformanceSettings;
import org.icepdf.fx.ri.ui.DevToolsWindow;
import org.icepdf.fx.ri.prefs.PreferencesDialog;
import org.icepdf.fx.ri.prefs.ViewerPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ICEpdf JavaFX viewer application: {@link PdfViewer} windows sharing one set of
 * {@link ViewerPreferences} and one {@link UserLayout}.  It adds what an application owns and a
 * viewer doesn't: the windows, "New Window" and "Exit", Open Recent, the preferences dialog, window
 * placement, opening links in a browser and the prompt for unsaved changes.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:run --args="/path/to/file.pdf"}.  Every file named on the
 * command line opens as a tab of the first window (a window each, if the features turn tabs off).
 */
public class ViewerApp extends Application {

    private static final Logger logger = Logger.getLogger(ViewerApp.class.getName());

    /** System property naming a features file; see {@link #loadFeatures()}. */
    public static final String FEATURES_PROPERTY = "org.icepdf.fx.features";

    /** A window: its stage and the viewer in it. */
    public record AppWindow(Stage stage, PdfViewer viewer) {
    }

    private final List<AppWindow> windows = new ArrayList<>();
    private ViewerPreferences preferences;
    private org.icepdf.fx.ri.SigningStore signing;
    private ViewerFeatures features;
    private UserLayout layout;
    private final FileHistory history = new PreferenceHistory();
    private final org.icepdf.fx.ri.ColorHistory colorHistory = new org.icepdf.fx.ri.ColorHistory() {
        @Override
        public List<java.awt.Color> recent() {
            return preferences.getRecentColors();
        }

        @Override
        public void used(java.awt.Color color) {
            preferences.addRecentColor(color);
            preferences.save();
        }
    };

    private final org.icepdf.fx.ri.PrintStore printStore = new org.icepdf.fx.ri.PrintStore() {
        @Override
        public org.icepdf.fx.print.PrintDefaults load() {
            return preferences.getPrintDefaults();
        }

        @Override
        public void save(org.icepdf.fx.print.PrintDefaults defaults) {
            preferences.setPrintDefaults(defaults);
            preferences.save();
        }
    };

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        installErrorHandler();
        preferences = ViewerPreferences.load();
        // before anything loads core's Library or renders: the pools size themselves once.
        PerformanceSettings.apply(preferences);
        signing = org.icepdf.fx.ri.prefs.SigningPreferences.of(preferences);
        features = loadFeatures();
        layout = UserLayout.load(ViewerPreferences.home().resolve("layout.properties"));
        FontSettings.apply(FontSettings.defaultCacheFile(), FontDirectories.of(preferences));
        ImagingSettings.apply(preferences);
        List<String> args = getParameters().getUnnamed();
        AppWindow first = createWindow(stage);
        if (Boolean.getBoolean(DevToolsWindow.OPEN_PROPERTY)) DevToolsWindow.show(first.viewer().getTheme());
        boolean firstFile = true;
        for (String arg : args) {
            Path file = argumentPath(arg);
            if (file == null) {
                first.viewer().showError(Messages.format("viewerApp.badArgument", arg));
            } else if (firstFile) {
                first.viewer().open(file);
                firstFile = false;
            } else if (features.isMultipleDocuments()) {
                first.viewer().openInNewTab(file);
            } else {
                newWindow(file);
            }
        }
    }

    /**
     * A command-line argument as a file: a path, or a {@code file:} URI (what a desktop launcher's
     * {@code %U} passes).
     *
     * @return null when it names no local file path
     */
    static Path argumentPath(String arg) {
        if (arg == null || arg.isBlank()) return null;
        try {
            if (arg.regionMatches(true, 0, "file:", 0, 5)) return Paths.get(java.net.URI.create(arg));
            if (arg.matches("(?i)[a-z][a-z0-9+.-]+://.*")) return null;
            return Paths.get(arg);
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            // InvalidPathException is an IllegalArgumentException
            return null;
        }
    }

    // ---- links ------------------------------------------------------------------------------------

    /**
     * A link the user clicked in a document: a web or mail link opens in the system's browser or
     * mail client; anything else (a file: link, an application's own scheme) is asked about first,
     * since the document chose it and it may start a program.  A link with no scheme can't be
     * followed.
     */
    private void followLink(AppWindow window, String uri) {
        followLink(window.viewer(), window.stage(), uri, getHostServices()::showDocument);
    }

    /** {@link #followLink(AppWindow, String)}: {@code browser} gets the links that are followed. */
    static void followLink(org.icepdf.fx.ri.PdfViewer viewer, javafx.stage.Window owner, String uri,
                           java.util.function.Consumer<String> browser) {
        String scheme = linkScheme(uri);
        if (scheme == null) {
            viewer.showMessage(Messages.format("viewerApp.linkNotFollowed", uri));
            return;
        }
        if (!SAFE_SCHEMES.contains(scheme)) {
            ButtonType open = new ButtonType(Messages.get("viewerApp.openLink"), ButtonBar.ButtonData.YES);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.format("viewerApp.confirmLink", uri),
                    open, ButtonType.CANCEL);
            alert.setHeaderText(null);
            if (owner != null) alert.initOwner(owner);
            if (viewer.getDialogs().showAndWait(alert).filter(b -> b == open).isEmpty()) return;
        }
        browser.accept(uri);
    }

    private static final java.util.Set<String> SAFE_SCHEMES = java.util.Set.of("http", "https", "mailto");

    /** The URI's scheme, lower case, or null when it has none (a relative link) or isn't a URI. */
    static String linkScheme(String uri) {
        if (uri == null) return null;
        try {
            String scheme = new java.net.URI(uri.strip()).getScheme();
            return scheme != null ? scheme.toLowerCase(java.util.Locale.ROOT) : null;
        } catch (java.net.URISyntaxException e) {
            // "http://example.com/a b" - links in PDFs are often not escaped; the scheme still shows.
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):").matcher(uri.strip());
            return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
        }
    }

    // ---- unexpected errors --------------------------------------------------------------------------

    private String lastUnexpected;
    private long lastUnexpectedAt;

    /**
     * An exception nothing caught: always logged; on the FX thread (an event handler, a layout
     * pass) the user is also told, since a packaged application has no console to show it.
     * Background workers report their own failures, so theirs are only logged.
     */
    private void installErrorHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            logger.log(Level.SEVERE, "Uncaught exception on " + thread.getName(), e);
            if (Platform.isFxApplicationThread()) unexpected(e);
        });
    }

    private void unexpected(Throwable e) {
        String message = Messages.format("viewerApp.unexpected", Messages.reason(e));
        long now = System.currentTimeMillis();
        // a failing layout pass throws on every pulse: one message is enough.
        if (message.equals(lastUnexpected) && now - lastUnexpectedAt < 5000) return;
        lastUnexpected = message;
        lastUnexpectedAt = now;
        AppWindow target = windows.stream().filter(w -> w.stage().isFocused()).findFirst()
                .orElse(windows.isEmpty() ? null : windows.get(windows.size() - 1));
        if (target != null) target.viewer().showError(message);
    }

    @Override
    public void stop() {
        for (AppWindow window : new ArrayList<>(windows)) window.viewer().dispose();
        if (preferences != null) preferences.save();
    }

    public List<AppWindow> getWindows() {
        return List.copyOf(windows);
    }

    public ViewerPreferences getPreferences() {
        return preferences;
    }

    public ViewerFeatures getFeatures() {
        return features;
    }

    /** Opens a new window, with {@code file} in it if not null; it cascades from the last one. */
    public AppWindow newWindow(Path file) {
        AppWindow window = createWindow(new Stage());
        if (file != null) window.viewer().open(file);
        return window;
    }

    /** Closes every window (each may ask about unsaved changes); the application ends with the last. */
    public void exit() {
        for (AppWindow window : new ArrayList<>(windows)) {
            if (!close(window)) return;
        }
    }

    /**
     * Closes a window after asking about its unsaved documents.  The application exits when the last
     * window closes.
     *
     * @return false if the user cancelled
     */
    public boolean close(AppWindow window) {
        if (!window.viewer().confirmClose()) return false;
        storeBounds(window.stage());
        window.viewer().dispose();
        window.stage().hide();
        windows.remove(window);
        if (windows.isEmpty()) {
            preferences.save();
            Platform.exit();
        }
        return true;
    }

    // ---- windows ----------------------------------------------------------------------------------

    /**
     * Opens a window around a document taken from another window's tabs, near {@code at} (a screen
     * position; cascaded when null).
     */
    public AppWindow newWindow(DocumentPane document, javafx.geometry.Point2D at) {
        return createWindow(new Stage(), document, at);
    }

    // window icons, loaded with the first window (an Image needs the toolkit running).
    private List<javafx.scene.image.Image> appIcons;

    private List<javafx.scene.image.Image> appIcons() {
        if (appIcons == null) {
            appIcons = List.of(appIcon("icepdf-app-icon-32x32.png"), appIcon("icepdf-app-icon-64x64.png"));
        }
        return appIcons;
    }

    private static javafx.scene.image.Image appIcon(String name) {
        return new javafx.scene.image.Image(ViewerApp.class.getResource("/org/icepdf/fx/images/" + name).toExternalForm());
    }

    private AppWindow createWindow(Stage stage) {
        return createWindow(stage, null, null);
    }

    private AppWindow createWindow(Stage stage, DocumentPane document, javafx.geometry.Point2D at) {
        PdfViewer viewer = PdfViewer.create(features, layout);
        applyTheme(viewer);
        AppWindow window = new AppWindow(stage, viewer);
        viewer.setFileHistory(history);
        viewer.setColorHistory(colorHistory);
        viewer.setSigningStore(signing);
        viewer.setPrintStore(printStore);
        viewer.setOnNewWindow(() -> newWindow(null));
        viewer.setOnExit(this::exit);
        viewer.setOnPreferences(() -> showPreferences(window));
        viewer.setOnLink(uri -> followLink(window, uri));
        viewer.setConfirmDiscard(() -> askAboutChanges(window));
        viewer.setOnDocumentCreated(this::applyPreferences);
        viewer.setOnTearOff(this::newWindow);
        // the last tab went to another window: this one has nothing left.
        viewer.setOnEmptied(() -> Platform.runLater(() -> close(window)));
        if (document != null) viewer.adoptDocument(document, 0);

        stage.setScene(new Scene(viewer, preferences.getDouble(ViewerPreferences.WINDOW_WIDTH, 1200),
                preferences.getDouble(ViewerPreferences.WINDOW_HEIGHT, 860)));
        followTitle(window);
        stage.getIcons().setAll(appIcons());
        placeWindow(stage);
        if (at != null) {
            stage.setX(at.getX() - 120);
            stage.setY(at.getY() - 24);
        }
        stage.setOnCloseRequest(e -> {
            if (!close(window)) e.consume();
        });
        windows.add(window);
        stage.show();
        return window;
    }

    private void placeWindow(Stage stage) {
        if (windows.isEmpty()) {
            double x = preferences.getDouble(ViewerPreferences.WINDOW_X, Double.NaN);
            double y = preferences.getDouble(ViewerPreferences.WINDOW_Y, Double.NaN);
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Screen.getScreensForRectangle(x, y, 40, 40).isEmpty()) {
                stage.setX(x);
                stage.setY(y);
            }
            stage.setMaximized(preferences.getBoolean(ViewerPreferences.WINDOW_MAXIMIZED, false));
        } else {
            Stage last = windows.get(windows.size() - 1).stage();
            stage.setX(last.getX() + 32);
            stage.setY(last.getY() + 32);
        }
    }

    private void storeBounds(Stage stage) {
        if (!stage.isMaximized() && !stage.isIconified() && !stage.isFullScreen()) {
            preferences.putDouble(ViewerPreferences.WINDOW_X, stage.getX());
            preferences.putDouble(ViewerPreferences.WINDOW_Y, stage.getY());
            preferences.putDouble(ViewerPreferences.WINDOW_WIDTH, stage.getScene().getWidth());
            preferences.putDouble(ViewerPreferences.WINDOW_HEIGHT, stage.getScene().getHeight());
        }
        preferences.putBoolean(ViewerPreferences.WINDOW_MAXIMIZED, stage.isMaximized());
    }

    /** The window's title is the selected document's: "name – ICEpdf". */
    private static void followTitle(AppWindow window) {
        PdfViewer viewer = window.viewer();
        ChangeListener<String> text = (o, a, b) -> window.stage().setTitle(title(viewer.getCurrentDocument()));
        viewer.currentDocumentProperty().addListener((o, was, now) -> {
            if (was != null) was.titleProperty().removeListener(text);
            if (now != null) now.titleProperty().addListener(text);
            window.stage().setTitle(title(now));
        });
        DocumentPane now = viewer.getCurrentDocument();
        if (now != null) now.titleProperty().addListener(text);
        window.stage().setTitle(title(now));
    }

    private static String title(DocumentPane pane) {
        return pane == null || pane.getTitle().isEmpty() ? Messages.get("viewerApp.title") : Messages.format("viewerApp.documentTitle", pane.getTitle());
    }

    // ---- preferences ------------------------------------------------------------------------------

    private void showPreferences(AppWindow window) {
        new PreferencesDialog(window.stage(), preferences, window.viewer().getView()).showAndWait();
        preferences.save();
        for (AppWindow each : windows) applyTheme(each.viewer());
    }

    /** Applies what the user chose (and keeps what they change) to a document's view and search panel. */
    private void applyTheme(PdfViewer viewer) {
        viewer.getTheme().modeProperty().set(preferences.getEnum(ViewerPreferences.THEME, Theme.Mode.class, Theme.Mode.SYSTEM));
    }

    private void applyPreferences(DocumentPane pane) {
        var view = pane.getView();
        view.setViewMode(preferences.getEnum(ViewerPreferences.VIEW_MODE, ViewMode.class, ViewMode.CONTINUOUS));
        FitMode fit = preferences.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH);
        view.setFitMode(fit);
        if (fit == FitMode.NONE) view.setZoom(preferences.getDouble(ViewerPreferences.ZOOM, 1));
        view.setVerifySignaturesOnOpen(preferences.getBoolean(ViewerPreferences.VERIFY_SIGNATURES, true));
        view.setAnnotationAuthor(preferences.get(ViewerPreferences.ANNOTATION_AUTHOR, System.getProperty("user.name")));
        try {
            view.setAnnotationColor(javafx.scene.paint.Color.web(
                    preferences.get(ViewerPreferences.ANNOTATION_COLOR, "#ffff00")));
        } catch (IllegalArgumentException ignored) {
            // keep the view's default
        }
        view.setHighlightFormFields(preferences.getBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, false));
        view.setPaintAnnotations(preferences.getBoolean(ViewerPreferences.PAINT_ANNOTATIONS, true));
        // a document's /PageLayout changes the mode for that document only, not the user's preference.
        view.viewModeProperty().addListener((o, a, b) -> {
            if (!pane.isApplyingPageLayout()) preferences.putEnum(ViewerPreferences.VIEW_MODE, b);
        });
        view.fitModeProperty().addListener((o, a, b) -> preferences.putEnum(ViewerPreferences.FIT_MODE, b));
        view.zoomProperty().addListener((o, a, b) -> preferences.putDouble(ViewerPreferences.ZOOM, b.doubleValue()));
        view.highlightFormFieldsProperty().addListener((o, a, b) ->
                preferences.putBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, b));
        view.paintAnnotationsProperty().addListener((o, a, b) ->
                preferences.putBoolean(ViewerPreferences.PAINT_ANNOTATIONS, b));

        var search = pane.getSearchPanel();
        searchOption(search.caseSensitiveProperty(), ViewerPreferences.SEARCH_CASE, false);
        searchOption(search.wholeWordProperty(), ViewerPreferences.SEARCH_WHOLE_WORD, false);
        searchOption(search.foldAccentsProperty(), ViewerPreferences.SEARCH_FOLD_ACCENTS, true);
        searchOption(search.regexProperty(), ViewerPreferences.SEARCH_REGEX, false);
        searchOption(search.cumulativeProperty(), ViewerPreferences.SEARCH_CUMULATIVE, false);
        searchOption(search.commentsProperty(), ViewerPreferences.SEARCH_COMMENTS, true);
        searchOption(search.formFieldsProperty(), ViewerPreferences.SEARCH_FORMS, true);
        searchOption(search.outlinesProperty(), ViewerPreferences.SEARCH_OUTLINES, false);
        searchOption(search.destinationsProperty(), ViewerPreferences.SEARCH_DESTINATIONS, false);
    }

    private void searchOption(javafx.beans.property.BooleanProperty option, String key, boolean fallback) {
        option.set(preferences.getBoolean(key, fallback));
        option.addListener((o, a, b) -> preferences.putBoolean(key, b));
    }

    // ---- unsaved changes --------------------------------------------------------------------------

    /** Save, Don't Save or Cancel, about the viewer's selected document. @return true to go ahead */
    private boolean askAboutChanges(AppWindow window) {
        DocumentPane pane = window.viewer().getCurrentDocument();
        Path file = pane != null ? pane.getSession().getFile() : null;
        ButtonType save = new ButtonType(Messages.get("viewerApp.save"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(Messages.get("viewerApp.dontSave"), ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                file != null ? Messages.format("viewerApp.saveChanges", file.getFileName()) : Messages.get("viewerApp.saveChangesUntitled"),
                save, discard, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.initOwner(window.stage());
        Optional<ButtonType> answer = window.viewer().getDialogs().showAndWait(alert);
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) return false;
        return answer.get() != save || window.viewer().save();
    }

    // ---- features ---------------------------------------------------------------------------------

    /**
     * What this installation offers ({@link ViewerFeatures#fromProperties}): the file named by
     * {@code -Dorg.icepdf.fx.features}, else {@code features.properties} in the settings directory,
     * else everything.  Tabs are on unless the file says {@code features.multipleDocuments=false}.
     */
    public static ViewerFeatures loadFeatures() {
        String configured = System.getProperty(FEATURES_PROPERTY);
        Path file = configured != null && !configured.isBlank() ? Paths.get(configured)
                : ViewerPreferences.home().resolve("features.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                logger.log(Level.WARNING, "Could not read " + file + "; offering everything", e);
                properties.clear();
            }
        }
        properties.putIfAbsent("features.multipleDocuments", "true");
        return ViewerFeatures.fromProperties(properties);
    }

    // ---- recent files -----------------------------------------------------------------------------

    private final class PreferenceHistory implements FileHistory {
        @Override
        public List<Path> recent() {
            return preferences.getRecentFiles();
        }

        @Override
        public void opened(Path file) {
            preferences.addRecentFile(file);
            preferences.save();
        }

        @Override
        public void clearRecent() {
            preferences.clearRecentFiles();
            preferences.save();
        }

        @Override
        public Path lastDirectory() {
            if (!preferences.getBoolean(ViewerPreferences.REMEMBER_LAST_DIRECTORY, true)) return null;
            String folder = preferences.get(ViewerPreferences.LAST_DIRECTORY, null);
            try {
                return folder != null ? Paths.get(folder) : null;
            } catch (java.nio.file.InvalidPathException e) {
                return null; // written on another system
            }
        }

        @Override
        public void setLastDirectory(Path directory) {
            if (!preferences.getBoolean(ViewerPreferences.REMEMBER_LAST_DIRECTORY, true)) return;
            preferences.put(ViewerPreferences.LAST_DIRECTORY, directory != null ? directory.toString() : null);
            preferences.save();
        }
    }
}
