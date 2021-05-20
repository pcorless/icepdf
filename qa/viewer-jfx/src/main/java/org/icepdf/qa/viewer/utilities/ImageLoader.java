package org.icepdf.qa.viewer.utilities;

import javafx.scene.image.Image;

/**
 * Aid for loading image from image resources path.
 */
public class ImageLoader {

    public static Image loadImage(String filename) {
        return new Image(ImageLoader.class.getResourceAsStream(
                "/org/icepdf/qa/viewer/images/" + filename));
    }
}
