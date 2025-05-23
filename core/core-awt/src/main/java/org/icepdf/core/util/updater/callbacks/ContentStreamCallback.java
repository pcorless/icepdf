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

    protected static final Logger logger = Logger.getLogger(ContentStreamCallback.class.toString());

    protected Stream currentStream;
    protected ByteArrayOutputStream burnedContentOutputStream;
    protected byte[] originalContentStreamBytes;
    protected int lastTokenPosition;
    protected int lastTextPosition;
    protected float lastTjOffset;
    protected final Library library;
    protected final AffineTransform transform;
    protected boolean modifiedStream;
    protected StringObjectWriter stringObjectWriter;

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
        if (currentStream != null) {
            int contentStreamLength = originalContentStreamBytes.length;
            // make sure we don't miss any bytes.
            if (lastTokenPosition < originalContentStreamBytes.length) {
                burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                        (contentStreamLength - lastTokenPosition));
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

    public void setLastTokenPosition(int position, Integer token) throws IOException {
        // skip text writing operators as they will be handled by the StringObjectWriter implementation
        // other layout operators like ' and " are still handle by the TJ/Tj operators
        if (!isTextLayoutToken(token)) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        } else if (token == T_STAR || token == TD || token == Td) {
            // relative operators, so adjust for the modified content.
            writeLastTjOffset();
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTjOffset = 0;
            lastTokenPosition = position;
        } else if (token == BT || token == Tm) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            // hard reset, new coordinate system
            lastTjOffset = 0;
            lastTokenPosition = position;
        }
        lastTextPosition = position;
    }

    private void writeLastTjOffset() throws IOException {
        if (lastTjOffset > 0) {
            burnedContentOutputStream.write(' ');
            burnedContentOutputStream.write(String.valueOf(-lastTjOffset).getBytes());
            burnedContentOutputStream.write(' ');
            burnedContentOutputStream.write('0');
            burnedContentOutputStream.write(" Td ".getBytes());
            modifiedStream = true;
        }
    }

    private boolean isTextLayoutToken(int token) {
        return token == Tj || token == TJ || token == Td || token == TD || token == Tm || token == T_STAR || token == BT;
    }

    // write string/hex Object stored in glyphText using the specified StringObjectWriter
    public void writeModifiedStringObject(ArrayList<TextSprite> textOperators, final int operand) throws IOException {
        if (StringObjectWriter.containsFlaggedText(textOperators)) {
            // apply end string writer
            if (Operands.TJ == operand) {
                lastTjOffset = stringObjectWriter.writeTJ(burnedContentOutputStream, textOperators, lastTjOffset);
            } else {
                lastTjOffset = stringObjectWriter.writeTj(burnedContentOutputStream, textOperators, lastTjOffset);
            }
            modifiedStream = true;
        } else {
            // copy not flagged StringObjects verbatim
            int length = lastTextPosition - lastTokenPosition;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
        }
        lastTokenPosition = lastTextPosition;
    }
}
