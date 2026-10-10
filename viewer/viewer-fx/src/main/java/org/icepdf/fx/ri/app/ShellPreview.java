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

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.viewer.FontSettings;
import org.icepdf.fx.viewer.ViewerPreferences;

import java.nio.file.Paths;

/**
 * A window with the new {@link PdfViewer} shell, for trying it while it replaces the classic viewer
 * window: {@code ./gradlew :viewer:viewer-fx:runShell --args="file.pdf"}.  The layout is saved in the
 * viewer's settings directory ({@code layout.properties}).
 */
public class ShellPreview extends Application {

    private PdfViewer viewer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        FontSettings.apply(ViewerPreferences.load());
        viewer = PdfViewer.create(ViewerFeatures.full(),
                UserLayout.load(ViewerPreferences.home().resolve("layout.properties")));
        viewer.setOnExit(stage::close);
        stage.setScene(new Scene(viewer, 1200, 860));
        stage.setTitle("ICEpdf");
        stage.show();
        if (!getParameters().getUnnamed().isEmpty()) viewer.open(Paths.get(getParameters().getUnnamed().get(0)));
    }

    @Override
    public void stop() {
        if (viewer != null) viewer.dispose();
    }
}
