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
package org.icepdf.fx.render;

import org.icepdf.core.util.PoolSizing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.icepdf.fx.render.RenderService.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** The shared render pool's ordering, cancellation and out-of-memory gate.  No FX toolkit needed. */
class RenderServiceTest {

    private static final RenderService.Owner FOREGROUND = () -> true;
    private static final RenderService.Owner BACKGROUND = () -> false;

    private static RenderService service(int threads, AtomicLong clock) {
        return new RenderService(new PoolSizing.Size(threads, PoolSizing.Source.EXPLICIT, 8), clock::get);
    }

    /** Occupies one worker until released; returns once it is running. */
    private static CountDownLatch block(RenderService service) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service.submit(FOREGROUND, TILE, "blocker", () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        return release;
    }

    @DisplayName("queued work runs foreground first, then by kind, then first in first out")
    @Test
    void order() throws Exception {
        RenderService service = service(1, new AtomicLong());
        CountDownLatch release = block(service);
        List<String> ran = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(8);
        Object[][] submits = {
                {BACKGROUND, THUMBNAIL, "bg-thumb"},
                {FOREGROUND, PREVIEW, "fg-preview"},
                {BACKGROUND, TILE, "bg-tile"},
                {FOREGROUND, TILE, "fg-tile-1"},
                {FOREGROUND, TEXT, "fg-text"},
                {FOREGROUND, THUMBNAIL, "fg-thumb"},
                {FOREGROUND, TILE, "fg-tile-2"},
                {FOREGROUND, ANNOTATION, "fg-annotation"},
        };
        for (Object[] s : submits) {
            String label = (String) s[2];
            service.submit((RenderService.Owner) s[0], (RenderService.Kind) s[1], label, () -> {
                ran.add(label);
                done.countDown();
            });
        }
        assertEquals(8, service.queued());
        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("fg-tile-1", "fg-tile-2", "fg-annotation", "fg-text", "fg-preview", "fg-thumb",
                "bg-tile", "bg-thumb"), ran);
    }

    @DisplayName("an owner coming to the front takes its queued work ahead; going behind gives way")
    @Test
    void reprioritise() throws Exception {
        RenderService service = service(1, new AtomicLong());
        CountDownLatch release = block(service);
        boolean[] front = {false};
        RenderService.Owner switching = () -> front[0];
        List<String> ran = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(4);
        Future<?> first = service.submit(switching, TILE, "was-behind", () -> {
            ran.add("was-behind");
            done.countDown();
        });
        service.submit(FOREGROUND, TILE, "front", () -> {
            ran.add("front");
            done.countDown();
        });
        RenderService.Owner leaving = new RenderService.Owner() {
            boolean foreground = true;

            @Override
            public boolean isForeground() {
                boolean was = foreground;
                foreground = false; // in front when submitting, behind from then on
                return was;
            }
        };
        service.submit(leaving, TILE, "was-front", () -> {
            ran.add("was-front");
            done.countDown();
        });
        service.submit(BACKGROUND, PREVIEW, "behind", () -> {
            ran.add("behind");
            done.countDown();
        });
        front[0] = true;
        service.reprioritise(switching);
        service.reprioritise(leaving);
        assertEquals(4, service.queued());
        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        // same kind, both in front now: submit order.  Behind: TILE before PREVIEW.
        assertEquals(List.of("was-behind", "front", "was-front", "behind"), ran);
        assertTrue(first.isDone(), "the caller's future is the re-ranked task");
    }

    @DisplayName("a cancelled task leaves the queue at once and never runs")
    @Test
    void cancelLeavesQueue() throws Exception {
        RenderService service = service(1, new AtomicLong());
        CountDownLatch release = block(service);
        AtomicInteger ran = new AtomicInteger();
        Future<?> dropped = service.submit(FOREGROUND, TILE, "dropped", ran::incrementAndGet);
        CountDownLatch kept = new CountDownLatch(1);
        service.submit(BACKGROUND, THUMBNAIL, "kept", kept::countDown);
        assertEquals(2, service.queued());
        service.cancel(dropped);
        assertTrue(dropped.isCancelled());
        assertEquals(1, service.queued(), "removed from the queue, not left for a worker to skip");
        release.countDown();
        assertTrue(kept.await(10, TimeUnit.SECONDS));
        assertEquals(0, ran.get());
    }

    @DisplayName("cancelling a running task interrupts it")
    @Test
    void cancelInterrupts() throws Exception {
        RenderService service = service(1, new AtomicLong());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Future<?> task = service.submit(FOREGROUND, TILE, "long", () -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        service.cancel(task);
        assertTrue(interrupted.await(10, TimeUnit.SECONDS));
    }

    @DisplayName("after an out-of-memory paint, paints run one at a time until the window passes")
    @Test
    void outOfMemoryGate() throws Exception {
        AtomicLong clock = new AtomicLong(1_000);
        RenderService service = service(2, clock);
        assertFalse(service.isLowMemory());
        assertThrows(OutOfMemoryError.class, () -> service.paint(() -> {
            throw new OutOfMemoryError("test");
        }));
        assertTrue(service.isLowMemory());
        assertEquals(1, maxConcurrentPaints(service), "gated: serial");

        clock.addAndGet(RenderService.LOW_MEMORY_MS - 1);
        assertTrue(service.isLowMemory(), "still inside the window");
        clock.addAndGet(1);
        assertFalse(service.isLowMemory(), "lifted after the window");
        // both paints meet at a barrier, which only works if they run together.
        CyclicBarrier together = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger met = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            service.submit(FOREGROUND, TILE, "paint", () -> {
                try {
                    service.paint(() -> {
                        try {
                            together.await(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            return 0;
                        }
                        return met.incrementAndGet();
                    });
                } catch (Exception e) {
                    // left at 0 met
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(20, TimeUnit.SECONDS));
        assertEquals(2, met.get(), "ungated: paints run in parallel");
    }

    @DisplayName("an out-of-memory paint inside the window restarts it")
    @Test
    void outOfMemoryRestartsWindow() {
        AtomicLong clock = new AtomicLong(0);
        RenderService service = service(1, clock);
        assertThrows(OutOfMemoryError.class, () -> service.paint(() -> {
            throw new OutOfMemoryError("first");
        }));
        clock.addAndGet(RenderService.LOW_MEMORY_MS / 2);
        assertThrows(OutOfMemoryError.class, () -> service.paint(() -> {
            throw new OutOfMemoryError("second");
        }));
        clock.addAndGet(RenderService.LOW_MEMORY_MS / 2 + 1);
        assertTrue(service.isLowMemory(), "the window runs from the second error");
        clock.addAndGet(RenderService.LOW_MEMORY_MS);
        assertFalse(service.isLowMemory());
    }

    /** Runs four short paints on two workers and returns how many ever overlapped. */
    private static int maxConcurrentPaints(RenderService service) throws InterruptedException {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            service.submit(FOREGROUND, TILE, "paint", () -> {
                try {
                    service.paint(() -> {
                        max.accumulateAndGet(running.incrementAndGet(), Math::max);
                        Thread.sleep(50);
                        running.decrementAndGet();
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return max.get();
    }
}
