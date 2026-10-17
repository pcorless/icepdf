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

import org.icepdf.fx.view.AnnotationProperties;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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
public final class AnnotationEdits {

    /** Runs a mutation of a page's annotations under the renderer's lock for that page. */
    public interface Locker {
        void withAnnotationLock(int pageIndex, Runnable mutation);
    }

    public interface Edit {
        int pageIndex();

        /** Every page the edit touches (a form reset or radio group can span pages). */
        default java.util.Set<Integer> pages() {
            return java.util.Set.of(pageIndex());
        }

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
    public static Edit reshape(Locker locker, Page page, int pageIndex, Annotation annotation, Rectangle2D newView,
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

    /**
     * Moves/resizes a popup to a new /Rect, applied now.  Popups have no appearance stream; only the
     * rectangle changes, and it is not confined to the page.
     */
    public static Edit popupRect(Locker locker, Page page, int pageIndex, PopupAnnotation popup, Rectangle2D after) {
        Rectangle2D.Float before = new Rectangle2D.Float();
        before.setRect(popup.getUserSpaceRectangle());
        Rectangle2D.Float target = new Rectangle2D.Float();
        target.setRect(after);
        return applied(new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return popup;
            }

            public void undo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    popup.setUserSpaceRectangle(before);
                    page.updateAnnotation(popup);
                });
            }

            public void redo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    popup.setUserSpaceRectangle(target);
                    page.updateAnnotation(popup);
                });
            }
        });
    }

    /** Replaces a markup annotation's contents (its popup note text), applied now. */
    public static Edit contents(Locker locker, Page page, int pageIndex, MarkupAnnotation markup, String after) {
        String before = markup.getContents();
        return applied(new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return markup;
            }

            public void undo() {
                set(before);
            }

            public void redo() {
                set(after);
            }

            private void set(String text) {
                locker.withAnnotationLock(pageIndex, () -> {
                    markup.setContents(text);
                    markup.setModifiedDate(org.icepdf.core.pobjects.PDate.formatDateTime(new java.util.Date()));
                    page.updateAnnotation(markup);
                });
            }
        });
    }

    /**
     * Several edits, already applied, as one undo step (search and redact marks every match at once).
     * Undo goes in reverse; {@code alsoUndo}/{@code alsoRedo} run after the edits (null for none).
     * {@code edits} may be empty when only the extra steps change something (pageIndex -1, no
     * annotation).
     */
    public static Edit group(List<Edit> edits, Runnable alsoUndo, Runnable alsoRedo) {
        List<Edit> all = List.copyOf(edits);
        return new Edit() {
            public int pageIndex() {
                return all.isEmpty() ? -1 : all.get(0).pageIndex();
            }

            public java.util.Set<Integer> pages() {
                java.util.Set<Integer> pages = new java.util.TreeSet<>();
                for (Edit edit : all) pages.addAll(edit.pages());
                return pages;
            }

            public Annotation annotation() {
                return all.isEmpty() ? null : all.get(0).annotation();
            }

            public void undo() {
                for (int i = all.size() - 1; i >= 0; i--) all.get(i).undo();
                if (alsoUndo != null) alsoUndo.run();
            }

            public void redo() {
                for (Edit edit : all) edit.redo();
                if (alsoRedo != null) alsoRedo.run();
            }
        };
    }

    private static Edit applied(Edit edit) {
        edit.redo();
        return edit;
    }

    /** Adds a new annotation (and its popup, if any) to the page, applied now; undo deletes it. */
    public static Edit add(Locker locker, Page page, int pageIndex, Annotation annotation, PopupAnnotation popup) {
        boolean[] first = {true};
        // the appearance streams as they were before undo deleted them (core drops them from the library).
        org.icepdf.core.pobjects.Stream[] appearances = new org.icepdf.core.pobjects.Stream[2];
        return applied(new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return annotation;
            }

            public void undo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    appearances[0] = annotation.getAppearanceStream();
                    appearances[1] = popup != null ? popup.getAppearanceStream() : null;
                    page.deleteAnnotation(annotation);
                    if (popup != null) page.deleteAnnotation(popup);
                });
            }

            public void redo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    if (!first[0]) {
                        annotation.setDeleted(false);
                        if (popup != null) popup.setDeleted(false);
                    }
                    boolean redone = !first[0];
                    first[0] = false;
                    page.addAnnotation(annotation);
                    if (popup != null) page.addAnnotation(popup);
                    if (redone) {
                        revive(annotation, appearances[0]);
                        if (popup != null) revive(popup, appearances[1]);
                    }
                });
            }
        });
    }

    /** Sets a free text annotation's text and regenerates its appearance, applied now. */
    public static Edit freeTextContents(Locker locker, Page page, int pageIndex,
                                 org.icepdf.core.pobjects.annotations.FreeTextAnnotation freeText, String after,
                                 AffineTransform toPageSpace) {
        String before = freeText.getContents();
        AffineTransform toPage = new AffineTransform(toPageSpace);
        return applied(new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return freeText;
            }

            public void undo() {
                set(before);
            }

            public void redo() {
                set(after);
            }

            private void set(String text) {
                locker.withAnnotationLock(pageIndex, () -> {
                    freeText.setContents(text);
                    freeText.setModifiedDate(org.icepdf.core.pobjects.PDate.formatDateTime(new java.util.Date()));
                    freeText.resetAppearanceStream(toPage);
                    page.updateAnnotation(freeText);
                });
            }
        });
    }

    /**
     * Changes an annotation's properties (colour, opacity, border, fill, ...) as one edit, applied now.
     * Undo restores a full snapshot taken before; the appearance stream is regenerated each way.
     */
    public static Edit properties(Locker locker, Page page, int pageIndex, Annotation annotation,
                           AnnotationProperties change, AffineTransform toPageSpace) {
        AnnotationProperties before = AnnotationProperties.capture(annotation);
        AffineTransform toPage = new AffineTransform(toPageSpace);
        return applied(new Edit() {
            public int pageIndex() {
                return pageIndex;
            }

            public Annotation annotation() {
                return annotation;
            }

            public void undo() {
                set(before);
            }

            public void redo() {
                set(change);
            }

            private void set(AnnotationProperties values) {
                locker.withAnnotationLock(pageIndex, () -> {
                    values.apply(annotation);
                    if (annotation instanceof MarkupAnnotation markup) {
                        markup.setModifiedDate(org.icepdf.core.pobjects.PDate.formatDateTime(new java.util.Date()));
                    }
                    if (annotation instanceof org.icepdf.core.pobjects.annotations.FreeTextAnnotation freeText) {
                        freeText.resetAppearanceStream(toPage);
                    } else {
                        annotation.resetAppearanceStream(0, 0, toPage);
                    }
                    page.updateAnnotation(annotation);
                });
            }
        });
    }

    /** Deletes an annotation (and a markup annotation's popup), applied now. */
    public static Edit delete(Locker locker, Page page, int pageIndex, Annotation annotation) {
        PopupAnnotation popup = annotation instanceof MarkupAnnotation markup ? markup.getPopupAnnotation() : null;
        org.icepdf.core.pobjects.Stream[] appearances = new org.icepdf.core.pobjects.Stream[2];
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
                    revive(annotation, appearances[0]);
                    if (popup != null) {
                        popup.setDeleted(false);
                        page.addAnnotation(popup);
                        revive(popup, appearances[1]);
                    }
                });
            }

            public void redo() {
                locker.withAnnotationLock(pageIndex, () -> {
                    appearances[0] = annotation.getAppearanceStream();
                    appearances[1] = popup != null ? popup.getAppearanceStream() : null;
                    page.deleteAnnotation(annotation);
                    if (popup != null) page.deleteAnnotation(popup);
                });
            }
        };
        edit.redo();
        return edit;
    }

    /**
     * Puts back what core's delete ({@code AnnotationRemovalModifier}) took from an annotation besides
     * the annotation itself, after {@code page.addAnnotation} has re-added it: the appearance stream
     * it flagged deleted and dropped from the library and the pending changes, and the images and
     * embedded font under it that it registered as deletions.  Without this an annotation brought
     * back by undo or redo is written without its appearance - and a redaction burns nothing.
     *
     * @param ap the appearance stream as it was before the delete (afterwards the annotation can no
     *           longer find it: core removed it from the library)
     */
    static void revive(Annotation annotation, org.icepdf.core.pobjects.Stream ap) {
        org.icepdf.core.util.Library library = annotation.getLibrary();
        org.icepdf.core.pobjects.StateManager state = library.getStateManager();
        if (ap == null || ap.getPObjectReference() == null) return;
        ap.setDeleted(false);
        library.addObject(ap, ap.getPObjectReference());
        state.addChange(new org.icepdf.core.pobjects.PObject(ap, ap.getPObjectReference()));
        Object resources = library.getObject(ap.getEntries(), Page.RESOURCES_KEY);
        if (!(resources instanceof org.icepdf.core.pobjects.Resources res)) return;
        java.util.List<org.icepdf.core.pobjects.Reference> refs = new java.util.ArrayList<>();
        org.icepdf.core.pobjects.DictionaryEntries xObjects = res.getXObjects();
        if (xObjects != null) {
            for (Object value : xObjects.values()) {
                if (value instanceof org.icepdf.core.pobjects.Reference ref) refs.add(ref);
            }
        }
        org.icepdf.core.pobjects.Dictionary font = res.getFont(
                org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils.EMBEDDED_FONT_NAME);
        if (font != null && font.getPObjectReference() != null) {
            font.setDeleted(false);
            refs.add(font.getPObjectReference());
        }
        for (org.icepdf.core.pobjects.Reference ref : refs) {
            org.icepdf.core.pobjects.StateManager.Change change = state.getChange(ref);
            if (change == null || change.getType() != org.icepdf.core.pobjects.StateManager.Type.DELETE) continue;
            Object object = library.getObject(ref);
            if (object != null) state.addChange(new org.icepdf.core.pobjects.PObject(object, ref));
            else state.removeChange(new org.icepdf.core.pobjects.PObject(null, ref));
        }
    }

    /** An undo or redo that threw; {@link #edit()} names the pages it touched. */
    public static final class StepFailedException extends RuntimeException {
        private final transient Edit edit;

        StepFailedException(Edit edit, RuntimeException cause) {
            super(cause);
            this.edit = edit;
        }

        public Edit edit() {
            return edit;
        }
    }

    /** Undo/redo stacks; confined to the FX thread. */
    public static final class History {
        private final Deque<Edit> undo = new ArrayDeque<>();
        private final Deque<Edit> redo = new ArrayDeque<>();

        public void push(Edit edit) {
            undo.push(edit);
            redo.clear();
        }

        /** @throws StepFailedException when the edit failed part way: the document may hold half of it */
        public Edit undo() {
            Edit edit = undo.poll();
            if (edit != null) {
                run(edit, edit::undo);
                redo.push(edit);
            }
            return edit;
        }

        /** @throws StepFailedException when the edit failed part way: the document may hold half of it */
        public Edit redo() {
            Edit edit = redo.poll();
            if (edit != null) {
                run(edit, edit::redo);
                undo.push(edit);
            }
            return edit;
        }

        private static void run(Edit edit, Runnable step) {
            try {
                step.run();
            } catch (RuntimeException e) {
                throw new StepFailedException(edit, e);
            }
        }

        public boolean canUndo() {
            return !undo.isEmpty();
        }

        public boolean canRedo() {
            return !redo.isEmpty();
        }

        public void clear() {
            undo.clear();
            redo.clear();
        }
    }
}
