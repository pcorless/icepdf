package org.icepdf.core.util.updater.callbacks;

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
import org.icepdf.core.util.redaction.StringObjectWriter;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.icepdf.core.util.parser.content.Operands.*;

public class ContentStreamRedactorCallback {

    private static final Logger logger = Logger.getLogger(ContentStreamRedactorCallback.class.toString());

    private Stream currentStream;
    private ByteArrayOutputStream burnedContentOutputStream;
    private byte[] originalContentStreamBytes;
    private int lastTokenPosition;
    private int lastTextPosition;
    // keep track of Tj followed by TD layout interactions
    private int lastToken;
    private int lastTextToken;
    private float lastTjOffset;

    private Library library;

    private List<RedactionAnnotation> redactionAnnotations;

    public ContentStreamRedactorCallback(Library library, List<RedactionAnnotation> redactionAnnotations) {
        this.redactionAnnotations = redactionAnnotations;
        this.library = library;
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
            byte[] burnedContentStream = burnedContentOutputStream.toByteArray();
            currentStream.setRawBytes(burnedContentStream);
            String tmp = burnedContentOutputStream.toString(StandardCharsets.ISO_8859_1);
            burnedContentOutputStream.close();
            library.getStateManager().addChange(new PObject(currentStream, currentStream.getPObjectReference()));
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
            writeLastTjOffset();
            lastTextToken = 0;
            lastTjOffset = 0;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        } else if (token == BT) {
            lastTextToken = 0;
            lastTjOffset = 0;
        }
        if (isTextLayoutToken(token)) {
            lastTextToken = token;
        }
        lastTextPosition = position;
        lastToken = token;
    }

    private void writeLastTjOffset() throws IOException {
        if (lastTjOffset > 0) {
            burnedContentOutputStream.write(' ');
            burnedContentOutputStream.write(String.valueOf(-lastTjOffset).getBytes());
            burnedContentOutputStream.write(' ');
            burnedContentOutputStream.write('0');
            burnedContentOutputStream.write(" Td ".getBytes());
        }
    }

    private boolean isTextLayoutToken(int token) {
        return token == Tj || token == TJ || token == Td || token == TD || token == T_STAR || token == BT;
    }

    public void checkAndRedactText(GlyphText glyphText) {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath reactionPaths = annotation.getMarkupPath();
            Rectangle2D glyphBounds = glyphText.getBounds();
            if (reactionPaths.contains(glyphBounds)) {
                glyphText.redact();
            }
        }
    }

    public void checkAndRedactImageXObject(ImageReference imageReference) throws InterruptedException {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            GeneralPath redactionPath = annotation.getMarkupPath();
            ImageStream imageStream = imageReference.getImageStream();
            Rectangle2D imageBounds = imageStream.getNormalizedBounds();
            if (redactionPath.intersects(imageBounds)) {
                System.out.println("Redacting: " + imageStream.getPObjectReference() + " " +
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
        } else {
            // copy none redacted StringObjects verbatim
            int length = lastTextPosition - lastTokenPosition;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
        }
        lastTokenPosition = lastTextPosition;
    }
}
