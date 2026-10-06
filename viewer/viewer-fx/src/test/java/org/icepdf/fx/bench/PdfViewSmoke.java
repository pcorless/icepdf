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
            stage.setScene(new Scene(view, 1200, 900));
            stage.show();
            ready.countDown();
        });
        ready.await();
        System.out.printf("max heap %dMB, output scale %.2f%n", Runtime.getRuntime().maxMemory() >> 20,
                onFx(() -> stage.getOutputScaleX()));

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

    // ---- tool-mode checks: pass/fail by numbers -------------------------------------------

    private int failures;

    private void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.printf("  %-4s %-44s %s%n", ok ? "PASS" : "FAIL", name, detail);
    }

    private void checkTools(Document document) throws Exception {
        System.out.println("tool checks:");
        checkHitTesting(document);
        checkPanning();
        checkSelectionLayer(document);
        System.out.println(failures == 0 ? "tool checks: all passed" : "tool checks: " + failures + " FAILED");
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
