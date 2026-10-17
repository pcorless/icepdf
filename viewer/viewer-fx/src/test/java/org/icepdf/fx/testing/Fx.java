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
package org.icepdf.fx.testing;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Assumptions;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * JavaFX for unit tests, without a window on screen: the toolkit is started once, scenes are built
 * but never shown, and input is fired at nodes as events (no Robot, so no focus is taken and
 * nothing waits on the desktop).  A machine with no display skips the test instead of failing.
 */
public final class Fx {

    private static volatile Boolean started;

    private Fx() {
    }

    /** Starts the toolkit (once); skips the calling test when there is no display to start it on. */
    public static synchronized void start() {
        if (started == null) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(latch::countDown);
                started = latch.await(20, TimeUnit.SECONDS);
                Platform.setImplicitExit(false);
            } catch (IllegalStateException e) {
                started = true; // already running (another harness started it)
            } catch (RuntimeException | InterruptedException | UnsatisfiedLinkError e) {
                started = false;
            }
        }
        Assumptions.assumeTrue(started, "no JavaFX toolkit (no display)");
    }

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    /** Runs on the FX thread and waits; what it throws is thrown here. */
    public static <T> T call(Callable<T> work) {
        if (Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException r) throw r;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException(cause);
        } catch (InterruptedException | TimeoutException e) {
            throw new IllegalStateException("FX thread did not answer", e);
        }
    }

    public static void run(Action work) {
        call(() -> {
            work.run();
            return null;
        });
    }

    /** Waits (off the FX thread) until {@code condition}, read on the FX thread, holds. */
    public static void waitFor(String what, BooleanSupplier condition) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!call(condition::getAsBoolean)) {
            if (System.nanoTime() > end) throw new AssertionError("timed out waiting for " + what);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for " + what);
            }
        }
    }

    /** Lets everything already queued on the FX thread (runLater) run. */
    public static void settle() {
        call(() -> null);
        call(() -> null);
    }

    /** A scene for {@code root}, sized, with CSS and layout applied (FX thread). */
    public static Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        layout(root);
        return scene;
    }

    /** What a pulse would do for a scene that isn't shown: CSS, then layout. */
    public static void layout(Parent root) {
        root.applyCss();
        root.layout();
    }

    /** A key press at {@code target}, as the scene would deliver it (FX thread). */
    public static void press(Node target, KeyCode code, boolean shortcut) {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false,
                shortcut && !mac, false, shortcut && mac));
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false,
                shortcut && !mac, false, shortcut && mac));
    }

    /** Typing {@code text} at {@code target}, a character at a time (FX thread). */
    public static void type(Node target, String text) {
        for (char c : text.toCharArray()) {
            String s = String.valueOf(c);
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, s, "", KeyCode.UNDEFINED, false, false, false, false));
        }
    }
}
