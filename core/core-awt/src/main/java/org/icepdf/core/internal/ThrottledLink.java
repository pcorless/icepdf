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

import org.icepdf.core.pobjects.NetworkThrottle;

import java.io.IOException;

/**
 * One document's link through a {@link NetworkThrottle} (GH-662): each transfer waits the latency, in parallel
 * with the others, then takes its turn on a pipe of the throttle's bandwidth, shared by the document's requests.
 */
public final class ThrottledLink {

    private final NetworkThrottle throttle;
    // guarded by this: when the pipe is next free
    private long freeAtNanos;

    public ThrottledLink(NetworkThrottle throttle) {
        this.throttle = throttle;
    }

    /**
     * Waits as long as moving {@code bytes} over the throttled link would take.
     */
    public void transfer(long bytes) {
        Uninterruptibly.sleep(throttle.getLatency().toMillis());
        long bandwidth = throttle.getBytesPerSecond();
        if (bandwidth <= 0 || bytes <= 0) {
            return;
        }
        long doneAt;
        synchronized (this) {
            long now = System.nanoTime();
            long start = Math.max(now, freeAtNanos);
            freeAtNanos = start + bytes * 1_000_000_000L / bandwidth;
            doneAt = freeAtNanos;
        }
        Uninterruptibly.sleep((doneAt - System.nanoTime()) / 1_000_000);
    }

    /**
     * @return a fetcher that goes over this link
     */
    public RangeFetcher wrap(RangeFetcher fetcher) {
        return new RangeFetcher() {
            @Override
            public long length() {
                return fetcher.length();
            }

            @Override
            public void fetch(long from, byte[] target, int offset, int length, FetchAttempt attempt)
                    throws IOException {
                transfer(length);
                fetcher.fetch(from, target, offset, length, attempt);
            }

            @Override
            public void close() {
                fetcher.close();
            }
        };
    }
}
