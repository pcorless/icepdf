package org.icepdf.core.util.updater;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class FullUpdater {

    private Library library;
    private StateManager stateManager;

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
        library = catalog.getLibrary();
        stateManager = library.getStateManager();
        CrossReferenceRoot crossReferenceRoot = library.getCrossReferenceRoot();

        SecurityManager securityManager = library.getSecurityManager();
        CountingOutputStream output = new CountingOutputStream(outputStream);

        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, output, 0);
        writer.initializeWriters();
        Object mappedFileByteBufferLock = library.getMappedFileByteBufferLock();

        synchronized (mappedFileByteBufferLock) {
            // write header
            writer.writeHeader(library.getFileHeader());

            // use the document root/catalog to iterate over the object tree writing out each object.
            // and keep track of each
            writeDictionary(writer, catalog);

            writer.writeXRefTable();
            writer.writeFullTrailer();

        }

        // todo pass
        return writer.getBytesWritten();
    }

    private void writeDictionary(BaseWriter writer, Dictionary dictionary) throws IOException {
        Reference dictionaryReference = dictionary.getPObjectReference();
        if (writer.hasNotWrittenReference(dictionaryReference)) {
            StateManager.Change change = stateManager.getChange(dictionaryReference);
            if (change != null) {
                if (change.getType() == StateManager.Type.CHANGE) {
                    writer.writePObject(change.getPObject());
                }
            } else {
                writer.writePObject(new PObject(dictionary, dictionary.getPObjectReference()));
            }
        }
        DictionaryEntries entries = dictionary.getEntries();
        findChildDictionaries(writer, entries);
    }

    private void findChildDictionaries(BaseWriter writer, DictionaryEntries entries) throws IOException {
        for (Name name : entries.keySet()) {
            Object value = entries.get(name);
            if (value instanceof Reference && writer.hasNotWrittenReference((Reference) value)) {
                Object object = library.getObject(value);
                StateManager.Change change = stateManager.getChange((Reference) value);
                if (change != null) {
                    if (change.getType() == StateManager.Type.CHANGE) {
                        writer.writePObject(change.getPObject());
                    }
                } else {
                    writer.writePObject(new PObject(object, (Reference) value));
                }
                if (object instanceof Dictionary) {
                    writeDictionary(writer, (Dictionary) object);
                } else if (object instanceof DictionaryEntries) {
                    findChildDictionaries(writer, (DictionaryEntries) object);
                }
            } else if (value instanceof List) {
                for (Object object : (List) value) {
                    if (object instanceof Reference && writer.hasNotWrittenReference((Reference) object)) {
                        Object objectReferenceValue = library.getObject(object);
                        StateManager.Change change = stateManager.getChange((Reference) object);
                        if (change != null) {
                            if (change.getType() == StateManager.Type.CHANGE) {
                                writer.writePObject(change.getPObject());
                            }
                        } else {
                            writer.writePObject(new PObject(objectReferenceValue, (Reference) object));
                        }
                        if (objectReferenceValue instanceof Dictionary) {
                            writeDictionary(writer, (Dictionary) objectReferenceValue);
                        } else if (objectReferenceValue instanceof DictionaryEntries) {
                            findChildDictionaries(writer, (DictionaryEntries) objectReferenceValue);
                        }
                    }
                }
            } else if (value instanceof DictionaryEntries) {
                findChildDictionaries(writer, (DictionaryEntries) value);
            }
        }
    }
}
