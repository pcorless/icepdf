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
package org.icepdf.core.pobjects.functions.postscript;

import java.util.ArrayList;
import java.util.EmptyStackException;

/**
 * Operand stack used while evaluating a Type 4 (PostScript calculator) function.
 * <p>
 * This replaces {@link java.util.Stack}, whose every operation is synchronized
 * (it extends {@link java.util.Vector}); the function evaluator is single
 * threaded per call but runs in a tight loop once per colour sample, so the
 * lock overhead was pure waste.  The PostScript {@code index}, {@code copy} and
 * {@code roll} operators need random access, so an {@code ArrayDeque} cannot
 * back this; an {@link ArrayList} keeps the indexed access and the bottom-to-top
 * iteration order that {@link Procedure#eval} depends on, without the locking.
 *
 * @since 7.3
 */
public class OperandStack extends ArrayList<Object> {

    /**
     * Pushes an item onto the top of the stack.
     *
     * @param item item to push.
     * @return the item pushed (matching {@link java.util.Stack#push}).
     */
    public Object push(Object item) {
        add(item);
        return item;
    }

    /**
     * Removes and returns the item at the top of the stack.
     *
     * @return the former top of the stack.
     * @throws EmptyStackException if the stack is empty.
     */
    public Object pop() {
        int last = size() - 1;
        if (last < 0) {
            throw new EmptyStackException();
        }
        return remove(last);
    }

    /**
     * Returns the item at the top of the stack without removing it.
     *
     * @return the top of the stack.
     * @throws EmptyStackException if the stack is empty.
     */
    public Object peek() {
        int last = size() - 1;
        if (last < 0) {
            throw new EmptyStackException();
        }
        return get(last);
    }

    /**
     * Returns the item at the top of the stack (Vector-compatible alias).
     *
     * @return the top of the stack.
     */
    public Object lastElement() {
        return peek();
    }

    /**
     * Returns the item at the given index, counted from the bottom of the stack
     * (Vector-compatible alias for {@link #get(int)}).
     *
     * @param index index of the element.
     * @return element at the index.
     */
    public Object elementAt(int index) {
        return get(index);
    }

    /**
     * Inserts an item at the given index (Vector-compatible alias for
     * {@link #add(int, Object)}).
     *
     * @param obj   item to insert.
     * @param index index to insert before.
     */
    public void insertElementAt(Object obj, int index) {
        add(index, obj);
    }
}