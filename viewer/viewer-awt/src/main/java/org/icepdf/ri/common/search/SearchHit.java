/*
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
