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

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.content.Operands;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.util.parser.content.Operands.*;

/**
 * ContentStreamCallback is called when a pages content stream has been set for editing content streams.  The callback
 * is called as a content parsing starts, tokens are parsed and the content stream ends.   The callback writes
 * the original content stream to a new output stream using the current StringObjectWriter implementation.
 *
 * @since 7.3.0
 */
public abstract class ContentStreamCallback {

    protected static final Logger logger = Logger.getLogger(ContentStreamCallback.class.getName());

    protected Stream currentStream;
    protected ByteArrayOutputStream burnedContentOutputStream;
    protected byte[] originalContentStreamBytes;
    protected int lastTokenPosition;
    protected int lastTextPosition;
    protected final Library library;
    protected final AffineTransform transform;
    protected boolean modifiedStream;
    protected StringObjectWriter stringObjectWriter;
    // Bytes from the end of the previous content stream that no operator has claimed yet - see
    // endContentStream(boolean).
    private byte[] carriedBytes;

    public ContentStreamCallback(Library library, StringObjectWriter stringObjectWriter) {
        this.library = library;
        this.stringObjectWriter = stringObjectWriter;
        this.transform = new AffineTransform();
    }

    protected ContentStreamCallback(Library library, StringObjectWriter stringObjectWriter, AffineTransform transform) {
        this.library = library;
        this.stringObjectWriter = stringObjectWriter;
        // xObject text will have it's on transform that must be taken into when determining intersections of the
        // selected bounds and glyph bounds.
        this.transform = transform;
    }

    public abstract ContentStreamCallback createChildInstance(AffineTransform transform);

    /**
     * Marks any glyphText that intersect a flagged content bound.
     *
     * @param glyphText text to test for intersection with flagged content bounds
     */
    public abstract void checkAndModifyText(GlyphText glyphText);

    public abstract void checkAndModifyInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
            IOException;

    public abstract void checkAndModifyImageXObject(ImageReference imageReference) throws InterruptedException;

    public void startContentStream(Stream stream) throws IOException {
        if (currentStream != null) {
            endContentStream();
        }
        currentStream = stream;
        originalContentStreamBytes = stream.getDecompressedBytes();
        burnedContentOutputStream = new ByteArrayOutputStream();
    }

    public void endContentStream() throws IOException {
        endContentStream(false);
    }

    /**
     * Closes the current content stream.
     *
     * @param moreStreamsFollow true when this is one of several streams making up a page and another
     *                          follows. Bytes left over at the end of such a stream are not
     *                          necessarily trailing content: a page's content streams are
     *                          concatenated, so an operator's operands can sit at the end of one and
     *                          the operator itself at the start of the next. Those bytes are held
     *                          back and dealt with by whatever operator claims them, rather than
     *                          written out here where a rewrite could no longer remove them.
     * @throws IOException if the stream cannot be written
     */
    public void endContentStream(boolean moreStreamsFollow) throws IOException {
        if (currentStream != null) {
            // Anything held back from an earlier stream that no operator claimed is genuinely
            // content; write it before this stream's own bytes, which is where it sat.
            writeCarriedBytes();
            int contentStreamLength = originalContentStreamBytes.length;
            // make sure we don't miss any bytes.
            if (lastTokenPosition < originalContentStreamBytes.length) {
                if (moreStreamsFollow && !isWhitespace(originalContentStreamBytes, lastTokenPosition,
                        contentStreamLength)) {
                    carriedBytes = Arrays.copyOfRange(originalContentStreamBytes, lastTokenPosition,
                            contentStreamLength);
                    // Holding bytes back is itself a change to this stream: it no longer contains
                    // them.  Without saying so the stream is written out as it arrived, operand and
                    // all, and the replacement written into the next stream simply joins it.
                    modifiedStream = true;
                } else {
                    burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                            (contentStreamLength - lastTokenPosition));
                }
            }

            // assign accumulated byte[] to the stream
            if (modifiedStream) {
                byte[] burnedContentStream = burnedContentOutputStream.toByteArray();
                currentStream.setRawBytes(burnedContentStream);
                library.getStateManager().addChange(new PObject(currentStream, currentStream.getPObjectReference()));
                if (logger.isLoggable(Level.FINEST)) {
                    String modifiedContentStream = burnedContentOutputStream.toString(StandardCharsets.ISO_8859_1);
                    logger.finest(modifiedContentStream);
                }
            }
            burnedContentOutputStream.close();
            modifiedStream = false;
            lastTokenPosition = 0;
            lastTextPosition = 0;
            currentStream = null;
        }
    }

    /**
     * Writes out bytes held back from the previous stream. They belong ahead of whatever is written
     * next, which is where they were.
     */
    private void writeCarriedBytes() throws IOException {
        if (carriedBytes != null) {
            burnedContentOutputStream.write(carriedBytes);
            carriedBytes = null;
            // This stream now carries bytes it did not arrive with.
            modifiedStream = true;
        }
    }

    /**
     * @return true when a range holds nothing but whitespace, which is not worth relocating across a
     * stream boundary and would mark every multi-stream page as modified
     */
    private static boolean isWhitespace(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(bytes[i])) {
                return false;
            }
        }
        return true;
    }

    public void setLastTokenPosition(int position, Integer token) throws IOException {
        // A show operator decides for itself what to do with them, since it may be replacing the
        // very string they hold; anything else simply needs them written first.
        if (!isShowTextToken(token)) {
            writeCarriedBytes();
        }
        // skip text writing operators as they will be handled by the StringObjectWriter implementation
        // other layout operators like ' and " are still handle by the TJ/Tj operators
        if (!isTextLayoutToken(token)) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        } else if (token == T_STAR || token == TD || token == Td || token == BT || token == Tm) {
            // Positioning operators are copied through untouched. Rewritten text is emitted as TJ
            // adjustments, which move the text position only, so nothing a show operation did needs
            // undoing before one of these.
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        }
        lastTextPosition = position;
    }


    /**
     * @return true for the operators that show text, whose operands a redaction may be rewriting
     */
    private boolean isShowTextToken(int token) {
        return token == Tj || token == TJ || token == SINGLE_QUOTE || token == DOUBLE_QUOTE;
    }

    private boolean isTextLayoutToken(int token) {
        // ' and " show text just as Tj does, so their bytes belong to the StringObjectWriter too.
        // Leaving them out of this set copied the original string into the output ahead of the
        // replacement, and the redacted text stayed in the file.
        return token == Tj || token == TJ || token == Td || token == TD || token == Tm
                || token == T_STAR || token == BT
                || token == SINGLE_QUOTE || token == DOUBLE_QUOTE;
    }

    /**
     * Rewrites the string shown by a text operator, or copies it through untouched when nothing in
     * it was flagged.
     * <p>
     * Which operator it was no longer matters to the writer - every rewrite is emitted as a TJ
     * array - except for what the operator did <em>besides</em> showing text, which is what
     * {@code showPrefix} carries.
     *
     * @param textOperators sprites of the show operation
     * @param showPrefix    text to emit ahead of a rewritten string so an operator that does more
     *                      than show text keeps doing it: the line advance of {@code '} and
     *                      {@code "}, and the spacing {@code "} sets. Null for {@code Tj} and
     *                      {@code TJ}, which do nothing else. Unused when the string is copied
     *                      through unchanged, because then the original operator goes with it.
     * @throws IOException if the stream cannot be written
     */
    public void writeModifiedStringObject(ArrayList<TextSprite> textOperators, String showPrefix)
            throws IOException {
        if (StringObjectWriter.containsFlaggedText(textOperators)) {
            // The string being replaced may be the bytes held back from the previous stream, so
            // dropping them is how the original stops being written out alongside its replacement.
            carriedBytes = null;
            if (showPrefix != null) {
                // The bytes between the previous operator and this one are part of the range being
                // replaced, so the whitespace that separated them goes with it. Without a separator
                // here the previous "Tj" and a following "T*" would run together into one token.
                burnedContentOutputStream.write(' ');
                burnedContentOutputStream.write(showPrefix.getBytes());
            }
            // apply end string writer
            stringObjectWriter.writeShownText(burnedContentOutputStream, textOperators);
            modifiedStream = true;
        } else {
            // copy not flagged StringObjects verbatim, including any operand bytes that were left
            // at the end of the previous stream
            writeCarriedBytes();
            int length = lastTextPosition - lastTokenPosition;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
        }
        lastTokenPosition = lastTextPosition;
    }
}
