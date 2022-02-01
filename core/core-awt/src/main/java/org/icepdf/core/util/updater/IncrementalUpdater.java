package org.icepdf.core.util.updater;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.PTrailer;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

public class IncrementalUpdater {

    /**
     * Appends modified objects to the specified output stream.
     *
     * @param document       The Document that is being saved
     * @param outputStream   OutputStream to write the incremental update to
     * @param documentLength start of appender bytes,  can be zero if storing the bytes to another source.
     * @return The number of bytes written in the incremental update
     * @throws java.io.IOException error writing stream.
     */
    public long appendIncrementalUpdate(
            Document document, OutputStream outputStream, long documentLength)
            throws IOException {

        StateManager stateManager = document.getStateManager();
        PTrailer trailer = stateManager.getTrailer();

        if (stateManager.isNoChange()) {
            return 0L;
        }

        SecurityManager securityManager = document.getSecurityManager();
        CountingOutputStream output = new CountingOutputStream(outputStream);

        BaseWriter writer = new BaseWriter(trailer, securityManager, output, documentLength);
        writer.initializeWriters();
        writer.writeNewLine();
        Iterator<StateManager.Change> changes = stateManager.iteratorSortedByObjectNumber();
        while (changes.hasNext()) {
            PObject pobject = changes.next().getPObject();
            writer.writePObject(pobject);
        }

        if (trailer.isCompressedXref()) {
            writer.writeCompressedXrefTable();
        } else {
            writer.writeXRefTable();
            writer.writeTrailer();
        }

        return writer.getBytesWritten();
    }
}
