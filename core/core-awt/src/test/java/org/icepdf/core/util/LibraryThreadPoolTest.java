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
package org.icepdf.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared pools must only ever run on daemon threads, or an idle pool keeps the JVM alive after the last window closes. */
class LibraryThreadPoolTest {

    @DisplayName("tasks run on daemon threads, including the first one submitted right after the pool is created")
    @Test
    void daemonThreads() throws Exception {
        Library.shutdownThreadPool();
        CountDownLatch done = new CountDownLatch(2);
        AtomicBoolean commonDaemon = new AtomicBoolean();
        AtomicBoolean imageDaemon = new AtomicBoolean();
        Library.execute(() -> {
            commonDaemon.set(Thread.currentThread().isDaemon());
            done.countDown();
        });
        Library.executeImage(new java.util.concurrent.FutureTask<>(() -> {
            imageDaemon.set(Thread.currentThread().isDaemon());
            done.countDown();
            return null;
        }));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(commonDaemon.get(), "common pool thread");
        assertTrue(imageDaemon.get(), "image pool thread");
    }
}
