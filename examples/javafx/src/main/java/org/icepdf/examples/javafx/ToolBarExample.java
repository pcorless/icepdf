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
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.icepdf.fx.fonts.FontSettings;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.ActionRegistry;
import org.icepdf.fx.ri.actions.Conditions;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerAction;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.icons.SvgIcons;
import org.icepdf.fx.ri.ui.PageField;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;

import java.nio.file.Path;
import java.util.List;

/**
 * The ICEpdf JavaFX viewer with your own tool bar: page navigation (previous, current, next) and zoom.
 * <p>
 * The viewer's commands follow the command pattern.  Each command is a {@link ViewerAction}: an id,
 * a label, when it is enabled, and {@code execute}.  The actions are kept in an {@link ActionRegistry},
 * and {@link ActionControls} turns ids into buttons.  A tool bar is therefore a list of ids:
 * <ul>
 *     <li>pressing a button runs its action's {@code execute} against the viewer's
 *     {@link ViewerContext};</li>
 *     <li>the button is disabled while the action can't run (no document, already on the last page);</li>
 *     <li>your own command is one more action in the registry, used the same way as the built-in ones.</li>
 * </ul>
 * <p>
 * {@code ./gradlew :examples:javafx:run -PmainClass=org.icepdf.examples.javafx.ToolBarExample
 * --args="/path/to/file.pdf"}
 */
public class ToolBarExample extends Application {

    // markers in TOOL_BAR for the two controls that show state rather than run a command.
    private static final String PAGE_NUMBER = "#page-number";
    private static final String ZOOM = "#zoom";
    private static final String SEPARATOR = "|";

    /** The tool bar, as action ids. */
    private static final List<String> TOOL_BAR = List.of(
            "document.open",
            SEPARATOR,
            "navigation.previous", PAGE_NUMBER, "navigation.next",
            SEPARATOR,
            "view.zoom-out", ZOOM, "view.zoom-in");

    private PdfViewer viewer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        FontSettings.apply();

        // 1. The commands: every built-in action, plus one of our own.
        ActionRegistry registry = StandardActions.registry();
        registry.register(new StartOverAction());

        // 2. The viewer without its header bar, tool rail or side panels (chrome()), one document at a
        //    time, so it always shows the same PdfView.
        ViewerFeatures features = ViewerFeatures.builder(ViewerFeatures.Preset.READING)
                .chrome()
                .multipleDocuments(false)
                .add(StartOverAction.ID)
                .build();
        viewer = new PdfViewer(features, UserLayout.inMemory(), registry, SvgIcons.placeholders());
        viewer.setOnLink(getHostServices()::showDocument);

        // 3. The tool bar, built from ids.  The viewer's ActionControls runs each action against the
        //    viewer's context; button(id) makes a button with the action's icon and tool tip.
        ActionControls actions = viewer.getActions();
        ToolBar toolBar = new ToolBar();
        for (String id : TOOL_BAR) {
            if (id.equals(SEPARATOR)) {
                toolBar.getItems().add(new Separator());
            } else if (id.equals(PAGE_NUMBER)) {
                // "3 of 12": shows the current page and jumps to one you type.
                toolBar.getItems().add(new PageField(viewer.getView()));
            } else if (id.equals(ZOOM)) {
                toolBar.getItems().add(zoomLabel(viewer.getView()));
            } else if (actions.isAvailable(id)) {
                toolBar.getItems().add(actions.button(id));
            }
        }

        // 4. Your own button can run an action too: bind() keeps its text and style and adds the
        //    command, its enabled state and a tool tip.
        toolBar.getItems().addAll(new Separator(), actions.bind(new Button("Start over"), StartOverAction.ID));

        // the actions' keyboard shortcuts (Ctrl+O, Ctrl+plus/minus...) work anywhere in the window.
        BorderPane root = new BorderPane(viewer, toolBar, null, null, null);
        Scene scene = new Scene(root, 1100, 850);
        actions.installAccelerators(scene, TOOL_BAR.stream()
                .filter(actions::isAvailable).toArray(String[]::new));

        stage.setScene(scene);
        stage.setTitle("ICEpdf JavaFX viewer - own tool bar");
        stage.show();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) viewer.open(Path.of(args.get(0)));
    }

    /** The zoom as a percentage, kept up to date by binding to the view's zoom property. */
    private static Label zoomLabel(PdfView view) {
        Label label = new Label();
        label.setMinWidth(48);
        label.textProperty().bind(view.zoomProperty().multiply(100).asString("%.0f%%"));
        label.disableProperty().bind(view.documentProperty().isNull());
        return label;
    }

    @Override
    public void stop() {
        viewer.dispose();
    }

    /**
     * An application's own command: back to the first page, with the whole page in view.  It works
     * only through the {@link ViewerContext} it is handed, so one instance serves every viewer.
     */
    static final class StartOverAction implements ViewerAction {

        static final String ID = "example.start-over";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public String label() {
            return "Start over";
        }

        @Override
        public String tooltip() {
            return "Back to the first page, whole page in view";
        }

        @Override
        public ObservableBooleanValue enabled(ViewerContext context) {
            // a button for this action is disabled until a document is open.
            return Conditions.documentOpen(context);
        }

        @Override
        public void execute(ViewerContext context) {
            PdfView view = context.view();
            // zoom first, then go to the page, so the page lands at the top of the new zoom.
            view.setFitMode(FitMode.PAGE);
            view.setCurrentPageIndex(0);
        }
    }
}
