package org.icepdf.core.pobjects.structure;

public class CrossReferenceEntry {

    public static final int TYPE_FREE = 0;
    public static final int TYPE_USED = 1;
    public static final int TYPE_COMPRESSED = 2;

    protected int type;
    protected int objectNumber;

    public CrossReferenceEntry(int type, int objectNumber) {
        this.type = type;
        this.objectNumber = objectNumber;
    }

    public float getType() {
        return type;
    }

    public float getObjectNumber() {
        return objectNumber;
    }
}
