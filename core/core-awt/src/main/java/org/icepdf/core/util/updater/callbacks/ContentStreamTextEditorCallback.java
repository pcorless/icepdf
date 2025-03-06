package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.edit.content.TextStringObjectWriter;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;

/**
 * ContentStreamTextEditorCallback is called when a pages content stream has been set for edited content.  The callback
 * is called as a content parsing starts, tokens are parsed and the content stream ends.   The callback writes
 * the original content stream to a new output stream using the current content parsers state to redact content as
 * the original content stream is digested.
 *
 * @since 7.2.0
 */
public class ContentStreamTextEditorCallback extends ContentStreamCallback {

    private final Rectangle textBounds;
    private final String newText;

    public ContentStreamTextEditorCallback(Library library, Rectangle textBounds, String newText) {
        super(library, new TextStringObjectWriter());
        this.newText = newText;
        this.textBounds = textBounds;
    }

    protected ContentStreamTextEditorCallback(Library library, Rectangle textBounds, String newText,
                                              AffineTransform transform) {
        super(library, new TextStringObjectWriter(), transform);
        this.newText = newText;
        this.textBounds = textBounds;
    }

    public ContentStreamCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamTextEditorCallback(this.library, this.textBounds, this.newText, transform);
    }

    /**
     * Marks any glyphText that intersect a flagged content bound.
     *
     * @param glyphText text to test for intersection with flagged content bounds
     */
    public void checkAndModifyText(GlyphText glyphText) {
        System.out.println("Text: " + glyphText.getUnicode());
//        for (RedactionAnnotation annotation : redactionAnnotations) {
//            GeneralPath reactionPaths = annotation.getMarkupPath();
//            glyphText.normalizeToUserSpace(transform, null);
//            Rectangle2D glyphBounds = glyphText.getBounds();
//            if (reactionPaths != null && reactionPaths.contains(glyphBounds)) {
//                logger.finer(() -> "Redacting Text: " + glyphText.getCid() + " " + glyphText.getUnicode());
//                glyphText.flagged();
//            }
//        }
    }

    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        // nothing to do
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        // nothing to do
    }
}
