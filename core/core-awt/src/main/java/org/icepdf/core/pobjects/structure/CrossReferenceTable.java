package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.ObjectLoader;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class CrossReferenceTable extends Dictionary implements CrossReference {

    private ConcurrentHashMap<Reference, CrossReferenceEntry> indirectObjectReferences;
    private CrossReference prefCrossReference;

    public CrossReferenceTable(Library library, DictionaryEntries dictionaryEntries) {
        super(library, dictionaryEntries);
        indirectObjectReferences = new ConcurrentHashMap<>(1024);
    }

    @Override
    public DictionaryEntries getDictionaryEntries() {
        return entries;
    }

    @Override
    public PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {
        if (reference != null) {
            return objectLoader.loadObject(this, reference, hint);
        }
        return null;
    }

    @Override
    public CrossReferenceEntry getEntry(Reference reference) throws ObjectStateException, CrossReferenceStateException, IOException {
        CrossReferenceEntry crossReferenceEntry = indirectObjectReferences.get(reference);
        if (crossReferenceEntry == null && entries.get(PREV_KEY) != null) {
            if (prefCrossReference != null) {
                return prefCrossReference.getEntry(reference);
            } else {
                // try finding the entry in the previous table
                Parser parser = new Parser(library);
                CrossReferenceTable crossReference = (CrossReferenceTable) parser.getCrossReference(
                        library.getMappedFileByteBuffer(), getInt(PREV_KEY));
                if (crossReference != null) {
                    prefCrossReference = crossReference;
                    return prefCrossReference.getEntry(reference);
                }
            }
        }
        return crossReferenceEntry;
    }

    public void addEntry(CrossReferenceEntry crossReferenceEntry) {
        int generation = 0;
        if (crossReferenceEntry instanceof CrossReferenceUsedEntry) {
            generation = ((CrossReferenceUsedEntry) crossReferenceEntry).getGenerationNumber();
            indirectObjectReferences.put(new Reference(crossReferenceEntry.objectNumber, generation), crossReferenceEntry);
        }
    }

    @Override
    public ConcurrentHashMap<Reference, CrossReferenceEntry> getAllEntries() {
        return indirectObjectReferences;
    }

}