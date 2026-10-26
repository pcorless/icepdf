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
package org.icepdf.core.util.parser.content;

import java.util.EmptyStackException;
import java.util.Stack;

/**
 * The content parser's operand stack, without {@link java.util.Vector}'s locking.
 * <br>
 * A parser and its stack belong to one thread, but every {@code Stack} call takes the object's monitor, and a
 * content stream pushes and pops every operand: on 9216.pdf (122K images) {@code popFloatInOrder} alone was
 * 7.8% of the profile (GH-632).  The hot methods here work on {@code Vector}'s protected fields directly; the
 * rest (get, insertElementAt, ...) are inherited and still correct, as they use the same fields.
 * <br>
 * It stays a {@code Stack<Object>} so the parser's {@code consume_*} signatures are unchanged.
 */
final class OperandStack extends Stack<Object> {

    private static final long serialVersionUID = 1L;

    @Override
    public Object push(Object item) {
        if (elementCount == elementData.length) {
            // rare: grow through Vector, which keeps its own sizing rules.
            ensureCapacity(elementCount + 1);
        }
        modCount++;
        elementData[elementCount++] = item;
        return item;
    }

    @Override
    public Object pop() {
        if (elementCount == 0) {
            throw new EmptyStackException();
        }
        modCount++;
        Object item = elementData[--elementCount];
        elementData[elementCount] = null;
        return item;
    }

    @Override
    public Object peek() {
        if (elementCount == 0) {
            throw new EmptyStackException();
        }
        return elementData[elementCount - 1];
    }

    @Override
    public boolean empty() {
        return elementCount == 0;
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    @Override
    public int size() {
        return elementCount;
    }

    @Override
    public void clear() {
        modCount++;
        for (int i = 0; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = 0;
    }
}
