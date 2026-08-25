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
package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.util.Utils;
import org.icepdf.core.util.redaction.TermMasker;
import org.icepdf.core.util.updater.writeables.BaseWriter;
import org.icepdf.core.util.updater.writeables.DictionaryWriter;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.redaction.ImageBurner;
import org.icepdf.core.util.redaction.RedactionOptions;
import org.icepdf.core.util.redaction.RedactionReport;
import org.icepdf.core.util.redaction.RedactionTarget;
import org.icepdf.core.util.redaction.InlineImageWriter;
import org.icepdf.core.util.redaction.RedactedStringObjectWriter;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * ContentStreamRedactorCallback is called when a pages content stream has been set for redacting content.  The callback
 * is called as a content parsing starts, tokens are parsed and the content stream ends.   The callback writes
 * the original content stream to a new output stream using the current content parsers state to redact content as
 * the original content stream is digested.
 *
 * @since 7.2.0
 */
public class ContentStreamRedactorCallback extends ContentStreamCallback {

    private final List<RedactionAnnotation> redactionAnnotations;
    private final RedactionOptions options;
    private final RedactionReport report;
    private static final DictionaryWriter dictionaryWriter = new DictionaryWriter();
    private static final byte[] BDC_OPERATOR = "BDC".getBytes(StandardCharsets.ISO_8859_1);
    /** Marked-content entries that carry text a redaction has to reach. */
    private static final Name[] MARKED_CONTENT_TEXT_KEYS = {
            new Name("ActualText"), new Name("Alt"), new Name("E")};

    private final StringBuilder removedRun = new StringBuilder();
    // Null when the redaction was not given any terms, which is what an annotation-driven redaction
    // looks like; there is then nothing to match a marked-content string against.
    private final TermMasker masker;

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                         RedactionOptions options, RedactionReport report) {
        this(library, redactionAnnotations, options, report, (TermMasker) null);
    }

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                         RedactionOptions options, RedactionReport report,
                                         TermMasker masker) {
        super(library, new RedactedStringObjectWriter());
        this.redactionAnnotations = redactionAnnotations;
        this.options = options != null ? options : RedactionOptions.defaults();
        this.report = report != null ? report : new RedactionReport();
        this.masker = masker;
    }

    /**
     * @param transform maps the coordinates of the stream about to be parsed into page space, which
     *                  is where the redaction areas are. Used for a form XObject, and for an
     *                  annotation's appearance stream, both of which draw in their own space.
     */
    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                         RedactionOptions options, RedactionReport report,
                                         AffineTransform transform) {
        this(library, redactionAnnotations, options, report, transform, null);
    }

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                         RedactionOptions options, RedactionReport report,
                                         AffineTransform transform, TermMasker masker) {
        super(library, new RedactedStringObjectWriter(), transform);
        this.redactionAnnotations = redactionAnnotations;
        this.options = options;
        this.report = report;
        this.masker = masker;
    }

    /**
     * A form XObject is redacted by its own callback, since it has its own stream and byte offsets,
     * but it shares the options and the report: it is part of the same redaction.
     */
    public ContentStreamCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamRedactorCallback(this.library, this.redactionAnnotations,
                this.options, this.report, transform, this.masker);
    }

    @Override
    public boolean descendsIntoForms() {
        return options.redacts(RedactionTarget.FORM_XOBJECTS);
    }

    /**
     * Marks any glyphText that intersect a flagged content bound.
     *
     * @param glyphText text to test for intersection with flagged content bounds
     */
    public void checkAndModifyText(GlyphText glyphText) {
        if (!options.redacts(RedactionTarget.PAGE_CONTENT)) {
            return;
        }
        // normalizeToUserSpace rewrites the glyph's bounds in place, so it must happen once for the
        // glyph and not once per annotation - a second call re-applies the transform and every
        // annotation after the first tests against bounds that have drifted off the page.
        glyphText.normalizeToUserSpace(transform, null);
        Rectangle2D glyphBounds = glyphText.getBounds();
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            // Intersection, not containment.  A redaction rectangle drawn snugly over a word does
            // not contain the glyph bounds, which carry ascender, descender and side-bearing slack,
            // so containment left the glyph in the stream with the annotation merely painted over
            // it.  Erring towards removing a glyph that only grazes the region is the right
            // direction for a redaction, and it matches the predicate the image paths already use.
            if (covers(redactionPath, glyphBounds)) {
                logger.finer(() -> "Redacting Text: " + glyphText.getCid() + " " + glyphText.getUnicode());
                glyphText.flagged();
                report.recordGlyphsRemoved(1, RedactionTarget.PAGE_CONTENT);
                removedRun.append(glyphText.getUnicode());
                // flagged is not a counter, and the remaining annotations cannot unflag it
                return;
            }
        }
        // A glyph survived, so whatever run was being removed has ended.
        flushRemovedRun();
    }

    @Override
    public void endContentStream() throws IOException {
        // The stream ended while a run was still being removed, which happens whenever the redacted
        // text runs to the end of the last string shown.
        flushRemovedRun();
        super.endContentStream();
    }

    /**
     * Hands the run of characters just removed to the report.
     * <p>
     * Glyphs arrive in show order, so consecutive flagged ones are a word or phrase; keeping them
     * together gives the verification pass something meaningful to search the written file for.
     * Without it a redaction driven by annotations rather than terms has nothing to check against,
     * since nobody ever said what the words were.
     */
    private void flushRemovedRun() {
        if (removedRun.length() > 0) {
            report.recordRemovedText(removedRun.toString());
            removedRun.setLength(0);
        }
    }

    /**
     * Whether a redaction removes this glyph.
     * <p>
     * Any overlap counts by default. A redaction drawn snugly over a word does not contain the glyph
     * bounds, which carry ascender, descender and side-bearing slack, so requiring containment left
     * the text in the file with the annotation painted over it. Raising
     * {@link RedactionOptions#getGlyphCoverageThreshold()} trades that back against clipping a glyph
     * on a neighbouring line when leading is tight.
     */
    private boolean covers(GeneralPath redactionPath, Rectangle2D glyphBounds) {
        if (redactionPath == null || !redactionPath.intersects(glyphBounds)) {
            return false;
        }
        float threshold = options.getGlyphCoverageThreshold();
        if (threshold <= RedactionOptions.ANY_INTERSECTION) {
            return true;
        }
        double glyphArea = glyphBounds.getWidth() * glyphBounds.getHeight();
        if (glyphArea <= 0) {
            return true;
        }
        Area covered = new Area(redactionPath);
        covered.intersect(new Area(glyphBounds));
        Rectangle2D coveredBounds = covered.getBounds2D();
        double coveredArea = coveredBounds.getWidth() * coveredBounds.getHeight();
        return coveredArea / glyphArea >= threshold;
    }


    /**
     * Masks a term out of a marked-content property list.
     * <p>
     * A tagged PDF keeps a second copy of its words outside the glyphs: {@code /ActualText} and
     * {@code /Alt} say what a span really reads, which is exactly what "copy text" and a screen
     * reader use. Burning the glyphs leaves that copy sitting in the content stream, so a redaction
     * that stopped at the page's visible text would still hand over the sentence it removed.
     * <p>
     * Term-driven, like everything else without a position: a redaction driven only by rectangles was
     * never told what the words were, so there is nothing here to match against and the property list
     * is copied through unchanged.
     */
    @Override
    public void checkAndModifyMarkedContent(Name tag, Object properties, int pos) throws IOException {
        if (masker == null || !options.redacts(RedactionTarget.TAGGED_TEXT)
                || !(properties instanceof DictionaryEntries)) {
            super.checkAndModifyMarkedContent(tag, properties, pos);
            return;
        }
        DictionaryEntries entries = (DictionaryEntries) properties;
        boolean masked = false;
        for (Name key : MARKED_CONTENT_TEXT_KEYS) {
            Object value = library.getObject(entries, key);
            if (!(value instanceof StringObject)) {
                continue;
            }
            String text = Utils.convertStringObject(library, (StringObject) value);
            String replacement = masker.mask(text);
            if (!replacement.equals(text)) {
                entries.put(key, new LiteralStringObject(replacement));
                report.recordStringRewritten(RedactionTarget.TAGGED_TEXT);
                masked = true;
            }
        }
        if (!masked) {
            super.checkAndModifyMarkedContent(tag, properties, pos);
            return;
        }
        // Re-emitted rather than patched in place, using the writer the inline-image path already
        // uses, so the dictionary comes out well-formed whatever was in it.
        CountingOutputStream out = new CountingOutputStream(burnedContentOutputStream);
        out.write(("/" + tag.getName()).getBytes(StandardCharsets.ISO_8859_1));
        out.write(BaseWriter.SPACE);
        dictionaryWriter.writeInline(entries, out);
        out.write(BaseWriter.SPACE);
        out.write(BDC_OPERATOR);
        lastTokenPosition = pos;
        modifiedStream = true;
    }

    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        // One image, one decision, one write. Burning and emitting inside the annotation loop
        // produced one copy of the image per annotation: a page with one intersecting and one
        // non-intersecting redaction emitted the burned image AND the untouched original, which is
        // both corrupt and a leak. It also skipped the image entirely when the list was empty.
        ImageStream imageStream = imageReference.getImageStream();
        Rectangle2D imageBounds = imageReference.getNormalizedBounds();
        boolean burned = false;
        for (RedactionAnnotation annotation : options.redacts(RedactionTarget.IMAGES)
                ? redactionAnnotations : Collections.<RedactionAnnotation>emptyList()) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            if (redactionPath != null && redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting inline image: " + imageStream.getWidth() + "x" + imageStream.getHeight());
                // Successive burns accumulate on the stream's decoded image, so every intersecting
                // annotation is applied before the result is written once.
                ImageBurner.burn(imageReference, redactionPath, options.getRedactionColor());
                burned = true;
            }
        }
        if (burned) {
            CountingOutputStream countingOutputStream = new CountingOutputStream(burnedContentOutputStream);
            InlineImageWriter.write(countingOutputStream, imageStream);
            report.recordImageBurned(RedactionTarget.IMAGES);
            modifiedStream = true;
        } else {
            // no redaction touches this image, copy it through verbatim
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    pos - lastTokenPosition);
        }
        lastTokenPosition = pos;
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        if (!options.redacts(RedactionTarget.IMAGES)) {
            return;
        }
        // Bounds come from the reference, which is created per Do, not from the image stream, which
        // one instance of serves every placement of the image in the document.  Read off the stream
        // they described whichever placement was drawn first, so a redaction over a second placement
        // matched nothing and silently left it - and a redaction over the first matched a placement
        // it did not cover.
        Rectangle2D imageBounds = imageReference.getNormalizedBounds();
        if (imageBounds == null) {
            return;
        }
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            ImageStream imageStream = imageReference.getImageStream();
            if (redactionPath != null && redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting Image: " + imageStream.getPObjectReference() + " " +
                        imageStream.getWidth() + "x" + imageStream.getHeight());
                ImageBurner.burn(imageReference, redactionPath, options.getRedactionColor());
                report.recordImageBurned(RedactionTarget.IMAGES);
            }
        }
    }
}
