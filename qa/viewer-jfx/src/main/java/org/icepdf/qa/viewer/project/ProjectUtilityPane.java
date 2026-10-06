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
package org.icepdf.qa.viewer.project;

import javafx.animation.AnimationTimer;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import org.icepdf.qa.viewer.common.Mediator;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Contains a simple console view that takes System.out as a source.  Writes are buffered off the writing thread and
 * drained to the TextArea in batches on the JavaFX application thread, and the TextArea is capped to a rolling window
 * of the most recent output.  This keeps a busy test run from flooding the FX event queue or growing the console
 * without bound.
 */
public class ProjectUtilityPane extends TabPane {

    /**
     * Maximum number of characters retained in the console.  Older text is trimmed from the front once this is
     * exceeded, giving a rolling window of recent output.
     */
    private static final int MAX_CONSOLE_CHARS = 200_000;

    /**
     * Minimum time between TextArea updates, in nanoseconds.  Buffered output is coalesced into a single append per
     * interval rather than one update per byte.
     */
    private static final long FLUSH_INTERVAL_NANOS = 100_000_000L; // 100ms

    private final TextArea consoleTextArea;

    public ProjectUtilityPane(Mediator mediator) {
        super();
        setSide(Side.TOP);

        Tab consoleTab = new Tab("Console");
        consoleTab.setClosable(false);

        consoleTextArea = new TextArea();
        PrintStream printStream = new PrintStream(new Console(consoleTextArea), true);

        System.setOut(printStream);
//        System.setErr(printStream);
        System.out.println("Console logger initialized");

        consoleTab.setContent(consoleTextArea);
        getTabs().addAll(consoleTab);
    }

    public void clearConsole() {
        consoleTextArea.clear();
    }

    /**
     * OutputStream that buffers writes from any thread and periodically flushes them to a TextArea on the FX
     * application thread, capping the total length to {@link #MAX_CONSOLE_CHARS}.
     */
    public static class Console extends OutputStream {
        private final TextArea console;
        private final StringBuilder buffer = new StringBuilder();
        private long lastFlush;

        public Console(TextArea console) {
            this.console = console;
            // AnimationTimer.handle() runs on the FX application thread, so the drain can touch the TextArea directly.
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (now - lastFlush < FLUSH_INTERVAL_NANOS) {
                        return;
                    }
                    lastFlush = now;
                    flushToConsole();
                }
            }.start();
        }

        @Override
        public void write(int b) {
            synchronized (buffer) {
                buffer.append((char) (b & 0xFF));
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            synchronized (buffer) {
                for (int i = 0; i < len; i++) {
                    buffer.append((char) (b[off + i] & 0xFF));
                }
            }
        }

        private void flushToConsole() {
            String pending;
            synchronized (buffer) {
                if (buffer.length() == 0) {
                    return;
                }
                pending = buffer.toString();
                buffer.setLength(0);
            }
            console.appendText(pending);
            int overflow = console.getLength() - MAX_CONSOLE_CHARS;
            if (overflow > 0) {
                console.deleteText(0, overflow);
            }
        }
    }
}