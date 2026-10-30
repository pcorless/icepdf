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

import org.icepdf.fx.ri.prefs.BoardOrderFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The custom order's rules, without a board: merging, moving, stepping, and the file it's kept in. */
class BoardOrderTest {

    @TempDir
    Path dir;

    /** Cards that carry only a key (in their quote), ordered by it. */
    private static List<BoardModel.Card> cards(String... keys) {
        return java.util.Arrays.stream(keys).map(k -> new BoardModel.Card(null, k, 0)).toList();
    }

    private static List<String> keys(List<BoardModel.Card> cards) {
        return cards.stream().map(BoardModel.Card::quote).toList();
    }

    private static List<String> apply(List<String> sorted, List<String> stored) {
        return keys(BoardOrder.apply(cards(sorted.toArray(String[]::new)), stored, BoardModel.Card::quote));
    }

    @DisplayName("known cards take the stored order; unknown ones stay after the card they follow in the sort")
    @Test
    void applyMerges() {
        assertEquals(List.of("c", "a", "b"), apply(List.of("a", "b", "c"), List.of("c", "a", "b")));
        // n is new, sorted after b: it stays after b wherever b goes.
        assertEquals(List.of("b", "n", "a"), apply(List.of("a", "b", "n"), List.of("b", "a")));
        // a new card ahead of every known one stays first.
        assertEquals(List.of("n", "b", "a"), apply(List.of("n", "a", "b"), List.of("b", "a")));
        // keys of cards that are gone are ignored; no stored order leaves the sort.
        assertEquals(List.of("b", "a"), apply(List.of("a", "b"), List.of("gone", "b", "a")));
        assertEquals(List.of("a", "b"), apply(List.of("a", "b"), List.of()));
    }

    @DisplayName("moving puts cards in front of a card, or last; dropped on its own cards they stay together")
    @Test
    void move() {
        List<String> order = List.of("a", "b", "c", "d");
        assertEquals(List.of("c", "a", "b", "d"), BoardOrder.move(order, Set.of("c"), "a"));
        assertEquals(List.of("a", "c", "d", "b"), BoardOrder.move(order, Set.of("b"), null));
        assertEquals(List.of("b", "a", "c", "d"), BoardOrder.move(order, new java.util.LinkedHashSet<>(List.of("b")), "a"));
        // a and c to the top: dropped on a, they go in front of b, the first that stays.
        assertEquals(List.of("a", "c", "b", "d"), BoardOrder.move(order, Set.of("a", "c"), "a"));
        // a key from another column joins the order where it's dropped.
        assertEquals(List.of("a", "x", "b", "c", "d"), BoardOrder.move(order, Set.of("x"), "b"));
    }

    @DisplayName("a step up or down moves each picked card past one neighbour; a block moves together")
    @Test
    void nudge() {
        List<String> order = List.of("a", "b", "c", "d");
        assertEquals(List.of("a", "c", "b", "d"), BoardOrder.nudge(order, Set.of("c"), -1));
        assertEquals(List.of("b", "c", "a", "d"), BoardOrder.nudge(order, Set.of("b", "c"), -1));
        assertEquals(List.of("a", "d", "b", "c"), BoardOrder.nudge(order, Set.of("b", "c"), 1));
        assertEquals(order, BoardOrder.nudge(order, Set.of("a"), -1), "the top card stays");
    }

    @DisplayName("a reordered subset goes back into the slots it had; hidden cards keep their places")
    @Test
    void refill() {
        assertEquals(List.of("c", "x", "a", "y"), BoardOrder.refill(List.of("a", "x", "c", "y"), List.of("c", "a")));
    }

    @DisplayName("the order file keeps orders per document and column, survives a reload, and forgets the oldest")
    @Test
    void file() {
        Path path = dir.resolve("board-order.properties");
        BoardOrderFile file = BoardOrderFile.load(path);
        file.set("id:01", "label:ff0000", List.of("nm:b", "nm:a"));
        file.set("id:01", "other", List.of("nm:c"));
        assertTrue(file.has("id:01"));
        assertFalse(file.has("id:02"));

        BoardOrderFile again = BoardOrderFile.load(path);
        assertEquals(List.of("nm:b", "nm:a"), again.get("id:01", "label:ff0000"));
        again.set("id:01", "label:ff0000", List.of());
        again.set("id:01", "other", List.of());
        assertFalse(again.has("id:01"), "an emptied document is forgotten");

        for (int i = 0; i <= BoardOrderFile.MAX_DOCUMENTS; i++) again.set("id:" + i, "all", List.of("k"));
        int kept = 0;
        for (int i = 0; i <= BoardOrderFile.MAX_DOCUMENTS; i++) if (again.has("id:" + i)) kept++;
        assertEquals(BoardOrderFile.MAX_DOCUMENTS, kept);
    }
}
