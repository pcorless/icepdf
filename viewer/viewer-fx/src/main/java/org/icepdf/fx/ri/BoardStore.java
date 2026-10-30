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
package org.icepdf.fx.ri;

/**
 * Where the annotation board keeps the user's choices: how it looks, and the order the user put cards
 * in (see {@link BoardOrder}).  None of it is written to the document.  {@link #inMemory()} keeps them
 * while the viewer is open; the viewer application keeps them in its settings folder.
 */
public interface BoardStore {

    /** Text size used when none has been chosen: the board follows the window's font. */
    double DEFAULT_TEXT_SIZE = 0;

    /** Whether cards show one line each rather than everything. */
    boolean isCompact();

    void setCompact(boolean compact);

    /** The cards' text size in pixels, or {@link #DEFAULT_TEXT_SIZE}. */
    double getTextSize();

    void setTextSize(double size);

    /**
     * The user's order of a column's cards, as card keys first to last; empty when the column has none.
     *
     * @param document the document's key ({@link BoardOrder#documentKey})
     * @param column   the column's key (a label's colour, or "other", "none", "all")
     */
    java.util.List<String> getCardOrder(String document, String column);

    /** Keeps a column's card order; an empty list forgets it. */
    void setCardOrder(String document, String column, java.util.List<String> cards);

    /** Whether the user has put any column of the document in an order of their own. */
    boolean hasCardOrder(String document);

    /** A store that lives only as long as the viewer, for when the application doesn't supply one. */
    static BoardStore inMemory() {
        return new BoardStore() {
            private boolean compact;
            private double textSize = DEFAULT_TEXT_SIZE;
            private final java.util.Map<String, java.util.List<String>> orders = new java.util.HashMap<>();

            @Override
            public java.util.List<String> getCardOrder(String document, String column) {
                return orders.getOrDefault(document + "\n" + column, java.util.List.of());
            }

            @Override
            public void setCardOrder(String document, String column, java.util.List<String> cards) {
                if (cards == null || cards.isEmpty()) orders.remove(document + "\n" + column);
                else orders.put(document + "\n" + column, java.util.List.copyOf(cards));
            }

            @Override
            public boolean hasCardOrder(String document) {
                return orders.keySet().stream().anyMatch(k -> k.startsWith(document + "\n"));
            }

            @Override
            public boolean isCompact() {
                return compact;
            }

            @Override
            public void setCompact(boolean compact) {
                this.compact = compact;
            }

            @Override
            public double getTextSize() {
                return textSize;
            }

            @Override
            public void setTextSize(double size) {
                this.textSize = size;
            }
        };
    }
}
