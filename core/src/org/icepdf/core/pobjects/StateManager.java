package org.icepdf.core.pobjects;


import java.util.HashMap;

/**
 * This class is responsible for keeping track of which object in the document
 * have change.  When a file is written to disk this class is used to find
 * the object that shoud be written in the body section of the file as part of
 * an incremental update.
 * <p/>
 * Once this object is created should be added to the library so that is
 * accessable by any PObject.
 *
 * @since 4.0
 */
public class StateManager {

    // a list is all we might need. 
    private HashMap<Reference, PObject> changes;

    // access to xref size and next revision number.
    private PTrailer trailer;

    private int nextReferenceNumber;

    /**
     * Creates a new instance of the state manager.
     *
     * @param trailer document trailer
     */
    public StateManager(PTrailer trailer) {
        this.trailer = trailer;
        // cache of objects that have changed.
        changes = new HashMap<Reference, PObject>();

        // number of objects is always one more then the current size and
        // thus the next available number.
        nextReferenceNumber = trailer.getNumberOfObjects();
    }

    /**
     * Gets the next available reference number from the trailer.
     *
     * @return valid reference number.
     */
    public Reference getNewReferencNumber() {
        // zero revision number for now but technically we can reuse
        // deleted references and increment the rev number.  For no we
        // keep it simple
        Reference newReference = new Reference(nextReferenceNumber, 0);
        nextReferenceNumber++;
        return newReference;
    }

    /**
     * Add a new PObject containing changed data to the cache.
     *
     * @param pObject
     */
    public void addChange(PObject pObject) {
        changes.put(pObject.getReference(), pObject);
    }

    /**
     * Remove a PObject from the cache.
     *
     * @param pObject pObject to removed from the cache.
     */
    public void removeChange(PObject pObject) {
        changes.remove(pObject.getReference());
    }
}

