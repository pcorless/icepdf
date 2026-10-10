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

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Drives the viewer window - side panel tabs, a layer toggle, the properties and preferences
 * dialogs, a save - and snapshots each step in-process (no screen capture).  Settings go to a
 * scratch directory, never the user's.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:viewerSmoke -PsmokeArgs="outdir;doc1.pdf;doc2.pdf..."}
 */
public class ViewerSmoke {

    private static int failures;
    private static Path out;

    public static void main(String[] args) throws Exception {
        out = Paths.get(args[0]);
        Files.createDirectories(out);
        System.setProperty(ViewerPreferences.HOME_PROPERTY, out.resolve("settings").toString());
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        PdfViewerApp app = new PdfViewerApp();
        ViewerPreferences preferences = ViewerPreferences.load(out.resolve("settings/viewer.properties"));
        ViewerWindow window = onFx(() -> {
            ViewerWindow w = new ViewerWindow(app, new Stage(), preferences);
            w.show();
            return w;
        });

        for (int i = 1; i < args.length; i++) {
            Path file = Paths.get(args[i]);
            String tag = file.getFileName().toString().replaceAll("[^A-Za-z0-9]+", "_");
            check("open " + file.getFileName(), onFx(() -> window.open(file)));
            settle();
            snapshot(window.getStage().getScene().getRoot(), tag + "_open.png");
            TabPane side = (TabPane) window.getStage().getScene().lookup(".tab-pane");
            int tabs = onFx(() -> side.getTabs().size());
            System.out.println("  " + file.getFileName() + ": side tabs " + onFx(() -> side.getTabs().stream()
                    .map(t -> t.getId()).toList()));
            for (int t = 0; t < tabs; t++) {
                int index = t;
                String id = onFx(() -> {
                    side.getSelectionModel().select(index);
                    return side.getTabs().get(index).getId();
                });
                settle();
                snapshot(window.getStage().getScene().getRoot(), tag + "_tab_" + id + ".png");
            }
            if (i == 1 && args.length > 1) {
                searchSteps(window, side, tag);
                printDefaults(window);
            }
            // properties dialog
            Dialog<?> dialog = onFx(() -> {
                var d = new org.icepdf.fx.panels.DocumentPropertiesDialog(window.getStage(),
                        window.getView().getDocument());
                d.show();
                return d;
            });
            Thread.sleep(1500);
            TabPane propertyTabs = (TabPane) onFx(() -> dialog.getDialogPane().getContent());
            for (int t = 0; t < 4; t++) {
                int index = t;
                onFx(() -> {
                    propertyTabs.getSelectionModel().select(index);
                    return null;
                });
                Thread.sleep(300);
                snapshot(dialog.getDialogPane(), tag + "_properties_" + t + ".png");
            }
            onFx(() -> {
                dialog.close();
                return null;
            });
        }
        // preferences dialog
        Dialog<?> prefs = onFx(() -> {
            PreferencesDialog d = new PreferencesDialog(window.getStage(), preferences, window.getView());
            d.show();
            return d;
        });
        Thread.sleep(500);
        TabPane prefTabs = (TabPane) onFx(() -> prefs.getDialogPane().getContent());
        for (int t = 0; t < 4; t++) {
            int index = t;
            onFx(() -> {
                prefTabs.getSelectionModel().select(index);
                return null;
            });
            Thread.sleep(300);
            snapshot(prefs.getDialogPane(), "preferences_" + t + ".png");
        }
        onFx(() -> {
            prefs.close();
            return null;
        });

        // save: edit a copy, save it in place, the window reopens it unmodified.
        if (args.length > 1) {
            Path copy = out.resolve("save-copy.pdf");
            Files.copy(Paths.get(args[1]), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            check("open copy", onFx(() -> window.open(copy)));
            settle();
            onFx(() -> {
                var info = window.getView().getDocument().getInfo();
                if (info != null) {
                    info.setTitle("Saved by ViewerSmoke");
                }
                return null;
            });
            Thread.sleep(1200);
            check("modified after an edit", onFx(window::isModified));
            long before = Files.size(copy);
            check("save", onFx(window::save));
            settle();
            check("unmodified after save", !onFx(window::isModified));
            check("file grew (incremental update)", Files.size(copy) > before);
            check("title shows the file", onFx(() -> window.getStage().getTitle()).startsWith("save-copy.pdf"));
            String title = onFx(() -> window.getView().getDocument().getInfo().getTitle());
            check("saved value reads back", "Saved by ViewerSmoke".equals(title));
        }

        System.out.println(failures == 0 ? "viewer smoke: all passed" : "viewer smoke: " + failures + " FAILED");
        onFx(() -> {
            window.getStage().hide();
            return null;
        });
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    /** The search panel: text with context, plus bookmarks and comments; next-hit follows in the tree. */
    private static void searchSteps(ViewerWindow window, TabPane side, String tag) throws Exception {
        var panel = window.getSearchPanel();
        var view = window.getView();
        onFx(() -> {
            panel.outlinesProperty().set(true);
            panel.destinationsProperty().set(true);
            panel.commentsProperty().set(true);
            panel.formFieldsProperty().set(true);
            window.showSideTab("search");
            return panel.search("transparency");
        });
        for (int i = 0; i < 600 && onFx(view::isSearching); i++) Thread.sleep(100);
        settle();
        int hits = onFx(() -> view.getSearchHits().size());
        System.out.println("  search: " + hits + " text hits; status \"" + onFx(() -> statusOf(panel)) + "\"");
        check("search finds text", hits > 0);
        check("hits carry context", onFx(() -> view.getSearchHits().stream()
                .anyMatch(h -> !h.before().isEmpty() || !h.after().isEmpty())));
        check("current hit selected in the tree", onFx(() -> view.getCurrentSearchHitIndex() >= 0));
        onFx(() -> {
            view.nextSearchHit();
            view.nextSearchHit();
            return null;
        });
        settle();
        snapshot(window.getStage().getScene().getRoot(), tag + "_tab_search.png");
        onFx(() -> {
            panel.cumulativeProperty().set(true);
            return panel.search("annotation");
        });
        for (int i = 0; i < 600 && onFx(view::isSearching); i++) Thread.sleep(100);
        settle();
        int both = onFx(() -> view.getSearchHits().size());
        check("cumulative adds the second term's hits (" + both + " > " + hits + ")", both > hits);
        snapshot(window.getStage().getScene().getRoot(), tag + "_tab_search_cumulative.png");
        onFx(() -> {
            panel.cumulativeProperty().set(false);
            panel.clear();
            return null;
        });
        check("clear removes hits", onFx(() -> view.getSearchHits().isEmpty()));
    }

    /** The print dialog starts from remembered choices, and its result reduces to them again. */
    private static void printDefaults(ViewerWindow window) throws Exception {
        javax.print.StreamPrintService filePrinter = javax.print.StreamPrintServiceFactory
                .lookupStreamPrintServiceFactories(javax.print.DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                        "application/postscript")[0].getPrintService(java.io.OutputStream.nullOutputStream());
        String paper = javax.print.attribute.standard.MediaSizeName.ISO_A5.toString();
        var defaults = new org.icepdf.fx.print.PrintDefaults(filePrinter.getName(), paper,
                org.icepdf.fx.print.PrintSettings.Scaling.ACTUAL_SIZE,
                org.icepdf.fx.print.PrintSettings.Orientation.LANDSCAPE, null, false);
        var dialog = onFx(() -> {
            var d = new org.icepdf.fx.print.PdfPrintDialog(window.getStage(), window.getView().getDocument(), 0);
            d.setDefaults(defaults);
            d.setPrinters(List.of(filePrinter), null);
            d.show();
            return d;
        });
        Thread.sleep(800);
        snapshot(dialog.getDialogPane(), "print_defaults.png");
        onFx(() -> {
            ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                    dialog.getDialogPane().getButtonTypes().get(0))).fire();
            return null;
        });
        var settings = onFx(dialog::getResult);
        var back = settings != null ? org.icepdf.fx.print.PrintDefaults.of(settings) : null;
        System.out.println("  print defaults back: " + back);
        check("print dialog starts from remembered choices", back != null
                && filePrinter.getName().equals(back.printer())
                && back.scaling() == org.icepdf.fx.print.PrintSettings.Scaling.ACTUAL_SIZE
                && back.orientation() == org.icepdf.fx.print.PrintSettings.Orientation.LANDSCAPE
                && !back.annotations()
                && paper.equals(back.paper()));
    }

    private static String statusOf(Node panel) {
        StringBuilder sb = new StringBuilder();
        for (Node n : panel.lookupAll(".label")) {
            if (n instanceof javafx.scene.control.Label l && l.getText() != null && l.getText().contains("match")) {
                sb.append(l.getText());
            }
        }
        return sb.toString();
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what);
        if (!ok) failures++;
    }

    private static void settle() throws Exception {
        Thread.sleep(700);
        for (int i = 0; i < 100; i++) {
            if (!onFx(() -> false)) break;
            Thread.sleep(100);
        }
        Thread.sleep(800);
    }

    private static void snapshot(Node node, String name) throws Exception {
        WritableImage image = onFx(() -> node.snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out.resolve(name).toFile());
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
