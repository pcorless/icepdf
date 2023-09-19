package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;

import java.util.List;

public class ImageBurner {
    public static ImageStream[] burn(Page page,
                                     List<RedactionAnnotation> redactionAnnotations) {
        Library library = page.getLibrary();
        Shapes shapes = page.getShapes();
//        DictionaryEntries xObjects = page.getResources().getXObjects();

        // xobject images intersection
        // work through xobjects as defined in page references
        // option 1: work though shapes stack
        // option 2: parse page contents looking for intersection
        // rewrite image with black pixels.

        return new ImageStream[]{};
    }
}
