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
package org.icepdf.fx.ri;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.icepdf.fx.ri.ui.EdgeDrawer;
import org.icepdf.fx.ri.ui.Theme;
import org.icepdf.fx.viewer.ViewerPreferences;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Drives the new viewer shell ({@link PdfViewer}) and snapshots it in-process: rail, utility panel
 * (hover, click, pin, Esc), overlay vs docked document size, rail settings, themes, full screen, and
 * a READING product.  {@code ./gradlew :viewer:viewer-fx:shellSmoke -PsmokeArgs="outdir;doc.pdf"}
 */
public final class ShellSmoke {

    private static int failures;
    private static Path out;

    public static void main(String[] args) throws Exception {
        out = Paths.get(args[0]);
        Files.createDirectories(out);
        Path doc = Paths.get(args[1]);
        System.setProperty(ViewerPreferences.HOME_PROPERTY, out.resolve("settings").toString());
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        Path layoutFile = out.resolve("settings/layout.properties");
        Files.deleteIfExists(layoutFile);
        PdfViewer viewer = onFx(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.full(), UserLayout.load(layoutFile));
            v.getTheme().modeProperty().set(Theme.Mode.LIGHT);
            Stage stage = new Stage();
            stage.setScene(new Scene(v, 1200, 820));
            stage.show();
            v.open(doc);
            return v;
        });
        settle();
        snapshot(viewer, "shell-light.png");

        // the rail is the arrangement; the strip lists the panels with content.
        List<String> railIds = onFx(() -> {
            List<String> ids = new ArrayList<>();
            for (Node n : viewer.getToolRail().getChildren()) {
                if (n.getId() != null && !n.getId().startsWith("ui.")) ids.add(n.getId());
            }
            return ids;
        });
        System.out.println("  rail " + railIds + "; panels " + onFx(() -> viewer.getUtilityPanel().availablePanels()));
        check("rail shows the arrangement", railIds.equals(onFx(() -> viewer.getToolRail().arrangement().rail())));
        check("strip offers pages, bookmarks and search", onFx(() -> viewer.getUtilityPanel().availablePanels()
                .containsAll(List.of(SidePanel.THUMBNAILS, SidePanel.BOOKMARKS, SidePanel.SEARCH))));

        // overlay: the document keeps its size.
        EdgeDrawer panel = onFx(() -> viewer.getUtilityPanel().getDrawer());
        double width = onFx(() -> viewer.getView().getWidth());
        onFx(() -> {
            viewer.getUtilityPanel().open(SidePanel.BOOKMARKS);
            return null;
        });
        settle();
        check("click opens the panel as an overlay", onFx(panel::getState) == EdgeDrawer.State.OVERLAY);
        check("an overlay leaves the document's size alone", onFx(() -> viewer.getView().getWidth()) == width);
        snapshot(viewer, "shell-overlay.png");
        onFx(() -> {
            Event.fireEvent(viewer.getView(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            return null;
        });
        check("Esc closes it", onFx(panel::getState) == EdgeDrawer.State.COLLAPSED);

        // hover: rest on the strip, it opens; leave, it closes.
        Node strip = onFx(panel::getStrip);
        onFx(() -> {
            Event.fireEvent(strip, mouse(MouseEvent.MOUSE_ENTERED, strip));
            return null;
        });
        Thread.sleep(150);
        check("not yet open at 150 ms", onFx(panel::getState) == EdgeDrawer.State.COLLAPSED);
        Thread.sleep(400);
        check("hover opens it after the delay", onFx(panel::getState) == EdgeDrawer.State.OVERLAY);
        onFx(() -> {
            Event.fireEvent(strip, mouse(MouseEvent.MOUSE_EXITED, strip));
            return null;
        });
        Thread.sleep(800);
        check("leaving closes it after the grace delay", onFx(panel::getState) == EdgeDrawer.State.COLLAPSED);

        // pinned: docked, the document narrows; remembered.
        onFx(() -> {
            panel.pinnedProperty().set(true);
            return null;
        });
        settle();
        double docked = onFx(() -> viewer.getView().getWidth());
        check("pinned docks it and the document narrows (" + Math.round(width) + " -> " + Math.round(docked) + ")",
                onFx(panel::getState) == EdgeDrawer.State.DOCKED && docked < width - 100);
        snapshot(viewer, "shell-docked.png");

        // dark.
        onFx(() -> {
            viewer.getTheme().modeProperty().set(Theme.Mode.DARK);
            return null;
        });
        settle();
        BufferedImage dark = snapshot(viewer, "shell-dark.png");
        int rgb = dark.getRGB(dark.getWidth() / 2, 12);
        int luma = ((rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255)) / 3;
        check("dark theme darkens the header (luma " + luma + ")", luma < 90);
        onFx(() -> {
            viewer.getTheme().modeProperty().set(Theme.Mode.LIGHT);
            panel.pinnedProperty().set(false);
            return null;
        });

        // rail settings: move it right; the utility panel goes left; saved.
        onFx(() -> {
            MenuButton settings = (MenuButton) viewer.getToolRail().lookup("#ui\\.settings");
            if (settings == null) {
                for (Node n : viewer.getToolRail().getChildren()) if ("ui.settings".equals(n.getId())) settings = (MenuButton) n;
            }
            for (MenuItem item : settings.getItems()) {
                if (item.getText() != null && item.getText().startsWith("Move Rail")) item.fire();
            }
            return null;
        });
        settle();
        check("rail moves right, panel left", onFx(() -> viewer.getLayout().getRailSide() == UserLayout.Side.RIGHT
                && viewer.getUtilityPanel().getDrawer().getSide() == UserLayout.Side.LEFT));
        snapshot(viewer, "shell-rail-right.png");
        Thread.sleep(900);
        check("layout saved", Files.isRegularFile(layoutFile)
                && Files.readString(layoutFile).contains("rail.side=RIGHT"));

        // full screen.
        onFx(() -> {
            viewer.getActions().execute("view.full-screen");
            return null;
        });
        settle();
        check("full screen hides the chrome", onFx(() -> !viewer.getHeaderBar().isVisible()
                && viewer.getView().getViewMode() == org.icepdf.fx.view.ViewMode.SINGLE_PAGE));
        onFx(() -> {
            viewer.fullScreenProperty().set(false);
            return null;
        });
        settle();
        check("and comes back", onFx(() -> viewer.getHeaderBar().isVisible()));

        // a READING product: no annotation tools anywhere on the rail.
        PdfViewer reading = onFx(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.READING).build(), UserLayout.inMemory());
            Stage stage = new Stage();
            stage.setScene(new Scene(v, 900, 700));
            stage.show();
            v.open(doc);
            return v;
        });
        settle();
        check("READING rail is select and hand only", onFx(() -> reading.getToolRail().arrangement().rail())
                .equals(List.of("tool.select", "tool.pan")) && onFx(() -> reading.getToolRail().arrangement().overflow()).isEmpty());
        snapshot(reading, "shell-reading.png");

        System.out.println(failures == 0 ? "shell smoke: all passed" : "shell smoke: " + failures + " FAILED");
        onFx(() -> {
            viewer.dispose();
            reading.dispose();
            return null;
        });
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, Node target) {
        return new MouseEvent(type, 2, 2, 2, 2, MouseButton.NONE, 0, false, false, false, false,
                false, false, false, false, false, false, null);
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what);
        if (!ok) failures++;
    }

    private static void settle() throws Exception {
        Thread.sleep(1200);
        onFx(() -> null);
        Thread.sleep(400);
    }

    private static BufferedImage snapshot(Node node, String name) throws Exception {
        WritableImage image = onFx(() -> node.snapshot(null, null));
        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(buffered, "png", out.resolve(name).toFile());
        return buffered;
    }

    private static <T> T onFx(Callable<T> call) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(call.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(60, TimeUnit.SECONDS);
    }
}
