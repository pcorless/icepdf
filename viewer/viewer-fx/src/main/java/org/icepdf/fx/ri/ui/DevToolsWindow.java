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
package org.icepdf.fx.ri.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.icepdf.core.util.PoolSizing;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.render.RenderService;
import org.icepdf.fx.ri.Theme;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A live look at rendering, for debugging: the shared render pool's threads and queue, every
 * view's cache and jobs, and the heap, with 60-second sparklines of heap, queue depth and busy
 * threads.  One window for the process, whichever viewer window opened it; it polls
 * {@link RenderService#snapshot()} four times a second while it is showing, and not at all
 * otherwise.
 * <p>
 * {@link #KEY} (Ctrl+Shift+I, ⌘⌥I on macOS) toggles it in the reference viewer;
 * {@value #OPEN_PROPERTY}{@code =true} opens it at start.
 */
public final class DevToolsWindow {

    /** Opens the window when the viewer starts. */
    public static final String OPEN_PROPERTY = "org.icepdf.fx.devtools";

    /** The browser devtools key: Ctrl+Shift+I, ⌘⌥I on macOS. */
    public static final KeyCombination KEY = new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN,
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")
                    ? KeyCombination.ALT_DOWN : KeyCombination.SHIFT_DOWN);

    private static final Duration POLL = Duration.millis(250);
    // 60 seconds of samples.
    private static final int HISTORY = 240;
    private static final long MB = 1L << 20;

    private static DevToolsWindow instance;

    private final Stage stage = new Stage(StageStyle.UTILITY);
    private final VBox root = new VBox(10);
    private final ScrollPane pane = new ScrollPane(root);
    private final Timeline poll = new Timeline(new KeyFrame(POLL, e -> update()));

    private final ProgressBar heapBar = new ProgressBar(0);
    private final Label heapText = new Label();
    // one colour per figure, mid-tones that read on the light and dark themes; high contrast keeps
    // the theme's text colour instead.
    // the heap goes green, orange, red as it fills: below 60% of the max, to 85%, above.
    private static final Color HEAP_OK = Color.web("#2eaa5c");
    private static final Color HEAP_HIGH = Color.web("#f28c28");
    private static final Color HEAP_FULL = Color.web("#e5484d");
    private static final Color QUEUE = Color.web("#3b82f6");
    private static final Color ACTIVE = Color.web("#2eaa5c");
    private final Sparkline heapLine = new Sparkline(HEAP_OK);
    private final Sparkline queueLine = new Sparkline(QUEUE);
    private final Sparkline activeLine = new Sparkline(ACTIVE);
    private final Label heapCaption = new Label();
    private final Label queueCaption = new Label();
    private final Label activeCaption = new Label();
    private final TableView<RenderService.ThreadInfo> threads = new TableView<>();
    private final TableView<RenderService.QueuedInfo> queue = new TableView<>();
    private final TableView<RenderService.ViewStats> views = new TableView<>();
    private final Label counters = new Label();

    private Theme theme;
    private final ChangeListener<Theme.Mode> themeListener = (o, was, now) -> theme.style(pane);

    private DevToolsWindow() {
        stage.setTitle(Messages.get("devtools.title"));
        root.getStyleClass().add("devtools");
        root.setPadding(new Insets(12));

        heapBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(heapBar, Priority.ALWAYS);
        HBox heap = new HBox(10, heapBar, heapText);
        HBox lines = new HBox(16, caption(heapLine, heapCaption), caption(queueLine, queueCaption),
                caption(activeLine, activeCaption));

        threads.getColumns().setAll(List.of(
                column("devtools.thread", 150, RenderService.ThreadInfo::name),
                column("devtools.kind", 90, t -> t.task() == null ? Messages.get("devtools.idle") : kind(t.kind())),
                column("devtools.view", 140, RenderService.ThreadInfo::view),
                column("devtools.task", 230, t -> t.task() == null ? "" : t.task()),
                column("devtools.running", 80, t -> t.task() == null ? "" : t.runningMs() + " ms")));
        queue.getColumns().setAll(List.of(
                column("devtools.priority", 70, q -> Messages.get(q.foreground() ? "devtools.front" : "devtools.behind")),
                column("devtools.kind", 90, q -> kind(q.kind())),
                column("devtools.view", 140, RenderService.QueuedInfo::view),
                column("devtools.task", 230, RenderService.QueuedInfo::label),
                column("devtools.age", 80, q -> q.ageMs() + " ms")));
        views.getColumns().setAll(List.of(
                column("devtools.document", 160, RenderService.ViewStats::document),
                column("devtools.pages", 55, v -> v.currentPage() + "/" + v.pages()),
                column("devtools.zoom", 60, v -> Math.round(v.zoom() * 100) + "%"),
                column("devtools.cache", 110, v -> v.cacheUsed() / MB + " / " + v.cacheBudget() / MB + " MB"),
                column("devtools.tiles", 55, v -> String.valueOf(v.cacheEntries())),
                column("devtools.inFlight", 65, v -> String.valueOf(v.inFlight())),
                column("devtools.failed", 55, v -> String.valueOf(v.failed())),
                column("devtools.text", 50, v -> String.valueOf(v.textPages())),
                column("devtools.priority", 70, v -> Messages.get(v.foreground() ? "devtools.front" : "devtools.behind"))));
        for (TableView<?> table : List.of(threads, queue, views)) {
            table.setPlaceholder(new Label(Messages.get("devtools.none")));
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        }
        threads.setPrefHeight(150);
        queue.setPrefHeight(180);
        views.setPrefHeight(120);
        counters.setWrapText(true);

        root.getChildren().addAll(heading("devtools.memory"), heap, lines,
                heading("devtools.threads"), threads,
                heading("devtools.queue"), queue,
                heading("devtools.views"), views,
                heading("devtools.counters"), counters);
        pane.setFitToWidth(true);
        stage.setScene(new Scene(pane, 820, 860));
        stage.getScene().getAccelerators().put(KEY, this::hide);

        poll.setCycleCount(Animation.INDEFINITE);
        stage.setOnShown(e -> {
            update();
            poll.play();
        });
        stage.setOnHidden(e -> poll.stop());
    }

    /** Shows the window (creating it the first time), styled like {@code theme}'s viewer. */
    public static void show(Theme theme) {
        if (instance == null) instance = new DevToolsWindow();
        instance.follow(theme);
        instance.stage.show();
        instance.stage.toFront();
    }

    /** Shows the window, or hides it if it is showing. */
    public static void toggle(Theme theme) {
        if (isShowing()) instance.hide();
        else show(theme);
    }

    public static boolean isShowing() {
        return instance != null && instance.stage.isShowing();
    }

    /** The window, or null before it is first opened; for the smoke runs. */
    public static DevToolsWindow current() {
        return instance;
    }

    public Stage getStage() {
        return stage;
    }

    /** Rows in the threads, queue and views tables; for the smoke runs. */
    public int[] rowCounts() {
        return new int[]{threads.getItems().size(), queue.getItems().size(), views.getItems().size()};
    }

    private void hide() {
        stage.hide();
    }

    private void follow(Theme next) {
        if (theme != null) theme.effectiveProperty().removeListener(themeListener);
        theme = next;
        if (theme != null) {
            theme.style(pane);
            theme.effectiveProperty().addListener(themeListener);
        }
    }

    private void update() {
        RenderService.Snapshot s = RenderService.shared().snapshot();
        double used = s.heapUsed() / (double) MB;
        heapBar.setProgress(s.heapMax() > 0 ? s.heapUsed() / (double) s.heapMax() : 0);
        heapText.setText(Messages.format("devtools.heap", Math.round(used), s.heapCommitted() / MB, s.heapMax() / MB)
                + "; " + Messages.format("devtools.gc", s.gcCount(), s.gcMs()));
        heapLine.add(used);
        queueLine.add(s.queue().size());
        activeLine.add(s.active());
        heapCaption.setText(Messages.format("devtools.heapLine", Math.round(used)));
        queueCaption.setText(Messages.format("devtools.queueLine", s.queue().size()));
        activeCaption.setText(Messages.format("devtools.activeLine", s.active(), s.size().threads()));
        Paint ink = heapCaption.getTextFill();
        boolean plain = theme != null && theme.isHighContrast();
        double fill = s.heapMax() > 0 ? s.heapUsed() / (double) s.heapMax() : 0;
        heapLine.setColour(fill < 0.60 ? HEAP_OK : fill < 0.85 ? HEAP_HIGH : HEAP_FULL);
        heapBar.setStyle(plain ? "" : "-fx-accent: " + web(heapLine.colour) + ";");
        for (Sparkline line : List.of(heapLine, queueLine, activeLine)) line.draw(ink, plain);

        threads.getItems().setAll(s.threads());
        queue.getItems().setAll(s.queue());
        views.getItems().setAll(s.views());

        StringBuilder mean = new StringBuilder();
        for (Map.Entry<RenderService.Kind, Double> e : s.meanMs().entrySet()) {
            if (mean.length() > 0) mean.append(", ");
            mean.append(kind(e.getKey())).append(' ').append(String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        counters.setText(Messages.format("devtools.pool", s.size().threads(), source(s.size()))
                + "\n" + Messages.format("devtools.done", s.completed(), s.cancelled())
                + "\n" + Messages.format("devtools.mean", mean.length() > 0 ? mean : "-")
                + "\n" + (s.lowMemory() ? Messages.format("devtools.gateSerial", (s.lowMemoryMsLeft() + 999) / 1000)
                : Messages.get("devtools.gateOpen")));
    }

    private static String source(PoolSizing.Size size) {
        return switch (size.source()) {
            case AUTO -> Messages.get("devtools.sourceAuto");
            case CAP -> Messages.format("devtools.sourceCap", size.cap());
            case EXPLICIT -> Messages.get("devtools.sourceSet");
        };
    }

    private static String kind(RenderService.Kind kind) {
        return kind == null ? "" : Messages.get("devtools.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private static Label heading(String key) {
        Label heading = new Label(Messages.get(key));
        heading.getStyleClass().add("devtools-heading");
        heading.setStyle("-fx-font-weight: bold;");
        return heading;
    }

    private static VBox caption(Sparkline line, Label caption) {
        caption.setGraphic(line.dot);
        return new VBox(2, line, caption);
    }

    private static String web(Color c) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    private static <T> TableColumn<T, String> column(String key, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>();
        // the header is a label so a terse heading ("Rasters") can explain itself on hover.
        Label header = new Label(Messages.get(key));
        String tip = Messages.get(key + ".tooltip", "");
        if (!tip.isEmpty()) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tip);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(360);
            header.setTooltip(tooltip);
        }
        column.setGraphic(header);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    /** The last minute of one figure, scaled to its own peak, in its own colour. */
    private static final class Sparkline extends Canvas {
        Color colour;
        // the caption's legend dot, kept in the line's colour.
        final javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
        private final double[] samples = new double[HISTORY];
        private int count;
        private int next;

        Sparkline(Color colour) {
            super(HISTORY, 40);
            setColour(colour);
        }

        void setColour(Color colour) {
            this.colour = colour;
            dot.setFill(colour);
        }

        void add(double value) {
            samples[next] = value;
            next = (next + 1) % HISTORY;
            count = Math.min(HISTORY, count + 1);
        }

        /** @param plain draw in {@code ink} only (high contrast) */
        void draw(Paint ink, boolean plain) {
            GraphicsContext g = getGraphicsContext2D();
            g.clearRect(0, 0, getWidth(), getHeight());
            g.setStroke(ink instanceof Color c ? c.deriveColor(0, 1, 1, 0.3) : Color.GRAY);
            g.strokeLine(0, getHeight() - 0.5, getWidth(), getHeight() - 0.5);
            if (count == 0) return;
            double peak = 1;
            for (int i = 0; i < count; i++) peak = Math.max(peak, samples[(next - count + i + HISTORY) % HISTORY]);
            double[] xs = new double[count];
            double[] ys = new double[count];
            for (int i = 0; i < count; i++) {
                xs[i] = getWidth() - count + i;
                ys[i] = getHeight() - 2 - samples[(next - count + i + HISTORY) % HISTORY] / peak * (getHeight() - 4);
            }
            if (!plain) {
                // a soft wash under the line.
                g.setFill(colour.deriveColor(0, 1, 1, 0.18));
                g.beginPath();
                g.moveTo(xs[0], getHeight());
                for (int i = 0; i < count; i++) g.lineTo(xs[i], ys[i]);
                g.lineTo(xs[count - 1], getHeight());
                g.closePath();
                g.fill();
            }
            g.setStroke(plain ? ink : colour);
            g.setLineWidth(1.5);
            g.beginPath();
            g.moveTo(xs[0], ys[0]);
            for (int i = 1; i < count; i++) g.lineTo(xs[i], ys[i]);
            g.stroke();
        }
    }
}
