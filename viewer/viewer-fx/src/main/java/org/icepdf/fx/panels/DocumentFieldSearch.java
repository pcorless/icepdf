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

import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.Names;
import org.icepdf.core.pobjects.OutlineItem;
import org.icepdf.core.pobjects.Outlines;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.search.TextSearch;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches the parts of a document that aren't page text - comments, form field values, bookmark
 * titles and named destinations - with the same matching rules as the text search
 * ({@link TextSearch#compile}).  No toolkit in it: it runs on the caller's thread and reports
 * through a consumer, page by page for the per-page kinds.
 * <p>
 * The matching follows the Swing viewer's comment, form, outline and destination searches
 * ({@code DocumentSearchControllerImpl}), except that every term is used, not only the first, and
 * whole word and accent folding apply here too.
 */
public final class DocumentFieldSearch {

    /** What a hit was found in. */
    public enum Kind {
        COMMENT("Comments"), FORM_FIELD("Form fields"), OUTLINE("Bookmarks"), DESTINATION("Named destinations");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * One match.
     *
     * @param kind      where it was found
     * @param pageIndex its page, or -1 when it has none (a bookmark or destination to elsewhere)
     * @param label     a short name for it: the comment's author or type, the field's name
     * @param text      the text searched, whitespace collapsed
     * @param start     the match in {@code text}, or -1 when it can't be placed (an accent-folded match
     *                  whose folding changed the length)
     * @param end       end of the match in {@code text}
     * @param target    the {@link Annotation}, {@link OutlineItem} or {@link Destination}
     */
    public record Hit(Kind kind, int pageIndex, String label, String text, int start, int end, Object target) {
    }

    private final List<SearchTerm> terms;
    private final List<Pattern> patterns = new ArrayList<>();

    public DocumentFieldSearch(List<SearchTerm> terms) {
        this.terms = List.copyOf(terms);
        for (SearchTerm term : this.terms) patterns.add(TextSearch.compile(term));
    }

    /**
     * Searches every kind asked for, in document order: outlines and destinations first (they're
     * quick), then the pages' comments and fields.
     *
     * @return false if cancelled
     */
    public boolean run(Document document, boolean comments, boolean forms, boolean outlines, boolean destinations,
                       BooleanSupplier cancelled, Consumer<Hit> found) throws InterruptedException {
        if (document == null) return true;
        if (outlines) searchOutlines(document, found);
        if (destinations) searchDestinations(document, found);
        if (!comments && !forms) return true;
        int count = document.getNumberOfPages();
        for (int i = 0; i < count; i++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return false;
            Page page = document.getPageTree().getPage(i);
            if (page == null) continue;
            page.init();
            List<Annotation> annotations = page.getAnnotations();
            if (annotations == null) continue;
            for (Annotation annotation : new ArrayList<>(annotations)) {
                if (comments && annotation instanceof MarkupAnnotation markup) {
                    match(markup.getContents()).ifPresent(m -> found.accept(new Hit(Kind.COMMENT, page.getPageIndex(),
                            commentLabel(markup), m.text, m.start, m.end, markup)));
                } else if (forms && annotation instanceof AbstractWidgetAnnotation<?> widget) {
                    FieldDictionary field = widget.getFieldDictionary();
                    Object value = field != null ? field.getFieldValue() : null;
                    String text = value instanceof String s ? s
                            : value instanceof StringObject so ? Utils.convertStringObject(document.getCatalog().getLibrary(), so)
                            : null;
                    match(text).ifPresent(m -> found.accept(new Hit(Kind.FORM_FIELD, page.getPageIndex(),
                            fieldLabel(field), m.text, m.start, m.end, widget)));
                }
            }
        }
        return true;
    }

    void searchOutlines(Document document, Consumer<Hit> found) {
        Outlines outlines = document.getCatalog().getOutlines();
        OutlineItem root = outlines != null ? outlines.getRootOutlineItem() : null;
        if (root != null) searchOutlines(document, root, found);
    }

    private void searchOutlines(Document document, OutlineItem item, Consumer<Hit> found) {
        for (int i = 0, count = item.getSubItemCount(); i < count; i++) {
            OutlineItem child = item.getSubItem(i);
            match(child.getTitle()).ifPresent(m -> found.accept(new Hit(Kind.OUTLINE,
                    pageOf(document, child.getDest()), "", m.text, m.start, m.end, child)));
            if (child.getSubItemCount() > 0) searchOutlines(document, child, found);
        }
    }

    void searchDestinations(Document document, Consumer<Hit> found) {
        Names names = document.getCatalog().getNames();
        NameTree tree = names != null ? names.getDestsNameTree() : null;
        List<?> pairs = tree != null ? tree.getNamesAndValues() : null;
        if (pairs == null) return;
        Library library = document.getCatalog().getLibrary();
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            Object key = pairs.get(i);
            String name = key instanceof StringObject so ? Utils.convertStringObject(library, so)
                    : key != null ? key.toString() : null;
            Object value = pairs.get(i + 1);
            match(name).ifPresent(m -> {
                Destination destination = new Destination(library, value);
                found.accept(new Hit(Kind.DESTINATION, pageOf(document, destination), "", m.text, m.start, m.end,
                        destination));
            });
        }
    }

    private static int pageOf(Document document, Destination destination) {
        if (destination == null || destination.getPageReference() == null) return -1;
        return document.getPageTree().getPageNumber(destination.getPageReference());
    }

    private static String commentLabel(MarkupAnnotation markup) {
        String author = markup.getTitleText();
        if (author != null && !author.isBlank()) return author.trim();
        return markup.getSubType() != null ? markup.getSubType().getName() : "Comment";
    }

    private static String fieldLabel(FieldDictionary field) {
        if (field == null) return "";
        String name = field.getPartialFieldName();
        return name != null ? name : "";
    }

    /** A matched text: the collapsed text and the first match in it. */
    record Match(String text, int start, int end) {
    }

    /** The first match of any term in {@code raw} (whitespace collapsed first), if there is one. */
    java.util.Optional<Match> match(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Optional.empty();
        String text = raw.replaceAll("\\s+", " ").trim();
        for (int i = 0; i < terms.size(); i++) {
            Pattern pattern = patterns.get(i);
            if (pattern == null) continue;
            SearchTerm term = terms.get(i);
            String corpus = !term.isRegex() && term.isFoldDiacritics() ? TextSequence.foldDiacritics(text) : text;
            Matcher matcher = pattern.matcher(corpus);
            while (matcher.find()) {
                if (matcher.end() == matcher.start()) continue;
                boolean placed = corpus.length() == text.length();
                return java.util.Optional.of(new Match(text, placed ? matcher.start() : -1, placed ? matcher.end() : -1));
            }
        }
        return java.util.Optional.empty();
    }
}
