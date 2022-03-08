package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.structure.exceptions.CrossReferenceStateException;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.object.ObjectLoader;
import org.icepdf.core.util.parser.object.Parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Can be < 1.5 uncompressed or > 1.5 compressed format.
 */
public class CrossReferenceRoot {

    private final Library library;

    private Trailer trailer;

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
            int offset = crossReferenceTable.getInt(CrossReference.XREF_STRM_KEY);
            if (offset > 0) {
                CrossReferenceStream xrefStream = (CrossReferenceStream) parser.getCrossReference(byteBuffer, offset);
                crossReferences.add(xrefStream);
            }
        }
        // setup the Ptrailer so we can
//        new PTrailer(library, crossReference.getDictionaryEntries());
    }

    public DictionaryEntries getTrailerDictionary() {
        return crossReferences.get(0).getDictionaryEntries();
    }

    public PObject loadObject(ObjectLoader objectLoader, Reference reference, Name hint)
            throws ObjectStateException, CrossReferenceStateException, IOException {
        if (crossReferences != null) {
            PObject tmp;
            for (CrossReference crossReference : crossReferences) {
                tmp = crossReference.loadObject(objectLoader, reference, hint);
                if (tmp != null) return tmp;
            }
        }
        return null;
    }

    public void setLazyInitializationFailed(boolean failed) {
        lazyInitializationFailed = failed;
    }

    public boolean isLazyInitializationFailed() {
        return lazyInitializationFailed;
    }

    public ArrayList<CrossReference> getCrossReferences() {
        return crossReferences;
    }

    public void addCrossReference(CrossReference crossReferenceTable) {
        crossReferences.add(crossReferenceTable);
    }

}