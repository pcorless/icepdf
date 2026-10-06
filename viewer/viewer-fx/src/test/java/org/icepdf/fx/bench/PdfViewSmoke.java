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
package org.icepdf.fx.bench;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.Page;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ViewMode;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Drives a real {@link PdfView} through the plan's verification script using only its public API,
 * waits for {@code rendering == false} after each step, and snapshots the window: open, navigate,
 * zoom to 4000%, pan at depth, rotate, cycle view modes, overlay alignment.  Prints time-to-idle and
 * post-GC heap per step so the 4000% / small-heap criterion is checked by numbers, not by eye.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:smoke -PsmokeArgs="file.pdf;out-dir" [-PsmokeXmx=512m]}
 */
public final class PdfViewSmoke {

    private static final java.util.logging.Logger FONTBOX = java.util.logging.Logger.getLogger("org.apache.fontbox");

    private PdfView view;
    private Stage stage;
    private Path out;
    private int step;

    public static void main(String[] args) throws Exception {
        FONTBOX.setLevel(java.util.logging.Level.SEVERE);
        String[] parts = String.join(";", args).split(";");
        Path file = Paths.get(parts[0]);
        Path out = Paths.get(parts.length > 1 ? parts[1] : "build/smoke");
        new PdfViewSmoke().run(file, out);
        System.exit(0);
    }

    private void run(Path file, Path outDir) throws Exception {
        out = outDir;
        Files.createDirectories(out);
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            view = new PdfView();
            stage = new Stage();
            // hosted the way applications embed it - a BorderPane with bars around it - not as the
            // scene root, which sizes it directly and hid a pref-size layout loop once.
            javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(view);
            root.setTop(new javafx.scene.control.ToolBar(new javafx.scene.control.Button("toolbar")));
            root.setBottom(new javafx.scene.control.Label("status bar"));
            // fit the screen (at a 2x UI scale a 1200x900 window can run off it, and the Robot can't
            // click off-screen), top-left aligned.
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setScene(new Scene(root, Math.min(1200, screen.getWidth()), Math.min(900, screen.getHeight() - 30)));
            stage.setX(screen.getMinX());
            stage.setY(screen.getMinY());
            stage.show();
            ready.countDown();
        });
        ready.await();
        System.out.printf("max heap %dMB, output scale %.2f%n", Runtime.getRuntime().maxMemory() >> 20,
                onFx(() -> stage.getOutputScaleX()));

        if ("annotations".equals(System.getProperty("smoke.only"))) {
            annotationSnapshots(file);
            return;
        }
        if ("forms".equals(System.getProperty("smoke.only"))) {
            checkForms(file);
            System.out.println(failures == 0 ? "form checks: all passed" : "form checks: " + failures + " FAILED");
            return;
        }
        if ("annotation-ui".equals(System.getProperty("smoke.only"))) {
            checkAnnotationUi(file);
            System.out.println(failures == 0 ? "annotation UI checks: all passed"
                    : "annotation UI checks: " + failures + " FAILED");
            return;
        }
        Document document = new Document();
        document.setFile(file.toString());
        int pages = document.getNumberOfPages();
        int mid = Math.min(pages - 1, 99);

        act("open fit-width", v -> {
            v.setFitMode(FitMode.WIDTH);
            v.setDocument(document);
        });
        if ("tools".equals(System.getProperty("smoke.only"))) {
            checkTools(document);
            finish(document);
            return;
        }
        act("goto page " + (mid + 1), v -> v.setCurrentPageIndex(mid));
        for (double zoom : new double[]{1, 4, 16, 40}) {
            act("zoom " + (int) (zoom * 100) + "%", v -> {
                v.setFitMode(FitMode.NONE);
                v.setZoom(zoom);
            });
        }
        for (int i = 0; i < 6; i++) {
            int n = i;
            act("pan at 4000% #" + n, v -> v.scrollBy(n % 2 == 0 ? 3000 : -1500, 4000));
        }
        act("zoom 150%", v -> v.setZoom(1.5));
        for (int i = 1; i <= 4; i++) {
            act("rotate cw " + (i * 90 % 360), PdfView::rotateClockwise);
        }
        act("facing continuous, fit page", v -> {
            v.setViewMode(ViewMode.FACING_CONTINUOUS);
            v.setFitMode(FitMode.PAGE);
        });
        act("cover page", v -> v.setCoverPage(true));
        act("facing", v -> v.setViewMode(ViewMode.FACING));
        act("single page, next", v -> {
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.nextPage();
        });
        act("continuous + overlay, 200%", v -> {
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setFitMode(FitMode.NONE);
            v.setZoom(2);
            v.setPageOverlayFactory(PdfViewSmoke::cropFrame);
        });
        act("overlay rotated 90", PdfView::rotateClockwise);
        act("overlay rotated 270", v -> v.setRotation(270));
        checkTools(document);
        finish(document);
    }

    private void finish(Document document) throws Exception {
        if (Boolean.getBoolean("smoke.hold")) {
            // keep the process (and its heap) alive for jcmd/jmap inspection.
            System.out.println("HOLD pid " + ProcessHandle.current().pid());
            Thread.sleep(120_000);
        }
        onFx(() -> {
            view.setDocument(null);
            return null;
        });
        document.dispose();
    }

    private void act(String name, Consumer<PdfView> action) throws Exception {
        long t0 = System.nanoTime();
        onFx(() -> {
            action.accept(view);
            return null;
        });
        boolean idle = waitIdle(120_000);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.gc();
        long heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
        String fileName = String.format("%02d_%s.png", ++step, name.replaceAll("[^A-Za-z0-9%]+", "_"));
        snapshot(out.resolve(fileName).toFile());
        String state = onFx(() -> String.format("page %d/%d zoom %.0f%% rot %.0f",
                view.getCurrentPageIndex() + 1, view.getPageCount(), view.getZoom() * 100, view.getRotation()));
        System.out.printf("%-32s %s %8.0fms  heap %4dMB  %s%n", name, idle ? "idle   " : "TIMEOUT", ms, heap, state);
    }

    // ---- forms --------------------------------------------------------------------------------

    private Document formDoc;

    /** The widget(s) of a field by name in the open form document. */
    private java.util.List<org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation> widgets(String name) {
        java.util.List<org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation> out = new java.util.ArrayList<>();
        for (org.icepdf.core.pobjects.annotations.Annotation a : formDoc.getPageTree().getPage(0).getAnnotations()) {
            if (a instanceof org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w) {
                org.icepdf.core.pobjects.acroform.FieldDictionary f = w.getFieldDictionary();
                String partial = f.getPartialFieldName();
                String full = (partial == null || partial.isEmpty()) && f.getParent() != null
                        ? f.getParent().getFullyQualifiedFieldName() : f.getFullyQualifiedFieldName();
                if (name.equals(full)) out.add(w);
            }
        }
        return out;
    }

    private String nameOf(org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w) {
        if (w == null) return "null";
        org.icepdf.core.pobjects.acroform.FieldDictionary f = w.getFieldDictionary();
        String partial = f.getPartialFieldName();
        return (partial == null || partial.isEmpty()) && f.getParent() != null
                ? f.getParent().getFullyQualifiedFieldName() : f.getFullyQualifiedFieldName();
    }

    /** Form filling on the project fixture all_fields.pdf (step-by-step checks are appended per phase step). */
    private void checkForms(Path fixture) throws Exception {
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        formDoc = new Document();
        formDoc.setFile(fixture.toString());
        act("forms: all_fields fit page", v -> {
            v.setDocument(formDoc);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
        });
        javafx.scene.Node viewport = onFx(() -> view.getChildrenUnmodifiable().get(0));
        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation name = widgets("name").get(0);
        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation agree = widgets("agree").get(0);

        double[] namePoint = viewPointIn(0, name.getUserSpaceRectangle());
        robotMove(robot, namePoint);
        check("I-beam over a text field", onFx(viewport::getCursor) == javafx.scene.Cursor.TEXT,
                String.valueOf(onFx(viewport::getCursor)));
        robotMove(robot, viewPointIn(0, agree.getUserSpaceRectangle()));
        check("hand over a check box", onFx(viewport::getCursor) == javafx.scene.Cursor.HAND,
                String.valueOf(onFx(viewport::getCursor)));

        robotClick(robot, namePoint);
        check("click focuses the field", onFx(view::getFocusedField) == name, nameOf(onFx(view::getFocusedField)));
        int ring = countPixels(0, 119, 255, 12);
        check("focus ring drawn", ring > 40, ring + " px");

        // tab order through the API: the fixture has no /Tabs, so /Annots order.
        java.util.List<String> expected = java.util.List.of("notes", "secret", "code", "agree", "color", "color",
                "color", "pair", "pair", "pair", "country", "city", "fruit", "toppings", "reset", "submit", "name");
        java.util.List<String> seen = new java.util.ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            fx(view::focusNextField);
            seen.add(nameOf(onFx(view::getFocusedField)));
        }
        check("Tab order walks every field and wraps", seen.equals(expected), String.valueOf(seen));
        fx(view::focusPreviousField);
        check("Shift+Tab goes back", "submit".equals(nameOf(onFx(view::getFocusedField))),
                nameOf(onFx(view::getFocusedField)));
        // the key handling itself, deterministically: a synthetic Tab through the control's event chain
        fx(() -> {
            view.focusField(name);
            javafx.event.Event.fireEvent(view, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.TAB, false, false, false, false));
        });
        check("Tab handler moves focus (synthetic key event)", "notes".equals(nameOf(onFx(view::getFocusedField))),
                nameOf(onFx(view::getFocusedField)));
        fx(() -> javafx.event.Event.fireEvent(view, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                "", "", javafx.scene.input.KeyCode.TAB, true, false, false, false)));
        check("Shift+Tab handler goes back (synthetic key event)", "name".equals(nameOf(onFx(view::getFocusedField))),
                nameOf(onFx(view::getFocusedField)));
        // and with the real key (needs desktop keyboard focus)
        fx(() -> {
            stage.toFront();
            view.requestFocus();
            view.focusField(name);
        });
        keys(robot, javafx.scene.input.KeyCode.TAB);
        check("Tab key moves focus (real key; needs desktop focus)", "notes".equals(nameOf(onFx(view::getFocusedField))),
                nameOf(onFx(view::getFocusedField)));
        fx(view::clearFieldFocus);

        // field highlight: light blue only on the fields
        act("forms: highlight fields", v -> v.setHighlightFormFields(true));
        java.util.List<double[]> tint = pixelsNear(225, 232, 255, 10);
        int inside = 0;
        for (double[] p : tint) {
            java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(p[0], p[1]));
            if (hit.isEmpty()) continue;
            for (org.icepdf.core.pobjects.annotations.Annotation a : formDoc.getPageTree().getPage(0).getAnnotations()) {
                java.awt.geom.Rectangle2D r = a.getUserSpaceRectangle();
                if (a instanceof org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation
                        && hit.get().x() >= r.getMinX() - 1.5 && hit.get().x() <= r.getMaxX() + 1.5
                        && hit.get().y() >= r.getMinY() - 1.5 && hit.get().y() <= r.getMaxY() + 1.5) {
                    inside++;
                    break;
                }
            }
        }
        check("field highlight only on fields", tint.size() > 500 && inside >= tint.size() * 0.99,
                tint.size() + " tint px, " + inside + " inside field rects");
        snapshot(out.resolve("forms_highlight.png").toFile());
        act("forms: highlight off", v -> v.setHighlightFormFields(false));
        checkFormFilling(robot);
        fx(() -> view.setDocument(null));
        formDoc.dispose();
    }

    /** Filled in by later form steps. */
    private void checkFormFilling(javafx.scene.robot.Robot robot) throws Exception {
    }

    private java.util.List<double[]> pixelsNear(int r, int g, int b, int tolerance) throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        WritableImage image = onFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            if (Math.abs((c >> 16 & 0xff) - r) <= tolerance && Math.abs((c >> 8 & 0xff) - g) <= tolerance
                    && Math.abs((c & 0xff) - b) <= tolerance) {
                out.add(new double[]{(i % w + 0.5) / scale, (i / w + 0.5) / scale});
            }
        }
        return out;
    }

    // ---- annotation interaction -------------------------------------------------------------

    /**
     * Select, hover and links through real mouse input, on the annotation corpus directory: a
     * square annotation is hit-tested, selected by a click (chrome drawn) and deselected by a click
     * elsewhere; a /Dest link navigates and a GoToR link reaches the application callback.
     */
    private void checkAnnotationUi(Path dir) throws Exception {
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        // -- select / deselect
        Document rect = new Document();
        rect.setFile(dir.resolve("Invoice_rectangle.pdf").toString());
        act("annotation UI: Invoice_rectangle fit page", v -> {
            v.setDocument(rect);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
        });
        org.icepdf.core.pobjects.annotations.Annotation target = null;
        for (org.icepdf.core.pobjects.annotations.Annotation a : rect.getPageTree().getPage(0).getAnnotations()) {
            if (a instanceof org.icepdf.core.pobjects.annotations.SquareAnnotation) {
                target = a;
                break;
            }
        }
        if (target == null) {
            check("square annotation found", false, "fixture has none");
        } else {
            org.icepdf.core.pobjects.annotations.Annotation square = target;
            double[] p = viewPointIn(0, square.getUserSpaceRectangle());
            java.util.Optional<org.icepdf.core.pobjects.annotations.Annotation> hit =
                    onFx(() -> view.annotationAt(p[0], p[1]));
            check("annotationAt finds the square", hit.isPresent() && hit.get() == square, String.valueOf(hit));
            robotClick(robot, p);
            check("click selects it", onFx(view::getSelectedAnnotation) == square,
                    String.valueOf(onFx(view::getSelectedAnnotation)));
            int chrome = countPixels(0, 119, 255, 12);
            check("selection chrome drawn", chrome > 40, chrome + " chrome px");
            check("no text selection from that click", onFx(view::getTextSelection) == null,
                    String.valueOf(onFx(view::getTextSelection)));
            double[] away = viewPointWithoutAnnotation(0);
            robotClick(robot, away);
            check("click elsewhere deselects", onFx(view::getSelectedAnnotation) == null,
                    String.valueOf(onFx(view::getSelectedAnnotation)));
            check("chrome gone", countPixels(0, 119, 255, 12) < 5, "");
            checkAnnotationEdits(robot, square);
        }
        fx(() -> view.setDocument(null));
        rect.dispose();

        checkPopups(robot, dir);
        checkCreation(robot, dir);

        // -- links
        Document links = new Document();
        links.setFile(dir.resolve("links.pdf").toString());
        java.util.List<org.icepdf.fx.view.AnnotationActionEvent> events = new java.util.ArrayList<>();
        act("annotation UI: links.pdf page 1 fit page", v -> {
            v.setDocument(links);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setCurrentPageIndex(0);
            v.setFitMode(FitMode.PAGE);
            v.setOnAnnotationAction(events::add);
        });
        org.icepdf.core.pobjects.annotations.LinkAnnotation destLink = null;
        org.icepdf.core.pobjects.annotations.LinkAnnotation remoteLink = null;
        for (org.icepdf.core.pobjects.annotations.Annotation a : links.getPageTree().getPage(0).getAnnotations()) {
            if (!(a instanceof org.icepdf.core.pobjects.annotations.LinkAnnotation link)) continue;
            if (destLink == null && link.getAction() == null && link.getDestination() != null) destLink = link;
            if (remoteLink == null && link.getAction() instanceof org.icepdf.core.pobjects.actions.GoToRAction) remoteLink = link;
        }
        if (remoteLink != null) {
            double[] p = viewPointIn(0, remoteLink.getUserSpaceRectangle());
            robotMove(robot, p);
            javafx.scene.Node viewport = onFx(() -> view.getChildrenUnmodifiable().get(0));
            check("hand cursor over a link", onFx(viewport::getCursor) == javafx.scene.Cursor.HAND,
                    String.valueOf(onFx(viewport::getCursor)));
            robotClick(robot, p);
            check("GoToR link goes to the app callback", events.size() == 1
                            && events.get(0).action() instanceof org.icepdf.core.pobjects.actions.GoToRAction
                            && events.get(0).annotation() == remoteLink,
                    events.size() + " events");
        }
        if (destLink != null) {
            int expected = links.getPageTree().getPageNumber(destLink.getDestination().getPageReference());
            double[] p = viewPointIn(0, destLink.getUserSpaceRectangle());
            robotClick(robot, p);
            waitIdle(30_000);
            int page = onFx(view::getCurrentPageIndex);
            check("/Dest link navigates", page == expected && expected != 0, "page " + (page + 1)
                    + ", expected " + (expected + 1));
        }
        fx(() -> {
            view.setOnAnnotationAction(null);
            view.setDocument(null);
        });
        links.dispose();
    }

    /**
     * Move, resize, undo/redo and delete of a square annotation, with real mouse drags; edits must
     * change the annotation's /Rect as dragged and render no page-content tiles.
     */
    private void checkAnnotationEdits(javafx.scene.robot.Robot robot,
                                      org.icepdf.core.pobjects.annotations.Annotation square) throws Exception {
        java.awt.geom.Rectangle2D original = new java.awt.geom.Rectangle2D.Float();
        original.setRect(square.getUserSpaceRectangle());
        double zoom = onFx(view::getZoom);
        long contentBefore = org.icepdf.fx.view.TileRenderer.contentRenderCount();

        double[] p = viewPointIn(0, original);
        robotClick(robot, p);
        robotDragFrom(robot, p, 60, 40);
        waitIdle(30_000);
        java.awt.geom.Rectangle2D moved = square.getUserSpaceRectangle();
        check("drag moves the annotation", near(moved.getX(), original.getX() + 60 / zoom, 1.5)
                        && near(moved.getY(), original.getY() - 40 / zoom, 1.5)
                        && near(moved.getWidth(), original.getWidth(), 1.5),
                String.format("rect x %.1f -> %.1f, y %.1f -> %.1f (expected +%.1f, -%.1f)", original.getX(),
                        moved.getX(), original.getY(), moved.getY(), 60 / zoom, 40 / zoom));
        double[] inNew = viewPointIn(0, moved);
        check("hit-test follows the move", onFx(() -> view.annotationAt(inNew[0], inNew[1])).orElse(null) == square, "");
        snapshot(out.resolve("edit_moved.png").toFile());

        // resize from the bottom-right handle: view-space bottom-right = user (maxX, minY) at rotation 0.
        java.awt.geom.Rectangle2D beforeResize = new java.awt.geom.Rectangle2D.Float();
        beforeResize.setRect(moved);
        double[] br = viewPointNear(0, beforeResize.getMaxX(), beforeResize.getMinY());
        robotDragFrom(robot, br, 30, 20);
        waitIdle(30_000);
        java.awt.geom.Rectangle2D resized = square.getUserSpaceRectangle();
        check("handle drag resizes", near(resized.getWidth(), beforeResize.getWidth() + 30 / zoom, 1.5)
                        && near(resized.getHeight(), beforeResize.getHeight() + 20 / zoom, 1.5)
                        && near(resized.getX(), beforeResize.getX(), 1.5),
                String.format("w %.1f -> %.1f, h %.1f -> %.1f", beforeResize.getWidth(), resized.getWidth(),
                        beforeResize.getHeight(), resized.getHeight()));

        fx(view::undo);
        waitIdle(30_000);
        check("undo reverts the resize", sameRect(square.getUserSpaceRectangle(), beforeResize),
                String.valueOf(square.getUserSpaceRectangle()));
        fx(view::undo);
        waitIdle(30_000);
        check("undo reverts the move", sameRect(square.getUserSpaceRectangle(), original),
                square.getUserSpaceRectangle() + " vs " + original);
        fx(view::redo);
        waitIdle(30_000);
        check("redo re-applies the move", sameRect(square.getUserSpaceRectangle(), beforeResize),
                String.valueOf(square.getUserSpaceRectangle()));

        fx(() -> view.selectAnnotation(square));
        fx(view::deleteSelectedAnnotation);
        waitIdle(30_000);
        double[] at = viewPointIn(0, square.getUserSpaceRectangle());
        check("delete removes it", square.isDeleted() && onFx(() -> view.annotationAt(at[0], at[1])).isEmpty()
                && onFx(view::getSelectedAnnotation) == null, "deleted=" + square.isDeleted());
        fx(view::undo);
        waitIdle(30_000);
        check("undo restores it", !square.isDeleted()
                        && onFx(() -> view.annotationAt(at[0], at[1])).orElse(null) == square, "");
        long contentRenders = org.icepdf.fx.view.TileRenderer.contentRenderCount() - contentBefore;
        check("edits rendered no page-content tiles", contentRenders == 0, contentRenders + " content renders");
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static boolean sameRect(java.awt.geom.Rectangle2D a, java.awt.geom.Rectangle2D b) {
        return near(a.getX(), b.getX(), 1.01) && near(a.getY(), b.getY(), 1.01)
                && near(a.getWidth(), b.getWidth(), 1.01) && near(a.getHeight(), b.getHeight(), 1.01);
    }

    /** The view point mapping closest to a page user-space point. */
    private double[] viewPointNear(int pageIndex, double ux, double uy) throws Exception {
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        return onFx(() -> {
            double[] best = null;
            double bestDistance = Double.MAX_VALUE;
            for (double y = 2; y < size[1] - 2; y += 1) {
                for (double x = 2; x < size[0] - 20; x += 1) {
                    java.util.Optional<org.icepdf.fx.view.PagePoint> p = view.pageAt(x, y);
                    if (p.isEmpty() || p.get().pageIndex() != pageIndex) continue;
                    double d = Math.hypot(p.get().x() - ux, p.get().y() - uy);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = new double[]{x, y};
                    }
                }
            }
            return best;
        });
    }

    /** Presses at a view point and drags by (dx, dy) view px in steps, then releases. */
    private void robotDragFrom(javafx.scene.robot.Robot robot, double[] from, double dx, double dy) throws Exception {
        robotDrag(robot, from, new double[]{from[0] + dx, from[1] + dy}, 0);
        Thread.sleep(300);
    }

    /**
     * Popup notes: open one, drag it by its title bar past the page's left edge (it must stay there
     * and be saved outside the crop box), survive zoom and rotation, edit its text with undo,
     * minimise it and re-open it by double-clicking the note.
     */
    private void checkPopups(javafx.scene.robot.Robot robot, Path dir) throws Exception {
        Document doc = new Document();
        doc.setFile(dir.resolve("Invoice_sticky_note.pdf").toString());
        act("popups: Invoice_sticky_note fit page", v -> {
            v.setDocument(doc);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
        });
        org.icepdf.core.pobjects.Page page = doc.getPageTree().getPage(0);
        org.icepdf.core.pobjects.annotations.MarkupAnnotation note = null;
        for (org.icepdf.core.pobjects.annotations.Annotation a : page.getAnnotations()) {
            if (a instanceof org.icepdf.core.pobjects.annotations.MarkupAnnotation m && m.getPopupAnnotation() != null) {
                note = m;
                break;
            }
        }
        if (note == null) {
            check("fixture has a note with a popup", false, "");
            return;
        }
        org.icepdf.core.pobjects.annotations.MarkupAnnotation markup = note;
        org.icepdf.core.pobjects.annotations.PopupAnnotation popup = note.getPopupAnnotation();
        fx(() -> view.setPopupOpen(markup, false));
        act("popups: open", v -> v.setPopupOpen(markup, true));
        javafx.scene.layout.Region node = (javafx.scene.layout.Region) onFx(() -> popupNodeFor(popup));
        javafx.scene.control.TextArea text = node == null ? null
                : (javafx.scene.control.TextArea) onFx(() -> node.lookup(".text-area"));
        String expected = markup.getContents() == null ? "" : markup.getContents();
        check("popup opens with the note's text", node != null && text != null && expected.equals(onFx(text::getText)),
                node == null ? "no popup node" : "\"" + onFx(text::getText) + "\"");
        if (node == null) return;

        // drag the title bar until the popup's left edge is 80px past the page's left edge.
        double pageLeft = onFx(() -> {
            for (double x = 0; x < view.getWidth(); x += 1) {
                if (view.pageAt(x, view.getHeight() / 2).isPresent()) return x;
            }
            return 0.0;
        });
        javafx.geometry.Bounds b = onFx(() -> view.sceneToLocal(node.localToScene(node.getBoundsInLocal())));
        double[] grab = {b.getMinX() + 12, b.getMinY() + 5};
        double dx = (pageLeft - 80) - b.getMinX();
        robotDragFrom(robot, grab, dx, 30);
        waitIdle(30_000);
        org.icepdf.core.pobjects.PRectangle crop = page.getPageBoundary(org.icepdf.core.pobjects.Page.BOUNDARY_CROPBOX);
        java.awt.geom.Rectangle2D.Float moved = new java.awt.geom.Rectangle2D.Float();
        moved.setRect(popup.getUserSpaceRectangle());
        javafx.geometry.Bounds after = onFx(() -> view.sceneToLocal(node.localToScene(node.getBoundsInLocal())));
        check("popup dragged past the page edge stays there", moved.getMinX() < crop.getX() && after.getMinX() < pageLeft
                        && after.getMaxX() > 0,
                String.format("/Rect x %.1f (crop starts %.1f); view left %.0f, page left %.0f", moved.getMinX(),
                        crop.getX(), after.getMinX(), pageLeft));
        snapshot(out.resolve("popup_past_edge.png").toFile());

        act("popups: 150%", v -> {
            v.setFitMode(FitMode.NONE);
            v.setZoom(1.5);
        });
        act("popups: rotate 90", v -> v.setRotation(90));
        javafx.scene.Node still = onFx(() -> popupNodeFor(popup));
        check("popup survives zoom and rotation, /Rect untouched",
                still != null && sameRect(popup.getUserSpaceRectangle(), moved), String.valueOf(popup.getUserSpaceRectangle()));
        snapshot(out.resolve("popup_rotated.png").toFile());
        act("popups: back to fit page", v -> {
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
        });

        // edit the text; focus leaving the text area commits it
        javafx.scene.control.TextArea area = (javafx.scene.control.TextArea) onFx(() ->
                popupNodeFor(popup).lookup(".text-area"));
        String edited = "Edited by the smoke test";
        fx(() -> {
            area.requestFocus();
            area.setText(edited);
            view.requestFocus();
        });
        waitIdle(30_000);
        check("editing the note updates /Contents", edited.equals(markup.getContents()), markup.getContents());
        fx(view::undo);
        waitIdle(30_000);
        check("undo restores the text", expected.equals(markup.getContents() == null ? "" : markup.getContents()),
                markup.getContents());

        // minimise, then double-click the note to reopen
        javafx.scene.control.Button minimise = (javafx.scene.control.Button) onFx(() ->
                popupNodeFor(popup).lookupAll(".button").iterator().next());
        fx(minimise::fire);
        waitIdle(30_000);
        check("minimise closes it", !popup.isOpen() && onFx(() -> popupNodeFor(popup)) == null, "");
        double[] icon = viewPointIn(0, markup.getUserSpaceRectangle());
        javafx.geometry.Point2D screen = onFx(() -> view.localToScreen(icon[0], icon[1]));
        fx(() -> robot.mouseMove(screen));
        Thread.sleep(80);
        for (int i = 0; i < 2; i++) {
            fx(() -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY));
            Thread.sleep(30);
            fx(() -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY));
            Thread.sleep(60);
        }
        Thread.sleep(300);
        waitIdle(30_000);
        String hitAtIcon = String.valueOf(onFx(() -> view.annotationAt(icon[0], icon[1])).orElse(null));
        javafx.geometry.Rectangle2D screenBounds = onFx(() -> javafx.stage.Screen.getPrimary().getBounds());
        check("double-clicking the note reopens it", popup.isOpen() && onFx(() -> popupNodeFor(popup)) != null,
                "open=" + popup.isOpen() + ", view point " + icon[0] + "," + icon[1] + " screen " + screen
                        + " in " + screenBounds + ", hit " + hitAtIcon + ", selected "
                        + onFx(view::getSelectedAnnotation));
        fx(() -> view.setDocument(null));
        doc.dispose();
    }

    /**
     * Creation tools through the mouse: rectangle, ellipse, line and ink drags, note and free text
     * clicks, and a highlight from a text drag; each adds the right annotation type where drawn, and
     * undo removes them all.  No page-content tile is rendered by any of it.
     */
    private void checkCreation(javafx.scene.robot.Robot robot, Path dir) throws Exception {
        Document doc = new Document();
        doc.setFile(dir.resolve("Invoice_rectangle.pdf").toString());
        act("create: Invoice_rectangle fit page", v -> {
            v.setDocument(doc);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
        });
        org.icepdf.core.pobjects.Page page = doc.getPageTree().getPage(0);
        double zoom = onFx(view::getZoom);
        long contentBefore = org.icepdf.fx.view.TileRenderer.contentRenderCount();
        double[] base = viewPointWithoutAnnotation(0);
        java.util.List<org.icepdf.core.pobjects.annotations.Annotation> created = new java.util.ArrayList<>();

        record Draw(org.icepdf.fx.view.ToolMode mode, double dx, double dy,
                    Class<? extends org.icepdf.core.pobjects.annotations.Annotation> type) {
        }
        Draw[] draws = {
                new Draw(org.icepdf.fx.view.ToolMode.RECTANGLE, 70, -40, org.icepdf.core.pobjects.annotations.SquareAnnotation.class),
                new Draw(org.icepdf.fx.view.ToolMode.ELLIPSE, 70, -40, org.icepdf.core.pobjects.annotations.CircleAnnotation.class),
                new Draw(org.icepdf.fx.view.ToolMode.LINE, 90, -30, org.icepdf.core.pobjects.annotations.LineAnnotation.class),
                new Draw(org.icepdf.fx.view.ToolMode.INK, 60, -50, org.icepdf.core.pobjects.annotations.InkAnnotation.class),
                new Draw(org.icepdf.fx.view.ToolMode.NOTE, 0, 0, org.icepdf.core.pobjects.annotations.TextAnnotation.class),
                new Draw(org.icepdf.fx.view.ToolMode.FREE_TEXT, 0, 0, org.icepdf.core.pobjects.annotations.FreeTextAnnotation.class),
        };
        double offset = 0;
        for (Draw d : draws) {
            fx(() -> view.setToolMode(d.mode()));
            double[] at = {base[0] + offset, base[1]};
            offset = (offset + 110) % 440;
            java.util.Set<org.icepdf.core.pobjects.annotations.Annotation> before =
                    new java.util.HashSet<>(page.getAnnotations());
            if (d.dx() == 0 && d.dy() == 0) robotClick(robot, at);
            else robotDragFrom(robot, at, d.dx(), d.dy());
            waitIdle(30_000);
            org.icepdf.core.pobjects.annotations.Annotation added = null;
            for (org.icepdf.core.pobjects.annotations.Annotation a : page.getAnnotations()) {
                if (!before.contains(a) && d.type().isInstance(a)) added = a;
            }
            boolean placed = false;
            if (added != null) {
                java.awt.geom.Rectangle2D r = added.getUserSpaceRectangle();
                java.util.Optional<org.icepdf.fx.view.PagePoint> p = onFx(() -> view.pageAt(at[0], at[1]));
                // the press point lies on (or within a few points of) the new annotation's rect.
                placed = p.isPresent() && r.getMinX() - 12 <= p.get().x() && p.get().x() <= r.getMaxX() + 12
                        && r.getMinY() - 12 <= p.get().y() && p.get().y() <= r.getMaxY() + 12;
                if (d.dx() != 0) {
                    placed &= Math.abs(r.getWidth() - Math.abs(d.dx()) / zoom) < 20 / zoom + 6;
                }
                created.add(added);
            }
            String detail = added == null ? "nothing added" : added.getClass().getSimpleName() + " " + added.getUserSpaceRectangle();
            check(d.mode() + " creates " + d.type().getSimpleName(), added != null && placed, detail);
            if (d.mode() == org.icepdf.fx.view.ToolMode.NOTE && added != null) {
                org.icepdf.core.pobjects.annotations.PopupAnnotation popup =
                        ((org.icepdf.core.pobjects.annotations.MarkupAnnotation) added).getPopupAnnotation();
                check("new note opens its popup", popup != null && popup.isOpen() && onFx(() -> popupNodeFor(popup)) != null, "");
            }
            if (d.mode() == org.icepdf.fx.view.ToolMode.FREE_TEXT && added != null) {
                javafx.scene.control.TextArea editor = (javafx.scene.control.TextArea) onFx(() -> view.lookup(".pdf-free-text-editor"));
                if (editor != null) {
                    fx(() -> {
                        editor.setText("Typed into the box");
                        view.requestFocus();
                    });
                    waitIdle(30_000);
                    Thread.sleep(150);
                }
                check("free text editor commits its text", editor != null
                        && "Typed into the box".equals(added.getContents()), String.valueOf(added.getContents()));
            }
        }

        // highlight: a drag along a text line in the HIGHLIGHT tool
        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.HIGHLIGHT));
        org.icepdf.core.pobjects.graphics.text.TextSequence sequence = doc.getPageViewText(0).getTextSequence();
        double[] lineStart = null;
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        for (double y = 60; y < size[1] / 2 && lineStart == null; y += 4) {
            for (double x = 40; x < size[0] - 160 && lineStart == null; x += 4) {
                double px = x, py = y;
                java.util.Optional<org.icepdf.fx.view.PagePoint> p = onFx(() -> view.pageAt(px, py));
                java.util.Optional<org.icepdf.fx.view.PagePoint> q = onFx(() -> view.pageAt(px + 60, py));
                if (p.isPresent() && q.isPresent() && sequence.hitsText(p.get().toAwt()) && sequence.hitsText(q.get().toAwt())
                        && onFx(() -> view.annotationAt(px, py)).isEmpty()) {
                    lineStart = new double[]{x, y};
                }
            }
        }
        java.util.Set<org.icepdf.core.pobjects.annotations.Annotation> beforeHighlight = new java.util.HashSet<>(page.getAnnotations());
        if (lineStart != null) robotDragFrom(robot, lineStart, 60, 0);
        waitIdle(30_000);
        org.icepdf.core.pobjects.annotations.Annotation highlight = null;
        for (org.icepdf.core.pobjects.annotations.Annotation a : page.getAnnotations()) {
            if (!beforeHighlight.contains(a) && a instanceof org.icepdf.core.pobjects.annotations.TextMarkupAnnotation) highlight = a;
        }
        check("HIGHLIGHT turns a text drag into a highlight", highlight != null && onFx(view::getTextSelection) == null,
                highlight == null ? "none" : "\"" + highlight.getContents() + "\"");
        if (highlight != null) created.add(highlight);
        snapshot(out.resolve("created.png").toFile());

        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT));
        // undo everything created (free text has two edits: add + text)
        for (int i = 0; i < created.size() + 1; i++) {
            fx(view::undo);
        }
        waitIdle(30_000);
        boolean allGone = created.stream().allMatch(org.icepdf.core.pobjects.annotations.Annotation::isDeleted);
        check("undo removes every created annotation", allGone && !created.isEmpty(), created.size() + " created");
        long contentRenders = org.icepdf.fx.view.TileRenderer.contentRenderCount() - contentBefore;
        check("creation rendered no page-content tiles", contentRenders == 0, contentRenders + " content renders");
        fx(() -> view.setDocument(null));
        doc.dispose();
    }

    /** The popup node showing a popup annotation (they carry it as user data), or null. */
    private javafx.scene.Node popupNodeFor(org.icepdf.core.pobjects.annotations.PopupAnnotation popup) {
        for (javafx.scene.Node n : view.lookupAll(".pdf-annotation-popup")) {
            if (n.getUserData() == popup) return n;
        }
        return null;
    }

    /** A view point inside a page user-space rectangle, found by probing pageAt (nearest its centre). */
    private double[] viewPointIn(int pageIndex, java.awt.geom.Rectangle2D rect) throws Exception {
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        return onFx(() -> {
            double[] best = null;
            double bestDistance = Double.MAX_VALUE;
            for (double y = 2; y < size[1] - 2; y += 2) {
                for (double x = 2; x < size[0] - 20; x += 2) {
                    java.util.Optional<org.icepdf.fx.view.PagePoint> p = view.pageAt(x, y);
                    if (p.isEmpty() || p.get().pageIndex() != pageIndex || !rect.contains(p.get().x(), p.get().y())) {
                        continue;
                    }
                    double d = Math.hypot(p.get().x() - rect.getCenterX(), p.get().y() - rect.getCenterY());
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = new double[]{x, y};
                    }
                }
            }
            return best;
        });
    }

    /** A view point on the page, clear of annotations and text. */
    private double[] viewPointWithoutAnnotation(int pageIndex) throws Exception {
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        return onFx(() -> {
            for (double y = size[1] - 40; y > 20; y -= 9) {
                for (double x = 30; x < size[0] - 40; x += 9) {
                    java.util.Optional<org.icepdf.fx.view.PagePoint> p = view.pageAt(x, y);
                    if (p.isPresent() && p.get().pageIndex() == pageIndex && view.annotationAt(x, y).isEmpty()) {
                        return new double[]{x, y};
                    }
                }
            }
            return new double[]{size[0] / 2, size[1] / 2};
        });
    }

    private void robotMove(javafx.scene.robot.Robot robot, double[] p) throws Exception {
        javafx.geometry.Point2D screen = onFx(() -> view.localToScreen(p[0], p[1]));
        fx(() -> robot.mouseMove(screen));
        Thread.sleep(60);
        fx(() -> robot.mouseMove(screen.getX() + 1, screen.getY()));
        Thread.sleep(120);
    }

    private void robotClick(javafx.scene.robot.Robot robot, double[] p) throws Exception {
        robotMove(robot, p);
        fx(() -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY));
        Thread.sleep(40);
        fx(() -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY));
        Thread.sleep(400); // past the double-click interval
        waitIdle(30_000);
    }

    private int countPixels(int r, int g, int b, int tolerance) throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        WritableImage image = onFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        int count = 0;
        for (int c : argb) {
            if (Math.abs((c >> 16 & 0xff) - r) <= tolerance && Math.abs((c >> 8 & 0xff) - g) <= tolerance
                    && Math.abs((c & 0xff) - b) <= tolerance) {
                count++;
            }
        }
        return count;
    }

    // ---- annotation rendering A/B ------------------------------------------------------------

    private static final String[] ANNOTATION_DOCS = {
            "Invoice_highlight.pdf", "Invoice_highlight_note.pdf", "Invoice_ink.pdf", "Invoice_oval.pdf",
            "Invoice_rectangle.pdf", "Invoice_sticky_note.pdf", "Invoice_text_markup.pdf",
            "Invoice_free_text.pdf", "Invoice_lines.pdf", "Invoice_poly_line.pdf", "Invoice_text_callout.pdf",
            "Invoice_cross_out_note.pdf", "links.pdf", "pp_10431_annots.pdf", "93annrep.pdf", "a7_tryme_gb.pdf"};

    /**
     * Snapshots annotation-heavy documents (fit page, then 250% at the page centre) into the out
     * directory.  With {@code -Dsmoke.compare=<dir>} each snapshot is diffed against the one of the
     * same name there - run once with {@code -Dorg.icepdf.fx.view.singlePassAnnotations=true} to make
     * the reference, then without to check the split annotation layers match it.
     */
    private void annotationSnapshots(Path dir) throws Exception {
        String compare = System.getProperty("smoke.compare");
        System.out.println("annotation snapshots" + (compare != null ? ", comparing with " + compare : "")
                + (org.icepdf.fx.view.TileRenderer.SINGLE_PASS_ANNOTATIONS ? " [single pass]" : " [split layers]"));
        for (String name : ANNOTATION_DOCS) {
            Path file = dir.resolve(name);
            if (!Files.exists(file)) {
                System.out.println("  missing " + name);
                continue;
            }
            Document document = new Document();
            document.setFile(file.toString());
            int pages = Math.min(2, document.getNumberOfPages());
            for (int p = 0; p < pages; p++) {
                int page = p;
                for (double zoom : new double[]{0, 2.5}) {
                    onFx(() -> {
                        if (view.getDocument() != document) view.setDocument(document);
                        view.setViewMode(ViewMode.SINGLE_PAGE);
                        view.setRotation(0);
                        view.setCurrentPageIndex(page);
                        if (zoom == 0) {
                            view.setFitMode(FitMode.PAGE);
                        } else {
                            view.setFitMode(FitMode.NONE);
                            view.setZoom(zoom);
                        }
                        return null;
                    });
                    waitIdle(60_000);
                    if (zoom != 0) {
                        // centre on the page's first annotation, so the zoomed shot covers one.
                        java.util.List<org.icepdf.core.pobjects.annotations.Annotation> annots =
                                document.getPageTree().getPage(page).getAnnotations();
                        if (annots != null && !annots.isEmpty() && annots.get(0) != null) {
                            java.awt.geom.Rectangle2D r = annots.get(0).getUserSpaceRectangle();
                            onFx(() -> {
                                view.ensureVisible(new org.icepdf.fx.view.PagePoint(page, r.getCenterX(), r.getCenterY()));
                                return null;
                            });
                            waitIdle(60_000);
                        }
                    }
                    Thread.sleep(150);
                    waitIdle(60_000);
                    String shot = String.format("%s_p%d_%s.png", name.replace(".pdf", ""), page + 1,
                            zoom == 0 ? "fit" : "z250");
                    File out = this.out.resolve(shot).toFile();
                    snapshot(out);
                    if (compare != null) compareImages(Paths.get(compare).resolve(shot).toFile(), out, shot);
                }
            }
            onFx(() -> {
                view.setDocument(null);
                return null;
            });
            document.dispose();
        }
        if (compare != null) {
            System.out.println(failures == 0 ? "annotation A/B: all within tolerance"
                    : "annotation A/B: " + failures + " over tolerance");
        }
    }

    /**
     * Pixel diff: reports the largest channel difference and the share of pixels differing by more
     * than 8 and 48 levels.  Passes when under 0.1% of pixels differ by more than 48.
     */
    private void compareImages(File reference, File actual, String name) throws Exception {
        if (!reference.exists()) {
            check(name, false, "no reference image");
            return;
        }
        java.awt.image.BufferedImage a = ImageIO.read(reference);
        java.awt.image.BufferedImage b = ImageIO.read(actual);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            check(name, false, "size differs");
            return;
        }
        long over8 = 0, over48 = 0;
        int max = 0;
        long total = (long) a.getWidth() * a.getHeight();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
                int d = Math.max(Math.abs((pa >> 16 & 0xff) - (pb >> 16 & 0xff)),
                        Math.max(Math.abs((pa >> 8 & 0xff) - (pb >> 8 & 0xff)), Math.abs((pa & 0xff) - (pb & 0xff))));
                max = Math.max(max, d);
                if (d > 8) over8++;
                if (d > 48) over48++;
            }
        }
        double share48 = over48 * 100.0 / total;
        check(name, share48 < 0.1, String.format("max diff %d, >8: %.3f%%, >48: %.3f%%", max,
                over8 * 100.0 / total, share48));
    }

    // ---- tool-mode checks: pass/fail by numbers -------------------------------------------

    private int failures;

    private void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.printf("  %-4s %-44s %s%n", ok ? "PASS" : "FAIL", name, detail);
    }

    private void checkTools(Document document) throws Exception {
        System.out.println("tool checks:");
        checkLayoutStable();
        checkHitTesting(document);
        checkPanning();
        checkSelectionLayer(document);
        checkSelectionGestures(document);
        checkCaret(document);
        checkKeyboard(document);
        checkSearch(document);
        System.out.println(failures == 0 ? "tool checks: all passed" : "tool checks: " + failures + " FAILED");
    }

    /**
     * Embedded in a BorderPane, an idle view must be still: same size over a second and no render
     * cycles.  Guards the pref-size feedback loop (each pass grew the pref height by a scroll bar).
     */
    private void checkLayoutStable() throws Exception {
        act("layout setup: continuous 100%", v -> {
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setFitMode(FitMode.NONE);
            v.setZoom(1);
            v.setCurrentPageIndex(0);
        });
        double[] before = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        double sceneHeight = onFx(() -> view.getScene().getHeight());
        int[] renders = {0};
        javafx.beans.value.ChangeListener<Boolean> counter = (obs, o, n) -> {
            if (n) renders[0]++;
        };
        fx(() -> view.renderingProperty().addListener(counter));
        Thread.sleep(1000);
        fx(() -> view.renderingProperty().removeListener(counter));
        double[] after = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        check("idle embedded view is stable", before[0] == after[0] && before[1] == after[1]
                        && after[1] < sceneHeight && renders[0] == 0,
                String.format("%.0fx%.0f -> %.0fx%.0f in a %.0f high scene, %d render cycles",
                        before[0], before[1], after[0], after[1], sceneHeight, renders[0]));
    }

    /**
     * pageAt round trip: the overlay draws a solid red block at a known PDF user-space rectangle on
     * page 1; find it in a snapshot at each rotation, ask pageAt for its centre, and require a point
     * inside that rectangle.  Overlay placement and hit-testing are independent code paths over the
     * same core transform, so agreement at all four rotations checks both.
     */
    private void checkHitTesting(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        PRectangle crop = page.getPageBoundary(Page.BOUNDARY_CROPBOX);
        double x0 = crop.getX() + 36;
        double y0 = crop.getY() - crop.getHeight() + 36;
        for (int rotation : new int[]{0, 90, 180, 270}) {
            act("hit-test rotation " + rotation, v -> {
                v.setViewMode(ViewMode.SINGLE_PAGE);
                v.setCurrentPageIndex(0);
                v.setPageOverlayFactory(PdfViewSmoke::cropFrame);
                v.setRotation(rotation);
                v.setFitMode(FitMode.PAGE);
            });
            double[] centre = redBlockCentre();
            if (centre == null) {
                check("pageAt rotation " + rotation, false, "red block not found in snapshot");
                continue;
            }
            java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(centre[0], centre[1]));
            boolean ok = hit.isPresent() && hit.get().pageIndex() == 0
                    && hit.get().x() >= x0 && hit.get().x() <= x0 + 36
                    && hit.get().y() >= y0 && hit.get().y() <= y0 + 36;
            check("pageAt rotation " + rotation, ok, String.format("view (%.1f,%.1f) -> %s, block x[%.0f,%.0f] y[%.0f,%.0f]",
                    centre[0], centre[1], hit.map(Object::toString).orElse("none"), x0, x0 + 36, y0, y0 + 36));
        }
        java.util.Optional<org.icepdf.fx.view.PagePoint> gap = onFx(() -> view.pageAt(2, 2));
        check("pageAt outside the page", gap.isEmpty(), gap.map(Object::toString).orElse("empty"));
    }

    /** Centre of the solid red block, in view coordinates; the 2px frame stroke is filtered out. */
    private double[] redBlockCentre() throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        WritableImage image = onFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        int radius = 4;
        double sx = 0, sy = 0;
        long n = 0;
        for (int y = radius; y < h - radius; y++) {
            for (int x = radius; x < w - radius; x++) {
                if (!isRed(argb[y * w + x])) continue;
                // interior of a filled area, not the thin frame stroke
                if (isRed(argb[(y - radius) * w + x]) && isRed(argb[(y + radius) * w + x])
                        && isRed(argb[y * w + x - radius]) && isRed(argb[y * w + x + radius])) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        return n == 0 ? null : new double[]{sx / n / scale, sy / n / scale};
    }

    private static boolean isRed(int argb) {
        int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
        return r > 230 && g < 40 && b < 40;
    }

    /**
     * Real input through the FX Robot: middle-drag pans in every tool, Space+drag pans, a plain
     * primary drag pans only with the hand tool.  Movement is read back through pageAt at the view
     * centre, so it is checked in PDF units.
     */
    private void checkPanning() throws Exception {
        act("pan setup: continuous 300%", v -> {
            v.setPageOverlayFactory(null);
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setRotation(0);
            v.setFitMode(FitMode.NONE);
            v.setZoom(3);
            v.setCurrentPageIndex(0);
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.requestFocus();
        });
        drag("middle-drag pans (select tool)", javafx.scene.input.MouseButton.MIDDLE, false, true);
        drag("primary drag doesn't pan (select tool)", javafx.scene.input.MouseButton.PRIMARY, false, false);
        drag("Space+drag pans (select tool)", javafx.scene.input.MouseButton.PRIMARY, true, true);
        onFx(() -> {
            view.setToolMode(org.icepdf.fx.view.ToolMode.PAN);
            return null;
        });
        drag("primary drag pans (hand tool)", javafx.scene.input.MouseButton.PRIMARY, false, true);
        onFx(() -> {
            view.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            return null;
        });
        spaceTap();
    }

    /** A Space tap with no drag still pages down (0.9 of the viewport in continuous mode). */
    private void spaceTap() throws Exception {
        fx(() -> {
            view.scrollBy(-1e9, -1e9);
            view.scrollBy(300, 300);
        });
        waitIdle(30_000);
        double[] c = onFx(() -> new double[]{view.getWidth() / 2, view.getHeight() / 2});
        org.icepdf.fx.view.PagePoint before = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        fx(() -> {
            view.requestFocus();
            robot.keyPress(javafx.scene.input.KeyCode.SPACE);
        });
        Thread.sleep(80);
        fx(() -> robot.keyRelease(javafx.scene.input.KeyCode.SPACE));
        Thread.sleep(150);
        waitIdle(30_000);
        org.icepdf.fx.view.PagePoint after = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        double zoom = onFx(() -> view.getZoom());
        // the viewport height is the control height less the horizontal bar, if shown.
        double viewportH = onFx(() -> view.lookupAll(".scroll-bar").stream()
                .filter(n -> n instanceof javafx.scene.control.ScrollBar sb
                        && sb.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && sb.isVisible())
                .findFirst().map(n -> view.getHeight() - ((javafx.scene.control.ScrollBar) n).getHeight())
                .orElse(view.getHeight()));
        if (before == null || after == null || before.pageIndex() != after.pageIndex()) {
            check("Space tap pages down", false, "before " + before + " after " + after);
            return;
        }
        double moved = before.y() - after.y();
        double expect = viewportH * 0.9 / zoom;
        check("Space tap pages down", Math.abs(moved - expect) < 1,
                String.format("moved %.1fpt down, expected %.1fpt", moved, expect));
    }

    private void drag(String name, javafx.scene.input.MouseButton button, boolean withSpace, boolean expectPan)
            throws Exception {
        double dx = -120;
        double dy = -90;
        // from a fixed scroll position each time (top-left, then inward) so every drag has room;
        // done after the zoom so its re-anchoring can't move it.
        fx(() -> {
            view.scrollBy(-1e9, -1e9);
            view.scrollBy(300, 300);
        });
        waitIdle(30_000);
        double[] c = onFx(() -> new double[]{view.getWidth() / 2, view.getHeight() / 2});
        org.icepdf.fx.view.PagePoint before = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        javafx.geometry.Point2D screen = onFx(() -> view.localToScreen(c[0], c[1]));
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        onFx(() -> {
            stage.toFront();
            view.requestFocus();
            robot.mouseMove(screen);
            return null;
        });
        Thread.sleep(150);
        if (withSpace) {
            fx(() -> robot.keyPress(javafx.scene.input.KeyCode.SPACE));
            Thread.sleep(60);
        }
        fx(() -> robot.mousePress(button));
        for (int i = 1; i <= 10; i++) {
            int step = i;
            Thread.sleep(20);
            fx(() -> robot.mouseMove(screen.getX() + dx * step / 10, screen.getY() + dy * step / 10));
        }
        Thread.sleep(60);
        fx(() -> robot.mouseRelease(button));
        if (withSpace) {
            Thread.sleep(60);
            fx(() -> robot.keyRelease(javafx.scene.input.KeyCode.SPACE));
        }
        Thread.sleep(150);
        waitIdle(30_000);
        org.icepdf.fx.view.PagePoint after = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        if (before == null || after == null) {
            check(name, false, "no page under the view centre");
            return;
        }
        double zoom = onFx(() -> view.getZoom());
        // dragging the content left/up brings points to the right/below (lower y) under the centre.
        double movedX = after.x() - before.x();
        double movedY = after.y() - before.y();
        double expectX = expectPan ? -dx / zoom : 0;
        double expectY = expectPan ? dy / zoom : 0;
        boolean ok = after.pageIndex() == before.pageIndex()
                && Math.abs(movedX - expectX) < 2 / zoom + 0.5 && Math.abs(movedY - expectY) < 2 / zoom + 0.5;
        check(name, ok, String.format("moved (%.1f, %.1f)pt, expected (%.1f, %.1f)pt", movedX, movedY, expectX, expectY));
    }

    /**
     * A programmatic selection is drawn exactly over the core's selection rectangles, stays aligned
     * through zoom and rotation, never triggers a tile render, and clears; the cursor is an I-beam
     * over text and an arrow off the page.
     */
    private void checkSelectionLayer(Document document) throws Exception {
        int pageIndex = Math.min(document.getNumberOfPages() - 1, 99);
        org.icepdf.core.pobjects.graphics.text.TextSequence sequence =
                document.getPageViewText(pageIndex).getTextSequence();
        int end = Math.min(sequence.length(), 400);
        java.util.List<java.awt.geom.Rectangle2D.Double> rects =
                sequence.rectsFor(org.icepdf.core.pobjects.graphics.text.OffsetRange.of(0, end));

        act("selection setup: page " + (pageIndex + 1) + " fit width", v -> {
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setCurrentPageIndex(pageIndex);
            v.setFitMode(FitMode.WIDTH);
        });
        int[] renders = {0};
        javafx.beans.value.ChangeListener<Boolean> counter = (obs, o, n) -> {
            if (n) renders[0]++;
        };
        fx(() -> view.renderingProperty().addListener(counter));
        act("select offsets 0-" + end, v -> v.setTextSelection(
                org.icepdf.core.pobjects.graphics.text.DocumentSelection.of(pageIndex, 0, pageIndex, end)));
        fx(() -> view.renderingProperty().removeListener(counter));
        check("selection change renders no tiles", renders[0] == 0, renders[0] + " render cycles");
        checkHighlightAligned("highlight aligned (fit width)", pageIndex, rects);

        act("selection at 400%", v -> {
            v.setFitMode(FitMode.NONE);
            v.setZoom(4);
        });
        checkHighlightAligned("highlight aligned (400%)", pageIndex, rects);
        act("selection rotated 90", PdfView::rotateClockwise);
        checkHighlightAligned("highlight aligned (rotated 90)", pageIndex, rects);

        act("selection: fit page, rotation 0", v -> {
            v.setRotation(0);
            v.setFitMode(FitMode.PAGE);
        });
        checkCursor(pageIndex, sequence);

        act("clear selection", PdfView::clearSelection);
        check("cleared selection draws nothing", highlightPixels().isEmpty(), "");
    }

    /**
     * Real-input selection through the Robot: drag along a line, double-click a word, and a drag
     * held past the bottom edge that auto-scrolls onto the next page.
     */
    private void checkSelectionGestures(Document document) throws Exception {
        int pageIndex = Math.min(document.getNumberOfPages() - 2, 99);
        org.icepdf.core.pobjects.graphics.text.TextSequence sequence =
                document.getPageViewText(pageIndex).getTextSequence();
        act("gestures setup: page " + (pageIndex + 1) + " fit width", v -> {
            v.setTextSelection(null);
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setRotation(0);
            v.setFitMode(FitMode.WIDTH);
            v.setCurrentPageIndex(pageIndex);
        });
        // two points on one text line, 150px apart, found by probing.
        double[] a = null;
        double[] b = null;
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        for (double y = 80; y < size[1] / 2 && b == null; y += 5) {
            for (double x = 40; x < size[0] - 220 && b == null; x += 5) {
                double px = x, py = y;
                java.util.Optional<org.icepdf.fx.view.PagePoint> p = onFx(() -> view.pageAt(px, py));
                java.util.Optional<org.icepdf.fx.view.PagePoint> q = onFx(() -> view.pageAt(px + 150, py));
                if (p.isPresent() && q.isPresent() && p.get().pageIndex() == pageIndex
                        && sequence.hitsText(p.get().toAwt()) && sequence.hitsText(q.get().toAwt())
                        && sequence.lineIndexOf(sequence.caretAt(p.get().toAwt()).getOffset())
                        == sequence.lineIndexOf(sequence.caretAt(q.get().toAwt()).getOffset())) {
                    a = new double[]{x, y};
                    b = new double[]{x + 150, y};
                }
            }
        }
        if (b == null) {
            check("drag selects along a line", false, "no line found to drag along");
            return;
        }
        final double[] start = a;
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        robotDrag(robot, a, b, 0);
        org.icepdf.core.pobjects.graphics.text.DocumentSelection sel = onFx(view::getTextSelection);
        int expectA = sequence.caretAt(onFx(() -> view.pageAt(start[0], start[1])).get().toAwt()).getOffset();
        double[] bb = b;
        int expectB = sequence.caretAt(onFx(() -> view.pageAt(bb[0], bb[1])).get().toAwt()).getOffset();
        check("drag selects along a line",
                sel != null && sel.equals(org.icepdf.core.pobjects.graphics.text.DocumentSelection.of(
                        pageIndex, expectA, pageIndex, expectB)),
                sel + " expected " + pageIndex + ":" + expectA + "->" + expectB + " \""
                        + (sel == null ? "" : sel.extractText(i -> i == pageIndex ? sequence : null)) + "\"");

        // double-click the word at a
        javafx.geometry.Point2D sa = onFx(() -> view.localToScreen(start[0], start[1]));
        fx(() -> robot.mouseMove(sa));
        Thread.sleep(100);
        for (int i = 0; i < 2; i++) {
            fx(() -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY));
            Thread.sleep(30);
            fx(() -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY));
            Thread.sleep(60);
        }
        Thread.sleep(150);
        org.icepdf.core.pobjects.graphics.text.OffsetRange word = sequence.wordRange(expectA);
        sel = onFx(view::getTextSelection);
        check("double-click selects the word",
                sel != null && sel.equals(org.icepdf.core.pobjects.graphics.text.DocumentSelection.of(
                        pageIndex, word.getStart(), pageIndex, word.getEnd())),
                sel + " \"" + sequence.text(word) + "\"");

        // drag from the line down past the viewport's bottom edge and hold: auto-scroll carries the
        // selection onto following pages.
        Thread.sleep(600); // let the double-click window lapse
        double[] below = {a[0], size[1] + 40};
        robotDrag(robot, a, below, 1500);
        sel = onFx(view::getTextSelection);
        check("auto-scroll drag crosses onto the next page",
                sel != null && sel.getAnchorPage() == pageIndex && sel.getFocusPage() > pageIndex,
                String.valueOf(sel));
        act("clear after gestures", PdfView::clearSelection);
    }

    /** Press at {@code from}, move to {@code to} in steps, hold {@code holdMs}, release. */
    private void robotDrag(javafx.scene.robot.Robot robot, double[] from, double[] to, long holdMs) throws Exception {
        javafx.geometry.Point2D s0 = onFx(() -> view.localToScreen(from[0], from[1]));
        javafx.geometry.Point2D s1 = onFx(() -> view.localToScreen(to[0], to[1]));
        fx(() -> {
            stage.toFront();
            robot.mouseMove(s0);
        });
        Thread.sleep(120);
        fx(() -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY));
        for (int i = 1; i <= 12; i++) {
            int step = i;
            Thread.sleep(20);
            fx(() -> robot.mouseMove(s0.getX() + (s1.getX() - s0.getX()) * step / 12,
                    s0.getY() + (s1.getY() - s0.getY()) * step / 12));
        }
        Thread.sleep(holdMs + 60);
        fx(() -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY));
        Thread.sleep(120);
    }

    /**
     * The caret is whatever blinks: pixels that change across a burst of snapshots.  They must map
     * (via pageAt) onto the core's caretRect for the focus offset, at fit width and at 400%; the
     * hand tool shows no caret.
     */
    private void checkCaret(Document document) throws Exception {
        int pageIndex = Math.min(document.getNumberOfPages() - 1, 99);
        org.icepdf.core.pobjects.graphics.text.TextSequence sequence =
                document.getPageViewText(pageIndex).getTextSequence();
        int offset = Math.min(sequence.length() - 1, 645);
        java.awt.geom.Rectangle2D.Double expected = sequence.caretRect(new org.icepdf.core.pobjects.graphics.text.Caret(
                offset, org.icepdf.core.pobjects.graphics.text.Bias.FORWARD));
        act("caret setup: page " + (pageIndex + 1) + " caret at " + offset, v -> {
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setCurrentPageIndex(pageIndex);
            v.setFitMode(FitMode.WIDTH);
            stage.toFront();
            v.requestFocus();
            v.setTextSelection(org.icepdf.core.pobjects.graphics.text.DocumentSelection.collapsed(pageIndex, offset));
        });
        checkCaretAt("caret blinks at caretRect (fit width)", pageIndex, expected);
        act("caret at 400%", v -> {
            v.setFitMode(FitMode.NONE);
            v.setZoom(4);
        });
        act("ensureVisible(caret)", v -> v.ensureVisible(
                new org.icepdf.fx.view.PagePoint(pageIndex, expected.x, expected.getCenterY())));
        checkCaretAt("caret blinks at caretRect (400%)", pageIndex, expected);
        act("hand tool", v -> v.setToolMode(org.icepdf.fx.view.ToolMode.PAN));
        java.util.List<double[]> blinking = blinkingPixels();
        check("no caret in the hand tool", blinking.isEmpty(), blinking.size() + " blinking px");
        act("caret cleanup", v -> {
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.clearSelection();
        });
    }

    private void checkCaretAt(String name, int pageIndex, java.awt.geom.Rectangle2D.Double expected) throws Exception {
        boolean focused = onFx(() -> view.isFocused());
        java.util.List<double[]> blinking = blinkingPixels();
        if (blinking.size() < 4) {
            check(name, false, blinking.size() + " blinking px, focused=" + focused);
            return;
        }
        int inside = 0;
        for (double[] p : blinking) {
            java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(p[0], p[1]));
            if (hit.isPresent() && hit.get().pageIndex() == pageIndex
                    && Math.abs(hit.get().x() - expected.x) <= 1.5
                    && hit.get().y() >= expected.getMinY() - 1 && hit.get().y() <= expected.getMaxY() + 1) {
                inside++;
            }
        }
        double fraction = inside / (double) blinking.size();
        check(name, fraction >= 0.95, String.format("%d blinking px, %.0f%% on caretRect x=%.1f y[%.1f,%.1f]",
                blinking.size(), fraction * 100, expected.x, expected.getMinY(), expected.getMaxY()));
    }

    /** Pixels whose colour changes across ~1.3s of snapshots (two blink periods). */
    private java.util.List<double[]> blinkingPixels() throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        int[] min = null;
        int[] max = null;
        int w = 0;
        for (int frame = 0; frame < 14; frame++) {
            WritableImage image = onFx(() -> {
                SnapshotParameters params = new SnapshotParameters();
                params.setTransform(new Scale(scale, scale));
                return view.snapshot(params, null);
            });
            w = (int) image.getWidth();
            int h = (int) image.getHeight();
            int[] argb = new int[w * h];
            image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
            if (min == null) {
                min = new int[argb.length];
                max = new int[argb.length];
                java.util.Arrays.fill(min, 255);
            }
            for (int i = 0; i < argb.length; i++) {
                int grey = ((argb[i] >> 16 & 0xff) + (argb[i] >> 8 & 0xff) + (argb[i] & 0xff)) / 3;
                min[i] = Math.min(min[i], grey);
                max[i] = Math.max(max[i], grey);
            }
            Thread.sleep(95);
        }
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < min.length; i++) {
            if (max[i] - min[i] > 60) out.add(new double[]{(i % w + 0.5) / scale, (i / w + 0.5) / scale});
        }
        return out;
    }

    /** Real keys through the Robot: extend, copy, clear, select-all, line move, page crossing, scroll. */
    private void checkKeyboard(Document document) throws Exception {
        int pageIndex = Math.min(document.getNumberOfPages() - 2, 99);
        org.icepdf.core.pobjects.graphics.text.TextSequence sequence =
                document.getPageViewText(pageIndex).getTextSequence();
        int start = 641;
        org.icepdf.core.pobjects.graphics.text.OffsetRange word = sequence.wordRange(start);
        act("keyboard setup: caret at " + start, v -> {
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setRotation(0);
            v.setFitMode(FitMode.WIDTH);
            v.setCurrentPageIndex(pageIndex);
            stage.toFront();
            v.requestFocus();
            v.setTextSelection(org.icepdf.core.pobjects.graphics.text.DocumentSelection.collapsed(pageIndex, word.getStart()));
        });
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        javafx.scene.input.KeyCode shortcut = javafx.scene.input.KeyCode.CONTROL;

        for (int i = 0; i < word.length(); i++) {
            keys(robot, javafx.scene.input.KeyCode.SHIFT, javafx.scene.input.KeyCode.RIGHT);
        }
        org.icepdf.core.pobjects.graphics.text.DocumentSelection sel = onFx(view::getTextSelection);
        check("shift+right extends over the word",
                org.icepdf.core.pobjects.graphics.text.DocumentSelection.of(pageIndex, word.getStart(),
                        pageIndex, word.getEnd()).equals(sel), sel + " \"" + sequence.text(word) + "\"");

        fx(() -> javafx.scene.input.Clipboard.getSystemClipboard().clear());
        keys(robot, shortcut, javafx.scene.input.KeyCode.C);
        String clip = null;
        for (int i = 0; i < 40 && (clip == null || clip.isEmpty()); i++) {
            Thread.sleep(50);
            clip = onFx(() -> javafx.scene.input.Clipboard.getSystemClipboard().getString());
        }
        String expectedText = sequence.extractText(word);
        check("ctrl+C copies the selection", expectedText.equals(clip), "clipboard \"" + clip + "\"");

        keys(robot, javafx.scene.input.KeyCode.ESCAPE);
        check("Esc clears", onFx(view::getTextSelection) == null, String.valueOf(onFx(view::getTextSelection)));

        keys(robot, shortcut, javafx.scene.input.KeyCode.A);
        int pages = document.getNumberOfPages();
        sel = onFx(view::getTextSelection);
        check("ctrl+A selects all", org.icepdf.core.pobjects.graphics.text.DocumentSelection.all(pages).equals(sel),
                String.valueOf(sel));

        fx(() -> view.setTextSelection(org.icepdf.core.pobjects.graphics.text.DocumentSelection.collapsed(pageIndex, start + 3)));
        keys(robot, javafx.scene.input.KeyCode.DOWN);
        sel = onFx(view::getTextSelection);
        check("down moves the caret to the next line", sel != null && sel.isCollapsed() && sel.getFocusPage() == pageIndex
                        && sequence.lineIndexOf(sel.getFocusOffset()) == sequence.lineIndexOf(start + 3) + 1,
                sel + " line " + (sel == null ? -1 : sequence.lineIndexOf(sel.getFocusOffset())));

        fx(() -> view.setTextSelection(org.icepdf.core.pobjects.graphics.text.DocumentSelection.collapsed(pageIndex,
                sequence.length())));
        // the next page's text may still be loading: the first press requests it, a later one moves.
        for (int i = 0; i < 10 && onFx(view::getTextSelection).getFocusPage() == pageIndex; i++) {
            keys(robot, javafx.scene.input.KeyCode.RIGHT);
            Thread.sleep(150);
        }
        sel = onFx(view::getTextSelection);
        check("right at a page's end crosses to the next page",
                org.icepdf.core.pobjects.graphics.text.DocumentSelection.collapsed(pageIndex + 1, 0).equals(sel), String.valueOf(sel));

        fx(view::clearSelection);
        double[] c = onFx(() -> new double[]{view.getWidth() / 2, view.getHeight() / 2});
        org.icepdf.fx.view.PagePoint before = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        keys(robot, javafx.scene.input.KeyCode.DOWN);
        Thread.sleep(100);
        org.icepdf.fx.view.PagePoint after = onFx(() -> view.pageAt(c[0], c[1]).orElse(null));
        check("arrows scroll with no selection", before != null && after != null
                        && (after.pageIndex() != before.pageIndex() || Math.abs(after.y() - before.y()) > 1),
                before + " -> " + after);
    }

    /**
     * Whole-document search: the hit count equals a direct core count over every page, the first
     * hit is auto-selected, next/previous select and reveal hits, hit highlights sit on the hit
     * bounds, and clearing removes them.
     */
    private void checkSearch(Document document) throws Exception {
        String query = System.getProperty("smoke.search", "Functions");
        org.icepdf.core.search.SearchTerm term = new org.icepdf.core.search.SearchTerm(query, null, false, false, false);
        act("search setup: continuous fit width, page 1", v -> {
            v.clearSelection();
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setRotation(0);
            v.setFitMode(FitMode.WIDTH);
            v.setCurrentPageIndex(0);
        });
        long t0 = System.nanoTime();
        fx(() -> view.search(term));
        long deadline = System.currentTimeMillis() + 300_000;
        while (onFx(view::isSearching) && System.currentTimeMillis() < deadline) Thread.sleep(100);
        double seconds = (System.nanoTime() - t0) / 1e9;
        System.gc();
        long heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
        waitIdle(30_000);
        java.util.List<org.icepdf.fx.view.SearchHit> hits = onFx(() -> new java.util.ArrayList<>(view.getSearchHits()));
        int expected = 0;
        for (int p = 0; p < document.getNumberOfPages(); p++) {
            expected += org.icepdf.core.search.TextSearch.find(document.getPageViewText(p).getTextSequence(), term).size();
        }
        check("search finds every hit", !hits.isEmpty() && hits.size() == expected,
                String.format("\"%s\": %d hits, core count %d, %.1fs, heap %dMB after", query, hits.size(),
                        expected, seconds, heap));

        int current = onFx(view::getCurrentSearchHitIndex);
        org.icepdf.core.pobjects.graphics.text.DocumentSelection sel = onFx(view::getTextSelection);
        check("first hit auto-selected", current == 0 && sel != null && sel.equals(selectionOf(hits.get(0))),
                "current " + current + ", selection " + sel);

        fx(view::nextSearchHit);
        waitIdle(30_000);
        int next = onFx(view::getCurrentSearchHitIndex);
        sel = onFx(view::getTextSelection);
        org.icepdf.fx.view.SearchHit hit = hits.get(Math.max(0, next));
        check("next hit selected", next == 1 && sel.equals(selectionOf(hit)), "current " + next + " " + hit.text());
        check("next hit scrolled into view", isOnScreen(hit), "page " + (hit.pageIndex() + 1));

        // jump far: previous from the first wraps to the last hit, which is pages away.
        fx(() -> {
            view.selectSearchHit(0);
            view.previousSearchHit();
        });
        waitIdle(30_000);
        int last = onFx(view::getCurrentSearchHitIndex);
        org.icepdf.fx.view.SearchHit lastHit = hits.get(Math.max(0, last));
        check("previous wraps to the last hit and reveals it", last == hits.size() - 1 && isOnScreen(lastHit),
                "current " + last + " on page " + (lastHit.pageIndex() + 1));

        checkSearchHighlight(hits, lastHit.pageIndex());

        fx(view::clearSearch);
        waitIdle(30_000);
        boolean cleared = onFx(() -> view.getSearchHits().isEmpty()) && searchPixels().isEmpty();
        check("clear search removes hits and highlights", cleared, "");
    }

    private static org.icepdf.core.pobjects.graphics.text.DocumentSelection selectionOf(org.icepdf.fx.view.SearchHit hit) {
        return org.icepdf.core.pobjects.graphics.text.DocumentSelection.of(hit.pageIndex(), hit.range().getStart(),
                hit.pageIndex(), hit.range().getEnd());
    }

    /** True if some view point (sampled on a grid) maps inside the hit's bounds. */
    private boolean isOnScreen(org.icepdf.fx.view.SearchHit hit) throws Exception {
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        return onFx(() -> {
            for (double y = 0; y < size[1]; y += 3) {
                for (double x = 0; x < size[0]; x += 3) {
                    java.util.Optional<org.icepdf.fx.view.PagePoint> p = view.pageAt(x, y);
                    if (p.isPresent() && p.get().pageIndex() == hit.pageIndex()
                            && p.get().x() >= hit.x() && p.get().x() <= hit.x() + hit.width()
                            && p.get().y() >= hit.y() && p.get().y() <= hit.y() + hit.height()) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /** Search-highlight pixels (#CC00FF at 30% over white) all map into some hit's bounds. */
    private void checkSearchHighlight(java.util.List<org.icepdf.fx.view.SearchHit> hits, int pageIndex) throws Exception {
        java.util.List<double[]> pixels = searchPixels();
        if (pixels.isEmpty()) {
            check("hit highlights on the hit bounds", false, "no highlight pixels on screen");
            return;
        }
        int inside = 0;
        for (double[] p : pixels) {
            java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(p[0], p[1]));
            if (hit.isEmpty()) continue;
            for (org.icepdf.fx.view.SearchHit h : hits) {
                if (h.pageIndex() == hit.get().pageIndex()
                        && hit.get().x() >= h.x() - 1.5 && hit.get().x() <= h.x() + h.width() + 1.5
                        && hit.get().y() >= h.y() - 1.5 && hit.get().y() <= h.y() + h.height() + 1.5) {
                    inside++;
                    break;
                }
            }
        }
        double fraction = inside / (double) pixels.size();
        check("hit highlights on the hit bounds", fraction > 0.99,
                String.format("%d highlight px, %.2f%% inside hit bounds", pixels.size(), fraction * 100));
    }

    private java.util.List<double[]> searchPixels() throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        WritableImage image = onFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < argb.length; i += 3) {
            int c = argb[i];
            int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
            if (Math.abs(r - 240) <= 8 && Math.abs(g - 179) <= 8 && b >= 245) {
                out.add(new double[]{(i % w + 0.5) / scale, (i / w + 0.5) / scale});
            }
        }
        return out;
    }

    /** Presses the keys in order, releases them in reverse: a chord such as ctrl+C. */
    private void keys(javafx.scene.robot.Robot robot, javafx.scene.input.KeyCode... codes) throws Exception {
        for (javafx.scene.input.KeyCode code : codes) {
            fx(() -> robot.keyPress(code));
            Thread.sleep(15);
        }
        for (int i = codes.length - 1; i >= 0; i--) {
            javafx.scene.input.KeyCode code = codes[i];
            fx(() -> robot.keyRelease(code));
            Thread.sleep(15);
        }
        Thread.sleep(60);
    }

    /** Every highlight pixel maps (via pageAt) into one of the core's selection rectangles. */
    private void checkHighlightAligned(String name, int pageIndex,
                                       java.util.List<java.awt.geom.Rectangle2D.Double> rects) throws Exception {
        java.util.List<double[]> pixels = highlightPixels();
        if (pixels.size() < 200) {
            check(name, false, "only " + pixels.size() + " highlight pixels");
            return;
        }
        int sampled = 0;
        int inside = 0;
        for (int i = 0; i < pixels.size(); i += Math.max(1, pixels.size() / 2000)) {
            double[] p = pixels.get(i);
            java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(p[0], p[1]));
            sampled++;
            if (hit.isEmpty() || hit.get().pageIndex() != pageIndex) continue;
            double x = hit.get().x();
            double y = hit.get().y();
            for (java.awt.geom.Rectangle2D.Double r : rects) {
                // a pixel straddling a rect edge maps up to a pixel outside it.
                double tolerance = 1.5;
                if (x >= r.x - tolerance && x <= r.getMaxX() + tolerance
                        && y >= r.y - tolerance && y <= r.getMaxY() + tolerance) {
                    inside++;
                    break;
                }
            }
        }
        double fraction = inside / (double) sampled;
        check(name, fraction > 0.995, String.format("%d highlight px, %.2f%% of %d sampled inside core rects",
                pixels.size(), fraction * 100, sampled));
    }

    /** View coordinates of pixels tinted by the selection fill (#0077FF at 30% over white). */
    private java.util.List<double[]> highlightPixels() throws Exception {
        double scale = onFx(() -> stage.getOutputScaleX());
        WritableImage image = onFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] argb = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                if (Math.abs(r - 178) <= 8 && Math.abs(g - 214) <= 8 && b >= 245) {
                    out.add(new double[]{(x + 0.5) / scale, (y + 0.5) / scale});
                }
            }
        }
        return out;
    }

    /** Robot moves to a glyph (found through pageAt + hitsText), then off the page. */
    private void checkCursor(int pageIndex, org.icepdf.core.pobjects.graphics.text.TextSequence sequence)
            throws Exception {
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        double[] overText = null;
        double[] offPage = null;
        for (double y = 20; y < size[1] - 20 && (overText == null || offPage == null); y += 7) {
            for (double x = 4; x < size[0] - 30; x += 7) {
                double px = x, py = y;
                java.util.Optional<org.icepdf.fx.view.PagePoint> hit = onFx(() -> view.pageAt(px, py));
                if (hit.isEmpty()) {
                    if (offPage == null) offPage = new double[]{x, y};
                } else if (overText == null && hit.get().pageIndex() == pageIndex
                        && sequence.hitsText(hit.get().toAwt())) {
                    overText = new double[]{x, y};
                }
            }
        }
        if (overText == null || offPage == null) {
            check("I-beam over text", false, "no probe points found");
            return;
        }
        javafx.scene.Node viewport = onFx(() -> view.getChildrenUnmodifiable().get(0));
        javafx.scene.robot.Robot robot = onFx(javafx.scene.robot.Robot::new);
        for (double[] target : new double[][]{overText, offPage}) {
            javafx.geometry.Point2D screen = onFx(() -> view.localToScreen(target[0], target[1]));
            fx(() -> robot.mouseMove(screen));
            Thread.sleep(80);
            fx(() -> robot.mouseMove(screen.getX() + 1, screen.getY()));
            Thread.sleep(120);
            javafx.scene.Cursor cursor = onFx(viewport::getCursor);
            boolean text = target == overText;
            check(text ? "I-beam over text" : "arrow off the page",
                    cursor == (text ? javafx.scene.Cursor.TEXT : javafx.scene.Cursor.DEFAULT),
                    "cursor " + cursor + " at view " + target[0] + "," + target[1]);
        }
    }

    private static void fx(Runnable runnable) throws Exception {
        onFx(() -> {
            runnable.run();
            return null;
        });
    }

    private boolean waitIdle(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int quiet = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
            // two consecutive idle polls, so a refresh queued behind the action has run.
            quiet = onFx(() -> view.isRendering()) ? 0 : quiet + 1;
            if (quiet >= 2) return true;
        }
        return false;
    }

    private void snapshot(File file) throws Exception {
        WritableImage image = onFx(() -> {
            double scale = stage.getOutputScaleX();
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(new Scale(scale, scale));
            return view.snapshot(params, null);
        });
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    }

    private static <T> T onFx(Callable<T> callable) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(callable.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(60, TimeUnit.SECONDS);
    }

    /** Same check as the demo: a frame 36pt inside the crop box, lower-left corner block. */
    private static Group cropFrame(int pageIndex, Page page) {
        PRectangle crop = page.getPageBoundary(Page.BOUNDARY_CROPBOX);
        double x = crop.getX() + 36;
        double y = crop.getY() - crop.getHeight() + 36;
        Rectangle frame = new Rectangle(x, y, crop.getWidth() - 72, crop.getHeight() - 72);
        frame.setFill(Color.color(1, 0, 0, 0.06));
        frame.setStroke(Color.RED);
        frame.setStrokeWidth(2);
        Rectangle corner = new Rectangle(x, y, 36, 36);
        corner.setFill(Color.RED);
        return new Group(frame, corner);
    }
}
