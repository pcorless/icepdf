package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.structure.Header;

import java.io.IOException;

/**
 * Writes out the head of the document.  Nothing fancy uses the overused âãÏÓ marker and the version
 * specified in the original document.
 *
 * @since 7.2
 */
public class HeaderWriter extends BaseWriter {

    private static final byte[] commentMarker = "%".getBytes();

    private static final byte[] FOUR_BYTES = "âãÏÓ".getBytes();

    public void write(Header header, CountingOutputStream output) throws IOException {
        output.write(commentMarker);
        output.write(header.getWriterVersion().getBytes());
        output.write(NEWLINE);
        output.write(commentMarker);
        output.write(FOUR_BYTES);
        output.write(NEWLINE);
    }
}
