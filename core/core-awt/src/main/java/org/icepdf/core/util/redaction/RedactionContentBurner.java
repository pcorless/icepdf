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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
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
        // Glyphs inside an appearance arrive in its own coordinates, so the callback needs the way
        // out to page space to know which of them a redaction covers.
        ContentStreamRedactorCallback callback = new ContentStreamRedactorCallback(
                page.getLibrary(), redactionAnnotations, options, report,
                annotation.getAppearanceToPageSpace(appearanceState), masker);
        form.init(callback);
        callback.endContentStream();
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
