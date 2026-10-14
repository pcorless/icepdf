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

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.view.PdfView;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What the embedding smokes share: the FX thread, checks, snapshots, synthetic input, and a way to
 * answer a modal dialog the application shows with {@code showAndWait} (scripted runs must never be
 * left waiting on one).
 */
final class SmokeSupport {

    private final Path out;
    private int failures;

    SmokeSupport(Path out) {
        this.out = out;
    }

    // FontBox warns about every font with a format-14 cmap; keep the output to the checks.
    private static final java.util.logging.Logger FONTBOX = java.util.logging.Logger.getLogger("org.apache.fontbox");

    static void startFx() throws InterruptedException {
        FONTBOX.setLevel(java.util.logging.Level.SEVERE);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);
    }

    int failures() {
        return failures;
    }

    void check(String what, boolean ok, Object detail) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what + (ok || detail == null ? "" : "  [" + detail + "]"));
        if (!ok) failures++;
    }

    void check(String what, boolean ok) {
        check(what, ok, null);
    }

    /** Ends the run: a summary line, then the exit code (0 when everything passed). */
    void finish(String name) {
        System.out.println(failures == 0 ? name + ": all passed" : name + ": " + failures + " FAILED");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    static <T> T onFx(Callable<T> call) throws Exception {
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

    static void fx(Runnable run) throws Exception {
        onFx(() -> {
            run.run();
            return null;
        });
    }

    /** Waits for the session's file to show and the view to finish painting it. */
    static void waitOpened(DocumentSession session, Path file) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        Thread.sleep(100);
        while (System.currentTimeMillis() < deadline && (onFx(session::isLoading)
                || !file.toAbsolutePath().equals(onFx(session::getFile)))) {
            Thread.sleep(50);
        }
        waitIdle(session.getView());
    }

    /** Waits until the view has nothing left to render. */
    static void waitIdle(PdfView view) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        Thread.sleep(150);
        while (System.currentTimeMillis() < deadline && onFx(view::isRendering)) Thread.sleep(50);
        Thread.sleep(100);
    }

    void snapshot(Node node, String name) throws Exception {
        var image = onFx(() -> node.snapshot(null, null));
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out.resolve(name).toFile());
    }

    /** A key press and release delivered to whatever has the focus, as the keyboard would. */
    static void key(Stage stage, KeyCode code, boolean shortcut) throws Exception {
        fx(() -> {
            Node target = stage.getScene().getFocusOwner();
            Object to = target != null ? target : stage.getScene();
            for (var type : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
                KeyEvent event = new KeyEvent(type, "", "", code, false, shortcut && !isMac(), false, shortcut && isMac());
                if (to instanceof Node node) Event.fireEvent(node, event);
                else Event.fireEvent(stage.getScene(), event);
            }
        });
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** A primary-button drag in the view's coordinates, delivered to the node under the start point. */
    static void drag(PdfView view, double[] from, double[] to) throws Exception {
        fx(() -> dragNow(view, from, to));
        waitIdle(view);
    }

    /** {@link #drag} without waiting: for a drag whose release shows a modal dialog. */
    static void dragLater(PdfView view, double[] from, double[] to) {
        Platform.runLater(() -> dragNow(view, from, to));
    }

    private static void dragNow(PdfView view, double[] from, double[] to) {
        Point2D start = view.localToScene(from[0], from[1]);
        Node target = pick(view, start);
        int steps = 8;
        for (int i = 0; i <= steps + 1; i++) {
            double t = Math.min(1, i / (double) steps);
            double x = from[0] + (to[0] - from[0]) * t;
            double y = from[1] + (to[1] - from[1]) * t;
            Point2D scene = view.localToScene(x, y);
            Point2D screen = view.localToScreen(x, y);
            var type = i == 0 ? MouseEvent.MOUSE_PRESSED : i <= steps ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_RELEASED;
            Event.fireEvent(target, new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(),
                    MouseButton.PRIMARY, 1, false, false, false, false, type != MouseEvent.MOUSE_RELEASED,
                    false, false, true, false, true, new PickResult(target, scene.getX(), scene.getY())));
        }
    }

    /** The deepest visible node under a scene point (what a real click would target). */
    static Node pick(Node root, Point2D scene) {
        Node best = root;
        Deque<Node> stack = new ArrayDeque<>(List.of(root));
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (!n.isVisible() || n.isMouseTransparent()) continue;
            Point2D local = n.sceneToLocal(scene);
            if (local == null || !n.contains(local)) continue;
            best = n;
            if (n instanceof Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) stack.push(child);
            }
        }
        return best;
    }

    /** A node of a type anywhere under {@code root}, or null. */
    static <T> T find(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Answers the next dialog whose pane matches, whenever it shows - also from inside the nested
     * event loop of a {@code showAndWait} the application is blocked in.  Call on any thread, before
     * the action that shows the dialog.
     *
     * @return completes with true once answered, false if no such dialog showed in time
     */
    static CompletableFuture<Boolean> answerDialog(Predicate<DialogPane> which, Consumer<DialogPane> answer) {
        CompletableFuture<Boolean> answered = new CompletableFuture<>();
        Platform.runLater(() -> {
            Timeline poll = new Timeline();
            long deadline = System.currentTimeMillis() + 20_000;
            poll.getKeyFrames().add(new KeyFrame(Duration.millis(100), e -> {
                for (Window window : List.copyOf(Window.getWindows())) {
                    DialogPane pane = window.isShowing() && window.getScene() != null
                            ? find(window.getScene().getRoot(), DialogPane.class) : null;
                    if (pane != null && which.test(pane)) {
                        poll.stop();
                        try {
                            answer.accept(pane);
                            answered.complete(true);
                        } catch (Throwable t) {
                            answered.completeExceptionally(t);
                        }
                        return;
                    }
                }
                if (System.currentTimeMillis() > deadline) {
                    poll.stop();
                    answered.complete(false);
                }
            }));
            poll.setCycleCount(Timeline.INDEFINITE);
            poll.play();
        });
        return answered;
    }

    static boolean await(CompletableFuture<Boolean> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }
}
