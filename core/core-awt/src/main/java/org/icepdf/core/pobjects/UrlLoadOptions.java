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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 *
 * @since 8.1
 */
public final class UrlLoadOptions {

    private final boolean rangeRequests;
    private final int chunkSize;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    private UrlLoadOptions(Builder builder) {
        rangeRequests = builder.rangeRequests;
        chunkSize = builder.chunkSize;
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
        private boolean rangeRequests = true;
        private int chunkSize = 64 * 1024;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private final Map<String, String> headers = new LinkedHashMap<>();
        private HttpClient httpClient;

        private Builder() {
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
         * @param chunkSize the bytes each request asks for at least, 64 KB by default; at least 1 KB
         */
        public Builder chunkSize(int chunkSize) {
            if (chunkSize < 1024) {
                throw new IllegalArgumentException("chunk size below 1 KB: " + chunkSize);
            }
            this.chunkSize = chunkSize;
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
