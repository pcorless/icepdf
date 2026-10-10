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
package org.icepdf.fx.panels;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The document's comments - its markup annotations (notes, highlights, shapes, stamps...) - as a flat
 * list in document order, each knowing the comment it replies to (/IRT).  No toolkit in it.
 */
public final class AnnotationSummary {

    /**
     * One comment.
     *
     * @param pageIndex  zero-based page
     * @param annotation the annotation itself
     * @param type       a reader's name for its kind ("Note", "Highlight", "Rectangle"...)
     * @param author     /T, or "" when none
     * @param date       /M, else /CreationDate; null when neither parses
     * @param contents   /Contents, or ""
     * @param color      /C, or null
     * @param replyTo    the comment this one replies to, or null
     */
    public record Entry(int pageIndex, MarkupAnnotation annotation, String type, String author,
                        LocalDateTime date, String contents, Color color, MarkupAnnotation replyTo) {

        /** The text a filter matches: type, author and contents. */
        public boolean matches(String needle) {
            if (needle == null || needle.isBlank()) return true;
            String n = needle.trim().toLowerCase(Locale.ROOT);
            return type.toLowerCase(Locale.ROOT).contains(n) || author.toLowerCase(Locale.ROOT).contains(n)
                    || contents.toLowerCase(Locale.ROOT).contains(n);
        }
    }

    /** Ways to order the list. */
    public enum Order {
        PAGE("Page"), DATE("Date (newest first)"), AUTHOR("Author"), TYPE("Type");

        private final String label;

        Order(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public Comparator<Entry> comparator() {
            Comparator<Entry> byPage = Comparator.comparingInt(Entry::pageIndex);
            return switch (this) {
                case PAGE -> byPage;
                case DATE -> Comparator.comparing(Entry::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(byPage);
                case AUTHOR -> Comparator.comparing((Entry e) -> e.author().toLowerCase(Locale.ROOT))
                        .thenComparing(byPage);
                case TYPE -> Comparator.comparing(Entry::type).thenComparing(byPage);
            };
        }
    }

    private AnnotationSummary() {
    }

    /**
     * Every visible markup annotation, page by page (each page is initialised as needed - call off
     * the FX thread).  Deleted and hidden ones are left out.
     *
     * @return null if cancelled
     */
    public static List<Entry> collect(Document document, BooleanSupplier cancelled) throws InterruptedException {
        List<Entry> entries = new ArrayList<>();
        if (document == null) return entries;
        for (int i = 0, count = document.getNumberOfPages(); i < count; i++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return null;
            Page page = document.getPageTree().getPage(i);
            if (page == null) continue;
            page.init();
            List<Annotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            for (Annotation annotation : new ArrayList<>(annotations)) {
                if (annotation instanceof MarkupAnnotation markup && !markup.isDeleted() && !markup.getFlagHidden()) {
                    entries.add(entry(i, markup));
                }
            }
        }
        return entries;
    }

    static Entry entry(int pageIndex, MarkupAnnotation markup) {
        LocalDateTime date = dateTime(markup.getModifiedDate());
        if (date == null) date = dateTime(markup.getCreationDate());
        String author = markup.getTitleText();
        String contents = markup.getContents();
        return new Entry(pageIndex, markup, typeName(markup), author != null ? author.trim() : "", date,
                contents != null ? contents.trim() : "", markup.getColor(), markup.getInReplyToAnnotation());
    }

    /** The entries that are replies, keyed by the comment they answer (by object reference). */
    public static Map<Reference, List<Entry>> replies(List<Entry> entries) {
        Map<Reference, List<Entry>> replies = new HashMap<>();
        for (Entry entry : entries) {
            MarkupAnnotation parent = entry.replyTo();
            if (parent != null && parent.getPObjectReference() != null) {
                replies.computeIfAbsent(parent.getPObjectReference(), r -> new ArrayList<>()).add(entry);
            }
        }
        return replies;
    }

    /** A reader's name for a markup subtype. */
    static String typeName(Annotation annotation) {
        String subtype = annotation.getSubType() != null ? annotation.getSubType().getName() : "";
        return switch (subtype) {
            case "Text" -> "Note";
            case "FreeText" -> "Text box";
            case "StrikeOut" -> "Strikeout";
            case "Square" -> "Rectangle";
            case "Circle" -> "Ellipse";
            case "PolyLine" -> "Polyline";
            case "Ink" -> "Freehand";
            case "FileAttachment" -> "Attachment";
            case "Redact" -> "Redaction";
            case "" -> "Comment";
            default -> subtype;
        };
    }

    private static LocalDateTime dateTime(PDate date) {
        try {
            return date != null ? date.asLocalDateTime() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
