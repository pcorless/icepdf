package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.parser.object.ObjectLoader;

import java.io.IOException;
import java.util.HashMap;

public interface CrossReference {

    PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    CrossReferenceEntry getEntry(Reference reference)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    HashMap<Reference, CrossReferenceEntry> getEntries();

    int getXrefStartPos();

    DictionaryEntries getDictionaryEntries();
}