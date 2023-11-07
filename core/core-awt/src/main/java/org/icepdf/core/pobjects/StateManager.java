/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.structure.CrossReferenceRoot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * This class is responsible for keeping track of which object in the document
 * have change.  When a file is written to disk this class is used to find
 * the object that should be written in the body section of the file as part of
 * an incremental update.
 * <br>
 * Once this object is created should be added to the library so that is
 * accessible by any PObject.
 *
 * @since 4.0
 */
public class StateManager {
    private static final Logger logger = Logger.getLogger(StateManager.class.getName());

    // a list is all we might need.
    private final Map<Reference, Change> changes;

    // access to xref size and next revision number.
    private final CrossReferenceRoot crossReferenceRoot;

    private final AtomicInteger nextReferenceNumber;

    // snapshot of currently saved changes
    private Map<Reference, StateManager.Change> savedChangesSnapshot = new HashMap<>();

    /**
     * Creates a new instance of the state manager.
     *
     * @param crossReferenceRoot document cross reference root
     */
    public StateManager(CrossReferenceRoot crossReferenceRoot) {
        this.crossReferenceRoot = crossReferenceRoot;
        // cache of objects that have changed.
        changes = new HashMap<>();

        // number of objects is always one more than the current size and
        // thus the next available number.
        nextReferenceNumber = new AtomicInteger();
        nextReferenceNumber.set(this.crossReferenceRoot.getNextAvailableReferenceNumber());
    }

    /**
     * Gets the next available reference number from the trailer.
     *
     * @return valid reference number.
     */
    public Reference getNewReferenceNumber() {
        // zero revision number for now but technically we can reuse
        // deleted references and increment the rev number.  For no we
        // keep it simple
        return new Reference(nextReferenceNumber.getAndIncrement(), 0);
    }

    /**
     * Add a new PObject containing changed data to the cache.
     *
     * @param pObject object to add to cache.
     */
    public void addChange(PObject pObject) {
        addChange(pObject, true);
    }

    /**
     * Add a new PObject containing changed data to the cache.
     *
     * @param pObject object to add to cache.
     * @param isNew   new indicates a new object that should be saved when isChanged() is called.  If false the object
     *                was added but because the object wasn't present for rendering and was created by the core library.
     */
    public void addChange(PObject pObject, boolean isNew) {
        changes.put(pObject.getReference(), new Change(pObject, isNew ? Type.CHANGE : Type.SYNTHETIC));
        int objectNumber = pObject.getReference().getObjectNumber();
        // check the reference numbers
        synchronized (this) {
            if (nextReferenceNumber.get() <= objectNumber) {
                nextReferenceNumber.set(objectNumber + 1);
            }
        }
    }

    public void addDeletion(Reference reference) {
        changes.put(reference, new Change(reference, Type.DELETE));
    }

    public void addDeletion(PObject pObject) {
        changes.put(pObject.getReference(), new Change(pObject, Type.DELETE));
    }

    /**
     * Checks the state manager to see if an instance of the specified reference
     * already exists in the cache.
     *
     * @param reference reference to look for an existing usage.
     * @return true if reference is already a key in the cache; otherwise, false.
     */
    public boolean contains(Reference reference) {
        return changes.containsKey(reference);
    }

    /**
     * Returns an instance of the specified reference
     *
     * @param reference reference to look for an existing usage
     * @return Change of corresponding reference if present
     */
    public Change getChange(Reference reference) {
        Change change = changes.get(reference);
        if (change != null) {
            return change;
        } else {
            return null;
        }
    }

    /**
     * Remove a PObject from the cache.
     *
     * @param pObject pObject to removed from the cache.
     */
    public void removeChange(PObject pObject) {
        changes.remove(pObject.getReference());
    }

    /**
     * @return If there are any changes from objects that were manipulated by user interaction
     */
    public boolean isChange() {
        return changes.values().stream().anyMatch(c -> c.type != Type.SYNTHETIC);
    }

    /**
     * @return If there are any changes that end up in the state manager form user interactions or annotations
     * needing to create missing content streams or popups.
     */
    public boolean isNoChange() {
        return changes.isEmpty();
    }


    /**
     * Sets a snapshot of the current changes.
     */
    public void setChangesSnapshot() {
        savedChangesSnapshot = Map.copyOf(changes);
    }

    /**
     * Checks that the last changesSnapshot and the current list of changes are the same or not
     *
     * @return true if the changes are different, false otherwise
     */
    public boolean hasChangedSinceLastSnapshot() {
        if (savedChangesSnapshot.size() == changes.size()) {
            return savedChangesSnapshot.entrySet().stream()
                    .anyMatch(entry -> !Objects.equals(changes.get(entry.getKey()), entry.getValue()));
        } else {
            return true;
        }
    }

    /**
     * Gets the number of change object in the state manager.
     *
     * @return zero or more changed object count.
     */
    public int getChangedSize() {
        return changes.size();
    }

    /**
     * @return An Iterator&lt;PObject&gt; for all the changes objects, sorted
     */
    public Iterator<Change> iteratorSortedByObjectNumber() {
        Collection<Change> coll = changes.values();
        Change[] arr = coll.toArray(new Change[0]);
        Arrays.sort(arr, new PObjectComparatorByReferenceObjectNumber());
        List<Change> sortedList = Arrays.asList(arr);
        return sortedList.iterator();
    }

    public CrossReferenceRoot getCrossReferenceRoot() {
        return crossReferenceRoot;
    }

    public boolean hasRedactions() {
        if (changes.isEmpty()) return false;
        Collection<Change> changesValues = changes.values();
        for (Change change : changesValues) {
            Object object = change.getPObject().getObject();
            if (object instanceof RedactionAnnotation) {
                return true;
            }
        }
        return false;
    }

    private static class PObjectComparatorByReferenceObjectNumber
            implements Comparator<Change> {
        public int compare(Change a, Change b) {
            if (a == null && b == null)
                return 0;
            else if (a == null)
                return -1;
            else if (b == null)
                return 1;
            Reference ar = a.pObject.getReference();
            Reference br = b.pObject.getReference();
            if (ar == null && br == null)
                return 0;
            else if (ar == null)
                return -1;
            else if (br == null)
                return 1;
            int aron = ar.getObjectNumber();
            int bron = br.getObjectNumber();
            if (aron < bron)
                return -1;
            else if (aron > bron)
                return 1;
            return 0;
        }
    }

    public enum Type {
        SYNTHETIC,
        CHANGE,
        DELETE
    }

    /**
     * Wrapper class of a pObject and how it was created.  The newFlag differentiates if the object was created
     * by a user action vs the core library creating an object that isn't in the source file but needed for rendering.
     */
    public static class Change {

        private final PObject pObject;
        private Type type;

        public Change(final PObject pObject, final Type type) {
            this.pObject = pObject;
            this.type = type;
        }

        public Change(final Reference reference, final Type type) {
            this.pObject = new PObject(null, reference);
            this.type = type;
        }

        public PObject getPObject() {
            return pObject;
        }

        public Type getType() {
            return type;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Change change = (Change) o;
            return type == change.type && Objects.equals(pObject, change.pObject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pObject, type);
        }
    }
}