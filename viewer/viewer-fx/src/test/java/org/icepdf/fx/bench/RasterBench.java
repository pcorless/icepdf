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

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.jfree.fx.FXGraphics2D;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Measures the candidate ways of getting an ICEpdf Java2D page render onto a JavaFX scene, so the
 * JavaFX page view is built on numbers rather than folklore.  See JAVAFX-PAGEVIEW-PLAN.md step 1.
 * <p>
 * Each case renders a viewport-sized device-pixel region of a page (the same region the Swing
 * viewer's PageImageCaptureTask would paint) at a given zoom and output scale, by each path:
 * <ul>
 *     <li><b>A</b> Java2D into a BufferedImage, {@code SwingFXUtils.toFXImage} copy, ImageView.</li>
 *     <li><b>B</b> Java2D into a BufferedImage whose int[] is shared with a {@link PixelBuffer}
 *     (zero copy), ImageView.</li>
 *     <li><b>BT</b> as B, but the region is painted as 512px tiles, one page.paint per tile, to
 *     price the display-list traversal that region coalescing avoids.</li>
 *     <li><b>C</b> FXGraphics2D onto a Canvas (JavaFX draw ops).</li>
 * </ul>
 * Paint runs on a worker; "fx" is time spent on the FX thread handing the result to the scene;
 * "e2e" is from handoff until two pulses have completed (texture upload / canvas replay), run
 * with vsync off so it isn't quantised to the frame rate.
 * <p>
 * Usage (gradle): {@code ./gradlew :viewer:viewer-fx:bench -PbenchArgs="--runs=3;file.pdf#2"},
 * arguments separated by ';' because corpus file names contain spaces.  With no case arguments a
 * default set from ~/dev/pdf-qa is used.
 */
public final class RasterBench {

    private static final int TILE = 512;

    private record Case(Path file, int pageIndex) {
        String label() {
            return file.getFileName() + "#" + (pageIndex + 1);
        }
    }

    private record Result(String status, double paintMs, double convertMs, double fxMs, double e2eMs,
                          double allocMb, int width, int height) {
        static Result failed(String why) {
            return new Result(why, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "raster-bench-worker");
        t.setDaemon(true);
        return t;
    });
    private StackPane root;

    private int viewportW = 1600;
    private int viewportH = 1000;
    private float[] zooms = {1f, 4f, 16f, 40f};
    private double[] scales = {1.0, 2.0};
    private Set<String> paths = new LinkedHashSet<>(List.of("A", "B", "BT", "C"));
    private int runs = 3;
    private Path outDir = Paths.get("build", "raster-bench");
    private final List<Case> cases = new ArrayList<>();

    // strong refs, or the level settings are lost when the loggers are collected.
    private static final java.util.logging.Logger FONTBOX = java.util.logging.Logger.getLogger("org.apache.fontbox");
    private static final java.util.logging.Logger ICEPDF = java.util.logging.Logger.getLogger("org.icepdf");

    public static void main(String[] args) throws Exception {
        FONTBOX.setLevel(java.util.logging.Level.SEVERE);
        ICEPDF.setLevel(java.util.logging.Level.SEVERE);
        RasterBench bench = new RasterBench();
        bench.parse(args);
        bench.run();
        System.exit(0);
    }

    private void parse(String[] args) {
        List<String> all = new ArrayList<>();
        for (String a : args) all.addAll(Arrays.asList(a.split(";")));
        for (String a : all) {
            a = a.trim();
            if (a.isEmpty()) continue;
            if (a.startsWith("--viewport=")) {
                String[] wh = a.substring(11).split("x");
                viewportW = Integer.parseInt(wh[0]);
                viewportH = Integer.parseInt(wh[1]);
            } else if (a.startsWith("--zooms=")) {
                zooms = parseFloats(a.substring(8));
            } else if (a.startsWith("--scales=")) {
                float[] f = parseFloats(a.substring(9));
                scales = new double[f.length];
                for (int i = 0; i < f.length; i++) scales[i] = f[i];
            } else if (a.startsWith("--paths=")) {
                paths = new LinkedHashSet<>(Arrays.asList(a.substring(8).split(",")));
            } else if (a.startsWith("--runs=")) {
                runs = Integer.parseInt(a.substring(7));
            } else if (a.startsWith("--out=")) {
                outDir = Paths.get(a.substring(6));
            } else {
                int hash = a.lastIndexOf('#');
                Path file = Paths.get(hash > 0 ? a.substring(0, hash) : a);
                int page = hash > 0 ? Integer.parseInt(a.substring(hash + 1)) - 1 : 0;
                cases.add(new Case(file, page));
            }
        }
        if (cases.isEmpty()) {
            Path qa = Paths.get(System.getProperty("user.home"), "dev", "pdf-qa");
            cases.add(new Case(qa.resolve("PDF32000_2008.pdf"), 99));
            cases.add(new Case(qa.resolve("metrics/content-parser/203502_2016-05-28_04-23_CanmoreAlberta.pdf"), 0));
            cases.add(new Case(qa.resolve("graphics/blending/Java Magazine SeptemberOctober 2016.pdf"), 2));
            cases.add(new Case(qa.resolve("graphics/blending/Java Magazine SeptemberOctober 2016.pdf"), 6));
            cases.add(new Case(qa.resolve("metrics/full-monty/Run & Gun.pdf"), 2));
            cases.add(new Case(qa.resolve("metrics/full-monty/1.pdf"), 0));
        }
    }

    private static float[] parseFloats(String csv) {
        String[] parts = csv.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i]);
        return out;
    }

    private void run() throws Exception {
        Files.createDirectories(outDir);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            root = new StackPane();
            Stage stage = new Stage();
            stage.setTitle("RasterBench");
            stage.setScene(new Scene(root, viewportW, viewportH));
            stage.show();
            started.countDown();
        });
        started.await();

        System.out.println("JavaFX " + System.getProperty("javafx.runtime.version") + ", Java "
                + System.getProperty("java.version") + ", prism " + System.getProperty("prism.order", "default")
                + ", max heap " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
        System.out.println("heap IntBuffer PixelBuffer supported: " + checkHeapPixelBuffer());

        String header = String.format("%-48s %5s %6s %-3s %-10s %11s %9s %9s %8s %8s %9s",
                "case", "zoom", "scale", "pth", "status", "region", "paint", "convert", "fx", "e2e", "allocMB");
        System.out.println(header);
        try (PrintWriter csv = new PrintWriter(outDir.resolve("results.csv").toFile())) {
            csv.println("case,zoom,scale,path,status,width,height,paintMs,convertMs,fxMs,e2eMs,allocMb");
            for (Case c : cases) {
                runCase(c, csv);
            }
        }
        System.out.println("results: " + outDir.toAbsolutePath().resolve("results.csv"));
    }

    private boolean checkHeapPixelBuffer() {
        try {
            int[] data = new int[16];
            PixelBuffer<IntBuffer> pb = new PixelBuffer<>(4, 4, IntBuffer.wrap(data),
                    PixelFormat.getIntArgbPreInstance());
            new WritableImage(pb);
            return true;
        } catch (Throwable t) {
            System.out.println("  heap PixelBuffer failed: " + t);
            return false;
        }
    }

    private void runCase(Case c, PrintWriter csv) throws Exception {
        if (!Files.exists(c.file())) {
            System.out.println(c.label() + " MISSING, skipped");
            return;
        }
        Document document = new Document();
        document.setFile(c.file().toString());
        try {
            Page page = document.getPageTree().getPage(c.pageIndex());
            long t0 = System.nanoTime();
            page.init();
            System.out.printf("%s init %.1fms%n", c.label(), ms(t0));
            for (double scale : scales) {
                for (float zoom : zooms) {
                    for (String path : paths) {
                        Result r = measure(c, page, path, zoom, scale);
                        String region = r.width() + "x" + r.height();
                        System.out.printf("%-48s %5.0f %6.2f %-3s %-10s %11s %9.1f %9.1f %8.2f %8.1f %9.0f%n",
                                truncate(c.label(), 48), zoom * 100, scale, path, r.status(), region,
                                r.paintMs(), r.convertMs(), r.fxMs(), r.e2eMs(), r.allocMb());
                        csv.printf(Locale.ROOT, "\"%s\",%.0f,%.2f,%s,%s,%d,%d,%.2f,%.2f,%.3f,%.2f,%.0f%n",
                                c.label(), zoom * 100, scale, path, r.status(), r.width(), r.height(),
                                r.paintMs(), r.convertMs(), r.fxMs(), r.e2eMs(), r.allocMb());
                        csv.flush();
                    }
                }
            }
        } finally {
            document.dispose();
        }
    }

    /**
     * Runs one path {@code runs} times after a warm-up and reports the median of each timing.
     */
    private Result measure(Case c, Page page, String path, float zoom, double scale) throws Exception {
        PDimension size = page.getSize(Page.BOUNDARY_CROPBOX, 0f, zoom);
        long pageW = (long) Math.ceil(size.getWidth() * scale);
        long pageH = (long) Math.ceil(size.getHeight() * scale);
        int w = (int) Math.min(pageW, (long) (viewportW * scale));
        int h = (int) Math.min(pageH, (long) (viewportH * scale));
        // centre the viewport on the page, as if the user had scrolled to the middle.
        int x = (int) Math.max(0, (pageW - w) / 2);
        int y = (int) Math.max(0, (pageH - h) / 2);

        List<Result> results = new ArrayList<>();
        for (int i = 0; i <= runs; i++) {
            Result r;
            try {
                r = switch (path) {
                    case "A" -> pathA(page, x, y, w, h, zoom, scale);
                    case "B" -> pathB(page, x, y, w, h, zoom, scale, false);
                    case "BT" -> pathB(page, x, y, w, h, zoom, scale, true);
                    case "C" -> pathC(page, x, y, w, h, zoom, scale);
                    default -> Result.failed("bad-path");
                };
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof OutOfMemoryError) return Result.failed("OOM");
                System.out.println("  " + path + " failed: " + cause);
                return Result.failed("FAIL");
            }
            // warm-up run is discarded, but its output is the one written for the visual diff.
            if (i == 0) {
                saveSample(c, path, zoom, scale);
                continue;
            }
            results.add(r);
        }
        return median(results);
    }

    private Object lastSample;

    private Result pathA(Page page, int x, int y, int w, int h, float zoom, double scale) throws Exception {
        double[] paintConvert = worker.submit(() -> {
            long a0 = allocated();
            long t0 = System.nanoTime();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = image.createGraphics();
            paintRegion(g, page, x, y, w, h, zoom, scale);
            g.dispose();
            double paint = ms(t0);
            long t1 = System.nanoTime();
            WritableImage fx = SwingFXUtils.toFXImage(image, null);
            double convert = ms(t1);
            lastSample = fx;
            return new double[]{paint, convert, allocMb(a0)};
        }).get();
        WritableImage fx = (WritableImage) lastSample;
        double[] fxE2e = handoff(() -> imageView(fx, w, h, scale));
        return new Result("ok", paintConvert[0], paintConvert[1], fxE2e[0], fxE2e[1], paintConvert[2], w, h);
    }

    private Result pathB(Page page, int x, int y, int w, int h, float zoom, double scale, boolean tiled)
            throws Exception {
        double[] paintAlloc = worker.submit(() -> {
            long a0 = allocated();
            long t0 = System.nanoTime();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            if (tiled) {
                for (int ty = 0; ty < h; ty += TILE) {
                    for (int tx = 0; tx < w; tx += TILE) {
                        Graphics2D g = image.createGraphics();
                        g.translate(tx, ty);
                        paintRegion(g, page, x + tx, y + ty, Math.min(TILE, w - tx), Math.min(TILE, h - ty),
                                zoom, scale);
                        g.dispose();
                    }
                }
            } else {
                Graphics2D g = image.createGraphics();
                paintRegion(g, page, x, y, w, h, zoom, scale);
                g.dispose();
            }
            lastSample = image;
            return new double[]{ms(t0), allocMb(a0)};
        }).get();
        BufferedImage image = (BufferedImage) lastSample;
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(w, h, IntBuffer.wrap(data),
                PixelFormat.getIntArgbPreInstance());
        WritableImage fx = new WritableImage(pixelBuffer);
        double[] fxE2e = handoff(() -> {
            // a recycled buffer must be flagged dirty; done here so its cost is counted.
            pixelBuffer.updateBuffer(pb -> null);
            return imageView(fx, w, h, scale);
        });
        return new Result("ok", paintAlloc[0], 0, fxE2e[0], fxE2e[1], paintAlloc[1], w, h);
    }

    private Result pathC(Page page, int x, int y, int w, int h, float zoom, double scale) throws Exception {
        // a Canvas that isn't in a scene may be drawn on from any thread.
        Canvas canvas = new Canvas(w, h);
        double[] paintAlloc = worker.submit(() -> {
            long a0 = allocated();
            long t0 = System.nanoTime();
            FXGraphics2D g = new FXGraphics2D(canvas.getGraphicsContext2D());
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            paintRegion(g, page, x, y, w, h, zoom, scale);
            g.dispose();
            return new double[]{ms(t0), allocMb(a0)};
        }).get();
        lastSample = canvas;
        double[] fxE2e = handoff(() -> canvas);
        return new Result("ok", paintAlloc[0], 0, fxE2e[0], fxE2e[1], paintAlloc[1], w, h);
    }

    /**
     * The same region set-up as AbstractPageViewComponent.PageImageCaptureTask: device-space clip,
     * translate to the region origin, scale to device pixels, then page.paint at the zoom.
     */
    private static void paintRegion(Graphics2D g, Page page, int x, int y, int w, int h, float zoom, double scale)
            throws InterruptedException {
        g.setClip(0, 0, w, h);
        g.translate(-x, -y);
        g.scale(scale, scale);
        page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0f, zoom, true, false);
    }

    private static Node imageView(WritableImage image, int w, int h, double scale) {
        ImageView view = new ImageView(image);
        view.setFitWidth(w / scale);
        view.setFitHeight(h / scale);
        return view;
    }

    /**
     * Swaps the node into the scene on the FX thread, returning {FX-thread ms, ms until two pulses
     * later}.  The second pulse can't start until the first pulse's render finished, so it bounds
     * the texture upload / canvas replay.
     */
    private double[] handoff(Supplier<Node> nodeSupplier) throws Exception {
        CompletableFuture<double[]> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            long t0 = System.nanoTime();
            root.getChildren().setAll(nodeSupplier.get());
            double fx = ms(t0);
            new AnimationTimer() {
                int pulses;

                @Override
                public void handle(long now) {
                    if (++pulses == 2) {
                        stop();
                        done.complete(new double[]{fx, ms(t0)});
                    }
                }
            }.start();
        });
        return done.get(60, TimeUnit.SECONDS);
    }

    private void saveSample(Case c, String path, float zoom, double scale) {
        String name = String.format("%s_z%.0f_s%.1f_%s.png",
                c.label().replaceAll("[^A-Za-z0-9#._-]", "_"), zoom * 100, scale, path);
        File out = outDir.resolve(name).toFile();
        try {
            if (lastSample instanceof BufferedImage image) {
                ImageIO.write(image, "png", out);
            } else if (lastSample instanceof WritableImage image) {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            } else if (lastSample instanceof Canvas canvas) {
                CompletableFuture<WritableImage> snap = new CompletableFuture<>();
                Platform.runLater(() -> snap.complete(canvas.snapshot(null, null)));
                ImageIO.write(SwingFXUtils.fromFXImage(snap.get(60, TimeUnit.SECONDS), null), "png", out);
            }
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            System.out.println("  sample not saved: " + e);
        }
    }

    private static Result median(List<Result> results) {
        if (results.isEmpty()) return Result.failed("no-runs");
        Result first = results.get(0);
        return new Result(first.status(),
                median(results, Result::paintMs), median(results, Result::convertMs),
                median(results, Result::fxMs), median(results, Result::e2eMs),
                results.stream().mapToDouble(Result::allocMb).max().orElse(Double.NaN),
                first.width(), first.height());
    }

    private static double median(List<Result> results, java.util.function.ToDoubleFunction<Result> f) {
        double[] v = results.stream().mapToDouble(f).sorted().toArray();
        return v[v.length / 2];
    }

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    /**
     * Bytes allocated so far by the calling thread: the transient memory a paint path churns
     * through (buffers, decoded images, intermediate group/mask offscreens).
     */
    private static long allocated() {
        return THREADS.getCurrentThreadAllocatedBytes();
    }

    private static double allocMb(long startBytes) {
        return (allocated() - startBytes) / (1024.0 * 1024.0);
    }

    private static double ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
