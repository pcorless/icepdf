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
 * Redactor iterates over a document pages looking for redaction annotations and applies the content and
 * image stream burning.
 *
 * @since 7.2.0
 */
public class Redactor {

    public static void burnRedactions(Document document) throws InterruptedException, IOException {
        int pageCount = document.getNumberOfPages();
        StateManager stateManager = document.getCatalog().getLibrary().getStateManager();

        // work though each page
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Page page = document.getPageTree().getPage(pageIndex);
            // check for any redaction annotation
            List<RedactionAnnotation> redactionAnnotations = page.getRedactionAnnotations();
            if (redactionAnnotations != null && !redactionAnnotations.isEmpty()) {
                RedactionContentBurner.burn(page, redactionAnnotations);
            }
            // convert the redaction to Annotation.SUBTYPE_SQUARE.  This avoids any confusion in the exported document
            // and makes sure we show where the redaction took place.
            if (redactionAnnotations != null && !redactionAnnotations.isEmpty()) {
                convertRedactionToSquareAnnotation(stateManager, redactionAnnotations);
            }
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
