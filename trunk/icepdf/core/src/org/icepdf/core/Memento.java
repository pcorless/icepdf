package org.icepdf.core;

/**
 * Creates a memento containing a snapshot of the internal state.  The state
 * can be retreived when the restore method is called.  This interface should
 * be used by any object that plans to use the Caretaker implementation in the
 * RI.
 *
 *  @since 4.0
 */
public interface Memento {

    /**
     * Restore the state that was caputred when an instance of this object
     * was created. 
     */
    public void restore();
}
