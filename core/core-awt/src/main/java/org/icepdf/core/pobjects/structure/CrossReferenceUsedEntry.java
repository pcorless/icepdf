package org.icepdf.core.pobjects.structure;

/**
 *
 */
public class CrossReferenceUsedEntry extends CrossReferenceEntry {

    private int filePositionOfObject;
    private final int generationNumber;

    public CrossReferenceUsedEntry(int objectNumber, int generationNumber, int filePositionOfObject) {
        super(TYPE_USED, objectNumber);
        this.filePositionOfObject = filePositionOfObject;
        this.generationNumber = generationNumber;
    }

    public int getFilePositionOfObject() {
        return filePositionOfObject;
    }

    public int getGenerationNumber() {
        return generationNumber;
    }

    public void setFilePositionOfObject(int filePositionOfObject) {
        this.filePositionOfObject = filePositionOfObject;
    }
}