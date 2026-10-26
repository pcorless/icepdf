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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EmptyStackException;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-632: the unsynchronized operand stack behaves as {@link Stack} for every call the parser makes,
 * including the inherited ones that share its fields.
 */
public class OperandStackTest {

    @DisplayName("push, peek, pop and size match java.util.Stack, through growth")
    @Test
    void matchesStack() {
        Stack<Object> expected = new Stack<>();
        Stack<Object> actual = new OperandStack();
        // well past Vector's initial capacity of 10, so growth is exercised.
        for (int i = 0; i < 100; i++) {
            assertEquals(expected.push(i), actual.push(i));
            assertEquals(expected.peek(), actual.peek());
            assertEquals(expected.size(), actual.size());
        }
        for (int i = 0; i < 60; i++) {
            assertEquals(expected.pop(), actual.pop());
        }
        assertEquals(expected, actual);
        assertEquals(expected.isEmpty(), actual.isEmpty());
        assertEquals(expected.empty(), actual.empty());
    }

    @DisplayName("the inherited Vector calls the parser uses see the same elements")
    @Test
    void inheritedCalls() {
        Stack<Object> stack = new OperandStack();
        stack.push("a");
        stack.push("b");
        stack.push("c");
        assertEquals("b", stack.get(1));
        assertEquals("a", stack.elementAt(0));
        assertEquals("c", stack.lastElement());
        stack.insertElementAt("z", 0);
        assertEquals("z", stack.get(0));
        assertEquals(4, stack.size());
        assertEquals("c", stack.pop());
        stack.remove(0);
        assertEquals("a", stack.get(0));
        assertEquals("b", stack.peek());
        assertEquals(2, stack.size());
    }

    @DisplayName("empty stack throws, clear empties and releases the elements")
    @Test
    void emptyAndClear() {
        Stack<Object> stack = new OperandStack();
        assertThrows(EmptyStackException.class, stack::pop);
        assertThrows(EmptyStackException.class, stack::peek);
        stack.push(1);
        stack.push(2);
        stack.clear();
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
        assertThrows(EmptyStackException.class, stack::pop);
        stack.push(3);
        assertEquals(3, stack.pop());
    }
}
