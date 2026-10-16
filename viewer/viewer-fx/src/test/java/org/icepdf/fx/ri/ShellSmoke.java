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
import org.icepdf.fx.ri.ui.CustomiseToolsPane;
import org.icepdf.fx.ri.ui.EdgeDrawer;
import org.icepdf.fx.ri.prefs.ViewerPreferences;

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
        // high contrast, both ways round: header pure white / pure black, and an owned dialog follows.
        onFx(() -> {
            viewer.getTheme().modeProperty().set(Theme.Mode.HIGH_CONTRAST);
            return null;
        });
        settle();
        BufferedImage hc = snapshot(viewer, "shell-high-contrast.png");
        check("high contrast header is white", (hc.getRGB(hc.getWidth() / 2, 12) & 0xffffff) == 0xffffff);
        onFx(() -> {
            viewer.getTheme().modeProperty().set(Theme.Mode.HIGH_CONTRAST_DARK);
            return null;
        });
        settle();
        BufferedImage hcDark = snapshot(viewer, "shell-high-contrast-dark.png");
        check("high contrast dark header is black", (hcDark.getRGB(hcDark.getWidth() / 2, 12) & 0xffffff) == 0);
        boolean dialogThemed = onFx(() -> {
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initOwner(viewer.getScene().getWindow());
            javafx.scene.control.CheckBox box = new javafx.scene.control.CheckBox("Check box");
            box.setSelected(true);
            javafx.scene.control.ComboBox<String> combo = new javafx.scene.control.ComboBox<>(
                    javafx.collections.FXCollections.observableArrayList("One", "Two"));
            combo.setValue("One");
            javafx.scene.control.ListView<String> list = new javafx.scene.control.ListView<>(
                    javafx.collections.FXCollections.observableArrayList("Selected row", "Row"));
            list.getSelectionModel().select(0);
            list.setPrefHeight(60);
            javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane(
                    new javafx.scene.control.Tab("General", new javafx.scene.layout.VBox(8, box, combo,
                            new javafx.scene.control.TextField("Text field"), list,
                            new javafx.scene.layout.HBox(8, new javafx.scene.control.Button("OK"),
                                    new javafx.scene.control.Button("Cancel")))),
                    new javafx.scene.control.Tab("Fonts"));
            dialog.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(tabs), 300, 280));
            dialog.show();
            box.requestFocus();
            boolean themed = dialog.getScene().getRoot().getStyleClass().contains("theme-high-contrast-dark");
            try {
                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(dialog.getScene().snapshot(null), null),
                        "png", out.resolve("dialog-high-contrast-dark.png").toFile());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            dialog.hide();
            return themed;
        });
        check("a dialog the viewer's window owns takes the theme", dialogThemed);
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
                if (org.icepdf.fx.i18n.Messages.get("toolRail.moveRight").equals(item.getText())
                        || org.icepdf.fx.i18n.Messages.get("toolRail.moveLeft").equals(item.getText())) item.fire();
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

        // a click inside an overlay is the panel's own (it floats within the document's bounds).
        onFx(() -> {
            viewer.getUtilityPanel().open(SidePanel.BOOKMARKS);
            return null;
        });
        settle();
        javafx.geometry.Point2D inPanel = onFx(() -> {
            Node content = panel.getContent();
            javafx.geometry.Bounds b = content.localToScene(content.getBoundsInLocal());
            return new javafx.geometry.Point2D(b.getCenterX(), b.getCenterY());
        });
        onFx(() -> {
            Event.fireEvent(panel.getContent(), pressed(inPanel));
            return null;
        });
        check("pressing inside the overlay keeps it open", onFx(panel::getState) == EdgeDrawer.State.OVERLAY);
        onFx(() -> {
            Event.fireEvent(viewer.getView(), pressed(inPanel));
            return null;
        });
        check("pressing the document closes it", onFx(panel::getState) == EdgeDrawer.State.COLLAPSED);

        // customise: the pane edits a copy and applies once; every viewer on the layout follows.
        PdfViewer second = onFx(() -> {
            PdfViewer v = PdfViewer.create(viewer.getFeatures(), viewer.getLayout());
            Stage stage = new Stage();
            stage.setScene(new Scene(v, 700, 600));
            stage.show();
            return v;
        });
        settle();
        String first = onFx(() -> viewer.getToolRail().arrangement().rail().get(0));
        onFx(() -> {
            CustomiseToolsPane pane = new CustomiseToolsPane(viewer.getActions(), viewer.getFeatures(), viewer.getLayout());
            pane.model().move(first, org.icepdf.fx.ri.ToolArrangement.Zone.HIDDEN, -1);
            pane.load();
            check("load() discards unapplied edits", pane.model().rail().contains(first));
            pane.model().move(first, org.icepdf.fx.ri.ToolArrangement.Zone.HIDDEN, -1);
            check("the pane lists edits only after refresh, not the layout",
                    viewer.getToolRail().arrangement().rail().contains(first));
            pane.apply();
            return null;
        });
        settle();
        check("apply hides the tool in this viewer", onFx(() -> !viewer.getToolRail().arrangement().rail().contains(first)
                && !viewer.getToolRail().arrangement().overflow().contains(first)));
        check("and in the other viewer on the same layout", onFx(() -> !second.getToolRail().arrangement().rail().contains(first)));
        onFx(() -> {
            second.dispose();
            return null;
        });

        // documents: the chrome follows the selected one.
        org.icepdf.fx.ri.document.DocumentPane one = onFx(viewer::getCurrentDocument);
        org.icepdf.fx.ri.document.DocumentPane two = onFx(viewer::addDocument);
        settle();
        check("a new document is selected and shown", onFx(() -> viewer.getCurrentDocument() == two
                && viewer.getView() == two.getView() && viewer.getShell().lookup(".pdf-view") != null || viewer.getView() == two.getView()));
        check("the empty document disables document actions", onFx(() -> ((javafx.scene.control.ButtonBase)
                byId(viewer.getHeaderBar(), "view.zoom-in")).isDisabled()));
        check("and the strip has nothing to offer", onFx(() -> viewer.getUtilityPanel().availablePanels().isEmpty()));
        onFx(() -> {
            viewer.open(doc);
            return null;
        });
        settle();
        onFx(() -> {
            two.getView().setCurrentPageIndex(4);
            return null;
        });
        settle();
        check("opening fills the second document", onFx(() -> !((javafx.scene.control.ButtonBase)
                byId(viewer.getHeaderBar(), "view.zoom-in")).isDisabled()
                && viewer.getUtilityPanel().availablePanels().contains(SidePanel.THUMBNAILS)));
        check("the page field follows it (5)", "5".equals(onFx(() -> ((javafx.scene.control.TextField)
                viewer.getHeaderBar().lookup(".page-field .text-field")).getText())));
        onFx(() -> {
            viewer.selectDocument(one);
            return null;
        });
        settle();
        check("selecting the first brings its page back (1)", "1".equals(onFx(() -> ((javafx.scene.control.TextField)
                viewer.getHeaderBar().lookup(".page-field .text-field")).getText())) && onFx(() -> viewer.getView() == one.getView()));
        check("removing the second leaves one", onFx(() -> viewer.removeDocument(two) && viewer.getDocuments().size() == 1));

        // tabs: a viewer that holds several documents.
        if (args.length > 2) {
            Path other = Paths.get(args[2]);
            PdfViewer tabbed = onFx(() -> {
                PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).multipleDocuments(true).build(),
                        UserLayout.inMemory());
                Stage stage = new Stage();
                stage.setScene(new Scene(v, 1100, 760));
                stage.show();
                v.open(doc);
                return v;
            });
            settle();
            check("one tab for one document", onFx(() -> tabbed.getHeaderBar().lookupAll(".doc-tab").size()) == 1);
            onFx(() -> tabbed.open(other));
            settle();
            check("Open replaces the current tab", onFx(() -> tabbed.getDocuments().size()) == 1
                    && onFx(() -> tabbed.getCurrentDocument().getTitle()).equals(other.getFileName().toString()));
            onFx(() -> tabbed.open(doc, tabbed.getCurrentDocument()));
            settle();
            check("a drop on a tab replaces that tab's document", onFx(() -> tabbed.getDocuments().size()) == 1
                    && onFx(() -> tabbed.getCurrentDocument().getTitle()).equals(doc.getFileName().toString()));
            onFx(() -> tabbed.openInNewTab(other));
            settle();
            check("Open in New Tab adds a second tab", onFx(() -> tabbed.getDocuments().size()) == 2
                    && onFx(() -> tabbed.getHeaderBar().lookupAll(".doc-tab").size()) == 2);
            check("and is the selected one", onFx(() -> tabbed.getCurrentDocument() == tabbed.getDocuments().get(1)));
            snapshot(tabbed, "shell-tabs.png");
            onFx(() -> tabbed.openInNewTab(doc));
            settle();
            check("opening an open file selects its tab", onFx(() -> tabbed.getDocuments().size()) == 2
                    && onFx(() -> tabbed.getCurrentDocument() == tabbed.getDocuments().get(0)));
            onFx(() -> {
                Event.fireEvent(tabbed, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, true, false, false));
                return null;
            });
            check("Ctrl+Tab goes to the next tab", onFx(() -> tabbed.getCurrentDocument() == tabbed.getDocuments().get(1)));
            onFx(() -> {
                tabbed.getActions().execute("document.new-tab");
                return null;
            });
            settle();
            check("New Tab adds an empty tab", onFx(() -> tabbed.getDocuments().size()) == 3
                    && onFx(() -> tabbed.getView().getDocument()) == null);
            onFx(() -> {
                tabbed.getActions().execute("document.new-tab");
                return null;
            });
            check("and only one empty tab at a time", onFx(() -> tabbed.getDocuments().size()) == 3);
            onFx(() -> tabbed.openInNewTab(Paths.get(args[1] + ".missing")));
            settle();
            check("a failed open leaves no stray tab", onFx(() -> tabbed.getDocuments().size()) == 3);
            // reorder: by index, and Ctrl+Shift+PageDown/PageUp for the selected tab.
            java.util.List<org.icepdf.fx.ri.document.DocumentPane> order = onFx(() -> new ArrayList<>(tabbed.getDocuments()));
            org.icepdf.fx.ri.document.DocumentPane selectedBefore = onFx(tabbed::getCurrentDocument);
            onFx(() -> {
                tabbed.moveDocument(order.get(0), 2);
                return null;
            });
            check("moveDocument reorders the tabs", onFx(() -> new ArrayList<>(tabbed.getDocuments()))
                    .equals(List.of(order.get(1), order.get(2), order.get(0))));
            check("the selection stays with its document", onFx(tabbed::getCurrentDocument) == selectedBefore);
            onFx(() -> {
                tabbed.selectDocument(order.get(0));
                Event.fireEvent(tabbed, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.PAGE_UP, true, true, false, false));
                return null;
            });
            check("Ctrl+Shift+PageUp moves the selected tab left", onFx(() -> new ArrayList<>(tabbed.getDocuments()))
                    .equals(List.of(order.get(1), order.get(0), order.get(2))) && onFx(() -> tabbed.getCurrentDocument()) == order.get(0));
            onFx(() -> {
                tabbed.moveDocument(order.get(0), 99);
                return null;
            });
            check("an index past the end puts it last", onFx(() -> tabbed.getDocuments().get(2)) == order.get(0));
            onFx(() -> tabbed.removeDocument(tabbed.getDocuments().get(2)));
            onFx(() -> tabbed.removeDocument(tabbed.getDocuments().get(1)));
            check("closing tabs leaves the other", onFx(() -> tabbed.getDocuments().size()) == 1
                    && onFx(() -> tabbed.getView().getDocument()) != null);
            // moving a tab to another viewer (another window): it leaves one and joins the mover.
            PdfViewer mover = onFx(() -> PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL)
                    .multipleDocuments(true).build(), UserLayout.inMemory()));
            int[] emptied = {0};
            org.icepdf.fx.ri.document.DocumentPane moving = onFx(() -> {
                org.icepdf.fx.ri.document.DocumentPane added = tabbed.addDocument();
                added.getSession().open(Paths.get(args[1]));
                return added;
            });
            settle();
            int before = onFx(() -> tabbed.getDocuments().size());
            onFx(() -> {
                check("a tab detaches", tabbed.detachDocument(moving));
                mover.adoptDocument(moving, 0);
                return null;
            });
            settle();
            check("the mover viewer shows the moved document", onFx(() -> mover.getDocuments().size()) == 1
                    && onFx(() -> mover.getCurrentDocument()) == moving && onFx(() -> mover.getView().getDocument()) != null);
            check("the first viewer lost one", onFx(() -> tabbed.getDocuments().size()) == before - 1
                    && !onFx(() -> tabbed.getDocuments().contains(moving)));
            onFx(() -> {
                mover.setOnEmptied(() -> emptied[0]++);
                return null;
            });
            check("detaching the only tab tells the viewer it is empty", onFx(() -> mover.detachDocument(moving)) && emptied[0] == 1
                    && onFx(() -> mover.getDocuments().size()) == 1 && onFx(() -> mover.getView().getDocument()) == null);
            onFx(() -> {
                mover.dispose();
                return null;
            });
            // file history: opened files are reported; the menu lists them; the chooser folder is kept.
            java.util.List<Path> remembered = new ArrayList<>();
            onFx(() -> {
                tabbed.setFileHistory(new FileHistory() {
                    public java.util.List<Path> recent() {
                        return remembered;
                    }

                    public void opened(Path file) {
                        remembered.add(0, file);
                    }

                    public void clearRecent() {
                        remembered.clear();
                    }

                    public Path lastDirectory() {
                        return null;
                    }

                    public void setLastDirectory(Path directory) {
                    }
                });
                tabbed.open(Paths.get(args[1]));
                return null;
            });
            settle();
            check("an opened file is reported to the history", remembered.size() == 1);
            javafx.scene.control.Menu recent = onFx(() -> tabbed.getHeaderBar().getMenu().getItems().stream()
                    .filter(i -> i instanceof javafx.scene.control.Menu).map(i -> (javafx.scene.control.Menu) i)
                    .filter(m -> m.getText().equals(org.icepdf.fx.i18n.Messages.get("viewer.openRecent"))).findFirst().orElse(null));
            check("the menu has Open Recent", recent != null && onFx(recent::isVisible));
            onFx(() -> {
                recent.getOnShowing().handle(null);
                return null;
            });
            check("Open Recent lists the file and Clear", onFx(() -> recent.getItems().size()) == 3);
            check("a window with no changes closes without asking", onFx(tabbed::confirmClose));
            onFx(() -> {
                tabbed.dispose();
                return null;
            });
        }

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

    /** CSS lookup can't match ids with dots: walk the nodes. */
    private static Node byId(Node root, String id) {
        if (id.equals(root.getId())) return root;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = byId(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static MouseEvent pressed(javafx.geometry.Point2D scene) {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null);
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
