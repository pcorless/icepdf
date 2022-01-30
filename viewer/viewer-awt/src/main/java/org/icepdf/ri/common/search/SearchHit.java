package org.icepdf.ri.common.search;

/**
 * Represents a search hit (for the whole page search)
 */
public class SearchHit {
    private final int startOffset;
    private final int endOffset;
    private final String text;

    protected SearchHit(final int startOffset, final int endOffset, final String text) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.text = text.replace("\n", " ");
    }

    /**
     * @return The starting offset in the page text
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * @return The ending offset in the page text
     */
    public int getEndOffset() {
        return endOffset;
    }

    /**
     * @return The text found
     */
    public String getText() {
        return text;
    }
}
