package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;

/**
 *
 */
public class CrossReferenceTable extends CrossReferenceBase<Dictionary> {

    public CrossReferenceTable(Library library, DictionaryEntries dictionaryEntries, int xrefStartPos) {
        super(new Dictionary(library, dictionaryEntries), xrefStartPos);
    }

    public void addEntry(CrossReferenceEntry crossReferenceEntry) {
        int generation;
        if (crossReferenceEntry instanceof CrossReferenceUsedEntry) {
            generation = ((CrossReferenceUsedEntry) crossReferenceEntry).getGenerationNumber();
            indirectObjectReferences.put(new Reference(crossReferenceEntry.objectNumber, generation), crossReferenceEntry);
        }
    }

}