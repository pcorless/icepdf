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
package org.icepdf.fx.view.internal.annot;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Undo/redo stacks, no toolkit. */
class HistoryTest {

    private static AnnotationEdits.Edit edit(int page, Runnable undo, Runnable redo) {
        return new AnnotationEdits.Edit() {
            public int pageIndex() {
                return page;
            }

            public Annotation annotation() {
                return null;
            }

            public void undo() {
                undo.run();
            }

            public void redo() {
                redo.run();
            }
        };
    }

    @DisplayName("an undo that throws is reported with the edit that failed, which names its pages")
    @Test
    void failedUndoNamesTheEdit() {
        AnnotationEdits.History history = new AnnotationEdits.History();
        AnnotationEdits.Edit bad = edit(3, () -> {
            throw new IllegalStateException("broken appearance");
        }, () -> { });
        history.push(bad);
        AnnotationEdits.StepFailedException failed = assertThrows(AnnotationEdits.StepFailedException.class, history::undo);
        assertSame(bad, failed.edit());
        assertEquals(java.util.Set.of(3), failed.edit().pages());
        assertEquals("broken appearance", failed.getCause().getMessage());
    }

    @DisplayName("a redo that throws is reported the same way")
    @Test
    void failedRedoNamesTheEdit() {
        AnnotationEdits.History history = new AnnotationEdits.History();
        AnnotationEdits.Edit bad = edit(1, () -> { }, () -> {
            throw new IllegalStateException("x");
        });
        history.push(bad);
        assertSame(bad, history.undo());
        assertTrue(history.canRedo());
        assertSame(bad, assertThrows(AnnotationEdits.StepFailedException.class, history::redo).edit());
    }
}
