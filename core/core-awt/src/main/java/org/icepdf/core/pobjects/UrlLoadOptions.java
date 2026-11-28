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

import org.icepdf.core.internal.RangeCache;
import org.icepdf.core.util.Defs;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * How {@link Document#setUrl(java.net.URL, UrlLoadOptions)} opens a remote document (GH-662).
 * <p>
 * With range requests on (the default), an {@code http} or {@code https} document is fetched as it's read: the
 * end of the file for the cross-reference, then each object as a page needs it, so the first page shows without
 * the whole file arriving. Saving and signature validation fetch the rest. A server that doesn't serve byte
 * ranges sends the whole document instead, with no error. Other URL schemes are always read whole.
 * <pre>{@code
 * document.setUrl(url, UrlLoadOptions.builder()
 *         .header("Authorization", "Bearer " + token)
 *         .requestTimeout(Duration.ofSeconds(30))
 *         .build());
 * }</pre>
 * <p>
 * How the bytes are fetched can be tuned for a network without code: each builder setting starts from a system
 * property, when it's set.
 * <table>
 * <caption>System properties</caption>
 * <tr><th>Property</th><th>Default</th><th>Builder setting</th></tr>
 * <tr><td>{@value #RANGE_REQUESTS_PROPERTY}</td><td>true</td><td>{@link Builder#rangeRequests}</td></tr>
 * <tr><td>{@value #CHUNK_SIZE_PROPERTY}</td><td>65536</td><td>{@link Builder#chunkSize}</td></tr>
 * <tr><td>{@value #BLOCK_SIZE_PROPERTY}</td><td>2048</td><td>{@link Builder#blockSize}</td></tr>
 * <tr><td>{@value #PROBE_SIZE_PROPERTY}</td><td>512</td><td>{@link Builder#probeSize}</td></tr>
 * <tr><td>{@value #MAX_CONCURRENT_REQUESTS_PROPERTY}</td><td>4</td><td>{@link Builder#maxConcurrentRequests}</td></tr>
 * </table>
 * A high-latency link does better with bigger blocks and probes (fewer, larger requests); a slow or metered one with
 * smaller (fewer bytes). A value out of range is brought into range, and one that isn't a number is ignored.
 *
 * @since 8.1
 */
public final class UrlLoadOptions {

    private static final Logger logger = Logger.getLogger(UrlLoadOptions.class.getName());

    public static final String RANGE_REQUESTS_PROPERTY = "org.icepdf.core.url.rangeRequests";
    public static final String CHUNK_SIZE_PROPERTY = "org.icepdf.core.url.chunkSize";
    public static final String BLOCK_SIZE_PROPERTY = "org.icepdf.core.url.blockSize";
    public static final String PROBE_SIZE_PROPERTY = "org.icepdf.core.url.probeSize";
    public static final String MAX_CONCURRENT_REQUESTS_PROPERTY = "org.icepdf.core.url.maxConcurrentRequests";

    static final int MIN_CHUNK_SIZE = 1024;
    static final int MIN_BLOCK_SIZE = 256;
    static final int MIN_PROBE_SIZE = 64;

    private final boolean rangeRequests;
    private final int chunkSize;
    private final int blockSize;
    private final int probeSize;
    private final int maxConcurrentRequests;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    private UrlLoadOptions(Builder builder) {
        rangeRequests = builder.rangeRequests;
        chunkSize = builder.chunkSize;
        // a block or a probe bigger than a chunk would read ahead of what a chunk allows
        blockSize = Math.min(builder.blockSize, chunkSize);
        probeSize = Math.min(builder.probeSize, chunkSize);
        maxConcurrentRequests = builder.maxConcurrentRequests;
        connectTimeout = builder.connectTimeout;
        requestTimeout = builder.requestTimeout;
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        httpClient = builder.httpClient;
    }

    /**
     * @return options with range requests on and the default chunk size
     */
    public static UrlLoadOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isRangeRequests() {
        return rangeRequests;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getProbeSize() {
        return probeSize;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @return the client requests are sent with, or null for one built from the timeouts
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    public static final class Builder {
        private boolean rangeRequests = Defs.booleanProperty(RANGE_REQUESTS_PROPERTY, true);
        private int chunkSize = property(CHUNK_SIZE_PROPERTY, RangeCache.DEFAULT_CHUNK_SIZE, MIN_CHUNK_SIZE);
        private int blockSize = property(BLOCK_SIZE_PROPERTY, RangeCache.BLOCK_SIZE, MIN_BLOCK_SIZE);
        private int probeSize = property(PROBE_SIZE_PROPERTY, RangeCache.DEFAULT_PROBE_SIZE, MIN_PROBE_SIZE);
        private int maxConcurrentRequests = property(MAX_CONCURRENT_REQUESTS_PROPERTY,
                RangeCache.DEFAULT_MAX_CONCURRENT, 1);
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private final Map<String, String> headers = new LinkedHashMap<>();
        private HttpClient httpClient;

        private Builder() {
        }

        private static int property(String name, int defaultValue, int least) {
            int value = Defs.intProperty(name, defaultValue);
            if (value < least) {
                logger.warning(() -> name + "=" + value + " is below " + least + "; using " + least);
                return least;
            }
            return value;
        }

        /**
         * @param rangeRequests false to download the whole document before opening it, as
         *                      {@link Document#setUrl(java.net.URL)} does
         */
        public Builder rangeRequests(boolean rangeRequests) {
            this.rangeRequests = rangeRequests;
            return this;
        }

        /**
         * @param chunkSize the read-ahead of a range read, such as a stream's, 64 KB by default; at least 1 KB.
         *                  An object's dictionary is read in a few kilobytes.
         */
        public Builder chunkSize(int chunkSize) {
            if (chunkSize < MIN_CHUNK_SIZE) {
                throw new IllegalArgumentException("chunk size below 1 KB: " + chunkSize);
            }
            this.chunkSize = chunkSize;
            return this;
        }

        /**
         * @param blockSize the unit bytes are tracked and fetched in, 2 KB by default; at least 256 bytes, and at
         *                  most the chunk size. Smaller wastes fewer bytes around each object read alone; bigger
         *                  makes fewer requests.
         */
        public Builder blockSize(int blockSize) {
            if (blockSize < MIN_BLOCK_SIZE) {
                throw new IllegalArgumentException("block size below " + MIN_BLOCK_SIZE + ": " + blockSize);
            }
            this.blockSize = blockSize;
            return this;
        }

        /**
         * @param probeSize the first read of an object, 512 bytes by default (rounded up to the end of its block);
         *                  at least 64 bytes, and at most the chunk size. Most dictionaries end inside it; a bigger
         *                  object reads on in growing windows.
         */
        public Builder probeSize(int probeSize) {
            if (probeSize < MIN_PROBE_SIZE) {
                throw new IllegalArgumentException("probe size below " + MIN_PROBE_SIZE + ": " + probeSize);
            }
            this.probeSize = probeSize;
            return this;
        }

        /**
         * @param maxConcurrentRequests the requests a document has out at once, 4 by default (what a browser
         *                              allows a host); at least 1. Every reader shares them: renders, and the
         *                              prefetch of {@link PageTree#prefetchPages()}.
         */
        public Builder maxConcurrentRequests(int maxConcurrentRequests) {
            if (maxConcurrentRequests < 1) {
                throw new IllegalArgumentException("max concurrent requests below 1: " + maxConcurrentRequests);
            }
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        /**
         * @param connectTimeout the time allowed to connect; ignored with {@link #httpClient}
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * @param requestTimeout the time allowed each request, or null for no limit
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Adds a header to every request, such as {@code Authorization}.
         */
        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name), Objects.requireNonNull(value));
            return this;
        }

        /**
         * @param httpClient the client to send requests with: for a proxy, an authenticator, cookies or TLS
         *                   settings. It should follow redirects.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public UrlLoadOptions build() {
            return new UrlLoadOptions(this);
        }
    }
}
