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

import org.icepdf.fx.i18n.Messages;

import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.ri.actions.AbstractViewerAction;
import org.icepdf.fx.ri.actions.ViewerContext;

/** Shows the application name and the icepdf version. */
public final class AboutAction extends AbstractViewerAction {

    public static final String ID = "app.about";

    /** The project's home page. */
    public static final String HOME_PAGE = "https://github.com/pcorless/icepdf";

    public AboutAction() {
        super(ID, null);
    }

    @Override
    public void execute(ViewerContext context) {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setHeaderText(null);
        Label text = new Label(context.applicationName()
                + "\n" + Messages.format("about.version", Document.getLibraryVersion()));
        // a link only where the host can open one; otherwise the address as text
        javafx.scene.Node home;
        if (context.has(ViewerContext.Capability.OPEN_LINK)) {
            Hyperlink link = new Hyperlink(HOME_PAGE);
            link.setOnAction(e -> context.openLink(HOME_PAGE));
            link.setPadding(javafx.geometry.Insets.EMPTY);
            home = link;
        } else {
            home = new Label(HOME_PAGE);
        }
        VBox content = new VBox(8, text, home);
        about.getDialogPane().setContent(content);
        // the project's cube in place of the information icon
        java.net.URL cube = AboutAction.class.getResource("/org/icepdf/fx/images/icepdf-cube-128.png");
        if (cube != null) {
            // drawn from 128 px at 64 so it stays sharp on HiDPI screens
            ImageView graphic = new ImageView(new Image(cube.toExternalForm()));
            graphic.setFitWidth(64);
            graphic.setFitHeight(64);
            graphic.setSmooth(true);
            about.setGraphic(graphic);
        }
        if (context.window() != null) about.initOwner(context.window());
        about.showAndWait();
    }
}
