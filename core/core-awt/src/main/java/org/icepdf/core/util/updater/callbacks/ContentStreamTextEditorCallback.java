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

import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.edit.content.SubstituteFont;
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
        this(library, text, textBounds, newText, null);
    }

    /**
     * @param substitute font to write the replacement in when the run's own cannot express it
     */
    public ContentStreamTextEditorCallback(Library library, String text, Rectangle textBounds, String newText,
                                           SubstituteFont substitute) {
        super(library, new TextStringObjectWriter(newText, substitute));
        this.newText = newText;
        this.text = text;
        this.textBounds = textBounds;
    }

    /**
     * @param stringObjectWriter writer shared with the callback this one was derived from, so the
     *                           replacement text is written once for the edit rather than once per
     *                           content stream the selection happens to span
     */
    protected ContentStreamTextEditorCallback(Library library, String text, Rectangle textBounds, String newText,
                                              StringObjectWriter stringObjectWriter, AffineTransform transform) {
        super(library, stringObjectWriter, transform);
        this.textBounds = textBounds;
        this.newText = newText;
        this.text = text;
    }

    /**
     * A form XObject gets its own callback because it has its own content stream and byte offsets,
     * but it is part of the same edit. The writer is therefore shared: it carries the "replacement
     * already written" latch, and a fresh one per form would insert the new text again for every
     * stream the selection reaches into.
     */
    public ContentStreamCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamTextEditorCallback(this.library, this.text, this.textBounds,
                this.newText, this.stringObjectWriter, transform);
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

    /**
     * An edit has nothing to say about an image, but the image's bytes still have to be written.
     * <p>
     * Advancing {@code lastTokenPosition} past them without writing tells the copy-through machinery
     * they have been dealt with, when they have been skipped: what came out was not a page missing an
     * image but a {@code BI} with no {@code ID} and no {@code EI}, which is a content stream a strict
     * reader is entitled to reject. "Nothing to do" is true of the decision and false of the bytes.
     */
    public void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException {
        burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                pos - lastTokenPosition);
        lastTokenPosition = pos;
    }

    public void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException {
        // nothing to do
    }
}
