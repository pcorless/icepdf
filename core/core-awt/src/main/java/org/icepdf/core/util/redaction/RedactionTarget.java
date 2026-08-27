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

/**
 * The kinds of content a redaction can remove.
 * <p>
 * They divide by how a target is found rather than by what it is. The first group is
 * <em>geometric</em>: a redaction annotation covers an area of the page, and whatever is drawn there
 * is removed. The second is <em>term-driven</em>: the content is a string with no position at all -
 * an outline title, a form field's value - so nothing can be burned and the string itself is
 * rewritten instead.
 *
 * @since 7.5.0
 */
public enum RedactionTarget {

    /**
     * Text and vector content in a page's own content stream.
     */
    PAGE_CONTENT,

    /**
     * Images drawn by a page, whether an XObject or inline.
     * <p>
     * An image XObject is a shared object: one instance serves every placement of it, anywhere in
     * the document. A redaction over one placement therefore applies to all of them - redact a logo
     * on page one and it is redacted on every page that draws it. That is deliberate, and it errs
     * towards removing too much rather than too little, which is the right direction for a
     * redaction: the alternative silently leaves the same content visible everywhere else it
     * appears. It is usually what was wanted anyway, since the same image carrying the same content
     * is the same disclosure.
     */
    IMAGES,

    /**
     * Content streams of form XObjects a page draws. A redaction on a form applies to every
     * placement of it, since one stream cannot carry two different redactions.
     */
    FORM_XOBJECTS,

    /**
     * Appearance streams of annotations that overlap a redaction. Reached by geometry rather than by
     * search, because text inside an appearance stream never reaches the page's text.
     */
    ANNOTATION_APPEARANCES,

    /**
     * Marked-content {@code /ActualText} and {@code /Alt}, and the structure tree entries that go
     * with a redacted span. Positionless, so removed by association with the glyphs that went.
     */
    TAGGED_TEXT,

    /**
     * Outline (bookmark) titles.
     */
    OUTLINE,

    /**
     * The text of markup annotations - the {@code /Contents} string, and the appearance stream
     * generated from it.
     */
    ANNOTATION_CONTENTS,

    /**
     * Form field values, both {@code /V} and the {@code /DV} default that is easy to forget.
     */
    FORM_VALUES,

    /**
     * Named destinations, whose names are often derived from the heading text they point at.
     */
    DESTINATIONS,

    /**
     * Document information dictionary and XMP metadata, which routinely echo document content.
     */
    METADATA,

    /**
     * Files attached to the document. Nothing here can be masked - an attachment is an arbitrary
     * file - so this covers removing them, controlled by
     * {@link RedactionOptions#isRemoveAttachments()}.
     */
    ATTACHMENTS
}
