package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.edit.content.TextStringObjectWriter;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

/**
 * ContentStreamTextEditorCallback is called when a pages content stream has been set for edited content.  The callback
 * is called as a content parsing starts, tokens are parsed and the content stream ends.   The callback writes
 * the original content stream to a new output, removes the text marked as edited and replaces it with the new text.
 *
 * @since 7.3.0
 */
public class ContentStreamTextEditorCallback extends ContentStreamCallback {

    private final Rectangle textBounds;
    private String text;
    private String newText;


    public ContentStreamTextEditorCallback(Library library, String text, Rectangle textBounds, String newText) {
        super(library, new TextStringObjectWriter(newText));
        this.newText = newText;
        this.text = text;
        this.textBounds = textBounds;
    }

    protected ContentStreamTextEditorCallback(Library library, String text, Rectangle textBounds, String newText,
                                              AffineTransform transform) {
        super(library, new TextStringObjectWriter(newText), transform);
        this.textBounds = textBounds;
        this.newText = newText;
        this.text = text;
    }

    public ContentStreamCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamTextEditorCallback(this.library, this.text, this.textBounds, this.newText, transform);
    }

    /**
     * Marks any glyphText that intersect a flagged content bound.
     *
     * @param glyphText text to test for intersection with flagged content bounds
     */
    public void checkAndModifyText(GlyphText glyphText) {
        glyphText.normalizeToUserSpace(transform, null);
        Rectangle2D glyphBounds = glyphText.getBounds();
        if (textBounds != null && textBounds.contains(glyphBounds)) {
            glyphText.flagged();
        }
    }

    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        // nothing to do
        lastTokenPosition = pos;
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        // nothing to do
    }
}
