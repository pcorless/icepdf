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
package org.icepdf.fx.demo;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.Page;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PageOverlayFactory;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.util.List;

/**
 * Minimal host for {@link PdfView}: a toolbar driving the control's public properties and nothing
 * else.  It doubles as the "drop it into your app" example - anything it can't do through the
 * public API is an API gap to fix in the control, not to work around here.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:run --args="/path/to/file.pdf"}
 */
public class PdfViewDemo extends Application {

    private final PdfView view = new PdfView();
    private Document document;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane(view);
        root.setTop(buildToolBar());
        root.setBottom(buildStatusBar());
        stage.setScene(new Scene(root, 1200, 900));
        stage.setTitle("ICEpdf PdfView demo");
        stage.show();
        view.requestFocus();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) open(new File(args.get(0)));
    }

    @Override
    public void stop() {
        view.setDocument(null);
        if (document != null) document.dispose();
    }

    private ToolBar buildToolBar() {
        Button open = new Button("Open…");
        open.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) open(file);
        });

        Button previous = new Button("◀");
        previous.setOnAction(e -> view.previousPage());
        Button next = new Button("▶");
        next.setOnAction(e -> view.nextPage());
        TextField pageField = new TextField();
        pageField.setPrefColumnCount(4);
        pageField.setOnAction(e -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(pageField.getText().trim()) - 1);
            } catch (NumberFormatException ignored) {
                // leave as is; the listener below rewrites the field
            }
        });
        Label pageCount = new Label();
        view.currentPageIndexProperty().addListener((obs, o, n) -> pageField.setText(String.valueOf(n.intValue() + 1)));
        view.pageCountProperty().addListener((obs, o, n) -> pageCount.setText("/ " + n));

        Button zoomOut = new Button("−");
        zoomOut.setOnAction(e -> view.zoomOut());
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(e -> view.zoomIn());
        Label zoom = new Label();
        zoom.setMinWidth(56);
        zoom.setAlignment(Pos.CENTER);
        zoom.textProperty().bind(view.zoomProperty().multiply(100).asString("%.0f%%"));

        ToggleGroup fitGroup = new ToggleGroup();
        ToggleButton fitWidth = new ToggleButton("Fit width");
        ToggleButton fitPage = new ToggleButton("Fit page");
        fitWidth.setToggleGroup(fitGroup);
        fitPage.setToggleGroup(fitGroup);
        fitGroup.selectedToggleProperty().addListener((obs, o, n) ->
                view.setFitMode(n == fitWidth ? FitMode.WIDTH : n == fitPage ? FitMode.PAGE : FitMode.NONE));
        view.fitModeProperty().addListener((obs, o, n) -> {
            fitWidth.setSelected(n == FitMode.WIDTH);
            fitPage.setSelected(n == FitMode.PAGE);
        });

        Button rotateLeft = new Button("⟲");
        rotateLeft.setOnAction(e -> view.rotateCounterClockwise());
        Button rotateRight = new Button("⟳");
        rotateRight.setOnAction(e -> view.rotateClockwise());

        ComboBox<ViewMode> mode = new ComboBox<>();
        mode.getItems().setAll(ViewMode.values());
        mode.valueProperty().bindBidirectional(view.viewModeProperty());
        CheckBox cover = new CheckBox("Cover page");
        cover.selectedProperty().bindBidirectional(view.coverPageProperty());

        ToggleGroup toolGroup = new ToggleGroup();
        ToggleButton select = new ToggleButton("Select");
        ToggleButton hand = new ToggleButton("Hand");
        select.setToggleGroup(toolGroup);
        hand.setToggleGroup(toolGroup);
        select.setTooltip(new Tooltip("Drag selects text. Middle-drag or Space+drag pans in any tool."));
        hand.setTooltip(new Tooltip("Drag pans."));
        select.setSelected(view.getToolMode() == ToolMode.TEXT_SELECT);
        hand.setSelected(view.getToolMode() == ToolMode.PAN);
        toolGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) {
                o.setSelected(true); // one tool is always active
                return;
            }
            view.setToolMode(n == hand ? ToolMode.PAN : ToolMode.TEXT_SELECT);
        });

        CheckBox overlay = new CheckBox("Overlay test");
        overlay.setTooltip(new Tooltip("Draws a frame 36pt inside each page's crop box, in PDF user space,\n"
                + "to check native overlays stay locked to the content through zoom and rotation."));
        overlay.selectedProperty().addListener((obs, o, on) -> view.setPageOverlayFactory(on ? cropFrame() : null));

        return new ToolBar(open, new Separator(), previous, pageField, pageCount, next, new Separator(),
                zoomOut, zoom, zoomIn, fitWidth, fitPage, new Separator(), rotateLeft, rotateRight,
                new Separator(), mode, cover, new Separator(), select, hand, new Separator(), overlay);
    }

    private Node buildStatusBar() {
        Label memory = new Label();
        Timeline poll = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            memory.setText(String.format("heap %d / %d MB", used, rt.maxMemory() >> 20));
        }));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        HBox bar = new HBox(memory);
        bar.setPadding(new Insets(2, 8, 2, 8));
        return bar;
    }

    /** Overlay test: a red frame inset 36pt from the crop box, drawn in PDF user space. */
    private static PageOverlayFactory cropFrame() {
        return (pageIndex, page) -> {
            PRectangle crop = page.getPageBoundary(Page.BOUNDARY_CROPBOX);
            // PRectangle is stored top-left with y up: y is the top edge.
            double x = crop.getX() + 36;
            double y = crop.getY() - crop.getHeight() + 36;
            Rectangle frame = new Rectangle(x, y, crop.getWidth() - 72, crop.getHeight() - 72);
            frame.setFill(Color.color(1, 0, 0, 0.06));
            frame.setStroke(Color.RED);
            frame.setStrokeWidth(2);
            // a marker in the frame's lower-left (user space) corner shows orientation.
            Rectangle corner = new Rectangle(x, y, 36, 36);
            corner.setFill(Color.RED);
            return new javafx.scene.Group(frame, corner);
        };
    }

    private void open(File file) {
        Document next = new Document();
        try {
            next.setFile(file.getAbsolutePath());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + file + ":\n" + e.getMessage()).showAndWait();
            return;
        }
        Document old = document;
        document = next;
        view.setDocument(next);
        if (old != null) old.dispose();
        stage.setTitle(file.getName() + " - ICEpdf PdfView demo");
    }
}
