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

import java.time.Duration;

/**
 * A slower network than the real one, for trying out a remote document's fetching (GH-662), like a browser's
 * network throttling: each request waits the latency before it starts, and its bytes then flow no faster than the
 * bandwidth, which the document's requests share. Settings can change while documents are open; the next request
 * follows them. Pass it with {@link UrlLoadOptions.Builder#throttle}; one throttle may serve many documents, each
 * with its own share of the bandwidth.
 * <p>
 * The properties {@value UrlLoadOptions#THROTTLE_LATENCY_PROPERTY} (milliseconds) and
 * {@value UrlLoadOptions#THROTTLE_BANDWIDTH_PROPERTY} (kilobytes a second) give every document a throttle without
 * code.
 *
 * @since 8.1
 */
public final class NetworkThrottle {

    private volatile long latencyMillis;
    private volatile long bytesPerSecond;

    /**
     * @param latency        the wait before each request; zero or null for none
     * @param bytesPerSecond the bandwidth; 0 for no limit
     */
    public NetworkThrottle(Duration latency, long bytesPerSecond) {
        set(latency, bytesPerSecond);
    }

    /** @return a throttle that does nothing until it's {@link #set} */
    public static NetworkThrottle off() {
        return new NetworkThrottle(Duration.ZERO, 0);
    }

    /**
     * @param latency        the wait before each request; zero or null for none
     * @param bytesPerSecond the bandwidth; 0 for no limit
     */
    public void set(Duration latency, long bytesPerSecond) {
        this.latencyMillis = latency == null ? 0 : Math.max(0, latency.toMillis());
        this.bytesPerSecond = Math.max(0, bytesPerSecond);
    }

    public Duration getLatency() {
        return Duration.ofMillis(latencyMillis);
    }

    /** @return the bandwidth, 0 for no limit */
    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    /** @return true when it slows nothing */
    public boolean isOff() {
        return latencyMillis == 0 && bytesPerSecond == 0;
    }

    @Override
    public String toString() {
        return isOff() ? "off" : latencyMillis + " ms, " + (bytesPerSecond == 0 ? "unlimited" : bytesPerSecond + " B/s");
    }
}
