package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.util.updater.callbacks.ContentStreamRedactorCallback;

import java.util.List;
import java.util.logging.Logger;

public class TextBurner {

    private static final Logger logger =
            Logger.getLogger(TextBurner.class.toString());

    public static void burn(Page page,
                            List<RedactionAnnotation> redactionAnnotations) throws InterruptedException {

        ContentStreamRedactorCallback contentStreamRedactorCallback = new ContentStreamRedactorCallback();
        page.init(contentStreamRedactorCallback);

        // find intersection of text and redaction bounds (this will preserve text stream offsets in order)

        // apply char offset adjustment and remove glyphs and update x offset deltas
        //    should be able to unit test this like the original content parser

        // convert annotation to inline vector drawing

//        return contentStreams;
    }

}
