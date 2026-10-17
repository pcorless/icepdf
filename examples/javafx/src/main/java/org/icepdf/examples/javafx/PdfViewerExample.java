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
package org.icepdf.examples.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.fonts.FontSettings;

import java.nio.file.Path;
import java.util.List;

/**
 * The ICEpdf JavaFX viewer in a window of your own: {@link PdfViewer} is a JavaFX node holding the
 * page view, its header bar, tool rail and side panels.  Put it in any layout.
 * <p>
 * {@code ./gradlew :examples:javafx:run --args="/path/to/file.pdf"}
 */
public class PdfViewerExample extends Application {

    private PdfViewer viewer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // the system's fonts, for documents that don't embed theirs: read from a cache in
        // ~/.icepdf/fx, or scanned in the background the first time.
        FontSettings.apply();

        // every tool and panel; ViewerFeatures picks fewer.
        viewer = PdfViewer.create();
        // links in the document open in the browser.
        viewer.setOnLink(getHostServices()::showDocument);

        stage.setScene(new Scene(viewer, 1200, 900));
        stage.setTitle("ICEpdf JavaFX viewer");
        stage.show();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) viewer.open(Path.of(args.get(0)));
    }

    @Override
    public void stop() {
        // closes the document and stops background work.
        viewer.dispose();
    }
}
