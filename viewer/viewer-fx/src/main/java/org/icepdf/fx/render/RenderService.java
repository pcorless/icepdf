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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * One prioritised render pool for the whole process, shared by every view.
 * <p>
 * Tasks run in {@code (foreground, kind, sequence)} order: work for a view in the focused window
 * before work for one behind it, then by {@link Kind} (visible tiles first, thumbnails last), then
 * first in, first out.  An owner's foreground flag is read when its task is submitted, and again by
 * {@link #reprioritise} when it changes.
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

        /** The owner's view, for diagnostics; null if it doesn't report one.  On the FX thread. */
        default ViewStats stats() {
            return null;
        }
    }

    /**
     * One view, for diagnostics.
     *
     * @param document     the document's file name, or empty
     * @param currentPage  1-based
     * @param cacheEntries tiles and previews cached
     * @param layers       page layers on screen
     * @param inFlight     render jobs queued or running
     * @param failed       tiles that failed and aren't asked for again
     * @param textPages    pages whose text is loaded
     */
    public record ViewStats(String document, int pages, double zoom, int currentPage, long cacheUsed,
                            long cacheBudget, int cacheEntries, int layers, int inFlight, int failed,
                            int textPages, boolean foreground) {
    }

    /** A pool thread: idle when {@code task} is null. */
    public record ThreadInfo(String name, String task, Kind kind, String view, long runningMs) {
    }

    /** A queued task, in run order. */
    public record QueuedInfo(Kind kind, boolean foreground, String view, String label, long ageMs) {
    }

    /**
     * The pool, its queue and the process's heap at one moment.  Weakly consistent: tasks start
     * and finish while it is taken.
     *
     * @param meanMs           mean run time of finished tasks, by kind (kinds yet to run are absent)
     * @param lowMemoryMsLeft  while paints are serialised, how long until the gate lifts
     * @param views            the views of registered owners
     */
    public record Snapshot(PoolSizing.Size size, List<ThreadInfo> threads, List<QueuedInfo> queue,
                           long completed, long cancelled, Map<Kind, Double> meanMs, boolean lowMemory,
                           long lowMemoryMsLeft, List<ViewStats> views, long heapUsed, long heapCommitted,
                           long heapMax, long gcCount, long gcMs) {
        /** Threads running a task. */
        public int active() {
            return (int) threads.stream().filter(t -> t.task() != null).count();
        }
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

    // diagnostics: the pool's threads (they exit when idle), what each runs, and run times by kind.
    private final Set<Thread> workers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<Thread, Running> running = new ConcurrentHashMap<>();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final LongAdder[] runNanos = adders();
    private final LongAdder[] runs = adders();
    // owners that report a view; weak, so a view dropped without dispose doesn't stay listed.
    private final Map<Owner, Boolean> owners = Collections.synchronizedMap(new WeakHashMap<>());

    private record Running(RenderTask task, long startNanos) {
    }

    RenderService(PoolSizing.Size size, LongSupplier clockMs) {
        this.size = size;
        this.clockMs = clockMs;
        int threads = size.threads();
        executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), daemonThreads("icepdf-fx-render")) {
            @Override
            protected void beforeExecute(Thread thread, Runnable task) {
                running.put(thread, new Running((RenderTask) task, System.nanoTime()));
            }

            @Override
            protected void afterExecute(Runnable task, Throwable thrown) {
                Running done = running.remove(Thread.currentThread());
                if (done == null || ((RenderTask) task).isCancelled()) return;
                completed.incrementAndGet();
                int kind = done.task().kind.ordinal();
                runNanos[kind].add(System.nanoTime() - done.startNanos());
                runs[kind].increment();
            }
        };
        executor.allowCoreThreadTimeOut(true);
    }

    private static LongAdder[] adders() {
        LongAdder[] adders = new LongAdder[Kind.values().length];
        for (int i = 0; i < adders.length; i++) adders[i] = new LongAdder();
        return adders;
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
        RenderTask task = new RenderTask(job, owner, owner.isForeground(), kind, sequence.getAndIncrement(), label);
        executor.execute(task);
        return task;
    }

    /**
     * Re-ranks an owner's queued tasks after its {@link Owner#isForeground()} changed: a window
     * coming to the front jumps the queue with the work it already asked for, and one going behind
     * gives way.  Running tasks are left alone.
     */
    public void reprioritise(Owner owner) {
        boolean foreground = owner.isForeground();
        for (Object queued : executor.getQueue().toArray()) {
            // re-queue the same task, so callers' futures stay valid; a task a worker took meanwhile
            // isn't removed, and is left to run.
            if (queued instanceof RenderTask task && task.owner == owner && task.foreground != foreground
                    && executor.remove(task)) {
                task.foreground = foreground;
                executor.execute(task);
            }
        }
    }

    /**
     * Cancels a task, interrupting it if it is running, and takes it off the queue if it is not:
     * a cancelled task doesn't sit in the queue until a worker reaches it.
     */
    public void cancel(Future<?> task) {
        if (task == null) return;
        if (task.cancel(true)) cancelled.incrementAndGet();
        if (task instanceof Runnable runnable) executor.remove(runnable);
    }

    /** Lists an owner's view in {@link #snapshot()} while the owner is alive. */
    public void register(Owner owner) {
        owners.put(owner, Boolean.TRUE);
    }

    public void unregister(Owner owner) {
        owners.remove(owner);
    }

    /**
     * The pool now, for diagnostics; cheap enough to poll a few times a second.  Call it on the FX
     * thread: it asks registered owners for their {@link Owner#stats() views}.
     */
    public Snapshot snapshot() {
        long now = System.nanoTime();
        Map<Owner, ViewStats> views = new IdentityHashMap<>();
        Owner[] listed;
        synchronized (owners) {
            listed = owners.keySet().toArray(new Owner[0]);
        }
        for (Owner owner : listed) {
            ViewStats stats = owner.stats();
            if (stats != null) views.put(owner, stats);
        }

        List<ThreadInfo> threads = new ArrayList<>();
        Thread[] live;
        synchronized (workers) {
            live = workers.toArray(new Thread[0]);
        }
        Arrays.sort(live, (a, b) -> a.getName().compareTo(b.getName()));
        for (Thread thread : live) {
            if (!thread.isAlive()) continue;
            Running run = running.get(thread);
            threads.add(run == null ? new ThreadInfo(thread.getName(), null, null, "", 0)
                    : new ThreadInfo(thread.getName(), run.task().label, run.task().kind,
                    viewName(views, run.task().owner), (now - run.startNanos()) / 1_000_000));
        }

        Object[] queued = executor.getQueue().toArray();
        RenderTask[] tasks = new RenderTask[queued.length];
        for (int i = 0; i < queued.length; i++) tasks[i] = (RenderTask) queued[i];
        Arrays.sort(tasks);
        List<QueuedInfo> queue = new ArrayList<>(tasks.length);
        for (RenderTask task : tasks) {
            queue.add(new QueuedInfo(task.kind, task.foreground, viewName(views, task.owner), task.label,
                    (now - task.queuedNanos) / 1_000_000));
        }

        Map<Kind, Double> meanMs = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            long count = runs[kind.ordinal()].sum();
            if (count > 0) meanMs.put(kind, runNanos[kind.ordinal()].sum() / 1e6 / count);
        }
        boolean gated = isLowMemory();
        long left = gated ? Math.max(0, LOW_MEMORY_MS - (clockMs.getAsLong() - lowMemoryAt)) : 0;

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long gcCount = 0, gcMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMs += Math.max(0, gc.getCollectionTime());
        }
        return new Snapshot(size, List.copyOf(threads), List.copyOf(queue), completed.get(), cancelled.get(),
                Collections.unmodifiableMap(meanMs), gated, left, List.copyOf(views.values()), heap.getUsed(),
                heap.getCommitted(), heap.getMax(), gcCount, gcMs);
    }

    private static String viewName(Map<Owner, ViewStats> views, Owner owner) {
        ViewStats stats = views.get(owner);
        return stats != null ? stats.document() : "";
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

    private ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + count.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            workers.add(t);
            return t;
        };
    }

    /**
     * A queued job and its place in the queue.  Jobs go through {@code execute} wrapped in this
     * rather than {@code submit}, whose plain {@code FutureTask} isn't comparable.
     */
    static final class RenderTask extends FutureTask<Void> implements Comparable<RenderTask> {
        final Owner owner;
        // only changed while the task is out of the queue (see reprioritise), so the order holds.
        boolean foreground;
        final Kind kind;
        final long sequence;
        final String label;
        final long queuedNanos = System.nanoTime();

        RenderTask(Runnable job, Owner owner, boolean foreground, Kind kind, long sequence, String label) {
            super(job, null);
            this.owner = owner;
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
