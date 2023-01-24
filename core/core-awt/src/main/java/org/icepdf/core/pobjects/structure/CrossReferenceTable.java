package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.ObjectLoader;

import java.io.IOException;

/**
 *
 */
public class CrossReferenceTable extends CrossReferenceBase<Dictionary> {

    public CrossReferenceTable(Library library, DictionaryEntries dictionaryEntries, int xrefStartPos) {
        super(new Dictionary(library, dictionaryEntries), xrefStartPos);
    }

    @Override
    public PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {
        if (reference != null) {
            return objectLoader.loadObject(this, reference, hint);
        }
        return null;
    }

    public void addEntry(CrossReferenceEntry crossReferenceEntry) {
        int generation = 0;
        if (crossReferenceEntry instanceof CrossReferenceUsedEntry) {
            generation = ((CrossReferenceUsedEntry) crossReferenceEntry).getGenerationNumber();
            indirectObjectReferences.put(new Reference(crossReferenceEntry.objectNumber, generation), crossReferenceEntry);
        }
    }

}