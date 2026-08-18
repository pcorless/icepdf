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
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.redaction.ImageBurner;
import org.icepdf.core.util.redaction.InlineImageWriter;
import org.icepdf.core.util.redaction.RedactedStringObjectWriter;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
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

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations) {
        super(library, new RedactedStringObjectWriter());
        this.redactionAnnotations = redactionAnnotations;
    }

    protected ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                          AffineTransform transform) {
        super(library, new RedactedStringObjectWriter(), transform);
        this.redactionAnnotations = redactionAnnotations;
    }

    public ContentStreamCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamRedactorCallback(this.library, this.redactionAnnotations, transform);
    }

    /**
     * Marks any glyphText that intersect a flagged content bound.
     *
     * @param glyphText text to test for intersection with flagged content bounds
     */
    public void checkAndModifyText(GlyphText glyphText) {
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
            if (redactionPath != null && redactionPath.intersects(glyphBounds)) {
                logger.finer(() -> "Redacting Text: " + glyphText.getCid() + " " + glyphText.getUnicode());
                glyphText.flagged();
                // flagged is not a counter, and the remaining annotations cannot unflag it
                return;
            }
        }
    }

    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        // One image, one decision, one write. Burning and emitting inside the annotation loop
        // produced one copy of the image per annotation: a page with one intersecting and one
        // non-intersecting redaction emitted the burned image AND the untouched original, which is
        // both corrupt and a leak. It also skipped the image entirely when the list was empty.
        ImageStream imageStream = imageReference.getImageStream();
        Rectangle2D imageBounds = imageStream.getNormalizedBounds();
        boolean burned = false;
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            if (redactionPath != null && redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting inline image: " + imageStream.getWidth() + "x" + imageStream.getHeight());
                // Successive burns accumulate on the stream's decoded image, so every intersecting
                // annotation is applied before the result is written once.
                ImageBurner.burn(imageReference, redactionPath);
                burned = true;
            }
        }
        if (burned) {
            CountingOutputStream countingOutputStream = new CountingOutputStream(burnedContentOutputStream);
            InlineImageWriter.write(countingOutputStream, imageStream);
            modifiedStream = true;
        } else {
            // no redaction touches this image, copy it through verbatim
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    pos - lastTokenPosition);
        }
        lastTokenPosition = pos;
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            ImageStream imageStream = imageReference.getImageStream();
            Rectangle2D imageBounds = imageStream.getNormalizedBounds();
            if (redactionPath != null && redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting Image: " + imageStream.getPObjectReference() + " " +
                        imageStream.getWidth() + "x" + imageStream.getHeight());
                ImageBurner.burn(imageReference, redactionPath);
            }
        }
    }
}
