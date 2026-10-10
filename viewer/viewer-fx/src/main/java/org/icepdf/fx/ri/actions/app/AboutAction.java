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
package org.icepdf.fx.ri.actions.app;

import javafx.scene.control.Alert;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Shows the application name and the ICEpdf version. */
public final class AboutAction extends AbstractViewerAction {

    public static final String ID = "app.about";

    public AboutAction() {
        super(ID, null);
    }

    @Override
    public void execute(ViewerContext context) {
        Alert about = new Alert(Alert.AlertType.INFORMATION, context.applicationName()
                + "\nJavaFX viewer for ICEpdf " + Document.getLibraryVersion());
        about.setHeaderText(null);
        if (context.window() != null) about.initOwner(context.window());
        about.showAndWait();
    }
}
