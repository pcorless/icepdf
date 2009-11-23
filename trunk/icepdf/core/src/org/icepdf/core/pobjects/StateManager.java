/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
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

