package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.images.ImageStream;

import java.util.List;

public class Redactor {

    public static void burnRedactions(Document document) throws InterruptedException {
        Catalog catalog = document.getCatalog();
        int pageCount = document.getNumberOfPages();

        // work though each page
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Page page = document.getPageTree().getPage(pageIndex);
            page.init();
            // check for any redaction annotation
            List<RedactionAnnotation> redactionAnnotations = page.getRedactionAnnotations();
            if (redactionAnnotations != null && !redactionAnnotations.isEmpty()) {

                // burn text with given redaction bounds
                TextBurner.burn(page, redactionAnnotations);
                // inline images
                InlineImageBurner.burn(page, redactionAnnotations);

                // burn bounds into the image, may need an initialized page to calculate bounds
                ImageStream[] imageStreams = ImageBurner.burn(page, redactionAnnotations);

            }
        }
    }
}
