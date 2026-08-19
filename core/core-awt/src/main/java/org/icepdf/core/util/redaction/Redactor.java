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

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;

import java.io.IOException;
import java.util.List;

/**
 * Applies a document's redactions.
 * <p>
 * Redaction happens as part of writing the document, not before it: removing content means rewriting
 * every stream that carried it, which an incremental update cannot do by definition. So a caller
 * states what they want, saves, and reads what happened:
 * <pre>
 *     Redactor.configure(document, RedactionRequest.ofAnnotations()
 *             .with(RedactionOptions.defaults().maskString("[removed]")));
 *
 *     document.saveToOutputStream(outputStream, WriteMode.FULL_UPDATE);
 *
 *     RedactionReport report = document.getRedactionReport();
 * </pre>
 * Configuring is optional. A document saved with redaction annotations on it is redacted with
 * {@link RedactionOptions#defaults()} either way, which is what every release before this one did.
 *
 * @since 7.2.0
 */
public class Redactor {

    /**
     * States how the next write should redact this document. Optional - without it the write uses
     * {@link RedactionOptions#defaults()}.
     *
     * @param document document about to be written
     * @param request  what to redact and how
     */
    public static void configure(Document document, RedactionRequest request) {
        document.setRedactionRequest(request);
    }

    /**
     * Burns the redaction annotations on every page into the content and image streams.
     * <p>
     * Called by the writer once the document has been flattened, not by application code; a caller
     * who wants to redact saves the document. See the class javadoc.
     *
     * @param document document being written
     * @param request  what to redact and how
     * @return what was removed
     * @throws InterruptedException if page initialisation is interrupted
     * @throws IOException          if a content stream cannot be rewritten
     */
    public static RedactionReport redact(Document document, RedactionRequest request)
            throws InterruptedException, IOException {
        RedactionRequest effective = request != null ? request : RedactionRequest.ofAnnotations();
        RedactionReport report = new RedactionReport();
        StateManager stateManager = document.getCatalog().getLibrary().getStateManager();

        // Which of page content and images actually get removed is decided per glyph and per image
        // by the callback; this only asks whether there is any point walking the pages at all.
        if (effective.getOptions().redacts(RedactionTarget.PAGE_CONTENT)
                || effective.getOptions().redacts(RedactionTarget.IMAGES)) {
            burnAnnotations(document, effective, report, stateManager);
        }
        return report;
    }

    /**
     * @deprecated since 7.5.0, use {@link #redact(Document, RedactionRequest)}, or simply save a
     * document that has redaction annotations on it. Retained because it is the entry point every
     * release before 7.5.0 exposed.
     */
    @Deprecated
    public static void burnRedactions(Document document) throws InterruptedException, IOException {
        redact(document, RedactionRequest.ofAnnotations());
    }

    private static void burnAnnotations(Document document, RedactionRequest request,
                                        RedactionReport report, StateManager stateManager)
            throws InterruptedException, IOException {
        int pageCount = document.getNumberOfPages();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Page page = document.getPageTree().getPage(pageIndex);
            List<RedactionAnnotation> redactionAnnotations = page.getRedactionAnnotations();
            if (redactionAnnotations == null || redactionAnnotations.isEmpty()) {
                continue;
            }
            RedactionContentBurner.burn(page, redactionAnnotations, request.getOptions(), report);
            // Convert to a square annotation so the exported document shows where the redaction was
            // without still claiming to be a pending redaction.
            convertRedactionToSquareAnnotation(stateManager, redactionAnnotations);
        }
    }

    private static void convertRedactionToSquareAnnotation(StateManager stateManager,
                                                           List<RedactionAnnotation> redactionAnnotations) {
        for (RedactionAnnotation redactionAnnotation : redactionAnnotations) {
            redactionAnnotation.setSubtype(Annotation.SUBTYPE_SQUARE);
            redactionAnnotation.setFlag(Annotation.FLAG_LOCKED, true);
            redactionAnnotation.setFlag(Annotation.FLAG_READ_ONLY, true);
            redactionAnnotation.setFlag(Annotation.FLAG_LOCKED_CONTENTS, true);
            stateManager.addChange(new PObject(redactionAnnotation, redactionAnnotation.getPObjectReference()));
        }
    }
}
