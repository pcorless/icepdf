package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;
import org.icepdf.core.util.Library;

import java.nio.ByteBuffer;

/**
 *
 */
public class ObjectFactory {
    @SuppressWarnings("unchecked")
    public static PObject getInstance(Library library, int objectNumber, int generationNumber,
                                      Object objectData, ByteBuffer streamData) {
        // if we have as a byteBuffer then we have a stream.
        if (streamData != null) {
            DictionaryEntries entries = (DictionaryEntries) objectData;
            Name subTypeName = (Name) entries.get(Dictionary.TYPE_KEY);
            // todo come back an eval if we want byteBuffers or not as there is shit ton of refactoring work otherwise.
            byte[] bufferBytes = new byte[streamData.remaining()];
            streamData.get(bufferBytes);
            if (CrossReferenceStream.TYPE.equals(subTypeName)) {
                return new PObject(new CrossReferenceStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (ObjectStream.TYPE.equals(subTypeName)) {
                return new PObject(new ObjectStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else {
                return new PObject(new Stream(library, entries, bufferBytes), objectNumber, generationNumber);
            }
        } else if (objectData instanceof DictionaryEntries) {
            DictionaryEntries entries = (DictionaryEntries) objectData;
            Name subType = library.getName(entries, Dictionary.TYPE_KEY);
            if (subType != null) {
                if (Catalog.TYPE.equals(subType)) {
                    return new PObject(new Catalog(library, entries), objectNumber, generationNumber);
                } else if (PageTree.TYPE.equals(subType)) {
                    return new PObject(new PageTree(library, entries), objectNumber, generationNumber);
                } else if (Page.TYPE.equals(subType)) {
                    return new PObject(new Page(library, entries), objectNumber, generationNumber);
                }
            }
        }
        return new PObject(objectData, objectNumber, generationNumber);
    }
}
