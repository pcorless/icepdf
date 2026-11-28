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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.icepdf.core.pobjects.FetchStats;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GH-662: the devtools network tab shows a remote document's requests and progress, and its throttle slows every
 * remote document; against a local server that serves byte ranges, in a scene that is never shown.
 */
class NetworkTabTest {

    private static final Path FIXTURE = Paths.get("../../core/core-awt/src/test/resources/updater/DSCP73_om_en.pdf");

    private PdfViewer viewer;
    private HttpServer server;
    private byte[] served;
    // range requests still to answer 503
    private final AtomicInteger busyLeft = new AtomicInteger();
    private NetworkTab tab;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() throws IOException {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        served = Files.readAllBytes(FIXTURE);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        viewer = Fx.call(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).build(),
                    UserLayout.inMemory());
            Fx.scene(v, 1000, 700);
            return v;
        });
        tab = Fx.call(NetworkTab::new);
    }

    @AfterEach
    void dispose() {
        DocumentSession.NETWORK_THROTTLE.set(Duration.ZERO, 0);
        if (viewer != null) Fx.run(viewer::dispose);
        if (server != null) server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range != null && !range.startsWith("bytes=0-") && busyLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            exchange.getResponseHeaders().add("Retry-After", "0");
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            if (range != null && range.startsWith("bytes=")) {
                String[] bounds = range.substring(6).split("-");
                int start = Integer.parseInt(bounds[0]);
                int end = Math.min(served.length - 1, Integer.parseInt(bounds[1]));
                exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + served.length);
                exchange.sendResponseHeaders(206, end - start + 1);
                out.write(served, start, end - start + 1);
            } else {
                exchange.sendResponseHeaders(200, served.length);
                out.write(served);
            }
        }
    }

    private URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/manuals/DSCP73_om_en.pdf");
    }

    private void openRemote() {
        URI uri = address();
        Fx.run(() -> viewer.openUrl(uri));
        Fx.waitFor("the remote document to show", () -> {
            DocumentSession s = viewer.getSession();
            return !s.isLoading() && s.getDocument() != null && uri.equals(s.getUrl());
        });
        Fx.settle();
    }

    private void poll() {
        Fx.run(() -> tab.update(Color.BLACK, false));
    }

    @DisplayName("with no remote document open the tab says so, and the throttle can still be set")
    @Test
    void nothingOpen() {
        poll();
        Fx.run(() -> {
            if (DocumentSession.remoteDocuments().isEmpty()) {
                assertTrue(tab.getNode().lookupAll(".label").stream()
                        .anyMatch(n -> n instanceof Label l && l.isVisible()
                                && l.getText().equals(org.icepdf.fx.i18n.Messages.get("devtools.net.none"))));
            }
            assertTrue(tab.throttleChoice().isVisible());
        });
    }

    @DisplayName("a remote document's requests show newest first, each with what it was for")
    @Test
    void showsTheRequests() {
        openRemote();
        poll();
        int rows = Fx.call(tab::rowCount);
        assertTrue(rows > 1, rows + " rows");
        FetchStats stats = Fx.call(() -> viewer.getSession().getDocument().getFetchStats());
        assertTrue(stats.getRecentRequests().stream()
                .anyMatch(r -> r.getInitiator() == FetchStats.Initiator.PREFETCH), "the page prefetch is logged");
        String csv = Fx.call(tab::csvForTest);
        String[] lines = csv.split("\n");
        assertTrue(lines[0].startsWith("id,start_ms,from,length,status"), lines[0]);
        assertEquals(rows + 1, lines.length);
        // newest first
        long first = Long.parseLong(lines[1].split(",")[0]);
        long last = Long.parseLong(lines[lines.length - 1].split(",")[0]);
        assertTrue(first > last);
    }

    @DisplayName("retries after busy answers are counted, and the filter keeps only those")
    @Test
    void retriesShow() {
        busyLeft.set(2);
        openRemote();
        poll();
        FetchStats stats = Fx.call(() -> viewer.getSession().getDocument().getFetchStats());
        assertEquals(2, stats.getRetries());
        Fx.run(() -> {
            javafx.scene.control.CheckBox failedOnly = (javafx.scene.control.CheckBox) tab.getNode()
                    .lookupAll(".check-box").iterator().next();
            failedOnly.setSelected(true);
        });
        poll();
        int rows = Fx.call(tab::rowCount);
        assertTrue(rows >= 1 && rows <= 2, rows + " rows with retries");
    }

    @DisplayName("choosing a throttle slows every remote document")
    @Test
    void throttleSlows() {
        Fx.run(() -> tab.throttleChoice().getSelectionModel().select(
                NetworkTab.PRESETS.stream().filter(p -> "devtools.net.throttle.fast3g".equals(p.key()))
                        .findFirst().orElseThrow()));
        assertEquals(150, DocumentSession.NETWORK_THROTTLE.getLatency().toMillis());
        assertEquals(180 * 1024, DocumentSession.NETWORK_THROTTLE.getBytesPerSecond());
        long started = System.nanoTime();
        openRemote();
        long millis = (System.nanoTime() - started) / 1_000_000;
        long made = Fx.call(() -> viewer.getSession().getDocument().getFetchStats().getRequestCount());
        assertTrue(millis >= 150 * made / 4, millis + " ms for " + made + " requests");
        Fx.run(() -> tab.throttleChoice().getSelectionModel().select(0));
        assertTrue(DocumentSession.NETWORK_THROTTLE.isOff());
    }
}
