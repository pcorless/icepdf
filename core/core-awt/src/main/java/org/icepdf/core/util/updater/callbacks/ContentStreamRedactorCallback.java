package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.icepdf.core.util.parser.content.Operands.TJ;
import static org.icepdf.core.util.parser.content.Operands.Tj;

public class ContentStreamRedactorCallback {

    private Stream currentStream;
    private ByteArrayOutputStream burnedContentOutputStream;
    private byte[] originalContentStreamBytes;
    private int lastTokenPosition;
    private int lastTextPosition;

    private List<RedactionAnnotation> redactionAnnotations;

    public ContentStreamRedactorCallback(List<RedactionAnnotation> redactionAnnotations) {
        this.redactionAnnotations = redactionAnnotations;
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
            // assign accumulated byte[] to the stream
            byte[] burnedContentStream = burnedContentOutputStream.toByteArray();
            currentStream.setRawBytes(burnedContentStream);
            currentStream = null;
            String tmp = new String(burnedContentOutputStream.toByteArray(), StandardCharsets.ISO_8859_1);
            System.out.println("last " + tmp);
            burnedContentOutputStream.close();
            // TODO update the state manager with this streams changes, if any?
        }
    }

    public void setLastTokenPosition(int position, Integer token) {
//        System.out.println("last token " + position + " " + token);
        // skip text writing operators as they will be handled by the RedactionWriter
        if (token != Tj && token != TJ) {
            burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                    (position - lastTokenPosition));
            lastTokenPosition = position;
        }
        lastTextPosition = position;
    }

    // pass in current text states, so we can calculate offset of text should go if something before it was removed
    public void redactText(GlyphText glyphText, float lastx, float lasty) {
        // check for intersection with annotation bounds.
        for (RedactionAnnotation annotation : redactionAnnotations) {
            Rectangle2D bbox = annotation.getBbox();
            Rectangle2D glyphBounds = glyphText.getBounds();
            if (glyphBounds.intersects(bbox)) {
                System.out.println("redact " + glyphText.getCid() + " " + lastx + " " + lasty);
                // don't write
            }
        }

        burnedContentOutputStream.write(originalContentStreamBytes, lastTokenPosition,
                (lastTextPosition - lastTokenPosition));
        lastTokenPosition = lastTextPosition;
//        System.out.println("got some text @ " + lastTokenPosition);
    }
}
