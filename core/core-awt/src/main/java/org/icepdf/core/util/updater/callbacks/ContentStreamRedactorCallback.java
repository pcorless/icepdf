package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.graphics.images.references.ImageReference;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.content.Operands;
import org.icepdf.core.util.redaction.ImageBurner;
import org.icepdf.core.util.redaction.InlineImageWriter;
import org.icepdf.core.util.redaction.StringObjectWriter;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.util.parser.content.Operands.*;

/**
 * ContentStreamRedactorCallback is called when a pages content stream has been set for redacting content.  The callback
 * is called as a content parsing starts, tokens are parsed and the content stream ends.   The callback writes
 * the original content stream to a new output stream using the current content parsers state to redact content as
 * the original content stream is digested.
 *
 * @since 7.2.0
 */
public class ContentStreamRedactorCallback {

    private static final Logger logger = Logger.getLogger(ContentStreamRedactorCallback.class.toString());

    private Stream currentStream;
    private ByteArrayOutputStream burnedContentOutputStream;
    private byte[] originalContentStreamBytes;
    private int lastTokenPosition;
    private int lastTextPosition;
    private float lastTjOffset;
    private final Library library;
    private final AffineTransform transform;
    private boolean modifiedStream;

    private final List<RedactionAnnotation> redactionAnnotations;

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations) {
        this.redactionAnnotations = redactionAnnotations;
        this.library = library;
        this.transform = new AffineTransform();
    }

    private ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations,
                                          AffineTransform transform) {
        this.redactionAnnotations = redactionAnnotations;
        this.library = library;
        // xObject text will have it's on transform that must be taken into when determining intersections of the
        // redaction and glyph bounds.
        this.transform = transform;
    }

    public ContentStreamRedactorCallback createChildInstance(AffineTransform transform) {
        return new ContentStreamRedactorCallback(this.library, this.redactionAnnotations, transform);
    }

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
                    String redactedContentStream = burnedContentOutputStream.toString(StandardCharsets.ISO_8859_1);
                    logger.finest(redactedContentStream);
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
        // skip text writing operators as they will be handled by the RedactionWriter
        // other layout operators like ' and " are still handle by the TJ/Tj operators
        if (!isTextLayoutToken(token)) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        } else if (token == T_STAR || token == TD || token == Td) {
            // relative operators, so adjust for the redacted content.
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

    /**
     * Marks any glyphText that intersect a redaction bound.
     *
     * @param glyphText text to test for intersection with redact annotations
     */
    public void checkAndRedactText(GlyphText glyphText) {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath reactionPaths = annotation.getMarkupPath();
            glyphText.normalizeToUserSpace(transform, null);
            Rectangle2D glyphBounds = glyphText.getBounds();
            if (reactionPaths != null && reactionPaths.contains(glyphBounds)) {
                logger.finer(() -> "Redacting Text: " + glyphText.getCid() + " " + glyphText.getUnicode());
                glyphText.redact();
            }
        }
    }

    public void checkAndRedactInlineImage(ImageReference imageReference, int pos) throws InterruptedException,
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

    public void checkAndRedactImageXObject(ImageReference imageReference) throws InterruptedException {
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

    // write string/hex Object stored in glyphText, skipping and offsetting for any redacted glyphs.
    public void writeRedactedStringObject(ArrayList<TextSprite> textOperators, final int operand) throws IOException {
        if (StringObjectWriter.containsRedactions(textOperators)) {
            // apply redaction
            if (Operands.TJ == operand) {
                lastTjOffset = StringObjectWriter.writeTJ(burnedContentOutputStream, textOperators);
            } else {
                lastTjOffset = StringObjectWriter.writeTj(burnedContentOutputStream, textOperators);
            }
            modifiedStream = true;
        } else {
            // copy none redacted StringObjects verbatim
            int length = lastTextPosition - lastTokenPosition;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
        }
        lastTokenPosition = lastTextPosition;
    }
}
