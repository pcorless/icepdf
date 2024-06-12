package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.callbacks.ContentStreamRedactorCallback;

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
            Logger.getLogger(RedactionContentBurner.class.toString());

    public static void burn(Page page,
                            List<RedactionAnnotation> redactionAnnotations) throws InterruptedException, IOException {
        Library library = page.getLibrary();
        ContentStreamRedactorCallback contentStreamRedactorCallback =
                new ContentStreamRedactorCallback(library, redactionAnnotations);
        page.init(contentStreamRedactorCallback);
        // wrap up, ends the last or only content stream being processed and store the bytes
        contentStreamRedactorCallback.endContentStream();
    }

}
