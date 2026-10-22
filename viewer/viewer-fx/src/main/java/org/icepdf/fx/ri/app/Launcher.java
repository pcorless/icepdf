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

/**
 * Starts {@link ViewerApp} from a single jar with JavaFX inside it ({@code java -jar
 * icepdf-viewer-fx-<version>-jar-with-dependencies.jar}).  The Java launcher refuses a main class
 * that is a JavaFX {@code Application} unless JavaFX is on the module path; a plain class that
 * launches it has no such check.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(ViewerApp.class, args);
    }
}
