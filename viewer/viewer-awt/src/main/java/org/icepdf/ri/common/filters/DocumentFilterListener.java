package org.icepdf.ri.common.filters;

import org.icepdf.core.pobjects.Document;

import java.util.Set;

/**
 * Represents a listener of DocumentFilter
 */
public interface DocumentFilterListener {
    /**
     * Sent when a filtering has started
     *
     * @param filter   The filter
     * @param document The document the filter has started filtering
     */
    void documentFilterStarted(final DocumentFilter filter, final Document document);

    /**
     * Sent when a filtering has ended
     *
     * @param filter        The filter
     * @param document      The document the filter has finished filtering
     * @param filteredPages The indexes of the pages that passed the filter
     */
    void documentFilterCompleted(final DocumentFilter filter, final Document document, final Set<Integer> filteredPages);
}
