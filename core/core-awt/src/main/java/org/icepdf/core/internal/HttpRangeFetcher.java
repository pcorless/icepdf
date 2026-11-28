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
package org.icepdf.core.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches byte ranges of a document over HTTP range requests (GH-662).
 * <p>
 * {@link #open} asks for the first chunk with a {@code Range} header. A {@code 206 Partial Content} answer with a
 * {@code Content-Range} total, and no transfer compression, makes a range fetcher; anything else means the server
 * can't serve ranges, and the answer is read to the end as the whole document instead, so no request is wasted.
 */
public final class HttpRangeFetcher implements RangeFetcher {

    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+)");

    /** Tries after a 429 or 503 answer before the range fails. */
    static final int BUSY_RETRIES = 2;
    static final long MAX_RETRY_PAUSE_MILLIS = 10_000;

    private final HttpClient client;
    private final URI uri;
    private final Map<String, String> headers;
    private final Duration requestTimeout;
    private final long length;

    /** What {@link #open} learned: a range fetcher and the first chunk, or the whole document. */
    public static final class Opened {
        private final HttpRangeFetcher fetcher;
        private final byte[] firstChunk;
        private final byte[] wholeDocument;

        private Opened(HttpRangeFetcher fetcher, byte[] firstChunk, byte[] wholeDocument) {
            this.fetcher = fetcher;
            this.firstChunk = firstChunk;
            this.wholeDocument = wholeDocument;
        }

        /** @return the fetcher, or null when the server sent the whole document */
        public HttpRangeFetcher getFetcher() {
            return fetcher;
        }

        /** @return the bytes from offset 0 already fetched with the fetcher */
        public byte[] getFirstChunk() {
            return firstChunk;
        }

        /** @return the whole document when the server didn't serve a range, else null */
        public byte[] getWholeDocument() {
            return wholeDocument;
        }
    }

    private HttpRangeFetcher(HttpClient client, URI uri, Map<String, String> headers, Duration requestTimeout,
                             long length) {
        this.client = client;
        this.uri = uri;
        this.headers = headers;
        this.requestTimeout = requestTimeout;
        this.length = length;
    }

    /**
     * Asks for the first {@code firstChunk} bytes of the document.
     *
     * @param client         the client to send requests with
     * @param headers        extra request headers, such as authorization
     * @param requestTimeout the time allowed each request, or null for none
     */
    public static Opened open(HttpClient client, URI uri, Map<String, String> headers, Duration requestTimeout,
                              int firstChunk) throws IOException {
        HttpRequest request = request(uri, headers, requestTimeout)
                .header("Range", "bytes=0-" + (firstChunk - 1))
                .build();
        HttpResponse<byte[]> response = send(client, request);
        int status = response.statusCode();
        HttpHeaders responseHeaders = response.headers();
        boolean encoded = responseHeaders.firstValue("Content-Encoding")
                .map(v -> !v.equalsIgnoreCase("identity")).orElse(false);
        if (status == 206 && !encoded) {
            Optional<String> contentRange = responseHeaders.firstValue("Content-Range");
            Matcher m = contentRange.map(CONTENT_RANGE::matcher).orElse(null);
            if (m != null && m.matches() && Long.parseLong(m.group(1)) == 0) {
                long total = Long.parseLong(m.group(3));
                byte[] bytes = response.body();
                long expected = Long.parseLong(m.group(2)) + 1;
                if (bytes.length == expected) {
                    return new Opened(new HttpRangeFetcher(client, uri, headers, requestTimeout, total),
                            bytes, null);
                }
            }
            // a partial answer we can't place: ask again for everything
            return new Opened(null, null, fetchAll(client, uri, headers, requestTimeout));
        }
        if (status >= 200 && status < 300) {
            // the server ignored the range and sent the document
            return new Opened(null, null, response.body());
        }
        throw new IOException("HTTP " + status + " for " + uri);
    }

    /**
     * Fetches the whole document with one plain request.
     */
    public static byte[] fetchAll(HttpClient client, URI uri, Map<String, String> headers, Duration timeout)
            throws IOException {
        HttpResponse<byte[]> response = send(client, request(uri, headers, timeout).build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }

    private static HttpRequest.Builder request(URI uri, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
        if (timeout != null) {
            builder.timeout(timeout);
        }
        // a compressed transfer can't be addressed by byte range
        builder.header("Accept-Encoding", "identity");
        headers.forEach(builder::header);
        return builder;
    }

    /**
     * Sends a request and waits for the answer without heeding interrupts: a renderer that cancels its page
     * interrupts its thread, and the bytes it was waiting for are worth keeping (they're read like a slow disk
     * read). The thread stays interrupted for its caller to see. The request timeout bounds the wait.
     */
    private static HttpResponse<byte[]> send(HttpClient client, HttpRequest request) throws IOException {
        try {
            // a body is a chunk, or the whole document read anyway; byte arrays also keep clear of JDK 11's
            // response InputStream, whose internal assertion fails under -ea
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).join();
        } catch (CompletionException | CancellationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RangeFetchException("Could not fetch " + request.uri() + ": " + cause, cause);
        }
    }

    private static boolean isBusy(int status) {
        return status == 429 || status == 503;
    }

    /**
     * @return the pause a busy answer asks for in Retry-After's seconds, else one that doubles each retry;
     * at most {@link #MAX_RETRY_PAUSE_MILLIS}
     */
    static long retryAfterMillis(HttpResponse<?> response, int retry) {
        long millis = 500L << retry;
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                millis = Long.parseLong(retryAfter.trim()) * 1000;
            } catch (NumberFormatException e) {
                // an HTTP date: keep the doubling pause
            }
        }
        return Math.max(0, Math.min(millis, MAX_RETRY_PAUSE_MILLIS));
    }

    // not interruptible, like the fetch: the viewer interrupts the renders it cancels
    private static void pause(long millis) {
        long until = System.nanoTime() + millis * 1_000_000;
        boolean interrupted = false;
        for (long left = millis; left > 0; left = (until - System.nanoTime()) / 1_000_000) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public void fetch(long from, byte[] target, int offset, int count) throws IOException {
        long last = from + count - 1;
        HttpRequest request = request(uri, headers, requestTimeout)
                .header("Range", "bytes=" + from + "-" + last)
                .build();
        HttpResponse<byte[]> response = send(client, request);
        // a server that says it's busy gets a pause, as long as it asks for (within reason), then another try
        for (int retry = 1; retry <= BUSY_RETRIES && isBusy(response.statusCode()); retry++) {
            pause(retryAfterMillis(response, retry));
            response = send(client, request);
        }
        if (response.statusCode() != 206) {
            throw new RangeFetchException("HTTP " + response.statusCode() + " for range " + from + "-" + last
                    + " of " + uri);
        }
        Matcher m = response.headers().firstValue("Content-Range").map(CONTENT_RANGE::matcher).orElse(null);
        if (m == null || !m.matches() || Long.parseLong(m.group(1)) != from) {
            throw new RangeFetchException("Range " + from + "-" + last + " of " + uri + " answered with "
                    + response.headers().firstValue("Content-Range").orElse("no Content-Range"));
        }
        byte[] body = response.body();
        if (body.length != count) {
            throw new RangeFetchException("Short range " + from + "-" + last + " of " + uri + ": " + body.length + " bytes");
        }
        System.arraycopy(body, 0, target, offset, count);
    }
}
