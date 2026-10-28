/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.pobjects.Reference;

public class Entry {
    private static final long POSITION_DELETED = -1;

    private final Reference reference;
    private final long position;
    private int nextDeletedObjectNumber;
    // set for an object written inside an object stream
    private final ObjectStreamWriter.Group objectStream;
    private final int indexInObjectStream;

    protected Entry(Reference ref, long pos) {
        reference = ref;
        position = pos;
        objectStream = null;
        indexInObjectStream = -1;
    }

    protected Entry(Reference ref) {
        this(ref, POSITION_DELETED);
    }

    /** An object stored in an object stream, at the given index. */
    Entry(Reference ref, ObjectStreamWriter.Group objectStream, int index) {
        reference = ref;
        position = POSITION_DELETED;
        this.objectStream = objectStream;
        indexInObjectStream = index;
    }

    protected Reference getReference() {
        return reference;
    }

    protected boolean isDeleted() {
        return position == POSITION_DELETED && objectStream == null;
    }

    boolean isCompressed() {
        return objectStream != null;
    }

    int getObjectStreamNumber() {
        return objectStream.getObjectNumber();
    }

    int getIndexInObjectStream() {
        return indexInObjectStream;
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