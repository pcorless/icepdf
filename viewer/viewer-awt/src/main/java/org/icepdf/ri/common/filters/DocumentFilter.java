package org.icepdf.ri.common.filters;

import org.icepdf.core.pobjects.Document;

import java.util.Map;
import java.util.Set;

/**
 * Represents an object which will filter the pages of a given document
 */
public interface DocumentFilter {

    /**
     * Filters the pages of the given document
     *
     * @param document The document
     * @return The set of page indexes that passed the filter
     */
    Set<Integer> filterPages(final Document document);

    /**
     * Returns whether the filter is ready for the given document
     *
     * @param document The document
     * @return Whether the filter has the results
     */
    boolean isReady(final Document document);

    /**
     * @return The filter parameters
     */
    Map<String, Object> getParameters();

    /**
     * Adds a listener to this filter
     *
     * @param listener The listener
     */
    void addListener(final DocumentFilterListener listener);

    /**
     * Removes a listener from this filter
     *
     * @param listener The listener
     */
    void removeListener(final DocumentFilterListener listener);

    /**
     * Removes all listeners from this filter
     */
    void removeAllListeners();

    /**
     * Precaches a result (in case there is heavy computation required and the result is already saved somewhere)
     *
     * @param document The document
     * @param filtered The pre-computed set of pages that passed the filter
     */
    void precache(final Document document, final Set<Integer> filtered);

    /**
     * Stops the filtering (e.g. document was closed)
     *
     * @param document The document
     */
    void interrupt(final Document document);
}
