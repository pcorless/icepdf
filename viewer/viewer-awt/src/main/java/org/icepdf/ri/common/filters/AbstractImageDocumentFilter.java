package org.icepdf.ri.common.filters;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Filter using images of the document pages
 */
public abstract class AbstractImageDocumentFilter extends AbstractDocumentFilter {
    //Only use one thread and service because multithreaded access to images has bad performance
    private static final ExecutorService SERVICE = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r);
        thread.setName("image-pages-filter-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Instantiates the document filter
     *
     * @param parameters The filter parameters
     */
    protected AbstractImageDocumentFilter(final Map<String, Object> parameters) {
        super(parameters);
    }

    /**
     * Runs the given task
     *
     * @param supplier The task
     * @param <T>      The type of the supplier
     * @return A completable future for the task
     */
    protected <T> CompletableFuture<T> supply(final Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, SERVICE);
    }

    /**
     * Runs the given task
     *
     * @param runnable The task
     * @return A completable future for the task
     */
    protected CompletableFuture<Void> run(final Runnable runnable) {
        return CompletableFuture.runAsync(runnable, SERVICE);
    }
}
