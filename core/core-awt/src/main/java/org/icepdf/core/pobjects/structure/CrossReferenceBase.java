package org.icepdf.core.pobjects.structure;


import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.ObjectLoader;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public abstract class CrossReferenceBase<T extends Dictionary> implements CrossReference {

    public final T crossReference;

    protected final ConcurrentHashMap<Reference, CrossReferenceEntry> indirectObjectReferences;
    protected CrossReference prevCrossReference;

    protected int xrefStartPos;

    public CrossReferenceBase(T crossReference, int xrefStartPos) {
        this.crossReference = crossReference;
        this.xrefStartPos = xrefStartPos;
        indirectObjectReferences = new ConcurrentHashMap<>(1024);
    }

    public PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {
        if (reference != null) {
            return objectLoader.loadObject(this, reference, hint);
        }
        return null;
    }

    public CrossReferenceEntry getEntry(Reference reference) throws ObjectStateException,
            CrossReferenceStateException, IOException {
        CrossReferenceEntry crossReferenceEntry = indirectObjectReferences.get(reference);
        DictionaryEntries entries = crossReference.getEntries();
        Library library = crossReference.getLibrary();
        if (crossReferenceEntry == null && entries.get(PTrailer.PREV_KEY) != null) {
            if (prevCrossReference != null) {
                return prevCrossReference.getEntry(reference);
            } else {
                // try finding the entry in the previous table
                Parser parser = new Parser(library);
                synchronized (library.getMappedFileByteBufferLock()) {
                    CrossReference crossReference = parser.getCrossReference(
                            library.getMappedFileByteBuffer(), this.crossReference.getInt(PTrailer.PREV_KEY));
                    if (crossReference != null) {
                        prevCrossReference = crossReference;
                        return prevCrossReference.getEntry(reference);
                    }
                }
            }
        }
        return crossReferenceEntry;
    }

    public HashMap<Reference, CrossReferenceEntry> getEntries() {
        HashMap<Reference, CrossReferenceEntry> completeIndirectReferences =
                new HashMap<>(indirectObjectReferences.size());
        if (prevCrossReference != null) {
            completeIndirectReferences.putAll(prevCrossReference.getEntries());
        }
        // current after previous so we get the most recent object
        completeIndirectReferences.putAll(indirectObjectReferences);
        return completeIndirectReferences;
    }

    public CrossReferenceEntry getEntryNoDescendents(Reference reference) {
        return indirectObjectReferences.get(reference);
    }

    public int getXrefStartPos() {
        return xrefStartPos;
    }

    public void setXrefStartPos(int xrefStartPos) {
        this.xrefStartPos = xrefStartPos;
    }

    public DictionaryEntries getDictionaryEntries() {
        return crossReference.getEntries();
    }

}
