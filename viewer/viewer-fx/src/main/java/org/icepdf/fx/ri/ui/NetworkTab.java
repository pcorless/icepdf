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

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.FetchStats;
import org.icepdf.core.pobjects.NetworkThrottle;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.document.DocumentSession.RemoteDocument;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The devtools' network tab (GH-662), like a browser's: for one remote document, what's arrived of it (a bar and
 * a map of the file), the requests made, newest first, with their timings, retries and what they were for, and
 * a throttle that slows every remote document to try out a slower network. Polled with the rest of the window.
 */
final class NetworkTab {

    private static final long KB = 1024;
    private static final long MB = 1L << 20;
    private static final Color THROUGHPUT = Color.web("#3b82f6");
    private static final Color IN_FLIGHT = Color.web("#2eaa5c");
    private static final Color FETCHED = Color.web("#3b82f6");

    /** A throttle setting; {@code key} names it, or null for one set by property. */
    record Preset(String key, long latencyMillis, long kilobytesPerSecond) {
        boolean matches(NetworkThrottle throttle) {
            return throttle.getLatency().toMillis() == latencyMillis
                    && throttle.getBytesPerSecond() == kilobytesPerSecond * KB;
        }

        @Override
        public String toString() {
            if (latencyMillis == 0 && kilobytesPerSecond == 0) return Messages.get("devtools.net.throttle.off");
            String name = key != null ? Messages.get(key) : Messages.get("devtools.net.throttle.custom");
            String speed = kilobytesPerSecond == 0 ? Messages.get("devtools.net.unlimited")
                    : Messages.format("devtools.net.rate", kilobytesPerSecond);
            return Messages.format("devtools.net.throttle.preset", name, latencyMillis, speed);
        }
    }

    static final List<Preset> PRESETS = List.of(
            new Preset(null, 0, 0),
            new Preset("devtools.net.throttle.broadband", 20, 5 * 1024),
            new Preset("devtools.net.throttle.dsl", 50, 1024),
            new Preset("devtools.net.throttle.fast3g", 150, 180),
            new Preset("devtools.net.throttle.slow3g", 400, 50));

    private final BorderPane node = new BorderPane();
    private final ComboBox<RemoteDocument> documents = new ComboBox<>();
    private final ComboBox<Preset> throttle = new ComboBox<>();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label progressText = new Label();
    private final Label summary = new Label();
    private final DevToolsWindow.Sparkline throughputLine = new DevToolsWindow.Sparkline(THROUGHPUT);
    private final DevToolsWindow.Sparkline inFlightLine = new DevToolsWindow.Sparkline(IN_FLIGHT);
    private final Label throughputCaption = new Label();
    private final Label inFlightCaption = new Label();
    private final Canvas coverage = new Canvas(400, 14);
    private final TableView<FetchStats.Request> requests = new TableView<>();
    private final CheckBox failedOnly = new CheckBox(Messages.get("devtools.net.failedOnly"));
    private final CheckBox pause = new CheckBox(Messages.get("devtools.net.pause"));
    private final Button clear = new Button(Messages.get("devtools.net.clear"));
    private final Button copy = new Button(Messages.get("devtools.net.copy"));
    private final Label none = new Label(Messages.get("devtools.net.none"));
    private final VBox content;

    // the document the figures are for, and where its byte count stood at the last poll
    private Document shown;
    private long lastBytes;
    private long lastNanos;
    // requests up to this one were cleared from the table
    private long clearedThrough;
    private List<FetchStats.Request> lastRows = List.of();
    // the last figures drawn, to redraw the map with
    private FetchStats lastStats;
    private Paint lastInk = Color.BLACK;
    private boolean lastPlain;

    NetworkTab() {
        node.getStyleClass().add("devtools-network");
        node.setPadding(new Insets(12));

        documents.setPromptText(Messages.get("devtools.net.none"));
        documents.setButtonCell(documentCell());
        documents.setCellFactory(list -> documentCell());
        documents.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(documents, Priority.ALWAYS);

        throttle.getItems().setAll(PRESETS);
        NetworkThrottle current = DocumentSession.NETWORK_THROTTLE;
        Preset matching = PRESETS.stream().filter(p -> p.matches(current)).findFirst().orElse(null);
        if (matching == null) {
            matching = new Preset(null, current.getLatency().toMillis(), current.getBytesPerSecond() / KB);
            throttle.getItems().add(matching);
        }
        throttle.getSelectionModel().select(matching);
        throttle.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                DocumentSession.NETWORK_THROTTLE.set(Duration.ofMillis(now.latencyMillis()),
                        now.kilobytesPerSecond() * KB);
            }
        });
        Label throttleLabel = new Label(Messages.get("devtools.net.throttle"));
        throttleLabel.setLabelFor(throttle);
        HBox top = new HBox(10, documents, throttleLabel, throttle);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox bar = new HBox(10, progress, progressText);
        summary.setWrapText(true);
        HBox lines = new HBox(16, DevToolsWindow.caption(throughputLine, throughputCaption),
                DevToolsWindow.caption(inFlightLine, inFlightCaption));
        Label coverageCaption = new Label(Messages.get("devtools.net.coverage"));
        coverageCaption.setTooltip(new javafx.scene.control.Tooltip(Messages.get("devtools.net.coverage.tooltip")));
        // the map spans the tab: redrawn at each poll, and when the tab is resized
        coverage.widthProperty().bind(node.widthProperty().subtract(24));
        coverage.widthProperty().addListener((o, was, now) -> {
            if (lastStats != null) drawCoverage(lastStats, lastInk, lastPlain);
        });

        requests.getColumns().setAll(List.of(
                DevToolsWindow.column("devtools.net.id", 45, r -> String.valueOf(r.getId())),
                DevToolsWindow.column("devtools.net.start", 70, r -> r.getStartMillis() + " ms"),
                DevToolsWindow.column("devtools.net.range", 150, NetworkTab::range),
                DevToolsWindow.column("devtools.net.size", 65, r -> size(r.getLength())),
                DevToolsWindow.column("devtools.net.status", 60, NetworkTab::status),
                DevToolsWindow.column("devtools.net.time", 65, r -> r.isPending() ? "" : r.getDurationMillis() + " ms"),
                DevToolsWindow.column("devtools.net.queued", 60, r -> r.getQueuedMillis() + " ms"),
                DevToolsWindow.column("devtools.net.retries", 55, NetworkTab::retries),
                DevToolsWindow.column("devtools.net.for", 80, r -> initiator(r.getInitiator())),
                DevToolsWindow.column("devtools.net.thread", 130, FetchStats.Request::getThread),
                DevToolsWindow.column("devtools.net.error", 220, r -> r.getError() == null ? "" : r.getError())));
        requests.setPlaceholder(new Label(Messages.get("devtools.none")));
        requests.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(requests, Priority.ALWAYS);

        clear.setOnAction(e -> {
            clearedThrough = lastRows.stream().mapToLong(FetchStats.Request::getId).max().orElse(clearedThrough);
            requests.getItems().clear();
        });
        copy.setOnAction(e -> copyCsv());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox tools = new HBox(10, failedOnly, pause, spacer, clear, copy);
        tools.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        content = new VBox(10, DevToolsWindow.heading("devtools.net.progress"), bar, summary, lines,
                coverageCaption, coverage, DevToolsWindow.heading("devtools.net.requests"), tools, requests);
        content.setPadding(new Insets(10, 0, 0, 0));
        none.setWrapText(true);
        none.setPadding(new Insets(10, 0, 0, 0));
        // the throttle can be set before a document is opened
        node.setTop(top);
        node.setCenter(none);
    }

    Node getNode() {
        return node;
    }

    /** Rows in the request table; for the tests. */
    int rowCount() {
        return requests.getItems().size();
    }

    /** The throttle choices; for the tests. */
    ComboBox<Preset> throttleChoice() {
        return throttle;
    }

    void update(Paint ink, boolean plain) {
        List<RemoteDocument> open = DocumentSession.remoteDocuments();
        if (!sameDocuments(open, documents.getItems())) {
            RemoteDocument selected = documents.getValue();
            List<RemoteDocument> before = new ArrayList<>(documents.getItems());
            documents.getItems().setAll(open);
            // one just opened is the one to watch; else keep watching the same one
            RemoteDocument added = open.stream().filter(r -> indexOf(before, r) < 0).reduce((a, b) -> b).orElse(null);
            int kept = selected != null ? indexOf(open, selected) : -1;
            documents.setValue(added != null ? added : kept >= 0 ? open.get(kept)
                    : open.isEmpty() ? null : open.get(open.size() - 1));
        }
        RemoteDocument watched = documents.getValue();
        FetchStats stats = watched != null ? watched.document().getFetchStats() : null;
        if (stats == null) {
            show(null, ink, plain);
            return;
        }
        // opened by a server that sent it whole, or not yet opened: nothing to show
        if (watched.document() != shown) {
            shown = watched.document();
            lastBytes = stats.getBytesFetched();
            lastNanos = System.nanoTime();
            clearedThrough = 0;
            throughputLine.clear();
            inFlightLine.clear();
        }
        show(stats, ink, plain);
    }

    private static int indexOf(List<RemoteDocument> list, RemoteDocument doc) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).document() == doc.document()) return i;
        }
        return -1;
    }

    private static boolean sameDocuments(List<RemoteDocument> a, List<RemoteDocument> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).document() != b.get(i).document()) return false;
        }
        return true;
    }

    private void show(FetchStats stats, Paint ink, boolean plain) {
        if (stats == null) {
            if (node.getCenter() != none) node.setCenter(none);
            shown = null;
            return;
        }
        if (node.getCenter() != content) node.setCenter(content);
        long present = stats.getBytesPresent();
        long length = stats.getDocumentLength();
        progress.setProgress(length > 0 ? present / (double) length : 0);
        progressText.setText(Messages.format("devtools.net.fetched", mb(present), mb(length),
                length > 0 ? Math.round(100.0 * present / length) : 0));
        summary.setText(Messages.format("devtools.net.requestsLine", stats.getRequestCount(), stats.getInFlight(),
                stats.getMaxConcurrent())
                + "; " + Messages.format("devtools.net.queueLine", stats.getQueued(), stats.getRetries(),
                stats.getFailures())
                + "; " + Messages.format("devtools.net.sentLine", mb(stats.getBytesFetched()),
                String.format(Locale.ROOT, "%.1f", stats.getElapsedMillis() / 1000.0)));

        long now = System.nanoTime();
        double seconds = Math.max(1e-3, (now - lastNanos) / 1e9);
        double rate = Math.max(0, stats.getBytesFetched() - lastBytes) / seconds / KB;
        lastBytes = stats.getBytesFetched();
        lastNanos = now;
        throughputLine.add(rate);
        inFlightLine.add(stats.getInFlight());
        throughputCaption.setText(Messages.format("devtools.net.throughputLine", Math.round(rate)));
        inFlightCaption.setText(Messages.format("devtools.net.inFlightLine", stats.getInFlight(),
                stats.getMaxConcurrent()));
        throughputLine.draw(ink, plain);
        inFlightLine.draw(ink, plain);
        drawCoverage(stats, ink, plain);

        if (!pause.isSelected()) {
            List<FetchStats.Request> rows = new ArrayList<>();
            List<FetchStats.Request> log = stats.getRecentRequests();
            for (int i = log.size() - 1; i >= 0; i--) {
                FetchStats.Request r = log.get(i);
                if (r.getId() <= clearedThrough) break;
                if (failedOnly.isSelected() && !r.isFailed() && r.getRetries() == 0) continue;
                rows.add(r);
            }
            lastRows = rows;
            requests.getItems().setAll(rows);
        }
    }

    private void drawCoverage(FetchStats stats, Paint ink, boolean plain) {
        lastStats = stats;
        lastInk = ink;
        lastPlain = plain;
        GraphicsContext g = coverage.getGraphicsContext2D();
        double width = coverage.getWidth();
        double height = coverage.getHeight();
        g.clearRect(0, 0, width, height);
        if (width < 4) return;
        int cells = Math.max(1, (int) (width / 2));
        float[] here = stats.getCoverage(cells);
        Color fill = plain && ink instanceof Color c ? c : FETCHED;
        double cell = width / cells;
        for (int i = 0; i < cells; i++) {
            if (here[i] <= 0) continue;
            g.setFill(fill.deriveColor(0, 1, 1, plain ? (here[i] > 0.5f ? 1 : 0.5) : 0.25 + 0.75 * here[i]));
            g.fillRect(i * cell, 0, Math.ceil(cell), height);
        }
        g.setStroke(ink instanceof Color c ? c.deriveColor(0, 1, 1, 0.4) : Color.GRAY);
        g.strokeRect(0.5, 0.5, width - 1, height - 1);
    }

    private void copyCsv() {
        StringBuilder csv = new StringBuilder("id,start_ms,from,length,status,time_ms,queued_ms,retries,retry_wait_ms,"
                + "initiator,thread,error\n");
        for (FetchStats.Request r : requests.getItems()) {
            csv.append(r.getId()).append(',').append(r.getStartMillis()).append(',').append(r.getFrom()).append(',')
                    .append(r.getLength()).append(',').append(r.getStatus()).append(',')
                    .append(r.getDurationMillis()).append(',').append(r.getQueuedMillis()).append(',')
                    .append(r.getRetries()).append(',').append(r.getRetryWaitMillis()).append(',')
                    .append(r.getInitiator()).append(',').append(quote(r.getThread())).append(',')
                    .append(quote(r.getError())).append('\n');
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(csv.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** The table as CSV, as {@link #copyCsv} puts it on the clipboard; for the tests. */
    String csvForTest() {
        copyCsv();
        return Clipboard.getSystemClipboard().getString();
    }

    private static String quote(String text) {
        if (text == null) return "";
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static ListCell<RemoteDocument> documentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(RemoteDocument item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + "  " + item.uri());
            }
        };
    }

    private static String range(FetchStats.Request r) {
        return String.format(Locale.ROOT, "%,d-%,d", r.getFrom(), r.getFrom() + r.getLength() - 1);
    }

    private static String status(FetchStats.Request r) {
        if (r.isPending()) return Messages.get("devtools.net.pending");
        if (r.isFailed()) return r.getStatus() > 0 ? Messages.format("devtools.net.failedStatus", r.getStatus())
                : Messages.get("devtools.net.failed");
        return String.valueOf(r.getStatus());
    }

    private static String retries(FetchStats.Request r) {
        if (r.getRetries() == 0) return "";
        return Messages.format("devtools.net.retryValue", r.getRetries(), r.getRetryWaitMillis());
    }

    private static String initiator(FetchStats.Initiator initiator) {
        return Messages.get("devtools.net.for." + initiator.name().toLowerCase(Locale.ROOT));
    }

    private static String size(long bytes) {
        return bytes >= MB ? String.format(Locale.ROOT, "%.1f MB", bytes / (double) MB)
                : bytes >= KB ? Math.round(bytes / (double) KB) + " KB" : bytes + " B";
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (double) MB);
    }
}
