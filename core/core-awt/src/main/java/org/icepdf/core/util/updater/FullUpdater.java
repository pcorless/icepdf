package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.pobjects.structure.Indexer;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

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
        Catalog catalog = document.getCatalog();
        Library library = catalog.getLibrary();
        CrossReferenceRoot crossReferenceRoot = library.getCrossReferenceRoot();
        StateManager stateManager = library.getStateManager();

        Object mappedFileByteBufferLock = library.getMappedFileByteBufferLock();
        Indexer indexer = new Indexer(library);
        Parser parser = new Parser(library);
        synchronized (mappedFileByteBufferLock) {
            try {
                ByteBuffer mappedFileByteBuffer = library.getMappedFileByteBuffer();
                PObject next;
                int offset = 0;
                do {
                    next = parser.getPObject(mappedFileByteBuffer, offset);
                    offset = mappedFileByteBuffer.position();
                    if (stateManager.contains(next.getReference())) {
                        StateManager.Change change = stateManager.getChange(next.getReference());
                        System.out.println("found a change " + change.getType());
                    }
                    System.out.println(offset);
                }
                while (next != null);
            } catch (ObjectStateException e) {
                throw new RuntimeException(e);
            }
        }

        return 0;
    }
}
