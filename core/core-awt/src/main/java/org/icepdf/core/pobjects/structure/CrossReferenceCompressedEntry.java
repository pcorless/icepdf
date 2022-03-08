package org.icepdf.core.pobjects.structure;


import org.icepdf.core.pobjects.Reference;


/**
 *
 */
public class CrossReferenceCompressedEntry extends CrossReferenceEntry {

    private Reference objectNumberOfContainingObjectStream;
    private int indexWithinObjectStream;

    public CrossReferenceCompressedEntry(int objectNumber, int objectNumberOfContainingObjectStream, int indexWithinObjectStream) {
        super(TYPE_COMPRESSED, objectNumber);
        this.objectNumberOfContainingObjectStream = new Reference(objectNumberOfContainingObjectStream, 0);
        this.indexWithinObjectStream = indexWithinObjectStream;
    }

    public Reference getObjectNumberOfContainingObjectStream() {
        return objectNumberOfContainingObjectStream;
    }

    public int getIndexWithinObjectStream() {
        return indexWithinObjectStream;
    }
}
