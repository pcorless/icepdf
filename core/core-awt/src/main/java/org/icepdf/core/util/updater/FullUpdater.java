package org.icepdf.core.util.updater;

import org.icepdf.core.io.CountingOutputStream;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.security.SecurityManager;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.writeables.BaseWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writes a document stream in its entirety.  The document's root object is used ot traverse the page tree
 * checking each object's state in the state manager and writing any modifications.  Any object that is marked
 * as deleted will not be written to the new stream.
 *
 * @since 7.2.0
 */
public class FullUpdater {

    /**
     * Write the xrefTable in a compressed format by default.  Can be disabled if to aid in debugging or to
     * support old PDF versions.
     */
    public static boolean compressXrefTable = Defs.booleanProperty(
            "org.icepdf.core.utils.fullUpdater.compressXref", true);

    private Library library;
    private StateManager stateManager;

    public static boolean isCompressXrefTable() {
        return compressXrefTable;
    }

    public static void setCompressXrefTable(boolean compressXrefTable) {
        FullUpdater.compressXrefTable = compressXrefTable;
    }

    /**
     * Write a new document inserting and updating modified objects to the specified output stream.
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
        PTrailer pTrailer = crossReferenceRoot.getTrailerDictionary();

        SecurityManager securityManager = library.getSecurityManager();
        CountingOutputStream output = new CountingOutputStream(outputStream);

        BaseWriter writer = new BaseWriter(crossReferenceRoot, securityManager, output, 0);
        writer.initializeWriters();
        Object mappedFileByteBufferLock = library.getMappedFileByteBufferLock();

        synchronized (mappedFileByteBufferLock) {
            // write header
            writer.writeHeader(library.getFileHeader());

            // use the document root to iterate over the object tree writing out each object.
            writeDictionary(writer, pTrailer);

            // this can be optimized later, but we can't use a compressed xref table for /encrypt dictionary as there
            // is no way to decompress the stream as the key is encrypted.  Basically we can't encrypt /encrypt in
            // a compressed stream,  it needs to go in a xref table and the other objects all go in the compressed
            // xref stream.
            if (compressXrefTable && securityManager == null ||
                    (securityManager != null &&
                            securityManager.getEncryptionKey() == null)) {
                writer.writeFullCompressedXrefTable();
            } else {
                writer.writeXRefTable();
                writer.writeFullTrailer();
            }
        }
        output.close();

        return writer.getBytesWritten();
    }

    private void writeDictionary(BaseWriter writer, Dictionary dictionary) throws IOException {
        Reference dictionaryReference = dictionary.getPObjectReference();
        if (dictionaryReference != null && writer.hasNotWrittenReference(dictionaryReference)) {
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
        writeDictionaryEntries(writer, entries);
    }

    private void writePObject(BaseWriter writer, Name name, Object object) throws IOException {
        if (object instanceof Reference && writer.hasNotWrittenReference((Reference) object)) {
            PObject pobject = library.getPObject(object);
            // possible to have unreferenced object in a file,  todo: file could be corrected
            if (pobject == null) {
                return;
            }
            Object objectReferenceValue = pobject.getObject();
            StateManager.Change change = stateManager.getChange((Reference) object);
            if (change != null) {
                if (change.getType() != StateManager.Type.DELETE) {
                    writer.writePObject(change.getPObject());
                }
            } else if (pobject.getReference() != null) {
                // not happy about this, downside of recursion
                if (name != null && name.equals("Encrypt")) {
                    pobject.setDoNotEncrypt(true);
                }
                writer.writePObject(pobject);
            } else {
                writer.writePObject(new PObject(objectReferenceValue, (Reference) object));
            }
            if (objectReferenceValue instanceof Dictionary) {
                writeDictionary(writer, (Dictionary) objectReferenceValue);
            } else if (objectReferenceValue instanceof DictionaryEntries) {
                writeDictionaryEntries(writer, (DictionaryEntries) objectReferenceValue);
            } else if (objectReferenceValue instanceof List) {
                writeList(writer, name, (List) objectReferenceValue);
            }
        }
    }

    private void writeDictionaryEntries(BaseWriter writer, DictionaryEntries entries) throws IOException {
        for (Name name : entries.keySet()) {
            Object value = entries.get(name);
            if (value instanceof Reference && writer.hasNotWrittenReference((Reference) value)) {
                writePObject(writer, name, value);
            } else if (value instanceof Dictionary) {
                writeDictionary(writer, (Dictionary) value);
            } else if (value instanceof DictionaryEntries) {
                writeDictionaryEntries(writer, (DictionaryEntries) value);
            } else if (value instanceof List) {
                writeList(writer, name, (List) value);
            }
        }
    }

    private void writeList(BaseWriter writer, Name name, List values) throws IOException {
        for (Object object : values) {
            writePObject(writer, name, object);
        }
    }
}
