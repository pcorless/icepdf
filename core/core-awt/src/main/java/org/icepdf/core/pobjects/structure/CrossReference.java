package org.icepdf.core.pobjects.structure;


import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.parser.object.ObjectLoader;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public interface CrossReference {

    Name SIZE_KEY = new Name("Size");

    Name ROOT_KEY = new Name("Root");

    Name ENCRYPTION_KEY = new Name("Encrypt");

    Name ID_KEY = new Name("ID");

    Name INFO_KEY = new Name("Info");

    Name XREF_STRM_KEY = new Name("XRefStm");

    Name PREV_KEY = new Name("Prev");

    PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException;

    DictionaryEntries getDictionaryEntries();

    CrossReferenceEntry getEntry(Reference reference) throws ObjectStateException, CrossReferenceStateException, IOException;

    ConcurrentHashMap<Reference, CrossReferenceEntry> getAllEntries();
}
