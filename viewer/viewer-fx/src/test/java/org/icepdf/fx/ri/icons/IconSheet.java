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
package org.icepdf.fx.ri.icons;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * A contact sheet of an icon set - every id at 24 and 16 px, light and dark - for reviewing
 * placeholders or comparing candidate sets.  In-process snapshot, no screen capture.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:iconSheet -PiconArgs="outdir[;icons.properties]"}
 */
public final class IconSheet {

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]);
        Files.createDirectories(out);
        SvgIcons icons = args.length > 1 ? SvgIcons.from(Paths.get(args[1])) : SvgIcons.placeholders();
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        for (boolean dark : new boolean[]{false, true}) {
            CompletableFuture<WritableImage> shot = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    shot.complete(sheet(icons, dark));
                } catch (Throwable e) {
                    shot.completeExceptionally(e);
                }
            });
            Path file = out.resolve(dark ? "icons-dark.png" : "icons-light.png");
            ImageIO.write(SwingFXUtils.fromFXImage(shot.get(), null), "png", file.toFile());
            System.out.println("wrote " + file + " (" + icons.ids().size() + " icons)");
        }
        Platform.exit();
    }

    private static WritableImage sheet(SvgIcons icons, boolean dark) {
        Color background = dark ? Color.web("#242424") : Color.web("#fafafa");
        Color ink = dark ? Color.web("#eeeeec") : Color.web("#2e3436");
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        List<String> ids = new ArrayList<>(icons.ids());
        java.util.Collections.sort(ids);
        int columns = 4;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Node big = icons.icon(id, 24), small = icons.icon(id, 16);
            for (Node icon : List.of(big, small)) icon.lookupAll(".icon-path").forEach(n -> ((SVGPath) n).setStroke(ink));
            Label name = new Label(id);
            name.setTextFill(ink);
            name.setMinWidth(190);
            HBox cell = new HBox(10, big, small, name);
            cell.setAlignment(Pos.CENTER_LEFT);
            grid.add(cell, i % columns, i / columns);
        }
        Label title = new Label("ICEpdf placeholder icons - " + ids.size() + " (24 px, 16 px)" + (dark ? ", dark" : ""));
        title.setTextFill(ink);
        title.setStyle("-fx-font-weight: bold;");
        VBox root = new VBox(8, title, grid);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: " + (dark ? "#242424" : "#fafafa") + ";");
        Scene scene = new Scene(root, background);
        Stage stage = new Stage();
        stage.setScene(scene);
        root.applyCss();
        root.layout();
        return root.snapshot(null, null);
    }
}
