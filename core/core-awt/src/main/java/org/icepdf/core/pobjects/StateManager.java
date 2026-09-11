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
 * have changed.  When a file is written to disk this class is used to find
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

    // temp changes, don't want to serialize these, but need to keep the objects around.
    private final Map<Reference, PObject> tempChanges;

    // access to xref size and next revision number.
    private final CrossReferenceRoot crossReferenceRoot;

    private final AtomicInteger nextReferenceNumber;
    private final AtomicInteger nextImageNumber;

    // snapshot of currently saved changes
    private Map<Reference, StateManager.Change> savedChangesSnapshot = new HashMap<>();

    // per thread nesting depth of the repair scope; null means the thread is recording user edits.
    private final ThreadLocal<Integer> repairDepth = new ThreadLocal<>();

    /**
     * Creates a new instance of the state manager.
     *
     * @param crossReferenceRoot document cross reference root
     */
    public StateManager(CrossReferenceRoot crossReferenceRoot) {
        this.crossReferenceRoot = crossReferenceRoot;
        // cache of objects that have changed.
        changes = new HashMap<>();
        tempChanges = new HashMap<>();

        // number of objects is always one more than the current size and
        // thus the next available number.
        nextReferenceNumber = new AtomicInteger();
        nextReferenceNumber.set(this.crossReferenceRoot.getNextAvailableReferenceNumber());
        // named image reference count
        nextImageNumber = new AtomicInteger((int) (Math.random() * 1000));
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
     * Gets an image number to be used when generating image references.  The initial value is randomly
     * generated and incremented for each addition image added to the document.  There should be very little
     * chance that signature would have the same name.
     *
     * @return unique image number for building images names
     */
    public int getNextImageNumber() {
        return nextImageNumber.getAndIncrement();
    }

    /**
     * Runs a repair, recording every change it makes as {@link Type#REPAIR} rather than as a user edit.
     * <br>
     * A repair is a change the library makes to a deficient file so that it can be rendered - generating a missing
     * appearance stream, manufacturing a popup the file never had, honouring /NeedAppearances.  The user did not ask
     * for it, so on its own it must not make the document look modified.
     * <br>
     * The scope, rather than a flag on each call, is what makes this reliable: a repair reaches well past the method
     * that started it - into the appearance form, its font, the font descriptor, the font programme and the
     * /ToUnicode CMap - and none of those have any way of knowing why they were called.  Everything reached inside
     * the scope is a repair, however deep, and everything outside it is a user edit.  Scopes nest, and the depth is
     * per thread, so a page initialising on a worker thread cannot mark an edit made on the event thread as a repair.
     *
     * @param repair work to carry out with repair recording in effect.
     */
    public void repairing(Runnable repair) {
        Integer depth = repairDepth.get();
        repairDepth.set(depth == null ? 1 : depth + 1);
        try {
            repair.run();
        } finally {
            Integer current = repairDepth.get();
            if (current == null || current <= 1) {
                repairDepth.remove();
            } else {
                repairDepth.set(current - 1);
            }
        }
    }

    /**
     * Deliberately not public.  This is the read side of {@link #repairing(Runnable)}, not a check for callers to
     * make: the moment a call site can ask, it can also decide the type for itself, which is the boolean parameter
     * this scope replaced.  {@link #addChange(PObject)} is the only caller.
     *
     * @return true if the calling thread is inside a {@link #repairing(Runnable)} scope.
     */
    boolean isRepairing() {
        return repairDepth.get() != null;
    }

    /**
     * Add a new PObject containing changed data to the cache.  The change is recorded as a user edit unless the
     * calling thread is inside a {@link #repairing(Runnable)} scope, in which case it is recorded as a repair.
     *
     * @param pObject object to add to cache.
     */
    public void addChange(PObject pObject) {
        addChange(pObject, isRepairing() ? Type.REPAIR : Type.CHANGE);
    }

    /**
     * Add a new PObject containing temporary changed data to the cache.
     *
     * @param pObject object to add to cache.
     */
    public void addTempChange(PObject pObject) {
        tempChanges.put(pObject.getReference(), pObject);
    }

    private void addChange(PObject pObject, Type type) {
        changes.merge(pObject.getReference(), new Change(pObject, type), StateManager::strongest);
        int objectNumber = pObject.getReference().getObjectNumber();
        // check the reference numbers
        synchronized (this) {
            if (nextReferenceNumber.get() <= objectNumber) {
                nextReferenceNumber.set(objectNumber + 1);
            }
        }
    }

    /**
     * Resolves two changes to the same reference.  A repair must never quietly undo a user edit, which is what a
     * plain put allowed: an edit followed by a zoom-triggered appearance regeneration used to leave the reference
     * looking like something the library invented, and the document then closed without offering to save.  The
     * newest content always wins; the strongest reason for writing it out wins with it.
     */
    private static Change strongest(Change existing, Change incoming) {
        if (incoming.getType() != Type.REPAIR) {
            // a user edit, or a deletion, always takes precedence.
            return incoming;
        }
        if (existing.getType() == Type.DELETE) {
            // a repair must not resurrect an object the user deleted.
            return existing;
        }
        if (existing.getType() == Type.CHANGE) {
            // keep the repaired content, but the object is still here because the user changed it.
            return new Change(incoming.getPObject(), Type.CHANGE);
        }
        return incoming;
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
        return changes.containsKey(reference) || tempChanges.containsKey(reference);
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
     * Returns an instance of the specified reference from the temporary changes
     * @param reference reference to look for an existing usage
     * @return PObject of corresponding reference if present
     */
    public PObject getTempChange(Reference reference) {
        return tempChanges.get(reference);
    }

    /**
     * Checks to see if there are any temporary changes
     *
     * @return true if there are temporary changes, false otherwise
     */
    public boolean isTempChanges() {
        return !tempChanges.isEmpty();
    }

    /**
     * Clears all temporary changes
     */
    public void clearTempChanges() {
        tempChanges.clear();
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
     * Whether the document holds edits the user made and has not saved.  This is the question behind "do you want to
     * save your changes?" and behind enabling the save action, and it is the only query that filters out repairs.
     * <br>
     * Comparison is against the last {@link #setChangesSnapshot()}, so saving and then closing does not ask again.
     *
     * @return true if the user's edits differ from the ones last written out.
     */
    public boolean hasUnsavedUserChanges() {
        return !userChanges(changes).equals(userChanges(savedChangesSnapshot));
    }

    /**
     * Whether there is anything at all to write.  Deliberately blind to {@link Type}: once a save is happening for
     * any reason, the repaired appearance streams go out with everything else, because the file on disk should
     * match what the user was looking at.
     *
     * @return true if the state manager holds any change, repair or deletion.
     */
    public boolean hasWritableChanges() {
        return !changes.isEmpty();
    }

    private static Map<Reference, Change> userChanges(Map<Reference, Change> source) {
        Map<Reference, Change> userChanges = new HashMap<>(source.size());
        for (Map.Entry<Reference, Change> entry : source.entrySet()) {
            if (entry.getValue().getType() != Type.REPAIR) {
                userChanges.put(entry.getKey(), entry.getValue());
            }
        }
        return userChanges;
    }

    /**
     * @return If there are any changes from objects that were manipulated by user interaction
     * @deprecated use {@link #hasUnsavedUserChanges()}, which also accounts for what has already been saved.
     */
    @Deprecated
    public boolean isChange() {
        return changes.values().stream().anyMatch(c -> c.type != Type.REPAIR);
    }

    /**
     * @return If there are any changes that end up in the state manager form user interactions or annotations
     * needing to create missing content streams or popups.
     * @deprecated use {@link #hasWritableChanges()}, which reads the same way round as it is used.
     */
    @Deprecated
    public boolean isNoChange() {
        return !hasWritableChanges();
    }


    /**
     * Sets a snapshot of the current changes.  Call this once the document has been opened, and again after every
     * successful write, so that "since the last snapshot" always means "since the state the file on disk is in".
     */
    public void setChangesSnapshot() {
        savedChangesSnapshot = Map.copyOf(changes);
    }

    /**
     * Checks that the last changesSnapshot and the current list of changes are the same or not
     *
     * @return true if the changes are different, false otherwise
     * @deprecated use {@link #hasUnsavedUserChanges()}.  This method counts repairs as changes, so a document that
     * merely needed an appearance stream generated to be rendered looks modified.
     */
    @Deprecated
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

    /**
     * Checks to see if any redaction annotations are present in the changes.  This is used to determine if
     * the document has redactions that need to be burned in.
     *
     * @return true if redactions are present, false otherwise.
     */
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

    /**
     * Why an object is in the change set.
     * <ul>
     * <li>{@link #CHANGE} - the user edited it.</li>
     * <li>{@link #REPAIR} - the library wrote it so a deficient file could be rendered; the user never asked for it,
     * and on its own it must not make the document look modified.  See {@link StateManager#repairing(Runnable)}.</li>
     * <li>{@link #DELETE} - the user removed it.</li>
     * </ul>
     */
    public enum Type {
        REPAIR,
        CHANGE,
        DELETE
    }

    /**
     * Wrapper class of a pObject and why it is in the change set.  The type differentiates an object the user edited
     * from one the core library had to write itself because the source file was missing it.
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