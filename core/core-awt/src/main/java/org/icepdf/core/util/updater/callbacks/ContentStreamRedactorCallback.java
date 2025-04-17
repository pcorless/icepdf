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
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath reactionPaths = annotation.getMarkupPath();
            glyphText.normalizeToUserSpace(transform, null);
            Rectangle2D glyphBounds = glyphText.getBounds();
            if (reactionPaths != null && reactionPaths.contains(glyphBounds)) {
                logger.finer(() -> "Redacting Text: " + glyphText.getCid() + " " + glyphText.getUnicode());
                glyphText.flagged();
            }
        }
    }

    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            ImageStream imageStream = imageReference.getImageStream();
            Rectangle2D imageBounds = imageStream.getNormalizedBounds();
            if (redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting inline image: " + imageStream.getWidth() + "x" + imageStream.getHeight());
                ImageStream burnedImageStream = ImageBurner.burn(imageReference, redactionPath);
                CountingOutputStream countingOutputStream = new CountingOutputStream(burnedContentOutputStream);
                InlineImageWriter.write(countingOutputStream, burnedImageStream);
                modifiedStream = true;
            } else {
                // copy none redacted StringObjects verbatim
                int length = pos - lastTokenPosition;
                burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
            }
            lastTokenPosition = pos;
        }
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            ImageStream imageStream = imageReference.getImageStream();
            Rectangle2D imageBounds = imageStream.getNormalizedBounds();
            if (redactionPath.intersects(imageBounds)) {
                logger.finer(() -> "Redacting Image: " + imageStream.getPObjectReference() + " " +
                        imageStream.getWidth() + "x" + imageStream.getHeight());
                ImageBurner.burn(imageReference, redactionPath);
            }
        }
    }
}
