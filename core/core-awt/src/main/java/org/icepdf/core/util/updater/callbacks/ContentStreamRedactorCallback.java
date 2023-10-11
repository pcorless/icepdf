package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.content.Operands;
import org.icepdf.core.util.redaction.StringObjectWriter;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.icepdf.core.util.parser.content.Operands.TJ;
import static org.icepdf.core.util.parser.content.Operands.Tj;

public class ContentStreamRedactorCallback {

    private static final Logger logger = Logger.getLogger(ContentStreamRedactorCallback.class.toString());

    private Stream currentStream;
    private ByteArrayOutputStream burnedContentOutputStream;
    private byte[] originalContentStreamBytes;
    private int lastTokenPosition;
    private int lastTextPosition;

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
            if (lastTokenPosition < originalContentStreamBytes.length - 1) {
                burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                        (contentStreamLength - lastTokenPosition));
            }

            // assign accumulated byte[] to the stream
            byte[] burnedContentStream = burnedContentOutputStream.toByteArray();
            currentStream.setRawBytes(burnedContentStream);
            String tmp = burnedContentOutputStream.toString(StandardCharsets.ISO_8859_1);
            System.out.println("last " + tmp);
            burnedContentOutputStream.close();
            library.getStateManager().addChange(new PObject(currentStream, currentStream.getPObjectReference()));
            lastTokenPosition = 0;
            lastTextPosition = 0;
            currentStream = null;
        }
    }

    public void setLastTokenPosition(int position, Integer token) {
        // skip text writing operators as they will be handled by the RedactionWriter
        // other layout operators like ' and " are still handle by the TJ/Tj operators
        if (token != Tj && token != TJ) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        }
        lastTextPosition = position;
    }

    public void markAsRedact(GlyphText glyphText) {
        for (RedactionAnnotation annotation : redactionAnnotations) {
            Rectangle2D bbox = annotation.getBbox();
            Rectangle2D glyphBounds = glyphText.getBounds();
            if (bbox.contains(glyphBounds)) {
                glyphText.redact();
                System.out.println("redact " + glyphText.getCid() + " " + glyphText.getFontSubTypeFormat());
            }
        }
    }

    // write string/hex Object stored in glyphText, skipping and offsetting for any redacted glyphs.
    public void writeRedactedStringObject(ArrayList<TextSprite> textOperators, final int operand) throws IOException {
        if (StringObjectWriter.containsRedactions(textOperators)) {
            // apply redaction
            if (Operands.TJ == operand) {
                StringObjectWriter.writeTJ(burnedContentOutputStream, textOperators);
            } else {
                StringObjectWriter.writeTj(burnedContentOutputStream, textOperators);
            }
        } else {
            // copy none redacted StringObjects verbatim
            int length = lastTextPosition - lastTokenPosition;
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition, length);
        }
        lastTokenPosition = lastTextPosition;
    }
}
