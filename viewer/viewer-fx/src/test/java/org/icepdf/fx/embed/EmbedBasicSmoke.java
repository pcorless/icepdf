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
package org.icepdf.fx.embed;

import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.icepdf.fx.panels.ThumbnailPanel;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.ui.EdgeDrawer;
import org.icepdf.fx.ri.ui.HeaderBar;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.RenderStats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.icepdf.fx.embed.SmokeSupport.fx;
import static org.icepdf.fx.embed.SmokeSupport.onFx;

/**
 * An application that embeds the bare page view - no ICEpdf header, rail or panels - and drives it
 * from its own tool bar: previous / next page, zoom, and find.  Its buttons are its own controls;
 * they run ICEpdf's commands by id through {@link ActionControls}, and its labels (page "3 of 12",
 * "125%", "2 of 14 hits") are bound to the view's JavaFX properties.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:embedSmoke -PsmokeArgs="outdir;doc.pdf[;search term]"}
 */
public final class EmbedBasicSmoke {

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]);
        Files.createDirectories(out);
        Path doc = Paths.get(args[1]);
        String term = args.length > 2 ? args[2] : "the";
        SmokeSupport.startFx();
        SmokeSupport smoke = new SmokeSupport(out);

        Host host = onFx(Host::new);
        System.out.println("embedding, basic:");
        smoke.check("no ICEpdf chrome in the scene", onFx(() -> SmokeSupport.find(host.root, HeaderBar.class) == null
                && SmokeSupport.find(host.root, EdgeDrawer.class) == null));
        smoke.check("commands are disabled with no document", onFx(() -> host.next.isDisabled() && host.zoomIn.isDisabled()
                && host.findNext.isDisabled()));
        smoke.check("page label is empty with no document", onFx(() -> host.pageLabel.getText()).isEmpty(),
                onFx(() -> host.pageLabel.getText()));

        fx(() -> host.session.open(doc));
        SmokeSupport.waitOpened(host.session, doc);
        int pages = onFx(host.view::getPageCount);
        smoke.check("open: \"Page 1 of " + pages + "\"", onFx(() -> host.pageLabel.getText()).equals("Page 1 of " + pages),
                onFx(() -> host.pageLabel.getText()));
        smoke.check("commands enabled once open", onFx(() -> !host.next.isDisabled() && !host.zoomIn.isDisabled()));

        // previous / next: the application's buttons, ICEpdf's navigation actions.
        if (pages >= 3) {
            fx(host.next::fire);
            fx(host.next::fire);
            smoke.check("next twice: page 3", onFx(host.view::getCurrentPageIndex) == 2
                    && onFx(() -> host.pageLabel.getText()).equals("Page 3 of " + pages), onFx(() -> host.pageLabel.getText()));
            fx(host.previous::fire);
            smoke.check("previous: page 2", onFx(host.view::getCurrentPageIndex) == 1);
        }
        fx(() -> host.goTo.setText(String.valueOf(pages)));
        fx(() -> host.goTo.getOnAction().handle(null));
        smoke.check("the page field goes to the last page", onFx(host.view::getCurrentPageIndex) == pages - 1);
        smoke.check("Next greys out on the last page", onFx(host.next::isDisabled) && !onFx(host.previous::isDisabled));
        fx(() -> host.view.setCurrentPageIndex(0));
        smoke.check("Prev greys out on the first page", onFx(host.previous::isDisabled) && !onFx(host.next::isDisabled));

        // zoom: the label is bound to zoomProperty.
        fx(() -> {
            host.view.setFitMode(FitMode.NONE);
            host.view.setZoom(1);
        });
        smoke.check("zoom label 100%", onFx(() -> host.zoomLabel.getText()).equals("100%"), onFx(() -> host.zoomLabel.getText()));
        fx(host.zoomIn::fire);
        double in = onFx(host.view::getZoom);
        smoke.check("zoom in: larger and the label follows", in > 1
                && onFx(() -> host.zoomLabel.getText()).equals(Math.round(in * 100) + "%"), in + " " + onFx(() -> host.zoomLabel.getText()));
        fx(host.zoomOut::fire);
        smoke.check("zoom out: back to 100%", Math.abs(onFx(host.view::getZoom) - 1) < 1e-6, onFx(host.view::getZoom));
        SmokeSupport.waitIdle(host.view);

        // Ctrl+F: the find action, whose host operation focuses the application's own field.
        fx(host.view::requestFocus);
        SmokeSupport.key(host.stage, KeyCode.F, true);
        smoke.check("Ctrl+F puts the caret in the application's search field", onFx(host.searchField::isFocused),
                onFx(() -> host.stage.getScene().getFocusOwner()));

        // find: the application's field searches; next / previous are ICEpdf's actions.
        fx(() -> {
            host.searchField.setText(term);
            host.searchField.getOnAction().handle(null);
        });
        long deadline = System.currentTimeMillis() + 60_000;
        Thread.sleep(200);
        while (onFx(host.view::isSearching) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        int hits = onFx(() -> host.view.getSearchHits().size());
        smoke.check("search \"" + term + "\" finds hits", hits > 0, hits);
        smoke.check("hit label \"1 of " + hits + "\"", onFx(() -> host.hitLabel.getText()).equals("1 of " + hits),
                onFx(() -> host.hitLabel.getText()));
        if (hits > 1) {
            fx(host.findNext::fire);
            smoke.check("next hit", onFx(host.view::getCurrentSearchHitIndex) == 1
                    && onFx(() -> host.hitLabel.getText()).equals("2 of " + hits), onFx(() -> host.hitLabel.getText()));
            int hitPage = onFx(() -> host.view.getSearchHits().get(1).pageIndex());
            smoke.check("the hit's page is the current page", onFx(host.view::getCurrentPageIndex) == hitPage
                            && onFx(() -> host.pageLabel.getText()).startsWith("Page " + (hitPage + 1) + " "),
                    onFx(() -> host.pageLabel.getText()) + " vs hit on " + (hitPage + 1));
            // until the user scrolls: then it's the page showing most of itself again.
            fx(() -> host.view.scrollBy(0, 40));
            SmokeSupport.waitIdle(host.view);
            smoke.check("scrolling hands the current page back to the most visible", onFx(() -> pagesOnScreen(host.view))
                    .contains(onFx(host.view::getCurrentPageIndex)), onFx(host.view::getCurrentPageIndex));
            fx(host.findPrevious::fire);
            smoke.check("previous hit", onFx(host.view::getCurrentSearchHitIndex) == 0);
        }
        if (hits > 0) {
            int marked = onFx(host.view::redactSearchHits).get(60, java.util.concurrent.TimeUnit.SECONDS);
            smoke.check("redact all marks one redaction per hit, undoably", marked == hits
                    && onFx(host.view::isCanUndo), marked + " of " + hits);
            fx(host.view::undo);
            smoke.check("undo takes them all back", !onFx(host.view::isCanUndo) && !onFx(host.view::hasTermRedaction));
        }
        SmokeSupport.waitIdle(host.view);
        smoke.snapshot(host.root, "embed-basic.png");

        // a thumbnail strip next to the view turns the same way as the pages: compare the two snapshots.
        Stage strip = onFx(Stage::new);
        ThumbnailPanel thumbnails = onFx(() -> {
            ThumbnailPanel panel = new ThumbnailPanel(host.view);
            strip.setScene(new Scene(panel, 220, 700));
            strip.show();
            return panel;
        });
        fx(() -> {
            host.view.setCurrentPageIndex(0);
            host.view.setRotation(90);
        });
        SmokeSupport.waitIdle(host.view);
        Thread.sleep(1500);
        smoke.snapshot(host.root, "embed-rotated-90-view.png");
        smoke.snapshot(thumbnails, "embed-rotated-90-thumbnails.png");
        fx(() -> host.view.setRotation(0));
        SmokeSupport.waitIdle(host.view);

        fx(() -> host.session.dispose());
        smoke.check("closing empties the labels and disables the commands", onFx(() -> host.pageLabel.getText().isEmpty()
                && host.next.isDisabled()));
        fx(strip::close);
        fx(host.stage::close);
        fx(thumbnails::dispose);
        fx(host.view::dispose);
        Thread.sleep(500);
        smoke.check("dispose leaves nothing of the view's queued", RenderStats.queued() == 0, RenderStats.queued());
        smoke.check("no per-view render threads", perViewThreads().isEmpty(), perViewThreads());
        smoke.check("the shared render threads stay within the pool",
                RenderStats.poolThreadNames().size() <= RenderStats.poolThreads(), RenderStats.poolThreadNames());
        checkTwoWindows(smoke, doc);
        checkViewerWithoutChrome(smoke, doc);
        smoke.finish("embed basic smoke");
    }

    /**
     * Two windows on one document, both panning: they share the one render pool, so the process's
     * render threads stay within its size however many views paint.  A window that is hidden is in
     * the background, behind the other's work.
     */
    private static void checkTwoWindows(SmokeSupport smoke, Path doc) throws Exception {
        Host a = onFx(Host::new);
        Host b = onFx(Host::new);
        fx(() -> b.stage.setX(a.stage.getX() + 60));
        for (Host host : new Host[]{a, b}) {
            fx(() -> host.session.open(doc));
            SmokeSupport.waitOpened(host.session, doc);
            fx(() -> {
                host.view.setFitMode(FitMode.NONE);
                host.view.setZoom(3);
            });
        }
        int most = 0;
        for (int step = 0; step < 40; step++) {
            fx(() -> {
                a.view.scrollBy(0, 300);
                b.view.scrollBy(0, -200);
                if (b.view.getCurrentPageIndex() == 0) b.view.setCurrentPageIndex(20);
            });
            most = Math.max(most, RenderStats.poolThreadNames().size());
            Thread.sleep(30);
        }
        smoke.check("two windows panning: render threads within the pool size", most <= RenderStats.poolThreads()
                && perViewThreads().isEmpty(), most + " of " + RenderStats.poolThreads() + " " + perViewThreads());
        // not "at most one in front": under XWayland JavaFX can report both stages focused at once.
        for (Host host : new Host[]{a, b}) {
            smoke.check("a view is in front exactly when its window is focused",
                    onFx(() -> RenderStats.isForeground(host.view) == host.stage.isFocused()),
                    onFx(() -> "focused " + host.stage.isFocused() + ", in front " + RenderStats.isForeground(host.view)));
        }
        fx(b.stage::hide);
        smoke.check("a hidden window's view is behind", !onFx(() -> RenderStats.isForeground(b.view)));
        SmokeSupport.waitIdle(a.view);
        for (Host host : new Host[]{a, b}) {
            fx(() -> host.session.dispose());
            fx(host.stage::close);
            fx(host.view::dispose);
        }
        Thread.sleep(300);
        smoke.check("both disposed: nothing queued", RenderStats.queued() == 0, RenderStats.queued());
    }

    /** Threads of the per-view pools the shared render pool replaced; there should be none. */
    private static java.util.List<String> perViewThreads() {
        return Thread.getAllStackTraces().keySet().stream().map(Thread::getName)
                .filter(n -> n.startsWith("icepdf-fx-tile") || n.startsWith("icepdf-fx-preview")
                        || n.startsWith("icepdf-fx-text") || n.startsWith("icepdf-fx-thumbnails"))
                .sorted().toList();
    }

    /**
     * The other shape: the whole viewer component ({@link PdfViewer}: panels, dialogs, signing, the
     * annotation menu) with its header, rail and panel strip left out, the application's own tool
     * bar running its actions, and one of its side panels placed by the application.
     */
    private static void checkViewerWithoutChrome(SmokeSupport smoke, Path doc) throws Exception {
        System.out.println("PdfViewer without chrome:");
        Button previous = new Button("Prev"), next = new Button("Next"), fullScreen = new Button("Full screen");
        Label page = new Label();
        BorderPane root = new BorderPane();
        Stage stage = onFx(Stage::new);
        PdfViewer viewer = onFx(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).chrome().build(),
                    UserLayout.inMemory());
            v.getActions().bind(previous, "navigation.previous");
            v.getActions().bind(next, "navigation.next");
            v.getActions().bind(fullScreen, "view.full-screen");
            PdfView view = v.getView();
            page.textProperty().bind(Bindings.createStringBinding(() -> view.getPageCount() == 0 ? ""
                    : (view.getCurrentPageIndex() + 1) + " / " + view.getPageCount(),
                    view.currentPageIndexProperty(), view.pageCountProperty()));
            root.setTop(new ToolBar(previous, page, next, fullScreen));
            // the bookmarks panel, where the application wants it.
            SplitPane split = new SplitPane(v.getCurrentDocument().getOutline(), v);
            split.setDividerPositions(0.25);
            root.setCenter(split);
            stage.setScene(new Scene(root, 1100, 760));
            stage.show();
            v.open(doc);
            return v;
        });
        SmokeSupport.waitOpened(onFx(viewer::getSession), doc);
        smoke.check("no header, rail or panel strip", onFx(() -> SmokeSupport.find(root, HeaderBar.class) == null
                && SmokeSupport.find(root, EdgeDrawer.class) == null));
        smoke.check("the side panel commands are left out", onFx(() -> !viewer.getActions().isAvailable("view.side-panel")
                && !viewer.getActions().isAvailable("search.panel") && !viewer.getActions().isAvailable("search.find")));
        fx(next::fire);
        smoke.check("the application's Next runs the viewer's action", onFx(() -> viewer.getView().getCurrentPageIndex()) == 1
                && "2".equals(onFx(page::getText).split(" ")[0]), onFx(page::getText));
        double width = onFx(() -> viewer.getView().getWidth());
        smoke.check("the page view fills the viewer", Math.abs(width - onFx(viewer::getWidth)) < 1, width + " of " + onFx(viewer::getWidth));
        fx(fullScreen::fire);
        Thread.sleep(500);
        fx(() -> viewer.fullScreenProperty().set(false));
        Thread.sleep(500);
        smoke.check("leaving full screen brings no chrome back", onFx(() -> SmokeSupport.find(root, HeaderBar.class) == null
                && SmokeSupport.find(root, EdgeDrawer.class) == null));
        SmokeSupport.waitIdle(onFx(viewer::getView));
        smoke.snapshot(root, "embed-viewer-no-chrome.png");
        fx(() -> {
            viewer.dispose();
            stage.close();
        });
    }

    /** The pages under a column of points down the middle of the view. */
    static java.util.Set<Integer> pagesOnScreen(PdfView view) {
        java.util.Set<Integer> pages = new java.util.TreeSet<>();
        for (double y = 0; y < view.getHeight(); y += 10) {
            view.pageAt(view.getWidth() / 2, y).ifPresent(p -> pages.add(p.pageIndex()));
        }
        return pages;
    }

    /**
     * The application.  Everything here is what an embedder writes: a {@link PdfView} in its own
     * layout, a {@link DocumentSession} to own the document, a context and the actions.
     */
    static final class Host {
        final Stage stage = new Stage();
        final PdfView view = new PdfView();
        final DocumentSession session = new DocumentSession(view);
        final TextField searchField = new TextField();
        final ActionControls actions = new ActionControls(StandardActions.registry(),
                ViewerContext.of(view).onFind(searchField::requestFocus));
        final Button previous = new Button("Prev"), next = new Button("Next"), zoomIn = new Button("Zoom +"),
                zoomOut = new Button("Zoom −"), findNext = new Button("↓"), findPrevious = new Button("↑");
        final TextField goTo = new TextField();
        final Label pageLabel = new Label(), zoomLabel = new Label(), hitLabel = new Label();
        final BorderPane root;

        Host() {
            // the application's own buttons, running ICEpdf's commands and greying out with them.
            actions.bind(previous, "navigation.previous");
            actions.bind(next, "navigation.next");
            actions.bind(zoomIn, "view.zoom-in");
            actions.bind(zoomOut, "view.zoom-out");
            actions.bind(findNext, "search.next");
            actions.bind(findPrevious, "search.previous");

            // the application's own widgets show the view's state.
            pageLabel.textProperty().bind(Bindings.createStringBinding(() -> view.getPageCount() == 0 ? ""
                            : "Page " + (view.getCurrentPageIndex() + 1) + " of " + view.getPageCount(),
                    view.currentPageIndexProperty(), view.pageCountProperty()));
            zoomLabel.textProperty().bind(Bindings.createStringBinding(() -> Math.round(view.getZoom() * 100) + "%",
                    view.zoomProperty()));
            hitLabel.textProperty().bind(Bindings.createStringBinding(() -> view.getSearchHits().isEmpty() ? ""
                            : (view.getCurrentSearchHitIndex() + 1) + " of " + view.getSearchHits().size(),
                    view.getSearchHits(), view.currentSearchHitIndexProperty()));
            goTo.setPrefColumnCount(3);
            goTo.setOnAction(e -> {
                try {
                    view.setCurrentPageIndex(Integer.parseInt(goTo.getText().trim()) - 1);
                } catch (NumberFormatException ignored) {
                    // not a number
                }
            });
            searchField.setPromptText("Find");
            // there is no action taking a query: the field calls the view.
            searchField.setOnAction(e -> view.search(searchField.getText()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            ToolBar bar = new ToolBar(previous, pageLabel, next, goTo, new Separator(), zoomOut, zoomLabel, zoomIn,
                    spacer, searchField, findPrevious, findNext, hitLabel);
            root = new BorderPane(view);
            root.setTop(bar);
            Scene scene = new Scene(root, 1000, 760);
            // the application has no menu bar: put the shortcuts it wants on the scene.
            actions.installAccelerators(scene, "search.find", "search.next", "search.previous");
            stage.setScene(scene);
            stage.setTitle("Embedded PdfView");
            stage.show();
        }

    }
}
