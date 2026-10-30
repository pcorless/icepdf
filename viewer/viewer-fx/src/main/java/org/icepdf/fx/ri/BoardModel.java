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

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.fx.panels.AnnotationSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The annotation board's columns, without a toolkit: each comment filed under the colour label its
 * colour matches ({@link ColorLabels#match}), replies counted on the comment they answer rather
 * than shown as cards of their own.
 */
public final class BoardModel {

    /** What a column holds. */
    public enum Kind {
        /** The comments whose colour matches a label; shown even when empty, to drop onto. */
        LABEL,
        /** Coloured comments that match no label. */
        OTHER,
        /** Comments without a colour. */
        NONE,
        /** Every comment: there are no labels to sort by. */
        ALL,
        /** One author, page or type (see {@link GroupBy}); read only, cards aren't dropped on it. */
        GROUP
    }

    /** What the columns are. */
    public enum GroupBy {
        LABEL("annotationBoard.groupBy.label"), AUTHOR("annotationBoard.groupBy.author"),
        PAGE("annotationBoard.groupBy.page"), TYPE("annotationBoard.groupBy.type");

        private final String key;

        GroupBy(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return org.icepdf.fx.i18n.Messages.get(key);
        }
    }

    /**
     * One comment on the board.
     *
     * @param quote   the page text it marks (highlights and the like), or ""
     * @param replies how many replies it has, at any depth
     */
    public record Card(AnnotationSummary.Entry entry, String quote, int replies) {

        /** Matches the board filter: type, author, contents or the quoted text. */
        public boolean matches(String needle) {
            if (entry.matches(needle)) return true;
            return needle != null && !needle.isBlank()
                    && quote.toLowerCase(Locale.ROOT).contains(needle.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * A column: its kind, its label (for {@link Kind#LABEL}), its title (for {@link Kind#GROUP}),
     * and its cards in order.
     */
    public record Column(Kind kind, ColorLabels.Label label, String title, List<Card> cards) {
    }

    private BoardModel() {
    }

    /**
     * Sorts the comments into columns: one per label, in label order, then Other and No colour when
     * they hold anything; with no labels, a single column of everything.
     *
     * @param entries every comment, replies included
     * @param quotes  the marked text of text markup comments (missing means none)
     * @param order   the order of the cards in each column
     * @param shown   which cards to show (filters); replies are counted whatever it says
     */
    public static List<Column> columns(List<AnnotationSummary.Entry> entries, Map<MarkupAnnotation, String> quotes,
                                       List<ColorLabels.Label> labels, Comparator<AnnotationSummary.Entry> order,
                                       Predicate<Card> shown) {
        Set<Reference> present = new HashSet<>();
        for (AnnotationSummary.Entry entry : entries) {
            Reference ref = entry.annotation().getPObjectReference();
            if (ref != null) present.add(ref);
        }
        Map<Reference, List<AnnotationSummary.Entry>> replies = AnnotationSummary.replies(entries);

        Map<ColorLabels.Label, List<Card>> byLabel = new LinkedHashMap<>();
        for (ColorLabels.Label label : labels) byLabel.put(label, new ArrayList<>());
        List<Card> other = new ArrayList<>(), none = new ArrayList<>();
        List<AnnotationSummary.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(order);
        for (AnnotationSummary.Entry entry : sorted) {
            MarkupAnnotation parent = entry.replyTo();
            if (parent != null && present.contains(parent.getPObjectReference())) continue;
            Card card = new Card(entry, quotes.getOrDefault(entry.annotation(), ""), count(entry, replies, new HashSet<>()));
            if (!shown.test(card)) continue;
            ColorLabels.Label label = labels.isEmpty() ? null : ColorLabels.match(labels, entry.color());
            if (label != null) byLabel.get(label).add(card);
            else if (entry.color() != null) other.add(card);
            else none.add(card);
        }

        List<Column> columns = new ArrayList<>();
        if (labels.isEmpty()) {
            List<Card> all = new ArrayList<>(other);
            all.addAll(none);
            all.sort(Comparator.comparing(Card::entry, order));
            columns.add(new Column(Kind.ALL, null, null, all));
            return columns;
        }
        byLabel.forEach((label, cards) -> columns.add(new Column(Kind.LABEL, label, null, cards)));
        if (!other.isEmpty()) columns.add(new Column(Kind.OTHER, null, null, other));
        if (!none.isEmpty()) columns.add(new Column(Kind.NONE, null, null, none));
        return columns;
    }

    /**
     * As {@link #columns(List, Map, List, Comparator, Predicate)} for {@link GroupBy#LABEL}; otherwise
     * one {@link Kind#GROUP} column per author (by name), page (in order) or type (by name).
     */
    public static List<Column> columns(GroupBy by, List<AnnotationSummary.Entry> entries,
                                       Map<MarkupAnnotation, String> quotes, List<ColorLabels.Label> labels,
                                       Comparator<AnnotationSummary.Entry> order, Predicate<Card> shown) {
        if (by == null || by == GroupBy.LABEL) return columns(entries, quotes, labels, order, shown);
        // the cards as the label grouping would show them, then regrouped.
        List<Card> cards = new ArrayList<>();
        for (Column column : columns(entries, quotes, List.of(), order, shown)) cards.addAll(column.cards());
        java.util.SortedMap<Object, List<Card>> groups = new java.util.TreeMap<>((a, b) -> a instanceof Integer x
                ? Integer.compare(x, (Integer) b) : String.CASE_INSENSITIVE_ORDER.compare((String) a, (String) b));
        for (Card card : cards) {
            AnnotationSummary.Entry entry = card.entry();
            Object key = switch (by) {
                case AUTHOR -> entry.author().isEmpty()
                        ? org.icepdf.fx.i18n.Messages.get("annotationBoard.noAuthor") : entry.author();
                case PAGE -> entry.pageIndex();
                default -> entry.type();
            };
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(card);
        }
        List<Column> columns = new ArrayList<>();
        groups.forEach((key, group) -> columns.add(new Column(Kind.GROUP, null, key instanceof Integer page
                ? org.icepdf.fx.i18n.Messages.format("annotationBoard.pageColumn", page + 1)
                : (String) key, group)));
        return columns;
    }

    /** Replies under a comment, at any depth (a reference loop is counted once). */
    private static int count(AnnotationSummary.Entry entry, Map<Reference, List<AnnotationSummary.Entry>> replies,
                             Set<Reference> seen) {
        Reference ref = entry.annotation().getPObjectReference();
        if (ref == null || !seen.add(ref)) return 0;
        int n = 0;
        for (AnnotationSummary.Entry reply : replies.getOrDefault(ref, List.of())) {
            n += 1 + count(reply, replies, seen);
        }
        return n;
    }
}
