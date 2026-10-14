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

import org.icepdf.fx.i18n.Messages;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.Library;

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
        PAGE("page"), DATE("date"), AUTHOR("author"), TYPE("type");

        private final String key;

        Order(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return Messages.get("annotationSummary.order." + key);
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
     * Every visible markup annotation, page by page - call off the FX thread.  Deleted and hidden ones
     * are left out.
     * <p>
     * A page the view has already initialised gives its annotation list; any other page only has its
     * /Annots references resolved, as the Swing viewer's annotation panel does.  Initialising every
     * page would parse every page's content just to list comments, which on a long document is the
     * expensive part by far; the annotation getters read the dictionary lazily, so no init is needed.
     * The instances are the library's, the same ones the page gets when it initialises later.
     *
     * @return null if cancelled
     */
    public static List<Entry> collect(Document document, BooleanSupplier cancelled) throws InterruptedException {
        List<Entry> entries = new ArrayList<>();
        if (document == null) return entries;
        Library library = document.getCatalog().getLibrary();
        for (int i = 0, count = document.getNumberOfPages(); i < count; i++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return null;
            Page page = document.getPageTree().getPage(i);
            if (page == null) continue;
            List<Annotation> annotations = page.isInitiated() ? page.getAnnotations() : null;
            if (annotations != null) {
                for (Annotation annotation : new ArrayList<>(annotations)) add(entries, i, annotation);
                continue;
            }
            List<Reference> references = page.getAnnotationReferences();
            if (references == null) continue;
            for (Object reference : references) {
                if (reference instanceof Reference ref && library.getObject(ref) instanceof Annotation annotation) {
                    // as Page.init does; replies are matched to their comment by it.
                    if (annotation.getPObjectReference() == null) annotation.setPObjectReference(ref);
                    add(entries, i, annotation);
                }
            }
        }
        return entries;
    }

    private static void add(List<Entry> entries, int pageIndex, Annotation annotation) {
        if (annotation instanceof MarkupAnnotation markup && !markup.isDeleted() && !markup.getFlagHidden()) {
            entries.add(entry(pageIndex, markup));
        }
    }

    static Entry entry(int pageIndex, MarkupAnnotation markup) {
        LocalDateTime date = dateTime(markup.getModifiedDate());
        if (date == null) date = dateTime(markup.getCreationDate());
        String author = markup.getTitleText();
        String contents = markup.getContents();
        return new Entry(pageIndex, markup, typeName(markup), author != null ? author.trim() : "", date,
                contents != null ? contents.trim() : "", markup.getColor(), replyTo(markup));
    }

    /** /IRT, read from the dictionary: set by init, which a page not yet shown hasn't run. */
    private static MarkupAnnotation replyTo(MarkupAnnotation markup) {
        MarkupAnnotation initialised = markup.getInReplyToAnnotation();
        if (initialised != null) return initialised;
        Object irt = markup.getObject(MarkupAnnotation.IRT_KEY);
        Reference ref = irt instanceof Reference r ? r : null;
        if (ref != null) irt = markup.getLibrary().getObject(ref);
        if (!(irt instanceof MarkupAnnotation parent)) return null;
        if (ref != null && parent.getPObjectReference() == null) parent.setPObjectReference(ref);
        return parent;
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
            case "Text" -> Messages.get("annotationSummary.type.note");
            case "FreeText" -> Messages.get("annotationSummary.type.freeText");
            case "StrikeOut" -> Messages.get("annotationSummary.type.strikeOut");
            case "Square" -> Messages.get("annotationSummary.type.square");
            case "Circle" -> Messages.get("annotationSummary.type.circle");
            case "PolyLine" -> Messages.get("annotationSummary.type.polyLine");
            case "Ink" -> Messages.get("annotationSummary.type.ink");
            case "FileAttachment" -> Messages.get("annotationSummary.type.fileAttachment");
            case "Redact" -> Messages.get("annotationSummary.type.redact");
            case "" -> Messages.get("annotationSummary.type.comment");
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
