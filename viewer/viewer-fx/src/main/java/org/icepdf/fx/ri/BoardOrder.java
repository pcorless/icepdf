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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.fx.panels.AnnotationSummary;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The user's own order of the cards in a board column, kept outside the document ({@link BoardStore}).
 * A document is known by its file identifier and a card by its annotation's name, so an order outlives
 * saves, renames and moves; cards the order doesn't know take their place by the column's sort.
 */
public final class BoardOrder {

    private BoardOrder() {
    }

    /**
     * The document's key: the first part of the trailer's /ID (meant to stay the same for the life of
     * the document), else its path.
     *
     * @return the key, or null for a document with neither
     */
    public static String documentKey(Document document) {
        if (document == null) return null;
        try {
            PTrailer trailer = document.getCatalog().getLibrary().getCrossReferenceRoot().getTrailerDictionary();
            List<?> id = trailer != null ? trailer.getID() : null;
            if (id != null && !id.isEmpty() && id.get(0) instanceof StringObject first) {
                byte[] bytes = first.getRawBytes();
                if (bytes != null && bytes.length > 0) return "id:" + hex(bytes);
            }
        } catch (RuntimeException e) {
            // a broken trailer: fall back to the path.
        }
        String path = document.getDocumentLocation();
        return path != null && !path.isBlank() ? "path:" + path : null;
    }

    /**
     * A card's key: its annotation's name (/NM), else where it is (page, type, rectangle) and when it
     * was made.  Not its colour, contents or modified date, which change as it is filed and edited.
     */
    public static String cardKey(AnnotationSummary.Entry entry) {
        MarkupAnnotation annotation = entry.annotation();
        String name = annotation.getName();
        if (name != null && !name.isBlank()) return "nm:" + name.strip();
        StringBuilder key = new StringBuilder("at:").append(entry.pageIndex()).append(':')
                .append(annotation.getSubType() != null ? annotation.getSubType().getName() : "");
        Rectangle2D.Float rect = annotation.getUserSpaceRectangle();
        if (rect != null) {
            key.append(':').append(Math.round(rect.x)).append(',').append(Math.round(rect.y))
                    .append(',').append(Math.round(rect.width)).append(',').append(Math.round(rect.height));
        }
        // the date's own fields, not toString(), which is formatted for the user's locale.
        PDate created = annotation.getCreationDate();
        if (created != null) {
            key.append(':').append(created.getYear()).append(created.getMonth()).append(created.getDay())
                    .append(created.getHour()).append(created.getMinute()).append(created.getSecond());
        }
        return key.toString();
    }

    /**
     * Keys for every comment of a document, made unique: a second comment with the same key (a copied
     * annotation keeps its name) gets {@code #2}, counted in page and object order so the suffix
     * doesn't depend on how the cards are shown.
     */
    public static Map<MarkupAnnotation, String> keys(List<AnnotationSummary.Entry> entries) {
        Map<MarkupAnnotation, String> keys = new IdentityHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        List<AnnotationSummary.Entry> stable = new ArrayList<>(entries);
        stable.sort(java.util.Comparator.comparingInt(AnnotationSummary.Entry::pageIndex)
                .thenComparingInt(e -> e.annotation().getPObjectReference() != null
                        ? e.annotation().getPObjectReference().getObjectNumber() : Integer.MAX_VALUE));
        for (AnnotationSummary.Entry entry : stable) {
            String key = cardKey(entry);
            int n = seen.merge(key, 1, Integer::sum);
            keys.put(entry.annotation(), n == 1 ? key : key + "#" + n);
        }
        return keys;
    }

    /**
     * Puts cards in a stored order.  Known cards go in the stored order; each card the order doesn't
     * know stays after the card it follows in {@code sorted} (or first, when it leads).  Keys stored for
     * cards that are gone are ignored.
     *
     * @param sorted the column's cards in its sort order
     * @param stored card keys, first to last; empty leaves {@code sorted} as it is
     * @param keyOf  a card's key ({@link #keys})
     */
    public static List<BoardModel.Card> apply(List<BoardModel.Card> sorted, List<String> stored,
                                              java.util.function.Function<BoardModel.Card, String> keyOf) {
        if (stored == null || stored.isEmpty() || sorted.size() < 2) return sorted;
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < stored.size(); i++) rank.putIfAbsent(stored.get(i), i);
        List<BoardModel.Card> known = new ArrayList<>();
        for (BoardModel.Card card : sorted) {
            if (rank.containsKey(keyOf.apply(card))) known.add(card);
        }
        if (known.isEmpty()) return sorted;
        known.sort((a, b) -> Integer.compare(rank.get(keyOf.apply(a)), rank.get(keyOf.apply(b))));

        // the unknown cards, by the known card they follow in the sort (null: before every known one).
        Map<BoardModel.Card, List<BoardModel.Card>> after = new IdentityHashMap<>();
        List<BoardModel.Card> leading = new ArrayList<>();
        BoardModel.Card previous = null;
        for (BoardModel.Card card : sorted) {
            if (rank.containsKey(keyOf.apply(card))) {
                previous = card;
            } else if (previous == null) {
                leading.add(card);
            } else {
                after.computeIfAbsent(previous, k -> new ArrayList<>()).add(card);
            }
        }
        List<BoardModel.Card> ordered = new ArrayList<>(leading);
        for (BoardModel.Card card : known) {
            ordered.add(card);
            ordered.addAll(after.getOrDefault(card, List.of()));
        }
        return ordered;
    }

    /**
     * Moves cards to a place in a column's order.
     *
     * @param order  the column's card keys as shown, first to last
     * @param moving the keys to move, kept in the order they are shown
     * @param before the key to put them in front of; null puts them last
     * @return the new order
     */
    public static List<String> move(List<String> order, Set<String> moving, String before) {
        List<String> rest = new ArrayList<>();
        List<String> moved = new ArrayList<>();
        for (String key : order) (moving.contains(key) ? moved : rest).add(key);
        // a key that isn't in the column yet (a card dropped in from another column) goes along too.
        Set<String> placed = new HashSet<>(moved);
        for (String key : moving) if (placed.add(key)) moved.add(key);
        // dropped on one of the moving cards: in front of the first card after it that stays.
        if (before != null && moving.contains(before)) {
            int from = order.indexOf(before);
            before = null;
            for (int i = from + 1; from >= 0 && i < order.size(); i++) {
                if (!moving.contains(order.get(i))) {
                    before = order.get(i);
                    break;
                }
            }
        }
        int at = before == null ? rest.size() : rest.indexOf(before);
        if (at < 0) at = rest.size();
        List<String> result = new ArrayList<>(rest.subList(0, at));
        result.addAll(moved);
        result.addAll(rest.subList(at, rest.size()));
        return result;
    }

    /** One step up (-1) or down (+1) for each moving key, as Alt+Up / Alt+Down do. */
    public static List<String> nudge(List<String> order, Set<String> moving, int direction) {
        List<String> result = new ArrayList<>(order);
        if (direction < 0) {
            for (int i = 1; i < result.size(); i++) {
                if (moving.contains(result.get(i)) && !moving.contains(result.get(i - 1))) swap(result, i, i - 1);
            }
        } else {
            for (int i = result.size() - 2; i >= 0; i--) {
                if (moving.contains(result.get(i)) && !moving.contains(result.get(i + 1))) swap(result, i, i + 1);
            }
        }
        return result;
    }

    /**
     * Puts a reordered subset back into a whole order: the subset's slots in {@code whole} are filled
     * with {@code subset} in its new order; every other key stays where it is.
     */
    public static List<String> refill(List<String> whole, List<String> subset) {
        Set<String> members = new HashSet<>(subset);
        java.util.Iterator<String> next = subset.iterator();
        List<String> result = new ArrayList<>(whole);
        for (int i = 0; i < result.size() && next.hasNext(); i++) {
            if (members.contains(result.get(i))) result.set(i, next.next());
        }
        return result;
    }

    private static void swap(List<String> list, int a, int b) {
        String t = list.get(a);
        list.set(a, list.get(b));
        list.set(b, t);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return out.toString();
    }
}
