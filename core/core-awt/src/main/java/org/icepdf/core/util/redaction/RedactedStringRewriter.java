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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.Library;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Removes redacted terms from content that has no position on the page.
 * <p>
 * The burner handles everything a redaction rectangle can cover. This handles the rest, and the rest
 * is not a corner case: the same words a user redacted from a page routinely also sit in the bookmark
 * that points at it, in the comment somebody left on it, and in the document title. None of those are
 * drawn on the page, so no rectangle reaches them and no amount of burning removes them.
 * <p>
 * Nothing here has coordinates, so nothing can be burned; the strings are rewritten in place, with
 * each match replaced by {@link RedactionOptions#getMaskString()}. That keeps the structure working -
 * a bookmark still navigates, a field still has a value - while the content goes.
 *
 * @since 7.5.0
 */
public class RedactedStringRewriter {

    private static final Logger logger = Logger.getLogger(RedactedStringRewriter.class.getName());

    private final Document document;
    private final RedactionOptions options;
    private final RedactionReport report;
    private final TermMasker masker;

    public RedactedStringRewriter(Document document, RedactionRequest request, RedactionReport report) {
        this.document = document;
        this.options = request.getOptions();
        this.report = report;
        this.masker = new TermMasker(request.getTerms(), options.getMaskString());
    }

    /**
     * Rewrites every in-scope string the request's terms appear in.
     */
    public void rewrite() {
        if (options.redacts(RedactionTarget.OUTLINE)) {
            rewriteOutline();
        }
        if (options.redacts(RedactionTarget.ANNOTATION_CONTENTS)) {
            rewriteAnnotationContents();
        }
        if (options.redacts(RedactionTarget.METADATA)) {
            rewriteMetadata();
        }
    }

    /**
     * Bookmark titles. Usually the cheapest place a redacted heading survives, since a bookmark is
     * generally made from the heading it points at.
     */
    private void rewriteOutline() {
        Outlines outlines = document.getCatalog().getOutlines();
        if (outlines == null) {
            return;
        }
        rewriteOutlineItem(outlines.getRootOutlineItem());
    }

    private void rewriteOutlineItem(OutlineItem item) {
        if (item == null) {
            return;
        }
        String title = item.getTitle();
        if (title != null) {
            String masked = masker.mask(title);
            if (!masked.equals(title)) {
                item.setTitle(masked);
                report.recordStringRewritten(RedactionTarget.OUTLINE);
            }
        }
        for (int i = 0, max = item.getSubItemCount(); i < max; i++) {
            rewriteOutlineItem(item.getSubItem(i));
        }
    }

    /**
     * The text of markup annotations - a comment, a sticky note.
     * <p>
     * Note what this does not do: an annotation's appearance stream is generated from its contents
     * and carries the same words as drawn glyphs. Those are reached by geometry, when the burner
     * descends into appearance streams, not from here.
     */
    private void rewriteAnnotationContents() {
        Library library = document.getCatalog().getLibrary();
        PageTree pageTree = document.getPageTree();
        for (int i = 0, max = pageTree.getNumberOfPages(); i < max; i++) {
            Page page = pageTree.getPage(i);
            List<Reference> references = page.getAnnotationReferences();
            if (references == null) {
                continue;
            }
            for (Reference reference : new ArrayList<>(references)) {
                Object annotation = library.getObject(reference);
                if (annotation instanceof MarkupAnnotation) {
                    rewriteAnnotation((MarkupAnnotation) annotation, library);
                }
            }
        }
    }

    private void rewriteAnnotation(MarkupAnnotation annotation, Library library) {
        String contents = annotation.getContents();
        if (contents == null) {
            return;
        }
        String masked = masker.mask(contents);
        if (!masked.equals(contents)) {
            annotation.setContents(masked);
            library.getStateManager().addChange(
                    new PObject(annotation, annotation.getPObjectReference()));
            report.recordStringRewritten(RedactionTarget.ANNOTATION_CONTENTS);
        }
    }

    /**
     * The document information dictionary. Title, subject, keywords and author routinely repeat
     * whatever the document is about, which is frequently the thing being redacted.
     */
    private void rewriteMetadata() {
        PInfo info = document.getInfo();
        if (info == null) {
            return;
        }
        // The typed setters rather than setProperty: they run the value through the document's
        // encryption, which writing the string straight into the dictionary would skip.
        rewriteInfoEntry(info.getTitle(), info::setTitle);
        rewriteInfoEntry(info.getAuthor(), info::setAuthor);
        rewriteInfoEntry(info.getSubject(), info::setSubject);
        rewriteInfoEntry(info.getKeywords(), info::setKeywords);
    }

    private void rewriteInfoEntry(String value, Consumer<String> setter) {
        if (value == null) {
            return;
        }
        String masked = masker.mask(value);
        if (!masked.equals(value)) {
            setter.accept(masked);
            report.recordStringRewritten(RedactionTarget.METADATA);
        }
    }
}
