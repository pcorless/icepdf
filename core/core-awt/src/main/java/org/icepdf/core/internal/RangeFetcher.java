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

import java.io.Closeable;
import java.io.IOException;

/**
 * Reads byte ranges of a remote document (GH-662). Internal: implemented over HTTP range requests, and by tests.
 * Called from many threads at once.
 */
public interface RangeFetcher extends Closeable {

    /**
     * @return the document's length in bytes
     */
    long length();

    /**
     * Reads {@code length} bytes starting at {@code from} into {@code target} at {@code offset}.
     *
     * @param attempt what the fetch took, filled in as it goes
     * @throws IOException if the bytes can't be had; the caller may retry
     */
    void fetch(long from, byte[] target, int offset, int length, FetchAttempt attempt) throws IOException;

    @Override
    default void close() {
    }
}
