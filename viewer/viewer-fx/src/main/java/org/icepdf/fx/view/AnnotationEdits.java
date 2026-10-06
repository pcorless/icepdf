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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Annotation edits as undoable commands, applied to the document through core much as the Swing
 * viewer applies them: a move or resize sets the annotation's /Rect from its new view bounds
 * ({@code commonBoundsNormalization} through the page-space transform), resets its appearance
 * stream with the gesture's view-space delta and records it with {@code page.updateAnnotation};
 * delete goes through {@code page.deleteAnnotation} (popup too) and undo re-adds it.
 * <p>
 * The /Rect is set directly ({@code setUserSpaceRectangle}), not through Swing's
 * {@code syncBBoxToUserSpaceRectangle}: that maps its argument through the appearance stream's
 * /Matrix, which for an annotation loaded from a file needn't be identity until the first
 * {@code resetAppearanceStream} - the first move of such an annotation landed off the page.
 * <p>
 * Each edit carries the page-space transform of the moment it was made, so undo and redo don't
 * depend on the zoom or rotation they happen at.  All mutation runs under the page's annotation
 * lock, the one the renderer holds while painting annotation tiles, so a worker never sees the
 * annotation list or an appearance half-changed.
 */
final class AnnotationEdits {

    /** Runs a mutation of a page's annotations under the renderer's lock for that page. */
    interface Locker {
        void withAnnotationLock(int pageIndex, Runnable mutation);
    }

    interface Edit {
        int pageIndex();

        Annotation annotation();

        void undo();

        void redo();
    }

    private AnnotationEdits() {
    }

    /**
     * A move or resize from {@code oldView} to {@code newView} (page view space), applied now.
     *
     * @param dx view-space x delta of the gesture (Swing's mouse delta convention)
     * @param dy view-space y delta of the gesture
     */
    static Edit reshape(Locker locker, Page page, int pageIndex, Annotation annotation, Rectangle2D newView,
                        double dx, double dy, AffineTransform toPageSpace) {
        Rectangle2D.Float before = new Rectangle2D.Float();
        before.setRect(annotation.getUserSpaceRectangle());
        Rectangle2D bounds = Annotation.commonBoundsNormalization(new GeneralPath(newView), toPageSpace);
        Rectangle2D.Float after = new Rectangle2D.Float();
        after.setRect(bounds);
        AffineTransform toPage = new AffineTransform(toPageSpace);
        Edit edit = new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return annotation;
            }

            public void undo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    annotation.setUserSpaceRectangle(before);
                    annotation.resetAppearanceStream(-dx, dy, toPage);
                    page.updateAnnotation(annotation);
                });
            }

            public void redo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    annotation.setUserSpaceRectangle(after);
                    annotation.resetAppearanceStream(dx, -dy, toPage);
                    page.updateAnnotation(annotation);
                });
            }
        };
        edit.redo();
        return edit;
    }

    /** Deletes an annotation (and a markup annotation's popup), applied now. */
    static Edit delete(Locker locker, Page page, int pageIndex, Annotation annotation) {
        PopupAnnotation popup = annotation instanceof MarkupAnnotation markup ? markup.getPopupAnnotation() : null;
        Edit edit = new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return annotation;
            }

            public void undo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    annotation.setDeleted(false);
                    page.addAnnotation(annotation);
                    if (popup != null) {
                        popup.setDeleted(false);
                        page.addAnnotation(popup);
                    }
                });
            }

            public void redo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    page.deleteAnnotation(annotation);
                    if (popup != null) page.deleteAnnotation(popup);
                });
            }
        };
        edit.redo();
        return edit;
    }

    /** Undo/redo stacks; confined to the FX thread. */
    static final class History {
        private final Deque<Edit> undo = new ArrayDeque<>();
        private final Deque<Edit> redo = new ArrayDeque<>();

        void push(Edit edit) {
            undo.push(edit);
            redo.clear();
        }

        Edit undo() {
            Edit edit = undo.poll();
            if (edit != null) {
                edit.undo();
                redo.push(edit);
            }
            return edit;
        }

        Edit redo() {
            Edit edit = redo.poll();
            if (edit != null) {
                edit.redo();
                undo.push(edit);
            }
            return edit;
        }

        boolean canUndo() {
            return !undo.isEmpty();
        }

        boolean canRedo() {
            return !redo.isEmpty();
        }

        void clear() {
            undo.clear();
            redo.clear();
        }
    }
}
