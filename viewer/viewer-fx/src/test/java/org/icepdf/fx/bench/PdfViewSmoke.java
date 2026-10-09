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
        if ("first-paint".equals(System.getProperty("smoke.only"))) {
            firstPaint(file);
            return;
        }
        if ("pan-fps".equals(System.getProperty("smoke.only"))) {
            // file may list several documents separated by '|'.
            for (String doc : file.toString().split("\\|")) panFrameTimes(Paths.get(doc));
            return;
        }
        if ("forms-corpus".equals(System.getProperty("smoke.only"))) {
            // file is the corpus directory; smoke.forms lists the documents (comma separated).
            for (String name : System.getProperty("smoke.forms",
                    "checking_application.pdf,OoPdfFormExample.pdf,form-400.pdf,support_2550.pdf").split(",")) {
                checkCorpusForm(file.resolve(name.trim()));
            }
            System.out.println(failures == 0 ? "corpus form checks: all passed"
                    : "corpus form checks: " + failures + " FAILED");
            return;
        }
        if ("signing".equals(System.getProperty("smoke.only"))) {
            // file is a PDF to sign (copied first); smoke.keystore is a PKCS#12 test keystore.
            checkSigning(file, Paths.get(System.getProperty("smoke.keystore",
                    "src/test/resources/signing/certificate.pfx")));
            System.out.println(failures == 0 ? "signing checks: all passed" : "signing checks: " + failures + " FAILED");
            return;
        }
        if ("signatures".equals(System.getProperty("smoke.only"))) {
            // file is the signature corpus directory.
            checkSignatures(file);
            System.out.println(failures == 0 ? "signature checks: all passed" : "signature checks: " + failures + " FAILED");
            return;
        }
        if ("print".equals(System.getProperty("smoke.only"))) {
            // file is a multi-page document; smoke.encryption names the encryption corpus.
            checkPrinting(file, Paths.get(System.getProperty("smoke.encryption", "/home/pcorless/dev/pdf-qa/encryption")));
            System.out.println(failures == 0 ? "print checks: all passed" : "print checks: " + failures + " FAILED");
            return;
        }
        if ("encryption".equals(System.getProperty("smoke.only"))) {
            // file is the encryption corpus directory.
            checkEncryption(file);
            System.out.println(failures == 0 ? "encryption checks: all passed"
                    : "encryption checks: " + failures + " FAILED");
            return;
        }
        if ("reopen".equals(System.getProperty("smoke.only"))) {
            // file lists two documents separated by '|': a multi-page one, then a different one.
            String[] docs = file.toString().split("\\|");
            checkReopen(Paths.get(docs[0]), Paths.get(docs[1]));
            System.out.println(failures == 0 ? "reopen checks: all passed" : "reopen checks: " + failures + " FAILED");
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
        boolean idle = waitIdle(Long.getLong("smoke.idleTimeout", 120_000));
        if (!idle) dumpSkinState();
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
                if (name.equals(w.getFieldDictionary().getFullyQualifiedFieldName())) out.add(w);
            }
        }
        return out;
    }

    private String nameOf(org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w) {
        if (w == null) return "null";
        return w.getFieldDictionary().getFullyQualifiedFieldName();
    }

    /**
     * A real form: Tab through every field (each editor opens and closes, and a walk that types
     * nothing must leave no edits); editors sit on their fields; then fill some fields - single
     * line, multi-line, comb - and snapshot the regenerated appearances for review.
     */
    private void checkCorpusForm(Path file) throws Exception {
        String tag = file.getFileName().toString().replaceAll("[^A-Za-z0-9]+", "_");
        java.util.List<Throwable> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> errors.add(e));
        fx(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e)));
        Document doc = new Document();
        doc.setFile(file.toString());
        act("forms corpus: " + file.getFileName(), v -> {
            v.setDocument(doc);
            v.setViewMode(ViewMode.SINGLE_PAGE);
            v.setRotation(0);
            v.setFitMode(FitMode.WIDTH);
            v.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
        });
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        java.util.List<org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation> walked = new java.util.ArrayList<>();
        int misplaced = 0;
        String misplacedDetail = "";
        for (int i = 0; i < 400; i++) {
            fx(view::focusNextField);
            Thread.sleep(30);
            waitIdle(30_000);
            org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w = onFx(view::getFocusedField);
            if (w == null || walked.contains(w)) break;
            walked.add(w);
            kinds.merge(w.getClass().getSimpleName().replace("WidgetAnnotation", ""), 1, Integer::sum);
            javafx.scene.control.Control editor = onFx(() -> (javafx.scene.control.Control) view.lookup(".pdf-field-editor"));
            if (editor != null && onFx(view::getRotation) == 0) {
                java.awt.geom.Rectangle2D r = w.getUserSpaceRectangle();
                double zoom = onFx(view::getZoom);
                double dw = Math.abs(onFx(editor::getWidth) - r.getWidth() * zoom);
                double dh = Math.abs(onFx(editor::getHeight) - r.getHeight() * zoom);
                if (dw > 2 || dh > 2) {
                    misplaced++;
                    misplacedDetail = nameOf(w) + " " + onFx(editor::getWidth) + "x" + onFx(editor::getHeight)
                            + " vs " + r.getWidth() * zoom + "x" + r.getHeight() * zoom;
                }
            }
        }
        fx(view::clearFieldFocus);
        waitIdle(30_000);
        if (onFx(view::isFormFillingAllowed)) {
            check(file.getFileName() + ": Tab walks the fields " + kinds, walked.size() > 0, walked.size() + " fields");
        } else {
            // an encrypted document that forbids filling in forms: no field takes focus.
            check(file.getFileName() + ": filling forbidden, no field takes focus", walked.isEmpty(),
                    walked.size() + " fields");
        }
        check(file.getFileName() + ": walking without typing changes nothing", !onFx(() -> view.canUndoProperty().get()),
                "an edit was recorded");
        check(file.getFileName() + ": editors sit on their fields", misplaced == 0, misplaced + " off, e.g. " + misplacedDetail);

        // fill the first single-line, multi-line and comb text fields; snapshot each regenerated appearance.
        String[] wanted = {"single", "multi", "comb"};
        java.util.Map<String, org.icepdf.core.pobjects.annotations.TextWidgetAnnotation> picks = new java.util.LinkedHashMap<>();
        for (org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w : walked) {
            if (!(w instanceof org.icepdf.core.pobjects.annotations.TextWidgetAnnotation t)) continue;
            org.icepdf.core.pobjects.acroform.TextFieldDictionary f = t.getFieldDictionary();
            String kind = f.isComb() ? "comb"
                    : f.getTextFieldType() == org.icepdf.core.pobjects.acroform.TextFieldDictionary.TextFieldType.TEXT_AREA ? "multi"
                    : f.getTextFieldType() == org.icepdf.core.pobjects.acroform.TextFieldDictionary.TextFieldType.TEXT_INPUT ? "single" : null;
            if (kind != null) picks.putIfAbsent(kind, t);
        }
        java.util.List<String> filled = new java.util.ArrayList<>();
        for (String kind : wanted) {
            org.icepdf.core.pobjects.annotations.TextWidgetAnnotation t = picks.get(kind);
            if (t == null) continue;
            String text = switch (kind) {
                case "multi" -> "A multi-line value that is long enough to wrap across the width of the field, "
                        + "with a second sentence.\nAnd an explicit new line.";
                case "comb" -> "1234567890";
                default -> "Smoke test value 123";
            };
            fx(() -> view.focusField(t));
            waitIdle(30_000);
            javafx.scene.control.TextInputControl editor = editor();
            if (editor == null) continue;
            fx(() -> editor.setText(text));
            fx(view::clearFieldFocus);
            Thread.sleep(150);
            waitIdle(30_000);
            snapshot(out.resolve("corpus_" + tag + "_" + kind + ".png").toFile());
            filled.add(kind + "=" + nameOf(t) + " -> " + String.valueOf(t.getFieldDictionary().getFieldValue()).length() + " chars");
        }
        System.out.println("  filled " + filled);
        Thread.setDefaultUncaughtExceptionHandler(previous);
        check(file.getFileName() + ": no uncaught exceptions", errors.isEmpty(),
                errors.isEmpty() ? "" : errors.get(0).toString());
        fx(() -> view.setDocument(null));
        doc.dispose();
    }

    // ---- first paint (B1) ---------------------------------------------------------------------

    /**
     * Time to first content and to visually complete after opening, by in-process snapshots of the
     * view (the same measure the Swing harness takes): first change, and the last change before the view
     * holds still for 1.5 s.  Opening includes Document.setFile, as SwingController.openDocument does.
     */
    private void firstPaint(Path file) throws Exception {
        fx(() -> {
            view.setViewMode(ViewMode.CONTINUOUS);
            view.setFitMode(FitMode.WIDTH);
        });
        // warm up: open and close a small document, as the Swing harness does.
        String warmup = System.getProperty("warmup");
        if (warmup != null) {
            Document w = new Document();
            w.setFile(warmup);
            fx(() -> view.setDocument(w));
            Thread.sleep(1000);
            fx(() -> view.setDocument(null));
            w.dispose();
        }
        Thread.sleep(1500);
        // FX-thread health through the open: the longest gap between pulses, and when it began.
        long[] gap = new long[2];
        javafx.animation.AnimationTimer pulses = new javafx.animation.AnimationTimer() {
            long last;

            @Override
            public void handle(long now) {
                if (last != 0 && now - last > gap[0]) {
                    gap[0] = now - last;
                    gap[1] = last;
                }
                last = now;
            }
        };
        fx(pulses::start);
        long t0 = System.nanoTime();
        long[] phases = new long[3];
        fx(() -> {
            phases[0] = System.nanoTime();
            Document doc = new Document();
            try {
                doc.setFile(file.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            phases[1] = System.nanoTime();
            view.setDocument(doc);
            phases[2] = System.nanoTime();
        });
        System.out.printf("FX-PHASES runLater %d ms, setFile %d ms, setDocument %d ms%n",
                (phases[0] - t0) / 1_000_000, (phases[1] - phases[0]) / 1_000_000, (phases[2] - phases[1]) / 1_000_000);
        long opened = System.nanoTime();
        int[] first = null, last = null;
        long firstChange = -1, lastChange = -1, lastChangeAt = System.nanoTime();
        while (true) {
            long now = System.nanoTime();
            // in-process snapshot: a screen grab on Wayland goes through the screen-share portal.
            int[] px = onFx(() -> {
                javafx.scene.image.WritableImage img = view.snapshot(null, null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                int[] out = new int[w * h];
                img.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), out, 0, w);
                return out;
            });
            if (first == null) {
                first = px;
                last = px;
            } else if (!java.util.Arrays.equals(px, last)) {
                long ms = (now - t0) / 1_000_000;
                if (firstChange < 0) firstChange = ms;
                lastChange = ms;
                lastChangeAt = now;
                last = px;
            }
            if (now - lastChangeAt > 1_500_000_000L && firstChange >= 0) break;
            if (now - t0 > 90_000_000_000L) break;
            Thread.sleep(20);
        }
        fx(pulses::stop);
        System.out.printf("FX-PULSE longest gap %d ms, starting %d ms after open%n", gap[0] / 1_000_000,
                (gap[1] - t0) / 1_000_000);
        System.out.printf("FX    %-40s open() %5d ms  first content %6d ms  visually complete %6d ms  heap %d MB%n",
                file.getFileName(), (opened - t0) / 1_000_000, firstChange, lastChange,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20);
    }

    // ---- pan frame times (B2) ---------------------------------------------------------------

    /**
     * Frame times while panning: a warm pass first steps through a region until each viewport is
     * rendered (so its tiles are cached), then an AnimationTimer pans through that region at a
     * fixed speed and records every pulse: the interval between pulses (what the user sees) and
     * the FX thread's own work up to the end of layout.  A cold pan over unvisited pages follows
     * for comparison (tiles render asynchronously; previews cover the gaps).
     */
    private void panFrameTimes(Path file) throws Exception {
        Document doc = new Document();
        doc.setFile(file.toString());
        act("pan-fps open " + file.getFileName(), v -> {
            v.setViewMode(ViewMode.CONTINUOUS);
            v.setRotation(0);
            String zoom = System.getProperty("smoke.panZoom");
            v.setFitMode(zoom == null ? FitMode.WIDTH : FitMode.NONE);
            if (zoom != null) v.setZoom(Double.parseDouble(zoom));
            v.setDocument(doc);
        });
        double viewportHeight = onFx(view::getHeight);
        int step = 12; // px per pulse: 720 px/s at 60 Hz, a brisk scroll
        double range = viewportHeight * 4;
        // warm: walk the range a viewport at a time, waiting for each to finish rendering
        for (double y = 0; y < range + viewportHeight; y += viewportHeight * 0.5) {
            fx(() -> view.scrollBy(0, viewportHeight * 0.5));
            waitIdle(60_000);
        }
        fx(() -> view.scrollBy(0, -(range + viewportHeight)));
        waitIdle(60_000);
        System.gc();
        Thread.sleep(300);
        report(file.getFileName() + " warm", pan(step, (int) (range / step)));
        // cold: further down, never visited
        fx(() -> view.scrollBy(0, range * 2));
        waitIdle(60_000);
        report(file.getFileName() + " cold", pan(step, (int) (range / step)));
        fx(() -> view.setDocument(null));
        doc.dispose();
    }

    private record PanStats(double[] intervals, double[] layout, long renders) {
    }

    /** Pans {@code frames} pulses of {@code step} px; returns per-pulse intervals and layout times (ms). */
    private PanStats pan(int step, int frames) throws Exception {
        double[] intervals = new double[frames];
        double[] layout = new double[frames];
        long[] pulseStart = new long[1];
        int[] frame = {-1};
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Runnable postLayout = () -> {
            if (frame[0] >= 0 && frame[0] < frames && pulseStart[0] != 0) {
                layout[frame[0]] = (System.nanoTime() - pulseStart[0]) / 1e6;
            }
        };
        long rendersBefore = org.icepdf.fx.view.TileRenderer.contentRenderCount();
        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            long last;

            @Override
            public void handle(long now) {
                pulseStart[0] = System.nanoTime();
                if (frame[0] >= 0 && frame[0] < frames) intervals[frame[0]] = (now - last) / 1e6;
                last = now;
                frame[0]++;
                if (frame[0] >= frames) {
                    stop();
                    done.countDown();
                    return;
                }
                view.scrollBy(0, step);
            }
        };
        fx(() -> {
            view.getScene().addPostLayoutPulseListener(postLayout);
            timer.start();
        });
        done.await(120, java.util.concurrent.TimeUnit.SECONDS);
        fx(() -> view.getScene().removePostLayoutPulseListener(postLayout));
        long renders = org.icepdf.fx.view.TileRenderer.contentRenderCount() - rendersBefore;
        waitIdle(60_000);
        // the first interval is from timer start, not a pan frame
        return new PanStats(java.util.Arrays.copyOfRange(intervals, 1, frames),
                java.util.Arrays.copyOfRange(layout, 1, frames), renders);
    }

    private static double pct(double[] sorted, double p) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
    }

    private static void report(String label, PanStats stats) {
        double[] iv = stats.intervals().clone();
        double[] ly = stats.layout().clone();
        java.util.Arrays.sort(iv);
        java.util.Arrays.sort(ly);
        double mean = java.util.Arrays.stream(iv).average().orElse(0);
        long slow = java.util.Arrays.stream(iv).filter(v -> v > 1000.0 / 60 * 1.5).count();
        System.out.printf("PAN %-36s frames %4d  fps %5.1f  interval p50 %5.1f p95 %5.1f p99 %5.1f max %6.1f ms"
                        + "  >25ms %3d (%4.1f%%)  fx-layout p50 %4.1f p95 %4.1f max %5.1f ms  tile renders %d%n",
                label, iv.length, 1000 / mean, pct(iv, .5), pct(iv, .95), pct(iv, .99), iv[iv.length - 1],
                slow, 100.0 * slow / iv.length, pct(ly, .5), pct(ly, .95), ly[ly.length - 1], stats.renders());
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

    private static Object fieldValue(org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w) {
        org.icepdf.core.pobjects.acroform.FieldDictionary f = w.getFieldDictionary();
        return f.getParent() != null && f.getEntries().get(org.icepdf.core.pobjects.acroform.FieldDictionary.V_KEY) == null
                ? f.getParent().getFieldValue() : f.getFieldValue();
    }

    private javafx.scene.control.TextInputControl editor() throws Exception {
        return onFx(() -> (javafx.scene.control.TextInputControl) view.lookup(".pdf-field-editor"));
    }

    private void key(javafx.scene.Node target, javafx.scene.input.KeyCode code, boolean shift, boolean shortcut)
            throws Exception {
        fx(() -> javafx.event.Event.fireEvent(target, new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut, false, shortcut)));
        Thread.sleep(80);
        waitIdle(30_000);
    }

    /** Text editing (step 3); later steps append buttons and choices. */
    private void checkFormFilling(javafx.scene.robot.Robot robot) throws Exception {
        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation name = widgets("name").get(0);
        robotClick(robot, viewPointIn(0, name.getUserSpaceRectangle()));
        javafx.scene.control.TextInputControl edit = editor();
        check("click opens an editor with the field's text", edit != null && "Ada".equals(onFx(edit::getText)),
                edit == null ? "no editor" : onFx(edit::getText));
        if (edit == null) return;
        double[] editSize = onFx(() -> new double[]{edit.getWidth(), edit.getHeight()});
        check("the text editor covers the field", editSize[0] > 100 && editSize[1] > 15, java.util.Arrays.toString(editSize));
        snapshot(out.resolve("forms_text_editing.png").toFile());
        fx(() -> edit.setText("Grace"));
        key(edit, javafx.scene.input.KeyCode.ENTER, false, false);
        check("Enter commits the value", "Grace".equals(fieldValue(name)) && editor() == null,
                fieldValue(name) + ", editor " + (editor() != null));
        snapshot(out.resolve("forms_text_committed.png").toFile());
        fx(view::undo);
        waitIdle(30_000);
        check("undo restores the old value", "Ada".equals(fieldValue(name)), String.valueOf(fieldValue(name)));

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation notes = widgets("notes").get(0);
        fx(() -> view.focusField(notes));
        waitIdle(30_000);
        javafx.scene.control.TextInputControl area = editor();
        check("multi-line field gets a TextArea", area instanceof javafx.scene.control.TextArea,
                area == null ? "none" : area.getClass().getSimpleName());
        if (area != null) {
            fx(() -> area.setText("first line\nsecond line"));
            fx(view::clearFieldFocus);
            Thread.sleep(150);
            waitIdle(30_000);
            check("moving focus away commits", String.valueOf(fieldValue(notes)).contains("second line") && editor() == null,
                    String.valueOf(fieldValue(notes)));
        }

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation code = widgets("code").get(0);
        fx(() -> view.focusField(code));
        waitIdle(30_000);
        javafx.scene.control.TextInputControl codeEditor = editor();
        if (codeEditor != null) {
            fx(() -> codeEditor.replaceText(0, codeEditor.getLength(), "ABCDEFG"));
            fx(() -> codeEditor.appendText("XYZ"));
            check("/MaxLen 5 is enforced", onFx(codeEditor::getText).length() <= 5, onFx(codeEditor::getText));
            key(codeEditor, javafx.scene.input.KeyCode.ESCAPE, false, false);
        }

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation secret = widgets("secret").get(0);
        fx(() -> view.focusField(secret));
        waitIdle(30_000);
        check("password field gets a PasswordField", editor() instanceof javafx.scene.control.PasswordField,
                editor() == null ? "none" : editor().getClass().getSimpleName());
        if (editor() != null) key(editor(), javafx.scene.input.KeyCode.ESCAPE, false, false);

        fx(() -> view.focusField(name));
        waitIdle(30_000);
        javafx.scene.control.TextInputControl esc = editor();
        if (esc != null) {
            fx(() -> esc.setText("zzz"));
            key(esc, javafx.scene.input.KeyCode.ESCAPE, false, false);
        }
        check("Esc cancels the edit", "Ada".equals(fieldValue(name)) && editor() == null, String.valueOf(fieldValue(name)));

        fx(() -> view.focusField(name));
        waitIdle(30_000);
        javafx.scene.control.TextInputControl tab = editor();
        if (tab != null) {
            fx(() -> tab.setText("Tabbed"));
            key(tab, javafx.scene.input.KeyCode.TAB, false, false);
        }
        check("Tab commits and moves to the next field", "Tabbed".equals(fieldValue(name))
                        && "notes".equals(nameOf(onFx(view::getFocusedField))),
                fieldValue(name) + " -> " + nameOf(onFx(view::getFocusedField)));
        if (editor() != null) key(editor(), javafx.scene.input.KeyCode.ESCAPE, false, false);
        fx(view::clearFieldFocus);
        waitIdle(30_000);
        snapshot(out.resolve("forms_text.png").toFile());
        checkButtonsAndChoices(robot);
    }

    private static boolean on(org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w) {
        return ((org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation) w).isOn();
    }

    private void clickField(javafx.scene.robot.Robot robot, org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w)
            throws Exception {
        robotClick(robot, viewPointIn(0, w.getUserSpaceRectangle()));
        Thread.sleep(120);
        waitIdle(30_000);
    }

    /** Buttons and choices (step 4). */
    private void checkButtonsAndChoices(javafx.scene.robot.Robot robot) throws Exception {
        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation agree = widgets("agree").get(0);
        clickField(robot, agree);
        boolean afterClick = on(agree);
        clickField(robot, agree);
        check("click toggles a check box on and off", afterClick && !on(agree), afterClick + " then " + on(agree));
        key(view, javafx.scene.input.KeyCode.SPACE, false, false);
        check("Space toggles the focused check box", on(agree) && onFx(view::getFocusedField) == agree,
                String.valueOf(on(agree)));

        java.util.List<org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation> color = widgets("color");
        clickField(robot, color.get(1));
        check("click selects a radio, its siblings turn off", on(color.get(1)) && !on(color.get(0)) && !on(color.get(2)),
                on(color.get(0)) + " " + on(color.get(1)) + " " + on(color.get(2)));
        clickField(robot, color.get(1));
        check("clicking the selected radio keeps it (NoToggleToOff)", on(color.get(1)), String.valueOf(on(color.get(1))));
        java.util.List<org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation> pair = widgets("pair");
        clickField(robot, pair.get(0));
        check("radios in unison switch together", on(pair.get(0)) && on(pair.get(1)) && !on(pair.get(2)),
                on(pair.get(0)) + " " + on(pair.get(1)) + " " + on(pair.get(2)));
        snapshot(out.resolve("forms_buttons.png").toFile());

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation country = widgets("country").get(0);
        clickField(robot, country);
        Thread.sleep(300);
        javafx.scene.control.ComboBox<?> combo = onFx(() -> (javafx.scene.control.ComboBox<?>) view.lookup(".pdf-field-editor"));
        check("click opens a combo with its drop-down", combo != null && onFx(combo::isShowing),
                combo == null ? "no editor" : "showing " + onFx(combo::isShowing));
        if (combo != null) {
            double[] size = onFx(() -> new double[]{combo.getWidth(), combo.getHeight()});
            check("the combo editor covers the field", size[0] > 100 && size[1] > 15, java.util.Arrays.toString(size));
        }
        snapshot(out.resolve("forms_combo_open.png").toFile());
        if (combo != null) {
            // click "Japan" in the drop-down, as a user does (the popup is its own window).
            double[] cell = onFx(() -> {
                for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                    if (!(w instanceof javafx.stage.PopupWindow) || w.getScene() == null) continue;
                    for (javafx.scene.Node n : w.getScene().getRoot().lookupAll(".list-cell")) {
                        if (n instanceof javafx.scene.control.ListCell<?> c && "Japan".equals(c.getText())) {
                            javafx.geometry.Bounds b = c.localToScreen(c.getBoundsInLocal());
                            return new double[]{b.getCenterX(), b.getCenterY()};
                        }
                    }
                }
                return null;
            });
            if (cell != null) {
                fx(() -> robot.mouseMove(cell[0], cell[1]));
                Thread.sleep(80);
                fx(() -> robot.mouseClick(javafx.scene.input.MouseButton.PRIMARY));
            } else {
                fx(() -> combo.getSelectionModel().select(2));
            }
            Thread.sleep(200);
            waitIdle(30_000);
        }
        check("picking a combo entry commits it", "Japan".equals(fieldValue(country)) && editor() == null
                        && onFx(() -> view.lookup(".pdf-field-editor")) == null,
                String.valueOf(fieldValue(country)));

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation city = widgets("city").get(0);
        fx(() -> view.focusField(city));
        waitIdle(30_000);
        javafx.scene.control.ComboBox<?> editable = onFx(() -> (javafx.scene.control.ComboBox<?>) view.lookup(".pdf-field-editor"));
        check("editable combo is editable", editable != null && onFx(editable::isEditable),
                editable == null ? "no editor" : "editable " + onFx(editable::isEditable));
        if (editable != null) {
            fx(() -> editable.getEditor().setText("Montreal"));
            key(editable.getEditor(), javafx.scene.input.KeyCode.ENTER, false, false);
        }
        check("typed combo text commits on Enter", "Montreal".equals(fieldValue(city)), String.valueOf(fieldValue(city)));

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation fruit = widgets("fruit").get(0);
        clickField(robot, fruit);
        javafx.scene.control.ListView<?> list = onFx(() -> (javafx.scene.control.ListView<?>) view.lookup(".pdf-field-editor"));
        check("click opens a list", list != null, "focused " + nameOf(onFx(view::getFocusedField)));
        snapshot(out.resolve("forms_list_open.png").toFile());
        if (list != null) {
            double[] size = onFx(() -> new double[]{list.getWidth(), list.getHeight(),
                    list.lookupAll(".list-cell").stream().filter(n -> ((javafx.scene.control.ListCell<?>) n).getText() != null).count()});
            check("the list editor covers the field and shows its entries", size[0] > 100 && size[1] > 40 && size[2] == 3,
                    java.util.Arrays.toString(size));
        }
        if (list != null) {
            fx(() -> list.getSelectionModel().clearAndSelect(0));
            Thread.sleep(150);
            waitIdle(30_000);
        }
        check("picking a single-select list entry commits it", "Apple".equals(fieldValue(fruit)) && editor() == null,
                String.valueOf(fieldValue(fruit)));

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation toppings = widgets("toppings").get(0);
        fx(() -> view.focusField(toppings));
        waitIdle(30_000);
        javafx.scene.control.ListView<?> multi = onFx(() -> (javafx.scene.control.ListView<?>) view.lookup(".pdf-field-editor"));
        boolean multiple = multi != null && onFx(() -> multi.getSelectionModel().getSelectionMode())
                == javafx.scene.control.SelectionMode.MULTIPLE;
        check("multi-select list allows several", multiple, multi == null ? "no editor" : "mode");
        if (multi != null) {
            fx(() -> {
                multi.getSelectionModel().clearSelection();
                multi.getSelectionModel().selectIndices(1, 2);
            });
            fx(view::clearFieldFocus);
            Thread.sleep(150);
            waitIdle(30_000);
        }
        java.util.List<Integer> picked = ((org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation) toppings)
                .getFieldDictionary().getIndexes();
        check("multi-select commits on leaving", java.util.List.of(1, 2).equals(picked), String.valueOf(picked));
        snapshot(out.resolve("forms_choices.png").toFile());

        java.util.List<org.icepdf.fx.view.AnnotationActionEvent> actions = new java.util.ArrayList<>();
        fx(() -> view.setOnAnnotationAction(actions::add));
        clickField(robot, widgets("submit").get(0));
        check("submit goes to the application", actions.size() == 1
                        && actions.get(0).action() instanceof org.icepdf.core.pobjects.actions.SubmitFormAction,
                actions.toString());
        fx(() -> view.setOnAnnotationAction(null));

        clickField(robot, widgets("reset").get(0));
        check("reset button restores defaults", "".equals(String.valueOf(fieldValue(widgets("name").get(0))))
                        && !on(agree) && on(color.get(0)) && "Canada".equals(fieldValue(country)),
                fieldValue(widgets("name").get(0)) + " " + on(agree) + " " + on(color.get(0)) + " " + fieldValue(country));
        snapshot(out.resolve("forms_reset.png").toFile());
        fx(view::undo);
        waitIdle(30_000);
        check("undo brings the filled values back", on(agree) && on(color.get(1)) && "Japan".equals(fieldValue(country)),
                on(agree) + " " + on(color.get(1)) + " " + fieldValue(country));
        fx(view::clearFieldFocus);
        waitIdle(30_000);
        snapshot(out.resolve("forms_filled.png").toFile());
        checkFormApi();
    }

    /** The public form API (step 5): change events, values by name, reset. */
    private void checkFormApi() throws Exception {
        java.util.List<org.icepdf.fx.view.FormFieldChangeEvent> events = new java.util.ArrayList<>();
        fx(() -> view.setOnFormFieldChanged(events::add));

        org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation name = widgets("name").get(0);
        fx(() -> view.focusField(name));
        waitIdle(30_000);
        javafx.scene.control.TextInputControl edit = editor();
        if (edit != null) {
            fx(() -> edit.setText("Typed"));
            key(edit, javafx.scene.input.KeyCode.ENTER, false, false);
        }
        check("typing reports a field change", events.size() == 1 && "name".equals(events.get(0).name())
                        && "Typed".equals(events.get(0).newValue()), events.toString());
        String before = String.valueOf(events.isEmpty() ? null : events.get(0).oldValue());
        fx(view::undo);
        check("undo reports the change back", events.size() == 2 && before.equals(events.get(1).newValue())
                        && "Typed".equals(events.get(1).oldValue()), events.toString());

        check("getFieldValue reads each kind", onFx(() -> before.equals(view.getFieldValue("name"))
                        && "Green".equals(view.getFieldValue("color")) && "Japan".equals(view.getFieldValue("country"))
                        && java.util.List.of("Banana", "Cherry").equals(view.getFieldValue("toppings"))),
                onFx(() -> view.getFieldValue("name") + " " + view.getFieldValue("color") + " "
                        + view.getFieldValue("country") + " " + view.getFieldValue("toppings")));
        check("getFieldNames lists the fields", onFx(() -> view.getFieldNames().containsAll(
                java.util.List.of("name", "color", "pair", "toppings", "submit"))), onFx(() -> view.getFieldNames().toString()));

        events.clear();
        boolean set = onFx(() -> view.setFieldValue("name", "Set by app") && view.setFieldValue("agree", false)
                && view.setFieldValue("color", "Blue") && view.setFieldValue("country", "Canada")
                && view.setFieldValue("toppings", java.util.List.of("Apple")) && view.setFieldValue("city", "Paris"));
        waitIdle(30_000);
        check("setFieldValue sets each kind", set && onFx(() -> "Set by app".equals(view.getFieldValue("name"))
                        && "Off".equals(view.getFieldValue("agree")) && "Blue".equals(view.getFieldValue("color"))
                        && "Canada".equals(view.getFieldValue("country"))
                        && java.util.List.of("Apple").equals(view.getFieldValue("toppings"))
                        && "Paris".equals(view.getFieldValue("city"))),
                set + " " + onFx(() -> view.getFieldValue("name") + " " + view.getFieldValue("agree") + " "
                        + view.getFieldValue("color") + " " + view.getFieldValue("country") + " "
                        + view.getFieldValue("toppings") + " " + view.getFieldValue("city")));
        check("setFieldValue reports each change", events.size() == 6, events.size() + " events");
        check("setFieldValue refuses what doesn't fit", onFx(() -> !view.setFieldValue("nope", "x")
                        && !view.setFieldValue("color", "Purple") && !view.setFieldValue("country", "Atlantis")
                        && !view.setFieldValue("fruit", java.util.List.of("Apple", "Cherry"))),
                "accepted a bad value");
        snapshot(out.resolve("forms_api_set.png").toFile());

        events.clear();
        fx(view::resetForm);
        waitIdle(30_000);
        check("resetForm restores defaults and reports them", onFx(() -> "".equals(view.getFieldValue("name"))
                        && "Red".equals(view.getFieldValue("color")) && "Canada".equals(view.getFieldValue("country")))
                        && !events.isEmpty(),
                onFx(() -> view.getFieldValue("name") + " " + view.getFieldValue("color")) + ", " + events.size() + " events");
        fx(() -> view.setOnFormFieldChanged(null));

        // the edits survive a save and reopen.
        fx(() -> view.setFieldValue("name", "Saved value"));
        java.nio.file.Path saved = out.resolve("forms_saved.pdf");
        try (java.io.OutputStream os = new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(saved))) {
            formDoc.saveToOutputStream(os);
        }
        Document reopened = new Document();
        reopened.setFile(saved.toString());
        Object savedName = null;
        for (org.icepdf.core.pobjects.annotations.Annotation a : reopened.getPageTree().getPage(0).getAnnotations()) {
            if (a instanceof org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation w && "name".equals(nameOf(w))) {
                savedName = fieldValue(w);
            }
        }
        reopened.dispose();
        check("values survive save and reopen", "Saved value".equals(String.valueOf(savedName)), String.valueOf(savedName));
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

    /**
     * Opening a second document must show only that document: its page count, its page sizes and
     * nothing left over from the first (page layers, layout, scroll position, current page).
     */
    private void checkReopen(Path first, Path second) throws Exception {
        System.out.println("reopen checks:");
        Document a = new Document();
        a.setFile(first.toString());
        Document b = new Document();
        b.setFile(second.toString());
        int lastOfA = a.getNumberOfPages() - 1;
        for (org.icepdf.fx.view.ViewMode mode : new org.icepdf.fx.view.ViewMode[]{
                org.icepdf.fx.view.ViewMode.CONTINUOUS, org.icepdf.fx.view.ViewMode.SINGLE_PAGE}) {
            for (FitMode fit : new FitMode[]{FitMode.WIDTH, FitMode.NONE}) {
                fx(() -> {
                    view.setViewMode(mode);
                    view.setFitMode(fit);
                    view.setDocument(a);
                    view.setCurrentPageIndex(lastOfA);
                });
                waitIdle(60_000);
                fx(() -> view.setDocument(b));
                waitIdle(60_000);
                String label = mode + " " + fit + " ";
                Object[] state = onFx(() -> {
                    Object skin = view.getSkin();
                    java.util.Map<?, ?> layers = (java.util.Map<?, ?>) skinField(skin, "layers");
                    double[] widths = (double[]) skinField(skin, "unitWidths");
                    Object layout = skinField(skin, "layout");
                    Object slot = layout.getClass().getMethod("getSlot", int.class).invoke(layout, 0);
                    double height = (double) slot.getClass().getMethod("height").invoke(slot);
                    return new Object[]{view.getPageCount(), view.getCurrentPageIndex(),
                            new java.util.TreeSet<>(layers.keySet()), widths.length, height};
                });
                int pages = b.getNumberOfPages();
                double expected = b.getPageTree().getPage(0).getSize(view.getPageBoundary(), 0, 1f).getHeight()
                        * onFx(view::getZoom);
                check(label + "page count", (int) state[0] == pages, "pageCount " + state[0] + ", want " + pages);
                check(label + "current page reset", (int) state[1] == 0, "current " + state[1]);
                check(label + "only the new pages laid out", (int) state[3] == pages
                        && ((java.util.Set<?>) state[2]).stream().allMatch(k -> (int) k < pages),
                        "unit sizes " + state[3] + ", page layers " + state[2]);
                check(label + "page 1 sized as the new page", Math.abs((double) state[4] - expected) < 1,
                        String.format("slot height %.1f, want %.1f", (double) state[4], expected));
            }
        }
        fx(() -> view.setDocument(null));
        a.dispose();
        b.dispose();
    }

    /**
     * Encrypted documents: the password prompt (asked from a loader thread, a wrong password asks
     * again, cancel gives up) and the permissions the view enforces.
     */
    private void checkEncryption(Path corpus) throws Exception {
        System.out.println("encryption checks:");
        Path maltby = corpus.resolve("rev6/encrypted-maltby.pdf");

        // wrong password, then the right one: two prompts, the second saying the first was wrong.
        java.util.List<String> headers = new java.util.ArrayList<>();
        Object[] opened = openWithAnswers(maltby, headers, "wrong", "maltby");
        check("wrong then right password opens", opened[0] instanceof Document d && d.getNumberOfPages() > 0,
                String.valueOf(opened[0]));
        check("second prompt says the password was wrong", headers.size() == 2
                && headers.get(1).startsWith("Incorrect"), String.valueOf(headers));
        if (opened[0] instanceof Document d) d.dispose();

        headers.clear();
        opened = openWithAnswers(maltby, headers, (String) null);
        check("cancel gives up quietly", opened[0] instanceof org.icepdf.core.exceptions.PDFSecurityException
                && (boolean) opened[1], opened[0] + " cancelled " + opened[1]);

        // rev6/encrypted.pdf: forms may be filled, but no copying and no annotating.
        Document restricted = new Document();
        restricted.setFile(corpus.resolve("rev6/encrypted.pdf").toString());
        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.HIGHLIGHT));
        fx(() -> view.setDocument(restricted));
        waitIdle(60_000);
        boolean[] allowed = onFx(() -> new boolean[]{view.isCopyAllowed(), view.isAnnotationEditingAllowed(),
                view.isFormFillingAllowed()});
        check("permissions read from the document", !allowed[0] && !allowed[1] && allowed[2],
                "copy " + allowed[0] + " annotate " + allowed[1] + " fill " + allowed[2]);
        check("opening drops an annotation tool", onFx(view::getToolMode) == org.icepdf.fx.view.ToolMode.TEXT_SELECT,
                String.valueOf(onFx(view::getToolMode)));
        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.INK));
        check("annotation tools refused", onFx(view::getToolMode) == org.icepdf.fx.view.ToolMode.TEXT_SELECT,
                String.valueOf(onFx(view::getToolMode)));
        fx(view::selectAll);
        int marked = onFx(view::highlightSelection);
        check("highlighting the selection refused", marked == 0, marked + " created");
        String sentinel = "clipboard-sentinel-" + System.nanoTime();
        fx(() -> {
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString(sentinel);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            view.copySelection();
        });
        Thread.sleep(1500);
        String clip = onFx(() -> javafx.scene.input.Clipboard.getSystemClipboard().getString());
        check("copy refused", sentinel.equals(clip), clip == null ? "null" : clip.length() + " chars");
        String text = onFx(view::selectedTextAsync).get(30, TimeUnit.SECONDS);
        check("selectedTextAsync still extracts (application's call)", !text.isEmpty(), text.length() + " chars");
        fx(view::clearSelection);
        fx(view::focusNextField);
        check("fields can be filled", onFx(view::getFocusedField) != null, String.valueOf(onFx(view::getFocusedField)));
        fx(view::clearFieldFocus);
        fx(() -> view.setDocument(null));
        restricted.dispose();

        // chap4_pg1thru7.pdf: a form whose fields may not be filled.
        Document noFill = new Document();
        noFill.setFile(corpus.resolve("chap4_pg1thru7.pdf").toString());
        fx(() -> view.setDocument(noFill));
        waitIdle(60_000);
        check("filling refused by the document", !onFx(view::isFormFillingAllowed), "");
        fx(view::focusNextField);
        check("no field takes focus", onFx(view::getFocusedField) == null, String.valueOf(onFx(view::getFocusedField)));
        fx(() -> view.setDocument(null));
        noFill.dispose();

        // an unencrypted document allows everything again.
        Document plain = new Document();
        plain.setFile(corpus.resolve("doc1_security_exception.pdf").toString());
        fx(() -> view.setDocument(plain));
        waitIdle(60_000);
        allowed = onFx(() -> new boolean[]{view.isCopyAllowed(), view.isAnnotationEditingAllowed(),
                view.isFormFillingAllowed()});
        check("unencrypted document allows everything", allowed[0] && allowed[1] && allowed[2], "");
        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.INK));
        check("annotation tools available again", onFx(view::getToolMode) == org.icepdf.fx.view.ToolMode.INK, "");
        fx(() -> {
            view.setToolMode(org.icepdf.fx.view.ToolMode.TEXT_SELECT);
            view.setDocument(null);
        });
        plain.dispose();
    }

    /**
     * Opens a document on a loader thread with a {@link org.icepdf.fx.view.PasswordPrompt}, answering
     * each prompt in turn (null cancels).  Records each prompt's header.
     *
     * @return {document or the exception thrown, whether the prompt reports cancelled}
     */
    private Object[] openWithAnswers(Path file, java.util.List<String> headers, String... answers) throws Exception {
        org.icepdf.fx.view.PasswordPrompt prompt = onFx(() -> new org.icepdf.fx.view.PasswordPrompt(stage,
                file.getFileName().toString()));
        CompletableFuture<Object> result = new CompletableFuture<>();
        Thread loader = new Thread(() -> {
            Document d = new Document();
            d.setSecurityCallback(prompt);
            try {
                d.setFile(file.toString());
                result.complete(d);
            } catch (Throwable t) {
                d.dispose();
                result.complete(t);
            }
        }, "loader");
        loader.start();
        for (String answer : answers) {
            javafx.scene.control.DialogPane pane = null;
            long deadline = System.currentTimeMillis() + 15_000;
            while (pane == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                pane = onFx(() -> {
                    for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                        if (w.isShowing() && w.getScene() != null
                                && w.getScene().getRoot() instanceof javafx.scene.control.DialogPane p) return p;
                    }
                    return null;
                });
            }
            if (pane == null) break;
            javafx.scene.control.DialogPane shown = pane;
            headers.add(onFx(shown::getHeaderText));
            fx(() -> {
                if (answer == null) {
                    ((javafx.scene.control.Button) shown.lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire();
                } else {
                    ((javafx.scene.control.PasswordField) shown.lookup(".password-field")).setText(answer);
                    ((javafx.scene.control.Button) shown.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
                }
            });
            // wait for this dialog to close before looking for the next one.
            while (onFx(() -> shown.getScene() != null && shown.getScene().getWindow() != null
                    && shown.getScene().getWindow().isShowing())) Thread.sleep(20);
        }
        return new Object[]{result.get(30, TimeUnit.SECONDS), prompt.isCancelled()};
    }

    /**
     * The print dialog driven like a user (pick "Current page", 2 copies), the job printed through the
     * view to a PostScript file, and the print permissions.
     */
    private void checkPrinting(Path file, Path encryption) throws Exception {
        System.out.println("print checks:");
        Document document = new Document();
        document.setFile(file.toString());
        fx(() -> {
            view.setDocument(document);
            view.setCurrentPageIndex(3);
        });
        waitIdle(60_000);
        check("printing allowed for a plain document", onFx(view::isPrintAllowed), "");

        Path ps = out.resolve("print-smoke.ps");
        java.io.OutputStream stream = Files.newOutputStream(ps);
        javax.print.StreamPrintService filePrinter = javax.print.StreamPrintServiceFactory
                .lookupStreamPrintServiceFactories(javax.print.DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                        "application/postscript")[0].getPrintService(stream);
        org.icepdf.fx.print.PdfPrintDialog dialog = onFx(() -> {
            org.icepdf.fx.print.PdfPrintDialog d = new org.icepdf.fx.print.PdfPrintDialog(stage, document,
                    view.getCurrentPageIndex());
            d.setPrinters(java.util.List.of(filePrinter), filePrinter);
            d.show();
            return d;
        });
        Thread.sleep(1500);  // preview thumbnail
        // as a user would: "Current page (4)", two copies.
        fx(() -> {
            javafx.scene.control.DialogPane pane = dialog.getDialogPane();
            for (javafx.scene.Node n : pane.lookupAll(".radio-button")) {
                if (n instanceof javafx.scene.control.RadioButton r && r.getText().startsWith("Current page")) r.fire();
            }
            ((javafx.scene.control.Spinner<?>) pane.lookup(".spinner")).increment(1);
        });
        Thread.sleep(800);
        WritableImage shot = onFx(() -> dialog.getDialogPane().snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(shot, null), "png", out.resolve("print-dialog.png").toFile());
        fx(() -> ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                dialog.getDialogPane().getButtonTypes().get(0))).fire());
        org.icepdf.fx.print.PrintSettings settings = onFx(dialog::getResult);
        check("dialog result: current page, 2 copies, the file printer", settings != null
                        && java.util.Arrays.equals(settings.getPages(), new int[]{3}) && settings.getCopies() == 2
                        && settings.getPrinter() == filePrinter,
                String.valueOf(settings));
        javafx.concurrent.Task<Void> task = onFx(() -> view.print(settings));
        long deadline = System.currentTimeMillis() + 60_000;
        while (!onFx(task::isDone) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        stream.close();
        String taskState = onFx(() -> task.getState() + " " + task.getException() + " / " + task.getMessage());
        check("print task succeeded", taskState.startsWith("SUCCEEDED"), taskState);
        String postscript = Files.readString(ps, java.nio.charset.StandardCharsets.ISO_8859_1);
        int sheets = postscript.split("%%Page:", -1).length - 1;
        boolean copiesRequested = postscript.contains("#copies") || postscript.contains("NumCopies") || sheets == 2;
        check("PostScript has the page, two copies", sheets >= 1 && copiesRequested,
                sheets + " pages, copies " + copiesRequested + ", " + (postscript.length() >> 10) + " KB");
        fx(() -> view.setDocument(null));
        document.dispose();

        // permissions: no printing at all, and low quality only.
        Document noPrint = new Document();
        noPrint.setFile(encryption.resolve("aes/perf_graphics_v9.0.pdf").toString());
        fx(() -> view.setDocument(noPrint));
        check("printing refused by the document", !onFx(view::isPrintAllowed), "");
        boolean threw = onFx(() -> {
            try {
                view.print(new org.icepdf.fx.print.PrintSettings());
                return false;
            } catch (IllegalStateException e) {
                return true;
            }
        });
        check("print() refuses", threw, "");
        fx(() -> view.setDocument(null));
        noPrint.dispose();

        Document lowQuality = new Document();
        lowQuality.setFile(encryption.resolve("aes/256encryption_contentextractionOK.pdf").toString());
        fx(() -> view.setDocument(lowQuality));
        Path lowPs = out.resolve("print-low.ps");
        java.io.OutputStream lowStream = Files.newOutputStream(lowPs);
        org.icepdf.fx.print.PrintSettings low = new org.icepdf.fx.print.PrintSettings();
        low.setPrinter(javax.print.StreamPrintServiceFactory.lookupStreamPrintServiceFactories(
                javax.print.DocFlavor.SERVICE_FORMATTED.PAGEABLE, "application/postscript")[0].getPrintService(lowStream));
        low.setPages(new int[]{0});
        javafx.concurrent.Task<Void> lowTask = onFx(() -> view.print(low));
        deadline = System.currentTimeMillis() + 60_000;
        while (!onFx(lowTask::isDone) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        lowStream.close();
        String lowText = Files.readString(lowPs, java.nio.charset.StandardCharsets.ISO_8859_1);
        check("low-quality-only document prints as an image", low.isLowResolution()
                        && onFx(lowTask::getState) == javafx.concurrent.Worker.State.SUCCEEDED && lowText.contains("colorimage"),
                onFx(lowTask::getState) + ", " + (lowText.length() >> 10) + " KB");
        fx(() -> view.setDocument(null));
        lowQuality.dispose();
    }

    /** A primary-button drag in the view's coordinates, delivered as JavaFX mouse events. */
    private void syntheticDrag(double[] from, double[] to) throws Exception {
        fx(() -> {
            javafx.geometry.Point2D start = view.localToScene(from[0], from[1]);
            javafx.scene.Node target = pickNode(start);
            int steps = 8;
            for (int i = 0; i <= steps + 1; i++) {
                double t = Math.min(1, i / (double) steps);
                double x = from[0] + (to[0] - from[0]) * t;
                double y = from[1] + (to[1] - from[1]) * t;
                javafx.geometry.Point2D scene = view.localToScene(x, y);
                javafx.geometry.Point2D screen = view.localToScreen(x, y);
                javafx.event.EventType<javafx.scene.input.MouseEvent> type = i == 0
                        ? javafx.scene.input.MouseEvent.MOUSE_PRESSED : i <= steps
                        ? javafx.scene.input.MouseEvent.MOUSE_DRAGGED : javafx.scene.input.MouseEvent.MOUSE_RELEASED;
                javafx.event.Event.fireEvent(target, new javafx.scene.input.MouseEvent(type, scene.getX(), scene.getY(),
                        screen.getX(), screen.getY(), javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false,
                        false, type != javafx.scene.input.MouseEvent.MOUSE_RELEASED, false, false, true, false, true,
                        new javafx.scene.input.PickResult(target, scene.getX(), scene.getY())));
            }
        });
        waitIdle(30_000);
    }

    /**
     * Signing: the signature tool draws a field, the application is told, the sign dialog is filled
     * in with the test keystore, the document is saved signed, reopened and verified.
     */
    private void checkSigning(Path source, Path keystore) throws Exception {
        System.out.println("signing checks:");
        Path copy = out.resolve("to-sign.pdf");
        Files.copy(source, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Document document = new Document();
        document.setFile(copy.toString());
        java.util.List<org.icepdf.fx.signature.SignatureStatus> clicked = new java.util.concurrent.CopyOnWriteArrayList<>();
        fx(() -> {
            view.setOnSignatureClicked(clicked::add);
            view.setFitMode(FitMode.PAGE);
            view.setDocument(document);
        });
        waitIdle(60_000);
        fx(() -> view.setToolMode(org.icepdf.fx.view.ToolMode.SIGNATURE));
        check("signature tool available", onFx(view::getToolMode) == org.icepdf.fx.view.ToolMode.SIGNATURE, "");
        double[] size = onFx(() -> new double[]{view.getWidth(), view.getHeight()});
        double[] from = {size[0] * 0.55, size[1] * 0.78};
        double[] to = {size[0] * 0.75, size[1] * 0.86};
        syntheticDrag(from, to);
        Thread.sleep(300);
        org.icepdf.fx.signature.SignatureStatus created = clicked.isEmpty() ? null : clicked.get(0);
        check("drawing a field tells the application", created != null && !created.isSigned(),
                clicked.size() + " calls" + (created != null ? ", " + created.fieldName() : ""));
        check("tool back to text select", onFx(view::getToolMode) == org.icepdf.fx.view.ToolMode.TEXT_SELECT, "");
        check("the empty field shows a sign-here badge", badgeCount() == 1, badgeCount() + " badges");
        if (created == null) {
            fx(() -> view.setDocument(null));
            document.dispose();
            return;
        }

        org.icepdf.fx.signature.SignDialog dialog = onFx(() -> {
            org.icepdf.fx.signature.SignDialog d = new org.icepdf.fx.signature.SignDialog(stage, document,
                    created.widget());
            d.show();
            return d;
        });
        Thread.sleep(300);
        fx(() -> {
            javafx.scene.control.DialogPane pane = dialog.getDialogPane();
            for (javafx.scene.Node n : pane.lookupAll(".text-field")) {
                if (n instanceof javafx.scene.control.TextField t && "Keystore file (.p12, .pfx)".equals(t.getPromptText())) {
                    t.setText(keystore.toAbsolutePath().toString());
                }
            }
            ((javafx.scene.control.PasswordField) pane.lookup(".password-field")).setText("changeit");
            for (javafx.scene.Node n : pane.lookupAll(".button")) {
                if (n instanceof javafx.scene.control.Button b && "Open".equals(b.getText())) b.fire();
            }
        });
        Thread.sleep(500);
        int certificates = onFx(() -> dialog.getDialogPane().lookupAll(".list-view").stream()
                .map(n -> ((javafx.scene.control.ListView<?>) n).getItems())
                .filter(items -> !items.isEmpty() && items.get(0) instanceof org.icepdf.fx.signature.DocumentSigning.KeyEntry)
                .mapToInt(java.util.List::size).sum());
        check("the keystore lists its certificate", certificates == 1, certificates + " certificates");
        WritableImage shot = onFx(() -> dialog.getDialogPane().snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(shot, null), "png", out.resolve("sign-dialog.png").toFile());
        fx(() -> ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                dialog.getDialogPane().getButtonTypes().get(0))).fire());
        org.icepdf.fx.signature.SignDialog.Result result = onFx(dialog::getResult);
        check("sign returns the choices", result != null && result.request().name() != null,
                result == null ? "null" : result.request().toString());
        if (result == null) return;

        Path signed = out.resolve("signed.pdf");
        org.icepdf.fx.signature.DocumentSigning.prepare(created.widget(), result.signer(), result.request(),
                result.appearance());
        org.icepdf.fx.signature.DocumentSigning.saveSigned(document, signed);
        fx(() -> view.setDocument(null));
        document.dispose();

        Document reopened = new Document();
        reopened.setFile(signed.toString());
        fx(() -> view.setOnSignatureClicked(null));
        java.util.List<org.icepdf.fx.signature.SignatureStatus> statuses = openAndVerify(reopened);
        org.icepdf.fx.signature.SignatureStatus status = statuses.stream()
                .filter(org.icepdf.fx.signature.SignatureStatus::isSigned).findFirst().orElse(null);
        check("the saved file verifies intact", status != null && !status.signedDataModified()
                        && !status.modifiedAfterSigning(),
                status == null ? statuses.toString() : status.verdict() + " " + status.signerName());
        check("a badge on the new signature", badgeCount() == 1, badgeCount() + " badges");
        WritableImage page = onFx(() -> view.snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(page, null), "png", out.resolve("signed-page.png").toFile());
        fx(() -> view.setDocument(null));
        reopened.dispose();
    }

    /** A primary click at a point in the view's coordinates, delivered to the node under it. */
    private void syntheticClick(double[] p) throws Exception {
        fx(() -> {
            javafx.geometry.Point2D scene = view.localToScene(p[0], p[1]);
            javafx.geometry.Point2D screen = view.localToScreen(p[0], p[1]);
            javafx.scene.Node target = pickNode(scene);
            javafx.scene.input.PickResult pick = new javafx.scene.input.PickResult(target, scene.getX(), scene.getY());
            for (javafx.event.EventType<javafx.scene.input.MouseEvent> type : java.util.List.of(
                    javafx.scene.input.MouseEvent.MOUSE_PRESSED, javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED)) {
                javafx.scene.input.MouseEvent event = new javafx.scene.input.MouseEvent(type, scene.getX(), scene.getY(),
                        screen.getX(), screen.getY(), javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false,
                        false, type != javafx.scene.input.MouseEvent.MOUSE_RELEASED
                        && type != javafx.scene.input.MouseEvent.MOUSE_CLICKED, false, false, true, false, true, pick);
                javafx.event.Event.fireEvent(target, event);
            }
        });
        waitIdle(30_000);
    }

    /** The deepest node under a scene point (what a real click would target). */
    private javafx.scene.Node pickNode(javafx.geometry.Point2D scene) {
        javafx.scene.Node best = view;
        java.util.Deque<javafx.scene.Node> stack = new java.util.ArrayDeque<>(java.util.List.of(view));
        while (!stack.isEmpty()) {
            javafx.scene.Node n = stack.pop();
            if (!n.isVisible() || n.isMouseTransparent()) continue;
            javafx.geometry.Point2D local = n.sceneToLocal(scene);
            if (local == null || !n.contains(local)) continue;
            best = n;
            if (n instanceof javafx.scene.Parent parent) {
                for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) stack.push(child);
            }
        }
        return best;
    }

    /** Opens a document in the view and waits for its signature check. */
    private java.util.List<org.icepdf.fx.signature.SignatureStatus> openAndVerify(Document document) throws Exception {
        fx(() -> {
            view.setFitMode(FitMode.PAGE);
            view.setDocument(document);
        });
        long deadline = System.currentTimeMillis() + 120_000;
        Thread.sleep(200);
        while (onFx(view::isVerifyingSignatures) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        waitIdle(60_000);
        return onFx(() -> java.util.List.copyOf(view.getSignatures()));
    }

    private int badgeCount() throws Exception {
        return onFx(() -> view.lookupAll(".pdf-signature-badge").stream().filter(javafx.scene.Node::isVisible)
                .toList().size());
    }

    /**
     * Signatures: statuses and badges after opening, a click on a signed field reaching the
     * application's handler, the default properties dialog, a broken signature, and turning the check
     * on open off.
     */
    private void checkSignatures(Path corpus) throws Exception {
        System.out.println("signature checks:");

        Document signedDoc = new Document();
        signedDoc.setFile(corpus.resolve("sig-doc.pdf").toString());
        java.util.List<org.icepdf.fx.signature.SignatureStatus> statuses = openAndVerify(signedDoc);
        org.icepdf.fx.signature.SignatureStatus first = statuses.stream()
                .filter(org.icepdf.fx.signature.SignatureStatus::isSigned).findFirst().orElse(null);
        check("signatures checked on open", first != null
                        && first.verdict() == org.icepdf.fx.signature.SignatureStatus.Verdict.UNKNOWN
                        && !first.signedDataModified(),
                statuses.stream().map(st -> st.verdict() + " " + st.signerName()).toList().toString());
        fx(() -> view.revealSignature(first));
        waitIdle(30_000);
        check("a badge on the signed field", badgeCount() == 1, badgeCount() + " badges");
        WritableImage shot = onFx(() -> view.snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(shot, null), "png", out.resolve("signature-badge.png").toFile());

        // the application's handler gets the click.
        java.util.List<org.icepdf.fx.signature.SignatureStatus> clicked = new java.util.concurrent.CopyOnWriteArrayList<>();
        fx(() -> view.setOnSignatureClicked(clicked::add));
        double[] at = viewPointIn(first.pageIndex(), first.widget().getUserSpaceRectangle());
        // delivered as JavaFX mouse events: screen input (Robot) can be covered by other windows.
        if (at != null) syntheticClick(at);
        Thread.sleep(400);
        check("clicking the field calls onSignatureClicked", clicked.size() == 1 && clicked.get(0) == first,
                at == null ? "field not on screen" : clicked.size() + " calls");

        // no handler: the properties dialog opens.
        fx(() -> view.setOnSignatureClicked(null));
        if (at != null) syntheticClick(at);
        javafx.scene.control.DialogPane pane = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (pane == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            pane = onFx(() -> {
                for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                    if (w.isShowing() && w.getScene() != null && w.getScene().getRoot()
                            instanceof javafx.scene.control.DialogPane p && p.getStyleClass().contains("pdf-signature-dialog")) {
                        return p;
                    }
                }
                return null;
            });
        }
        check("by default a click opens the properties dialog", pane != null, "");
        if (pane != null) {
            javafx.scene.control.DialogPane shown = pane;
            Thread.sleep(300);
            WritableImage dialog = onFx(() -> shown.snapshot(null, null));
            ImageIO.write(SwingFXUtils.fromFXImage(dialog, null), "png", out.resolve("signature-dialog.png").toFile());
            fx(() -> ((javafx.scene.control.Button) shown.lookupButton(javafx.scene.control.ButtonType.CLOSE)).fire());
        }
        fx(() -> view.setDocument(null));
        signedDoc.dispose();

        Document broken = new Document();
        broken.setFile(corpus.resolve("sf-1700_time_error.pdf").toString());
        statuses = openAndVerify(broken);
        check("an altered document reads invalid", statuses.stream().anyMatch(st ->
                        st.verdict() == org.icepdf.fx.signature.SignatureStatus.Verdict.INVALID && st.signedDataModified()),
                statuses.stream().map(st -> st.verdict().toString()).toList().toString());
        fx(() -> view.setDocument(null));
        broken.dispose();

        Document many = new Document();
        many.setFile(corpus.resolve("Seller signed offer and Seller Counter Offer.pdf.pdf").toString());
        statuses = openAndVerify(many);
        check("several signatures, later revisions noted", statuses.stream().filter(
                        org.icepdf.fx.signature.SignatureStatus::isSigned).count() == 4
                        && statuses.stream().anyMatch(org.icepdf.fx.signature.SignatureStatus::modifiedAfterSigning),
                statuses.stream().map(st -> st.verdict() + (st.modifiedAfterSigning() ? "+later" : "")).toList().toString());
        fx(() -> view.setDocument(null));

        fx(() -> view.setVerifySignaturesOnOpen(false));
        statuses = openAndVerify(many);
        check("no check when verifySignaturesOnOpen is off", statuses.isEmpty() && badgeCount() == 0,
                statuses.size() + " statuses");
        java.util.List<org.icepdf.fx.signature.SignatureStatus> later = onFx(view::verifySignatures).get(120, TimeUnit.SECONDS);
        check("verifySignatures() on demand", later.size() == onFx(() -> view.getSignatures().size()) && !later.isEmpty(),
                later.size() + " statuses");
        fx(() -> {
            view.setVerifySignaturesOnOpen(true);
            view.setDocument(null);
        });
        many.dispose();
    }

    private static Object skinField(Object skin, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = skin.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(skin);
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

    /** On a stuck act: the skin's render gates, read reflectively (diagnostic). */
    private void dumpSkinState() throws Exception {
        System.out.println("STALL " + onFx(() -> {
            StringBuilder out = new StringBuilder();
            Object skin = view.getSkin();
            for (String name : new String[]{"zoomSettling", "zoomSettle", "refreshQueued", "layoutZoom", "viewportW",
                    "viewportH", "scrollX", "scrollY"}) {
                try {
                    java.lang.reflect.Field f = skin.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(skin);
                    if (v instanceof javafx.animation.Animation a) v = a.getStatus() + " t=" + a.getCurrentTime();
                    out.append(name).append('=').append(v).append("  ");
                } catch (ReflectiveOperationException e) {
                    out.append(name).append("=?  ");
                }
            }
            return out.toString();
        }));
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
