package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Document;

import java.io.IOException;
import java.io.OutputStream;

public class FullUpdater {

    /**
     * Write a new document inserted and updating modified objects to the specified output stream.
     *
     * @param document     The Document that is being saved
     * @param outputStream OutputStream to write the incremental update to
     * @return The number of bytes written generating the new document
     * @throws java.io.IOException error writing stream.
     */
    public long writeDocument(
            Document document, OutputStream outputStream)
            throws IOException {
        return 0;
    }
}
