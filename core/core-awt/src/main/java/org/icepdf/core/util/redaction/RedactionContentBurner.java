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

package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.Appearance;
import org.icepdf.core.pobjects.annotations.AppearanceState;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.callbacks.ContentStreamRedactorCallback;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sets up the callback needed to rewrite the content stream as text and image that has been marked for redaction.
 * Text and image burning is kicked off by this process.
 *
 * @since 7.2.0
 */
public class RedactionContentBurner {
    private static final Logger logger =
            Logger.getLogger(RedactionContentBurner.class.getName());

    /**
     * Rewrites a page's content and image streams with everything the given redactions cover
     * removed.
     *
     * @param page                 page to redact
     * @param redactionAnnotations areas to remove
     * @param options              how the redaction should behave
     * @param report               collects what was removed and anything that degraded
     * @throws InterruptedException if page initialisation is interrupted
     * @throws IOException          if a content stream cannot be rewritten
     */
    public static void burn(Page page, List<RedactionAnnotation> redactionAnnotations,
                            RedactionOptions options, RedactionReport report)
            throws InterruptedException, IOException {
        burn(page, redactionAnnotations, options, report, null);
    }

    /**
     * @param masker masks the request's terms out of text a rectangle cannot reach but that lives in
     *               a content stream - a marked-content property list. Null when the redaction was
     *               given no terms.
     */
    public static void burn(Page page, List<RedactionAnnotation> redactionAnnotations,
                            RedactionOptions options, RedactionReport report, TermMasker masker)
            throws InterruptedException, IOException {
        Library library = page.getLibrary();
        ContentStreamRedactorCallback contentStreamRedactorCallback =
                new ContentStreamRedactorCallback(library, redactionAnnotations, options, report, masker);
        page.init(contentStreamRedactorCallback);
        // wrap up, ends the last or only content stream being processed and store the bytes
        contentStreamRedactorCallback.endContentStream();

        if (options.redacts(RedactionTarget.ANNOTATION_APPEARANCES)) {
            burnAppearanceStreams(page, redactionAnnotations, options, report, masker);
        }
    }

    /**
     * Redacts the appearance streams of annotations sitting under a redaction.
     * <p>
     * An appearance stream is drawn on the page but is not part of the page's content, so nothing
     * above reaches it: the text in a comment's box, a form field's value as drawn, a stamp's
     * caption. It never enters the page's text either, which is why search cannot find it and why a
     * redaction driven by search will not have covered it.
     * <p>
     * Reached by geometry rather than by search for exactly that reason - whether the annotation's
     * own text was ever findable is beside the point, since the rectangle covers whatever is drawn
     * beneath it.
     *
     * @param page                 page being redacted
     * @param redactionAnnotations areas being removed
     * @param options              how the redaction should behave
     * @param report               collects what was removed
     */
    private static void burnAppearanceStreams(Page page, List<RedactionAnnotation> redactionAnnotations,
                                              RedactionOptions options, RedactionReport report,
                                              TermMasker masker)
            throws InterruptedException, IOException {
        List<Annotation> annotations = page.getAnnotations();
        if (annotations == null) {
            return;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof RedactionAnnotation) {
                // The redaction's own appearance is the black rectangle marking the redaction; it is
                // not content being removed.
                continue;
            }
            if (!intersectsRedaction(annotation, redactionAnnotations)) {
                continue;
            }
            burnAppearance(page, annotation, redactionAnnotations, options, report, masker);
        }
    }

    private static boolean intersectsRedaction(Annotation annotation,
                                               List<RedactionAnnotation> redactionAnnotations) {
        Rectangle2D bounds = annotation.getUserSpaceRectangle();
        if (bounds == null) {
            return false;
        }
        for (RedactionAnnotation redaction : redactionAnnotations) {
            GeneralPath path = redaction.getMarkupPath();
            if (path != null && path.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    private static void burnAppearance(Page page, Annotation annotation,
                                       List<RedactionAnnotation> redactionAnnotations,
                                       RedactionOptions options, RedactionReport report,
                                       TermMasker masker)
            throws InterruptedException, IOException {
        Appearance appearance = annotation.getAppearances().get(annotation.getCurrentAppearance());
        if (appearance == null) {
            return;
        }
        AppearanceState appearanceState = appearance.getSelectedAppearanceState();
        if (appearanceState == null) {
            return;
        }
        Object stream = page.getLibrary().getObject(annotation.getEntries(),
                Annotation.APPEARANCE_STREAM_KEY);
        if (stream == null) {
            // No /AP at all, so the annotation draws nothing from a stream and there is nothing here
            // to leak. Its text, if it has any, is a string the term axis handles.
            return;
        }
        Form form = appearanceForm(page, stream, annotation);
        if (form == null) {
            report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                    "Annotation " + annotation.getPObjectReference() + " lies under a redaction but "
                            + "its appearance is not a form and was not redacted");
            return;
        }
        // An appearance can be one stream drawn by many annotations - a watermark shared by every page
        // of a size. Burning it in place would punch this redaction's hole into every one of them.
        if (form.getPObjectReference() != null
                && drawnByAnotherAnnotation(page.getLibrary(), form.getPObjectReference(), annotation)) {
            form = ownCopy(page.getLibrary(), annotation, form);
        }
        // Glyphs inside an appearance arrive in its own coordinates, so the callback needs the way
        // out to page space to know which of them a redaction covers.
        ContentStreamRedactorCallback callback = new ContentStreamRedactorCallback(
                page.getLibrary(), redactionAnnotations, options, report,
                annotation.getAppearanceToPageSpace(appearanceState), masker);
        form.init(callback);
        callback.endContentStream();
    }

    /**
     * Whether any annotation other than {@code self}, on any page, draws the given appearance stream.
     * Read from the raw dictionaries, so pages need not be initialised.
     */
    static boolean drawnByAnotherAnnotation(Library library, Reference appearance, Annotation self) {
        PageTree pages = library.getCatalog().getPageTree();
        for (int i = 0; i < pages.getNumberOfPages(); i++) {
            Page page = pages.getPage(i);
            if (page == null) {
                continue; // counted but missing from the tree (GH-702)
            }
            List<?> annots = library.getArray(page.getEntries(), Page.ANNOTS_KEY);
            if (annots == null) {
                continue;
            }
            for (Object entry : annots) {
                if (entry instanceof Reference && entry.equals(self.getPObjectReference())) {
                    continue;
                }
                Object annot = entry instanceof Reference ? library.getObject((Reference) entry) : entry;
                DictionaryEntries entries = annot instanceof Dictionary ? ((Dictionary) annot).getEntries()
                        : annot instanceof DictionaryEntries ? (DictionaryEntries) annot : null;
                if (entries != null && entries != self.getEntries() && drawsAppearance(library, entries, appearance)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean drawsAppearance(Library library, DictionaryEntries annotation, Reference appearance) {
        Object ap = library.getObject(annotation, Annotation.APPEARANCE_STREAM_KEY);
        if (!(ap instanceof DictionaryEntries)) {
            return false;
        }
        for (Object state : ((DictionaryEntries) ap).values()) {
            if (appearance.equals(state)) {
                return true;
            }
            // an appearance with on/off states keeps a dictionary of streams under each key
            if (state instanceof Reference) {
                state = library.getObject((Reference) state);
            }
            if (state instanceof DictionaryEntries && ((DictionaryEntries) state).containsValue(appearance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives the annotation a copy of its normal appearance to burn, leaving the shared original as
     * it was for the annotations that still draw it.
     */
    private static Form ownCopy(Library library, Annotation annotation, Form shared) {
        StateManager stateManager = library.getStateManager();
        byte[] content = shared.getDecodedStreamBytes();
        DictionaryEntries entries = new DictionaryEntries();
        for (Map.Entry<Name, Object> e : shared.getEntries().entrySet()) {
            Name key = e.getKey();
            // the copy is written decoded; the writer compresses it again
            if (!Stream.FILTER_KEY.equals(key) && !Stream.DECODEPARAM_KEY.equals(key)
                    && !Dictionary.LENGTH_KEY.equals(key)) {
                entries.put(key, e.getValue());
            }
        }
        Reference reference = stateManager.getNewReferenceNumber();
        Form copy = new Form(library, entries, content);
        copy.setPObjectReference(reference);
        copy.setRawBytes(content);
        library.addObject(copy, reference);
        stateManager.addChange(new PObject(copy, reference));

        DictionaryEntries appearances = new DictionaryEntries();
        Object ap = library.getObject(annotation.getEntries(), Annotation.APPEARANCE_STREAM_KEY);
        if (ap instanceof DictionaryEntries) {
            appearances.putAll((DictionaryEntries) ap);
        }
        appearances.put(Annotation.APPEARANCE_STREAM_NORMAL_KEY, reference);
        annotation.getEntries().put(Annotation.APPEARANCE_STREAM_KEY, appearances);
        stateManager.addChange(new PObject(annotation, annotation.getPObjectReference()));
        return copy;
    }

    /**
     * The appearance stream as a form, whichever way the annotation stores it.
     */
    private static Form appearanceForm(Page page, Object appearanceStream, Annotation annotation) {
        Object normal = appearanceStream;
        if (normal instanceof DictionaryEntries) {
            normal = page.getLibrary().getObject((DictionaryEntries) normal,
                    Annotation.APPEARANCE_STREAM_NORMAL_KEY);
        }
        if (normal instanceof Reference) {
            normal = page.getLibrary().getObject((Reference) normal);
        }
        return normal instanceof Form ? (Form) normal : null;
    }

}
