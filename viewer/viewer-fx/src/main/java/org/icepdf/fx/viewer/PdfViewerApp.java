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
package org.icepdf.fx.viewer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The ICEpdf JavaFX viewer application: a {@link ViewerWindow} per document, sharing one set of
 * {@link ViewerPreferences}.  Each command line argument opens in a window of its own.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:run --args="/path/to/file.pdf"}
 */
public class PdfViewerApp extends Application {

    private final List<ViewerWindow> windows = new ArrayList<>();
    private ViewerPreferences preferences;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        preferences = ViewerPreferences.load();
        FontSettings.apply(preferences);
        List<String> args = getParameters().getUnnamed();
        ViewerWindow first = new ViewerWindow(this, stage, preferences);
        windows.add(first);
        first.show();
        if (!args.isEmpty()) first.open(Paths.get(args.get(0)));
        for (int i = 1; i < args.size(); i++) newWindow(Paths.get(args.get(i)));
    }

    /** Opens a new window, with {@code file} in it if not null. */
    public ViewerWindow newWindow(Path file) {
        Stage stage = new Stage();
        ViewerWindow window = new ViewerWindow(this, stage, preferences);
        windows.add(window);
        // a new window cascades from the last one.
        if (windows.size() > 1) {
            Stage last = windows.get(windows.size() - 2).getStage();
            stage.setX(last.getX() + 32);
            stage.setY(last.getY() + 32);
        }
        window.show();
        if (file != null) window.open(file);
        return window;
    }

    /** Closes every window (each may ask about unsaved changes); exits when they're all closed. */
    public void exit() {
        for (ViewerWindow window : new ArrayList<>(windows)) {
            if (!window.close()) return;
        }
    }

    /** Opens a URI (a link, an attachment) with the desktop's handler. */
    public void showDocument(String uri) {
        getHostServices().showDocument(uri);
    }

    void windowClosed(ViewerWindow window) {
        windows.remove(window);
        if (windows.isEmpty()) {
            preferences.save();
            Platform.exit();
        }
    }
}
