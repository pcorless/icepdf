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
    private static final int maxHistorySize;

    static {
        // enables interactive annotation support.
        maxHistorySize =
                Defs.sysPropertyInt(
                        "org.icepdf.ri.viewer.undo.size", 100);
    }

    private final ArrayList<Memento> mementoStateHistory;
    private int cursor;

    public UndoCaretaker() {
        mementoStateHistory = new ArrayList<>(maxHistorySize);
        cursor = -1;
    }

    /**
     * Undo the last state change.  Only possible if there are items in the
     * undo history list.
     */
    public Memento undo() {
        if (isUndo()) {
            // move the point reference
            cursor -= 1;
            final Memento ret = restore();
            cursor -= 1;
            return ret;
        } else {
            return null;
        }
    }

    /**
     * Gets the status of the undo command.
     *
     * @return true if an undo command is possible, false if undo can not be done.
     */
    public boolean isUndo() {
        return cursor >= 0;
    }

    /**
     * Redo the last state change.  ONly possible if there have been previous
     * undo call.
     */
    public Memento redo() {
        if (isRedo()) {
            // move the pointer
            cursor += 2;
            // restore the old state
            return restore();
        } else {
            return null;
        }
    }

    private Memento restore() {
        final Memento state = mementoStateHistory.get(cursor);
        state.restore();
        return state;
    }

    /**
     * Gets the status of the redo command.
     *
     * @return true if an redo command is possible, false if the redo can not be done.
     */
    public boolean isRedo() {
        // check for at least one history state in the next index.
        return cursor < mementoStateHistory.size() - 1;
    }

    /**
     * Adds the give states to the history list.
     *
     * @param previousState previous state
     * @param newState      new state.
     */
    public void addState(final Memento previousState, final Memento newState) {
        if (isRedo()) {
            mementoStateHistory.subList(cursor + 1, mementoStateHistory.size()).clear();
        }
        mementoStateHistory.add(previousState);
        mementoStateHistory.add(newState);
        while (mementoStateHistory.size() > maxHistorySize) {
            mementoStateHistory.remove(0);
        }
        cursor = mementoStateHistory.size() - 1;
    }
}
