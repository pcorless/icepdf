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
package org.icepdf.core.pobjects;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-662: a document opened over HTTP range requests renders and saves exactly as the same bytes opened locally,
 * fetching only what it reads; a server that can't serve ranges sends the whole document instead.
 */
public class RangeLoadingTest {

    private enum Mode {RANGES, NO_RANGES, GZIP}

    private HttpServer server;
    private volatile byte[] served;
    private volatile Mode mode = Mode.RANGES;
    private volatile String requiredAuthorization;
    private final AtomicLong bytesServed = new AtomicLong();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger live = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    // range requests still to answer 503, "busy"
    private final AtomicInteger busyLeft = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        peak.accumulateAndGet(live.incrementAndGet(), Math::max);
        try {
            answer(exchange);
        } finally {
            live.decrementAndGet();
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        byte[] data = served;
        if (requiredAuthorization != null
                && !requiredAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range != null && !range.startsWith("bytes=0-") && busyLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            exchange.getResponseHeaders().add("Retry-After", "0");
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            if (mode == Mode.GZIP) {
                ByteArrayOutputStream zipped = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(zipped)) {
                    gzip.write(data);
                }
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                exchange.getResponseHeaders().add("Content-Range", "bytes 0-" + (zipped.size() - 1) + "/" + zipped.size());
                exchange.sendResponseHeaders(range != null ? 206 : 200, zipped.size());
                out.write(zipped.toByteArray());
                bytesServed.addAndGet(zipped.size());
            } else if (mode == Mode.RANGES && range != null && range.startsWith("bytes=")) {
                String[] bounds = range.substring(6).split("-");
                int start = Integer.parseInt(bounds[0]);
                int end = Math.min(data.length - 1, Integer.parseInt(bounds[1]));
                exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + data.length);
                exchange.sendResponseHeaders(206, end - start + 1);
                out.write(data, start, end - start + 1);
                bytesServed.addAndGet(end - start + 1);
            } else {
                exchange.sendResponseHeaders(200, data.length);
                out.write(data);
                bytesServed.addAndGet(data.length);
            }
        }
    }

    private URL url() throws IOException {
        return new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/doc.pdf");
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = RangeLoadingTest.class.getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }

    private static Document local(byte[] bytes) throws Exception {
        Document document = new Document();
        document.setByteArray(bytes, 0, bytes.length, "local.pdf");
        return document;
    }

    private Document remote(UrlLoadOptions options) throws Exception {
        Document document = new Document();
        document.setUrl(url(), options);
        return document;
    }

    private static UrlLoadOptions chunks(int size) {
        return UrlLoadOptions.builder().chunkSize(size).build();
    }

    private static BufferedImage render(Document document, int page) throws InterruptedException {
        return (BufferedImage) document.getPageImage(page, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX,
                0f, 0.5f);
    }

    private static void assertSameImage(BufferedImage expected, BufferedImage actual, String what) {
        assertEquals(expected.getWidth(), actual.getWidth(), what);
        assertEquals(expected.getHeight(), actual.getHeight(), what);
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                    fail(what + ": pixel " + x + "," + y + " differs");
                }
            }
        }
    }

    private void assertRendersAsLocal(byte[] bytes, UrlLoadOptions options, int pages) throws Exception {
        served = bytes;
        Document local = local(bytes);
        Document remote = remote(options);
        try {
            assertEquals(local.getNumberOfPages(), remote.getNumberOfPages());
            for (int page = 0; page < Math.min(pages, local.getNumberOfPages()); page++) {
                assertSameImage(render(local, page), render(remote, page), "page " + page);
            }
        } finally {
            local.dispose();
            remote.dispose();
        }
    }

    @Test
    public void thePageRendersAsLocalFromAFractionOfTheFile() throws Exception {
        byte[] bytes = resource("/updater/DSCP73_om_en.pdf");
        served = bytes;
        Document local = local(bytes);
        Document remote = remote(chunks(16 * 1024));
        try {
            assertSameImage(render(local, 0), render(remote, 0), "page 0");
            long fetched = bytesServed.get();
            assertTrue(fetched < bytes.length / 4,
                    "page 1 took " + fetched + " of " + bytes.length + " bytes");
            // a page later in the book brings its own objects, not the rest of the file
            int last = local.getNumberOfPages() - 1;
            assertSameImage(render(local, last), render(remote, last), "page " + last);
            assertTrue(bytesServed.get() < bytes.length / 2, bytesServed.get() + " of " + bytes.length);
        } finally {
            local.dispose();
            remote.dispose();
        }
    }

    @Test
    public void prefetchedPagesHaveTheirSizesFromAFewPercentOfTheFile() throws Exception {
        // a linearized manual: each page's dictionary sits beside its content, 132 of them across the file
        byte[] bytes = resource("/updater/DSCP73_om_en.pdf");
        served = bytes;
        Document local = local(bytes);
        Document remote = remote(UrlLoadOptions.defaults());
        try {
            long beforePrefetch = requests.get();
            remote.getPageTree().prefetchPages();
            long prefetchRequests = requests.get() - beforePrefetch;
            long afterPrefetch = requests.get();
            for (int page = 0; page < local.getNumberOfPages(); page++) {
                PDimension expected = local.getPageTree().getPage(page).getSize(Page.BOUNDARY_CROPBOX, 0f, 1f);
                PDimension actual = remote.getPageTree().getPage(page).getSize(Page.BOUNDARY_CROPBOX, 0f, 1f);
                assertEquals(expected.getWidth(), actual.getWidth(), "page " + page);
                assertEquals(expected.getHeight(), actual.getHeight(), "page " + page);
            }
            assertTrue(prefetchRequests > 1, prefetchRequests + " prefetch requests");
            assertTrue(requests.get() - afterPrefetch <= 2,
                    "the sizes waited on " + (requests.get() - afterPrefetch) + " requests after the prefetch");
            assertTrue(bytesServed.get() < bytes.length / 5, bytesServed.get() + " of " + bytes.length);
            assertTrue(peak.get() <= 4, "at most 4 requests out at once, not " + peak.get());
            assertSameImage(render(local, 0), render(remote, 0), "page 0");
        } finally {
            local.dispose();
            remote.dispose();
        }
    }

    @Test
    public void aBusyServerIsWaitedFor() throws Exception {
        busyLeft.set(2);
        byte[] bytes = resource("/updater/DSCP73_om_en.pdf");
        served = bytes;
        Document local = local(bytes);
        Document remote = remote(UrlLoadOptions.defaults());
        try {
            assertSameImage(render(local, 0), render(remote, 0), "page 0");
            assertEquals(0, busyLeft.get(), "the busy answers were all given");
            FetchStats stats = remote.getFetchStats();
            assertEquals(2, stats.getRetries());
            assertEquals(0, stats.getFailures());
            assertTrue(stats.getRecentRequests().stream().anyMatch(r -> r.getRetries() > 0 && r.getStatus() == 206));
            assertEquals(FetchStats.Initiator.OPEN, stats.getRecentRequests().get(0).getInitiator());
            assertEquals(requests.get(), stats.getRequestCount() + stats.getRetries(),
                    "every request the server saw is in the log, retries as retries");
            assertNull(local.getFetchStats(), "a local document has none");
        } finally {
            local.dispose();
            remote.dispose();
        }
    }

    @Test
    public void aThrottleSlowsTheFetch() throws Exception {
        byte[] bytes = resource("/updater/DSCP73_om_en.pdf");
        served = bytes;
        long started = System.nanoTime();
        Document remote = remote(UrlLoadOptions.builder()
                .throttle(new NetworkThrottle(Duration.ofMillis(100), 0)).build());
        try {
            render(remote, 0);
            long millis = (System.nanoTime() - started) / 1_000_000;
            long requestsMade = remote.getFetchStats().getRequestCount();
            // each request waits 100 ms; at most four at once
            assertTrue(millis >= 100 * requestsMade / 4, millis + " ms for " + requestsMade + " requests");
        } finally {
            remote.dispose();
        }
    }

    @Test
    public void savingFetchesTheRestAndWritesTheSameBytes() throws Exception {
        byte[] bytes = resource("/updater/R&D-05-Carbon.pdf");
        served = bytes;
        Document local = local(bytes);
        Document remote = remote(chunks(4 * 1024));
        try {
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            local.writeToOutputStream(expected, WriteMode.INCREMENT_UPDATE);
            remote.writeToOutputStream(actual, WriteMode.INCREMENT_UPDATE);
            assertArrayEquals(expected.toByteArray(), actual.toByteArray());
        } finally {
            local.dispose();
            remote.dispose();
        }
    }

    @Test
    public void anEncryptedDocument() throws Exception {
        assertRendersAsLocal(resource("/updater/encrypted-aes-v2.pdf"), chunks(4 * 1024), 3);
    }

    @Test
    public void junkBeforeTheHeader() throws Exception {
        byte[] pdf = resource("/updater/R&D-05-Carbon.pdf");
        byte[] junk = "HTTP/1.1 junk that some servers prepend\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = Arrays.copyOf(junk, junk.length + pdf.length);
        System.arraycopy(pdf, 0, bytes, junk.length, pdf.length);
        assertRendersAsLocal(bytes, chunks(2 * 1024), 2);
    }

    @Test
    public void aBrokenCrossReferenceFetchesEverythingAndStillOpens() throws Exception {
        byte[] bytes = resource("/updater/R&D-05-Carbon.pdf");
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        int startxref = text.lastIndexOf("startxref");
        // point startxref at nothing: the index is rebuilt by scanning the whole file
        byte[] broken = (text.substring(0, startxref) + "startxref\n7\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
        assertRendersAsLocal(broken, chunks(4 * 1024), 2);
        assertTrue(bytesServed.get() >= broken.length);
    }

    @Test
    public void aServerWithoutRangesSendsTheWholeDocument() throws Exception {
        mode = Mode.NO_RANGES;
        assertRendersAsLocal(resource("/updater/R&D-05-Carbon.pdf"), chunks(4 * 1024), 2);
        assertEquals(1, requests.get(), "the first answer was the document");
    }

    @Test
    public void aCompressedAnswerIsNeverTakenForRanges() throws Exception {
        mode = Mode.GZIP;
        byte[] bytes = resource("/updater/R&D-05-Carbon.pdf");
        served = bytes;
        // the client asked for identity and got gzip anyway: it can't address that by byte range, so it asks
        // again for the whole thing, which the JDK client doesn't unzip either. Opening must fail cleanly or
        // succeed; it must not render garbage from misplaced ranges.
        Document remote = new Document();
        try {
            remote.setUrl(url(), chunks(4 * 1024));
            fail("a gzip body isn't a PDF");
        } catch (IOException expected) {
            // not a PDF
        } finally {
            remote.dispose();
        }
    }

    @Test
    public void requestHeadersGoWithEveryRequest() throws Exception {
        requiredAuthorization = "Bearer 662";
        byte[] bytes = resource("/updater/R&D-05-Carbon.pdf");
        assertRendersAsLocal(bytes, UrlLoadOptions.builder().chunkSize(2 * 1024)
                .header("Authorization", "Bearer 662").build(), 2);
        assertTrue(requests.get() > 1, "ranges were requested");
        Document denied = new Document();
        try {
            assertThrows(IOException.class, () -> denied.setUrl(url(), chunks(2 * 1024)));
        } finally {
            denied.dispose();
        }
    }

    @Test
    public void rangeRequestsOffDownloadsFirst() throws Exception {
        byte[] bytes = resource("/updater/R&D-05-Carbon.pdf");
        assertRendersAsLocal(bytes, UrlLoadOptions.builder().rangeRequests(false).build(), 1);
        assertEquals(1, requests.get());
        assertEquals(bytes.length, bytesServed.get());
    }
}
