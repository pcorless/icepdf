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
package org.icepdf.fx.ri;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.scene.Node;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.document.DocumentPane;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.ScriptedDialogs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GH-662: Open URL in the JavaFX viewer opens a remote PDF fetched as it's read - in a scene that is never
 * shown, against a local server that serves byte ranges, the prompt answered by a script.
 */
class OpenUrlTest {

    /** A 3.5 MB manual: its first page needs a small part of it. */
    private static final Path FIXTURE = Paths.get("../../core/core-awt/src/test/resources/updater/DSCP73_om_en.pdf");

    @TempDir
    Path dir;
    private PdfViewer viewer;
    private final ScriptedDialogs dialogs = new ScriptedDialogs();
    private HttpServer server;
    private byte[] served;
    private final AtomicLong bytesServed = new AtomicLong();
    // a slow server: each range answer waits this long, so a page's fetch spans several status polls
    private volatile long rangeDelayMillis;

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
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).multipleDocuments(true).build(),
                    UserLayout.inMemory());
            v.setDialogs(dialogs);
            Fx.scene(v, 1000, 700);
            return v;
        });
    }

    @AfterEach
    void dispose() {
        if (viewer != null) Fx.run(viewer::dispose);
        if (server != null) server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        String range = exchange.getRequestHeaders().getFirst("Range");
        try (OutputStream out = exchange.getResponseBody()) {
            if (range != null && range.startsWith("bytes=")) {
                if (rangeDelayMillis > 0) {
                    try {
                        Thread.sleep(rangeDelayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String[] bounds = range.substring(6).split("-");
                int start = Integer.parseInt(bounds[0]);
                int end = Math.min(served.length - 1, Integer.parseInt(bounds[1]));
                exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + served.length);
                exchange.sendResponseHeaders(206, end - start + 1);
                out.write(served, start, end - start + 1);
                bytesServed.addAndGet(end - start + 1);
            } else {
                exchange.sendResponseHeaders(200, served.length);
                out.write(served);
                bytesServed.addAndGet(served.length);
            }
        }
    }

    private String address() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/manuals/DSCP73_om_en.pdf";
    }

    private void openTyped(String typed) {
        dialogs.type(typed);
        Fx.run(() -> viewer.getActions().execute("document.open-url"));
    }

    private void awaitShowing() {
        Fx.waitFor("the remote document to show", () -> {
            DocumentSession s = viewer.getSession();
            return !s.isLoading() && s.getDocument() != null && s.getUrl() != null;
        });
        Fx.run(() -> Fx.layout(viewer));
    }

    @DisplayName("Open URL shows the document from a part of it, named by its address, with no file")
    @Test
    void opensFromAPart() {
        openTyped(address());
        awaitShowing();
        DocumentSession session = Fx.call(viewer::getSession);
        assertEquals(URI.create(address()), Fx.call(session::getUrl));
        assertNull(Fx.call(session::getFile), "a remote document has no file");
        assertEquals("DSCP73_om_en.pdf", Fx.call(() -> viewer.getDocuments().get(0).getTitle()));
        assertTrue(dialogs.asked.contains(Messages.get("viewer.openUrlPrompt")));
        Fx.waitFor("the first page to draw", () -> viewer.getView().getCurrentPageIndex() == 0);
        Fx.settle();
        // the view reads every page's dictionary (sizes, comments), and in this 40-page manual each sits in a
        // chunk of its own; the pages' content and images stay behind
        Document document = Fx.call(session::getDocument);
        assertFalse(document.isFullyLoaded());
        assertTrue(document.getBytesLoaded() < document.getDocumentLength(),
                document.getBytesLoaded() + " of " + document.getDocumentLength());
    }

    @DisplayName("the address of an open document shows its tab instead of opening it again")
    @Test
    void sameAddressSelectsItsTab() {
        openTyped(address());
        awaitShowing();
        Fx.run(() -> viewer.getActions().execute("document.new-tab"));
        Fx.waitFor("a second tab", () -> viewer.getDocuments().size() == 2);
        assertNull(Fx.call(() -> viewer.getSession().getUrl()), "the new tab is selected");
        openTyped(address());
        Fx.settle();
        assertEquals(URI.create(address()), Fx.call(() -> viewer.getSession().getUrl()));
        long remote = Fx.call(() -> viewer.getDocuments().stream()
                .filter(p -> p.getSession().getUrl() != null || p.getSession().getLoadingUrl() != null).count());
        assertEquals(1, remote);
    }

    @DisplayName("what isn't an http or https address is refused with a message; a bare host gets https")
    @Test
    void notAWebAddress() {
        openTyped("ftp://example.com/a.pdf");
        Fx.waitFor("the error", () -> !viewer.getToast().getError().isEmpty());
        assertTrue(Fx.call(() -> viewer.getToast().getError()).contains("ftp://example.com/a.pdf"));
        assertNull(Fx.call(() -> viewer.getSession().getLoadingUrl()));
        assertEquals(URI.create("https://example.com/a.pdf"), Fx.call(() -> viewer.parseUrl(" example.com/a.pdf ")));
        assertNull(Fx.call(() -> viewer.parseUrl("   ")));
    }

    @DisplayName("a remote document can't be saved in place; Save As writes it all, and the file becomes the open document")
    @Test
    void saveAsksWhere() throws IOException {
        openTyped(address());
        awaitShowing();
        Path target = dir.resolve("copy.pdf");
        dialogs.choose(target);
        // Save is disabled until there are changes; Save As is what an unmodified remote document gets
        assertFalse(Fx.call(() -> viewer.getSession().canSaveInPlace()));
        Fx.run(viewer::saveAs);
        Fx.waitFor("the saved copy to open", () -> {
            DocumentSession s = viewer.getSession();
            return !s.isLoading() && target.toAbsolutePath().equals(s.getFile());
        });
        assertTrue(dialogs.asked.contains(Messages.get("viewer.saveAsTitle")));
        assertNull(Fx.call(() -> viewer.getSession().getUrl()));
        assertTrue(Files.size(target) >= served.length);
    }

    @DisplayName("the fetch status shows while a page's bytes arrive")
    @Test
    void fetchStatusShows() {
        openTyped(address());
        awaitShowing();
        DocumentPane pane = Fx.call(() -> viewer.getDocuments().get(0));
        Node status = Fx.call(() -> pane.getNode().lookup(".fetch-status"));
        assertNotNull(status);
        // a page far into the manual brings bytes not fetched yet, slowly
        rangeDelayMillis = 400;
        Fx.run(() -> viewer.getView().setCurrentPageIndex(viewer.getView().getPageCount() - 1));
        Fx.waitFor("the status", status::isVisible);
        String text = ((javafx.scene.control.Label) status).getText();
        assertTrue(text.contains("MB"), text);
    }
}
