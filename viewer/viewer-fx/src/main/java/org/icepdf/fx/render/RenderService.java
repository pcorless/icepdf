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

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * One prioritised render pool for the whole process, shared by every view.
 * <p>
 * Tasks run in {@code (foreground, kind, sequence)} order: work for a view in the focused window
 * before work for one behind it, then by {@link Kind} (visible tiles first, thumbnails last), then
 * first in, first out.  An owner's foreground flag is read once, when its task is submitted.
 * <p>
 * The pool is sized from the machine with core's {@link PoolSizing} rule, half the cores within
 * {@code [2, cap]}: page paints are memory-bandwidth heavy and drive core's own decode pools as
 * well.  {@value #THREADS_PROPERTY} sets the count outright and {@value #CAP_PROPERTY} the cap
 * (default {@value #DEFAULT_CAP}).  Threads are low-priority daemons that exit after 30s idle, so
 * the pool is never shut down.
 * <p>
 * The service also owns the out-of-memory gate: after any {@link #paint paint} runs out of heap,
 * every paint in the process runs one at a time until {@link #LOW_MEMORY_MS} pass without another.
 * Running out of heap is the process's problem, not one view's, and N threads each painting a
 * dense page at once is what multiplies it.
 */
public final class RenderService {

    /** Explicit render thread count. */
    public static final String THREADS_PROPERTY = "org.icepdf.fx.render.threads";
    /** Cap on the auto-sized render thread count. */
    public static final String CAP_PROPERTY = "org.icepdf.fx.render.threadPoolCap";
    public static final int DEFAULT_CAP = 8;
    /** How long paints stay serial after the last out-of-memory error. */
    public static final long LOW_MEMORY_MS = 60_000;

    /** What a task is, most urgent first. */
    public enum Kind {
        /** Visible page tiles. */
        TILE,
        /** Annotation layers and the drag proxy. */
        ANNOTATION,
        /** Page text, for selection, search and the caret. */
        TEXT,
        /** The low-resolution whole-page pass. */
        PREVIEW,
        THUMBNAIL
    }

    /** Whoever submits work, one per view. */
    public interface Owner {
        /**
         * Whether the owner's view is showing in the focused window.  Read on the submitting thread
         * at each submit, so it should be a cheap field read.
         */
        boolean isForeground();
    }

    /** A paint run through the out-of-memory gate. */
    @FunctionalInterface
    public interface Paint<T> {
        T run() throws InterruptedException;
    }

    private static final class Holder {
        static final RenderService SHARED = new RenderService(
                PoolSizing.resolve(THREADS_PROPERTY, CAP_PROPERTY, DEFAULT_CAP, cores -> cores / 2),
                () -> System.nanoTime() / 1_000_000);
    }

    private final PoolSizing.Size size;
    private final ThreadPoolExecutor executor;
    private final AtomicLong sequence = new AtomicLong();
    private final LongSupplier clockMs;
    private final Semaphore serialPaint = new Semaphore(1);
    // clock time of the last out-of-memory paint; valid while lowMemory is set.
    private volatile boolean lowMemory;
    private volatile long lowMemoryAt;

    RenderService(PoolSizing.Size size, LongSupplier clockMs) {
        this.size = size;
        this.clockMs = clockMs;
        int threads = size.threads();
        executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), daemonThreads("icepdf-fx-render"));
        executor.allowCoreThreadTimeOut(true);
    }

    /** The process's pool, created on first use. */
    public static RenderService shared() {
        return Holder.SHARED;
    }

    /** The thread count and where it came from. */
    public PoolSizing.Size size() {
        return size;
    }

    /**
     * Queues a job.
     *
     * @param owner the view it is for; its {@link Owner#isForeground()} is read now
     * @param kind  what it is
     * @param label a short description for diagnostics, e.g. {@code "report.pdf p12 tile"}
     * @return the queued task, for {@link #cancel}
     */
    public Future<?> submit(Owner owner, Kind kind, String label, Runnable job) {
        RenderTask task = new RenderTask(job, owner.isForeground(), kind, sequence.getAndIncrement(), label);
        executor.execute(task);
        return task;
    }

    /**
     * Cancels a task, interrupting it if it is running, and takes it off the queue if it is not:
     * a cancelled task doesn't sit in the queue until a worker reaches it.
     */
    public void cancel(Future<?> task) {
        if (task == null) return;
        task.cancel(true);
        if (task instanceof Runnable runnable) executor.remove(runnable);
    }

    /** Tasks waiting for a thread. */
    public int queued() {
        return executor.getQueue().size();
    }

    /** Tasks running now. */
    public int active() {
        return executor.getActiveCount();
    }

    /**
     * Runs a paint, alone if a paint ran out of memory in the last {@link #LOW_MEMORY_MS}.  An
     * out-of-memory error is rethrown after it (re)starts the window.
     */
    public <T> T paint(Paint<T> paint) throws InterruptedException {
        if (!isLowMemory()) {
            try {
                return paint.run();
            } catch (OutOfMemoryError e) {
                outOfMemory();
                throw e;
            }
        }
        serialPaint.acquire();
        try {
            return paint.run();
        } catch (OutOfMemoryError e) {
            outOfMemory();
            throw e;
        } finally {
            serialPaint.release();
        }
    }

    /** Whether paints are serialised because one ran out of memory recently. */
    public boolean isLowMemory() {
        if (!lowMemory) return false;
        if (clockMs.getAsLong() - lowMemoryAt < LOW_MEMORY_MS) return true;
        lowMemory = false;
        return false;
    }

    private void outOfMemory() {
        lowMemoryAt = clockMs.getAsLong();
        lowMemory = true;
    }

    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    /**
     * A queued job and its place in the queue.  Jobs go through {@code execute} wrapped in this
     * rather than {@code submit}, whose plain {@code FutureTask} isn't comparable.
     */
    static final class RenderTask extends FutureTask<Void> implements Comparable<RenderTask> {
        final boolean foreground;
        final Kind kind;
        final long sequence;
        final String label;

        RenderTask(Runnable job, boolean foreground, Kind kind, long sequence, String label) {
            super(job, null);
            this.foreground = foreground;
            this.kind = kind;
            this.sequence = sequence;
            this.label = label;
        }

        @Override
        public int compareTo(RenderTask other) {
            if (foreground != other.foreground) return foreground ? -1 : 1;
            int byKind = kind.compareTo(other.kind);
            return byKind != 0 ? byKind : Long.compare(sequence, other.sequence);
        }

        @Override
        public String toString() {
            return (foreground ? "fg " : "bg ") + kind + " #" + sequence + " " + label;
        }
    }
}
