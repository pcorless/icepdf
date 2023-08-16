package org.icepdf.ri.common.filters;

import org.icepdf.core.pobjects.Document;

import java.util.*;

/**
 * Abstract implementation of a DocumentFilter to handle caching
 */
public abstract class AbstractDocumentFilter implements DocumentFilter {

    private final Set<DocumentFilterListener> listeners;
    private final Map<Document, Set<Integer>> cache;
    private final Map<String, Object> parameters;

    /**
     * Instantiates the document filter
     */
    protected AbstractDocumentFilter(final Map<String, Object> parameters) {
        this.cache = new WeakHashMap<>();
        this.listeners = new HashSet<>();
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    @Override
    public Set<Integer> filterPages(final Document document) {
        if (cache.containsKey(document)) {
            return cache.get(document);
        } else {
            listeners.forEach(l -> l.documentFilterStarted(this, document));
            final Set<Integer> filtered = filterPagesUncached(document);
            cache.put(document, filtered);
            listeners.forEach(l -> l.documentFilterCompleted(this, document, filtered));
            return filtered;
        }
    }

    /**
     * Actual implementation of the filter when there is a cache miss
     *
     * @param document The document
     * @return The set of page indexes that passed the filter
     */
    protected abstract Set<Integer> filterPagesUncached(final Document document);

    @Override
    public boolean isReady(final Document document) {
        return cache.containsKey(document);
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public void addListener(final DocumentFilterListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(final DocumentFilterListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void removeAllListeners() {
        listeners.clear();
    }

    @Override
    public void precache(final Document document, final Set<Integer> filtered) {
        cache.put(document, filtered);
    }
}
