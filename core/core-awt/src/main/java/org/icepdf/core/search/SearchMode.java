package org.icepdf.core.search;

/**
 * Enum for search mode
 * WORD is the original implementation, where searchTerms are split and search is done on per-WordText basis
 * PAGE is a mode where a searchTerm is kept as-is and the search is executed on the whole page at once
 */
public enum SearchMode {
    WORD, PAGE
}
