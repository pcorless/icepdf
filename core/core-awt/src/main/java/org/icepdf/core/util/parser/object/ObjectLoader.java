package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.ObjectStream;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.pobjects.structure.CrossReferenceCompressedEntry;
import org.icepdf.core.pobjects.structure.CrossReferenceEntry;
import org.icepdf.core.pobjects.structure.CrossReferenceUsedEntry;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;

import java.io.IOException;

public class ObjectLoader {

    private final Library library;
    private final Parser parser;

    public ObjectLoader(Library library) {
        this.library = library;
        parser = new Parser(library);
    }

    public synchronized PObject loadObject(CrossReference crossReference, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {

        CrossReferenceEntry entry = crossReference.getEntry(reference);

        if (entry instanceof CrossReferenceUsedEntry) {
            CrossReferenceUsedEntry crossReferenceEntry = (CrossReferenceUsedEntry) entry;
            // parse the object
            int offset = crossReferenceEntry.getFilePositionOfObject();
            if (offset > 0) {
                synchronized (library.getMappedFileByteBufferLock()) {
                    return parser.getPObject(library.getMappedFileByteBuffer(), offset);
                }
            }
        } else if (entry instanceof CrossReferenceCompressedEntry) {
            CrossReferenceCompressedEntry compressedEntry = (CrossReferenceCompressedEntry) entry;
            Reference objectStreamRef = compressedEntry.getObjectNumberOfContainingObjectStream();
            ObjectStream objectStream = (ObjectStream) library.getObject(objectStreamRef);
            return objectStream.decompressObject(parser, compressedEntry.getIndexWithinObjectStream());
        }
        return null;
    }
}