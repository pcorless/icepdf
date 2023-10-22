package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;

import java.util.List;

/**
 * Future home of Image Burner code.
 */
public class InlineImageBurner {
    public static void burn(Page page,
                            List<RedactionAnnotation> redactionAnnotations) {

        // needs to be parse page contents, only way to catch the bounds
        // find intersection of inline images
        // update intersection with black bounds.

    }
}
