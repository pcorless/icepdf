package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.pobjects.Reference;

public class Entry {
    private static final long POSITION_DELETED = -1;

    private final Reference reference;
    private final long position;
    private int nextDeletedObjectNumber;

    protected Entry(Reference ref, long pos) {
        reference = ref;
        position = pos;
    }

    protected Entry(Reference ref) {
        reference = ref;
        position = POSITION_DELETED;
    }

    protected Reference getReference() {
        return reference;
    }

    protected boolean isDeleted() {
        return position == POSITION_DELETED;
    }

    protected long getPosition() {
        return position;
    }

    protected void setNextDeletedObjectNumber(int nextDelObjNum) {
        nextDeletedObjectNumber = nextDelObjNum;
    }

    protected int getNextDeletedObjectNumber() {
        return nextDeletedObjectNumber;
    }
}