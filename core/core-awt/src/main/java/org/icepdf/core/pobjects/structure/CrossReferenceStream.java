package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class CrossReferenceStream extends CrossReferenceBase<Stream> implements CrossReference{

    private static final Logger logger =
            Logger.getLogger(CrossReferenceStream.class.toString());

    public static final Name TYPE = new Name("XRef");
    public static final Name SIZE_KEY = new Name("Size");
    public static final Name INDEX_KEY = new Name("Index");
    public static final Name W_KEY = new Name("W");

    public CrossReferenceStream(Library library, DictionaryEntries dictionaryEntries, byte[] rawBytes) {
        super(new Stream(library, dictionaryEntries, rawBytes), 0);
    }

    public void initialize() throws IOException {
        int size = crossReference.getInt(SIZE_KEY);
        List<Number> objNumAndEntriesCountPairs = crossReference.getList(INDEX_KEY);
        if (objNumAndEntriesCountPairs == null) {
            objNumAndEntriesCountPairs = new ArrayList<>(2);
            objNumAndEntriesCountPairs.add(0);
            objNumAndEntriesCountPairs.add(size);
        }
        // three int's: field values, x,y and z bytes in length.
        List fieldSizesVec = crossReference.getList(W_KEY);
        int[] fieldSizes = null;
        if (fieldSizesVec != null) {
            fieldSizes = new int[fieldSizesVec.size()];
            for (int i = 0; i < fieldSizesVec.size(); i++)
                fieldSizes[i] = ((Number) fieldSizesVec.get(i)).intValue();
        }

        // not doing anything with PREV.
        ByteBuffer byteBuffer = crossReference.getDecodedStreamByteBuffer();
        byteBuffer.position(0);

        int fieldTypeSize = fieldSizes[0];
        int fieldTwoSize = fieldSizes[1];
        int fieldThreeSize = fieldSizes[2];
        try {
            // parse out the object data.
            for (int xrefSubsection = 0; xrefSubsection < objNumAndEntriesCountPairs.size(); xrefSubsection += 2) {
                int startingObjectNumber = objNumAndEntriesCountPairs.get(xrefSubsection).intValue();
                int entriesCount = objNumAndEntriesCountPairs.get(xrefSubsection + 1).intValue();
                int afterObjectNumber = startingObjectNumber + entriesCount;
                for (int objectNumber = startingObjectNumber; objectNumber < afterObjectNumber; objectNumber++) {
                    int entryType = CrossReferenceEntry.TYPE_USED;    // Default value is 1
                    if (fieldTypeSize > 0)
                        entryType = Utils.readIntWithVaryingBytesBE(byteBuffer, fieldTypeSize);
                    // used object but not compressed
                    if (entryType == CrossReferenceEntry.TYPE_USED) {
                        int filePositionOfObject = (int) Utils.readLongWithVaryingBytesBE(byteBuffer, fieldTwoSize);
                        int generationNumber = 0;       // Default value is 0
                        if (fieldThreeSize > 0) {
                            generationNumber = Utils.readIntWithVaryingBytesBE(byteBuffer, fieldThreeSize);
                        }
                        addUsedEntry(objectNumber, generationNumber, filePositionOfObject);
                    }
                    // entries define compress objects.
                    else if (entryType == CrossReferenceEntry.TYPE_COMPRESSED) {
                        int objectNumberOfContainingObjectStream = Utils.readIntWithVaryingBytesBE(byteBuffer, fieldTwoSize);
                        int indexWithinObjectStream = Utils.readIntWithVaryingBytesBE(byteBuffer, fieldThreeSize);
                        addCompressedEntry(objectNumber, objectNumberOfContainingObjectStream, indexWithinObjectStream);
                    }
                    // free objects, no used.
                    else if (entryType == CrossReferenceEntry.TYPE_FREE) {
                        // we do nothing, but we still need to move the cursor.
                        Utils.readIntWithVaryingBytesBE(byteBuffer, fieldTwoSize);
                        Utils.readIntWithVaryingBytesBE(byteBuffer, fieldThreeSize);
                    }
                }
            }
        } catch (IOException e) {
            // trigger a reindexing of the file.
            logger.log(Level.WARNING, "Failed to initialized object stream: ", e );
            throw e;
        }
    }

    private void addUsedEntry(int objectNumber, int generationNumber, int filePositionOfObject) {
        CrossReferenceUsedEntry entry = new CrossReferenceUsedEntry(objectNumber, generationNumber, filePositionOfObject);
        indirectObjectReferences.put(new Reference(objectNumber, generationNumber), entry);
    }

    private void addCompressedEntry(int objectNumber, int objectNumberOfContainingObjectStream, int indexWithinObjectStream) {
        CrossReferenceCompressedEntry entry = new CrossReferenceCompressedEntry(objectNumber, objectNumberOfContainingObjectStream, indexWithinObjectStream);
        indirectObjectReferences.put(new Reference(objectNumber, 0), entry);
    }

}