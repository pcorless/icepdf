package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;

import java.util.List;
import java.util.logging.Logger;

public class TextBurner {

    private static final Logger logger =
            Logger.getLogger(TextBurner.class.toString());

    public static void burn(Page page,
                            List<RedactionAnnotation> redactionAnnotations) {

//        if (contentStreams == null || contentStreams.length == 0) {
//            logger.fine("Skipping burnText, no content");
//            return contentStreams;
//        }
//        ContentParser contentParser;
//        for (String content: contentStreams) {
//
//        }
        // find intersection of text and redaction bounds (this will preserve text stream offsets in order)

        // apply char offset adjustment and remove glyphs and update x offset deltas
        //    should be able to unit test this like the original content parser

        // convert annotation to inline vector drawing

//        return contentStreams;
    }

}
