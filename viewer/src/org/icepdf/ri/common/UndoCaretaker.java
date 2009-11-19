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
package org.icepdf.ri.common;

import org.icepdf.core.Memento;
import org.icepdf.core.util.Defs;

import java.util.ArrayList;

/**
 * Undo caretaker implementation for the Viewer RI.  Currently only annotation
 * can be manipulate but this class can easily handle any class that implements
 * the Memento interfce.
 *
 * @since 4.0
 */
public class UndoCaretaker {

    // max number of object to store in undo list.
    private static int maxHistorySize;

    static {
        // enables interactive annotation support.
        maxHistorySize =
                Defs.sysPropertyInt(
                        "org.icepdf.ri.viewer.undo.size", 25);
    }

    private ArrayList<Memento> mementoStateHistory;
    private int cursor;

    public UndoCaretaker() {
        mementoStateHistory = new ArrayList<Memento>(maxHistorySize);
        cursor = 0;
    }

    /**
     * Undo the last state change.  Only possible if there are items in the
     * undo history list.
     */
    public void undo() {
        if (isUndo()) {
            // move the point reference
            cursor = cursor - 1;
            Memento tmp = mementoStateHistory.get(cursor);
            // restore the old state
            tmp.restore();
        }
    }

    /**
     * Gets the status of the undo command.
     *
     * @return true if an undo command is possible, false if undo can not be done.
     */
    public boolean isUndo() {
        return mementoStateHistory.size() > 0 && cursor > 0;
    }

    /**
     * Redo the last state change.  ONly possible if there have been previous
     * undo call.
     */
    public void redo() {
        if (isRedo()) {
            // move the pointer
            cursor = cursor + 1;
            Memento tmp = mementoStateHistory.get(cursor);
            // restore the old state
            tmp.restore();
        }
    }

    /**
     * Gets the status of the redo command.
     *
     * @return true if an redo command is possible, false if the redo can not be done.
     */
    public boolean isRedo() {
        // check for at least one history state in the next index.
        return cursor + 1 < mementoStateHistory.size();
    }

    /**
     * Adds the give states to the history list.
     *
     * @param previousState previous state
     * @param newState new state. 
     */
    public void addState(Memento previousState, Memento newState) {
        // first check history bounds, if we are in an none
        if (cursor + 1 >= maxHistorySize) {
            // get rid of first index.
            mementoStateHistory.remove(0);
        }
        // check to see if we are in a possible redo state, if so we clear
        // all states from the current pointer.
        if (isRedo()) {
            for (int i = cursor + 1, max = mementoStateHistory.size(); i < max; i++) {
                mementoStateHistory.remove(cursor + 1);
            }
        }
        // first entry is special case, add them as is.
        if (mementoStateHistory.size() == 0) {
            mementoStateHistory.add(previousState);
            mementoStateHistory.add(newState);
            cursor = 1;
        }
        // we do an offset add
        else {
            mementoStateHistory.set(cursor, previousState);
            mementoStateHistory.add(newState);
            cursor++;
        }
    }
}
