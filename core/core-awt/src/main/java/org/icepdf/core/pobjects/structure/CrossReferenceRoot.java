package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.ObjectLoader;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Specifies the root cross-reference entry for the PDF file.  The class takes into account the possible
 * cross-reference formats, table, compressed and hybrid.   Use this class to find the byte offset of an object in
 * the pdf document.
 */
public class CrossReferenceRoot {

    private static final Logger log = Logger.getLogger(CrossReferenceRoot.class.toString());

    private final Library library;
    private Trailer trailer;
    private PTrailer pTrailer;

    private final ArrayList<CrossReference> crossReferences;

    private boolean lazyInitializationFailed;

    public CrossReferenceRoot(Library library) {
        this.library = library;
        crossReferences = new ArrayList<>();
    }

    public void setTrailer(Trailer trailer) {
        this.trailer = trailer;
    }

    public void initialize(ByteBuffer byteBuffer)
            throws CrossReferenceStateException, ObjectStateException, IOException {
        if (trailer == null) {
            throw new CrossReferenceStateException("Trailer not specified");
        }
        // validate the xref start and
        int startXref = trailer.getStartXref();
        if (startXref >= byteBuffer.limit()) {
            throw new CrossReferenceStateException("Xref offset is greater then file length");
        }
        // we can expect either standard < 1.5 trailer or 1.5 > cross-reference stream.
        // we can peak at the first 18 bytes or so to make a guess as to if we should be parsing the table
        // or the PDF object.
        Parser parser = new Parser(library);

        CrossReference crossReference = parser.getCrossReference(byteBuffer, startXref);
        crossReferences.add(crossReference);
        // check for a hybrid entry
        if (crossReference instanceof CrossReferenceTable) {
            CrossReferenceTable crossReferenceTable = (CrossReferenceTable) crossReference;
            int offset = library.getInt(crossReferenceTable.getDictionaryEntries(), PTrailer.XREF_STRM_KEY);
            if (offset > 0) {
                CrossReferenceStream xrefStream = (CrossReferenceStream) parser.getCrossReference(byteBuffer, offset);
                crossReferences.add(xrefStream);
            }
        }
        // PTrailer dictionary wrapper to aid in getting the trailer dictionary values.
        pTrailer = new PTrailer(library, crossReference.getDictionaryEntries());
    }

    public PTrailer getTrailerDictionary() {
        return pTrailer;
    }

    public ArrayList<CrossReference> getCrossReferences() {
        return crossReferences;
    }

    public int getNextAvailableReferenceNumber() {
        // to be sure we have the max number we need initialize all crossReferences and one easy way to do this
        // is to look for a fictional object.
        int maxSize = 0;
        for (CrossReference crossReference : crossReferences) {
            DictionaryEntries entries = crossReference.getDictionaryEntries();
            int size = library.getInt(entries, PTrailer.SIZE_KEY);
            maxSize = Math.max(maxSize, size);
        }
        Object shouldNotExist = library.getObject(new Reference(maxSize, 0));
        if (shouldNotExist != null) {
            log.warning("Cross reference size specifies an object that already exists.");
            maxSize += 1000;
        }
        return maxSize;
    }

    public PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {
        PObject tmp;
        for (CrossReference crossReference : crossReferences) {
            tmp = crossReference.loadObject(objectLoader, reference, hint);
            if (tmp != null) return tmp;
        }
        return null;
    }

    public void setLazyInitializationFailed(boolean failed) {
        lazyInitializationFailed = failed;
    }

    public boolean isLazyInitializationFailed() {
        return lazyInitializationFailed;
    }

    public void addCrossReference(CrossReference crossReferenceTable) {
        crossReferences.add(crossReferenceTable);
    }

}